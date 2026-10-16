package com.bitsson.gensokyou.danmaku.program;

public record TickSpan(long ticks) {
    public TickSpan {
        if (ticks < 0) throw new IllegalArgumentException("duration must be non-negative");
    }

    public static TickSpan of(long ticks) { return new TickSpan(ticks); }
}
