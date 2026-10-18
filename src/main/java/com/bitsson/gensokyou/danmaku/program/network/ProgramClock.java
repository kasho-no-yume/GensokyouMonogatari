package com.bitsson.gensokyou.danmaku.program.network;

import com.bitsson.gensokyou.danmaku.motion.DanmakuServerClock;

/** Per-dimension display clock. Recovery freezes the last pose instead of blanking attacks. */
public final class ProgramClock {
    private final DanmakuServerClock estimator;
    private double last;
    private boolean trusted;
    private double local=Double.NaN;
    private static final double MAX_SMOOTH_ERROR=2;
    private boolean recovering;
    private int recoveryLocal;
    private long recoveries;
    private boolean holdover;
    private boolean renderGrace;
    private int renderGraceLocal;
    private double lastRate=1;
    private String reason="none";
    private double recoveryError;
    private long disruptions;
    private long mappingResets;
    private DanmakuServerClock.Outcome outcome;
    private int lastObserveLocal=Integer.MIN_VALUE;
    private long firstServerAtLocal=Long.MIN_VALUE;
    private long newestServer=Long.MIN_VALUE;
    public ProgramClock(int staleTicks) { estimator=new DanmakuServerClock(new DanmakuServerClock.Params(.8,1.25,4,3,staleTicks,40,16)); }
    public void observe(long serverTick,int localTick) {
        // All owners share this dimension clock. An older owner's checkpoint or a
        // duplicate digest is a fact update, not a new observation of time.
        if (serverTick<=newestServer) return;
        newestServer=serverTick;
        if (!Double.isNaN(local)) time(localTick);
        // Rendering can already have advanced past this integer client tick. Compare
        // observations at that displayed boundary, not a sample up to one tick older.
        double sampleAtDisplay=serverTick+(Double.isNaN(local)?0:Math.max(0,local-localTick)*lastRate);
        if (localTick!=lastObserveLocal) firstServerAtLocal=serverTick;
        boolean queued=firstServerAtLocal!=Long.MIN_VALUE && localTick==lastObserveLocal
                && serverTick-firstServerAtLocal>2;
        lastObserveLocal=localTick;
        if (recovering) {
            // Queued packets processed in one client tick are not independent clock samples.
            if (localTick<=recoveryLocal) {
                if (Math.abs(sampleAtDisplay-last)>MAX_SMOOTH_ERROR) holdover=false;
                if (Math.abs(sampleAtDisplay-last)>4 || queued) renderGrace=false;
                return;
            }
            boolean continuous=holdover && localTick-recoveryLocal<=2 && Math.abs(sampleAtDisplay-last)<=MAX_SMOOTH_ERROR;
            estimator.clear(); estimator.observe(serverTick,localTick);
            if (!continuous) last=serverTick+Math.max(0,local-localTick);
            local=Math.max(local,localTick); trusted=true; recovering=false; holdover=false; renderGrace=false; lastRate=1; recoveries++;
            return;
        }
        if (queued) {
            // A burst spanning several authority ticks can still end at the
            // correct display boundary. Keep the existing bounded small-error
            // holdover; later packets in this same burst revoke it above if the
            // latest sample moves beyond the two-tick error budget.
            beginRecovery("queued-samples",serverTick,localTick,true);
            return;
        }
        outcome=estimator.observe(serverTick,localTick);
        trusted=estimator.availability()==DanmakuServerClock.Availability.TRACKING;
        if (trusted && Double.isNaN(local)) { last=estimate(localTick); local=localTick; }
        else if (!trusted || Math.abs(estimate(local)-last)>MAX_SMOOTH_ERROR) {
            if (trusted && Math.abs(sampleAtDisplay-last)<=MAX_SMOOTH_ERROR) {
                // A changed fit is not evidence that the displayed attack is wrong.
                // Sparse heartbeats can move the fit while the actual sample remains close.
                estimator.clear(); estimator.observe(serverTick,localTick);
                lastRate=1; mappingResets++;
            } else beginRecovery(!trusted?"estimator-"+outcome:"display-error",serverTick,localTick,true);
        }
    }
    private void beginRecovery(String cause,long serverTick,int localTick,boolean allowHoldover) {
        reason=cause; recoveryError=serverTick+Math.max(0,local-localTick)*lastRate-last; disruptions++;
        // Diagnostics retain the modest-recovery grace marker. Actual display now
        // preserves its last pose even after grace; only holdover advances time.
        renderGrace=allowHoldover && cause.equals("display-error") && Math.abs(recoveryError)<=4;
        renderGraceLocal=localTick;
        holdover=allowHoldover && Math.abs(recoveryError)<=MAX_SMOOTH_ERROR;
        recovering=true; trusted=false; recoveryLocal=localTick;
    }
    private double estimate(double localTick) { return estimator.anchorServerTime()+(localTick-estimator.anchorClientTick())/estimator.rate(); }
    public double time(double localTick) {
        estimator.noteTick((int)StrictMath.floor(localTick));
        boolean tracking=estimator.availability()==DanmakuServerClock.Availability.TRACKING;
        trusted=!recovering && tracking;
        if (Double.isNaN(local)) return last;
        double elapsed=StrictMath.max(0,localTick-local);
        if (trusted && elapsed>0) {
            double target=estimate(localTick);
            double rate=1/estimator.rate();
            double predicted=last+elapsed*rate;
            // Packet jitter changes the target mapping, never the already displayed position.
            double correction=StrictMath.max(-elapsed*.05,StrictMath.min(elapsed*.05,target-predicted));
            last+=elapsed*rate+correction;
            lastRate=rate;
        } else if (recovering && holdover && elapsed>0
                && localTick-recoveryLocal<=2) {
            // At most 100 ms on the previous mapping; never a long invisible/frozen attack.
            last+=elapsed*lastRate;
        }
        local=StrictMath.max(local,localTick);
        return last;
    }
    public boolean trusted() { return trusted; }
    public boolean renderable() {
        // Clock confidence controls advancement, not existence. Once initialized,
        // keep the last pose while uncertain; the next independent observation
        // may correct it in either direction. End/Release still clear replica state.
        return !Double.isNaN(local);
    }
    public long disruptions() { return disruptions; }
    public String diagnostic() {
        return "reason="+reason+" outcome="+outcome+" recoveryError="+recoveryError
                +" holdover="+holdover+" frozenGrace="+renderGrace+" disruptions="+disruptions+" recoveries="+recoveries+" mappingResets="+mappingResets;
    }
    public long recoveries() { return recoveries; }
    public double error(double localTick) { return estimate(localTick)-time(localTick); }
}
