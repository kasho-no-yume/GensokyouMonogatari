package com.bitsson.gensokyou.danmaku.program.gameplay;

import java.util.concurrent.ConcurrentHashMap;

/** Immutable completed authority boundaries shared across integrated-server/render threads. */
public final class IntegratedProgramTimeline {
    public record Boundary(long tick, long nanos) {
        public double time(long now, boolean paused) {
            return tick + (paused ? 0 : Math.clamp((now - nanos) / 50_000_000.0, 0, 1));
        }
    }
    private static final ConcurrentHashMap<String, Boundary> BOUNDARIES = new ConcurrentHashMap<>();
    private IntegratedProgramTimeline() {}
    public static void publish(String dimension, long tick, long nanos) {
        BOUNDARIES.compute(dimension,(key,previous)->{
            if (previous==null || tick<=previous.tick() || tick-previous.tick()>4)
                return new Boundary(tick,nanos);
            long expected=previous.nanos()+(tick-previous.tick())*50_000_000L;
            long error=nanos-expected;
            // Completion-time jitter must not reset the render phase every tick.
            // Slowly discipline the phase; large stalls/catch-up are explicit reanchors.
            if (Math.abs(error)>150_000_000L) return new Boundary(tick,nanos);
            long correction=Math.clamp(error/20,-500_000L,500_000L);
            return new Boundary(tick,expected+correction);
        });
    }
    public static Boundary boundary(String dimension) { return BOUNDARIES.get(dimension); }
    public static void remove(String dimension) { BOUNDARIES.remove(dimension); }
}
