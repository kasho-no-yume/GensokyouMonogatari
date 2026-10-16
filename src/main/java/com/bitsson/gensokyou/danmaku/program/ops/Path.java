package com.bitsson.gensokyou.danmaku.program.ops;

import com.bitsson.gensokyou.danmaku.program.math.*;

/** Pure arbitrary-time extension point. Offsets start at zero unless explicitly documented. */
public interface Path {
    record State(Vec3 position, Vec3 velocity, boolean ended) {
        public State { java.util.Objects.requireNonNull(position); java.util.Objects.requireNonNull(velocity); }
        public State quantized() { return new State(position.quantized(), velocity.quantized(), ended); }
    }
    State at(double tick);
    /** Conservative radius about the origin during [0, untilTick]. */
    double radiusBound(double untilTick);
}
