package com.bitsson.gensokyou.danmaku.program.network;

import com.bitsson.gensokyou.danmaku.motion.DanmakuServerClock;

/** One per dimension. Loss of trust freezes the last rendered authority time. */
public final class ProgramClock {
    private final DanmakuServerClock estimator;
    private double last;
    private boolean trusted;
    public ProgramClock(int staleTicks) { estimator=new DanmakuServerClock(new DanmakuServerClock.Params(.8,1.25,4,3,staleTicks,40,16)); }
    public void observe(long serverTick,int localTick) {
        estimator.observe(serverTick,localTick);
        trusted=estimator.availability()==DanmakuServerClock.Availability.TRACKING;
        if (trusted) last=StrictMath.max(last,estimate(localTick));
    }
    private double estimate(double localTick) { return estimator.anchorServerTime()+(localTick-estimator.anchorClientTick())/estimator.rate(); }
    public double time(double localTick) {
        estimator.noteTick((int)StrictMath.floor(localTick));
        trusted=estimator.availability()==DanmakuServerClock.Availability.TRACKING;
        if (trusted) last=StrictMath.max(last,estimate(localTick)); return last;
    }
    public boolean trusted() { return trusted; }
}
