package com.bitsson.gensokyou.danmaku.program.network;

/** Frame-owned playout clock. Observations never change an already displayed pose.
 * One confirmed tick of buffering absorbs ordinary scheduling jitter; underflow freezes.
 * This is a presentation delay, not lag compensation for authoritative damage.
 */
public final class ProgramPresentationClock {
    private long authority=Long.MIN_VALUE, observedNanos, frameNanos;
    private double time=Double.NaN;
    private boolean paused;
    private long freezes, resets;
    public void observe(long tick,long now) {
        if (tick<=authority) return;
        authority=tick; observedNanos=now;
    }
    public double time(long now,boolean pause) {
        if (authority==Long.MIN_VALUE) return Double.NaN;
        if (Double.isNaN(time)) { time=Math.max(0,authority-1); frameNanos=now; paused=pause; return time; }
        double elapsed=Math.max(0,(now-frameNanos)/50_000_000.0);
        frameNanos=now;
        if (pause || paused) { paused=pause; return time; }
        // A stalled frame/long pause must not spend seconds playing obsolete attacks.
        // Explicit discontinuity is counted, never hidden in normal clock calibration.
        if (authority-time>4) { time=authority-1; resets++; return time; }
        double target=authority-1+Math.clamp((now-observedNanos)/50_000_000.0,0,1);
        double rate=Math.clamp(1+(target-time)*.1,.9,1.1);
        double next=Math.min(authority,time+elapsed*rate);
        if (next==time && elapsed>0) freezes++;
        time=next; return time;
    }
    public long authority() { return authority; }
    public long freezes() { return freezes; }
    public long resets() { return resets; }
}
