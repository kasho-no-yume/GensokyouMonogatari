package com.bitsson.gensokyou.danmaku.program.network;

import com.bitsson.gensokyou.danmaku.program.math.Vec3;

/** Presentation-only correction; never fed back into trajectory or collision state. */
public final class VisualOffset {
    private Vec3 offset=Vec3.ZERO;
    private double start;
    private long snaps;
    public void correct(Vec3 oldPosition,Vec3 newPosition,double time,boolean birthOrDeath) {
        Vec3 delta=oldPosition.subtract(newPosition);
        if (birthOrDeath || delta.length()>.25) { offset=Vec3.ZERO; snaps++; }
        else { offset=delta; start=time; }
    }
    public Vec3 at(double time) { return offset.multiply(StrictMath.max(0,StrictMath.min(1,1-(time-start)/2))); }
    public long snaps() { return snaps; }
}
