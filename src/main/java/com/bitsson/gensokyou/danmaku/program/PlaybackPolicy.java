package com.bitsson.gensokyou.danmaku.program;

public sealed interface PlaybackPolicy permits PlaybackPolicy.Once, PlaybackPolicy.Loop, PlaybackPolicy.Stream {
    record Once(TailPolicy tailPolicy) implements PlaybackPolicy {
        public Once { java.util.Objects.requireNonNull(tailPolicy); }
    }
    record Loop(TickSpan length, TailPolicy tailPolicy) implements PlaybackPolicy {
        public Loop {
            java.util.Objects.requireNonNull(tailPolicy);
            if (length.ticks() == 0) throw new IllegalArgumentException("loop length must be positive");
        }
    }
    /** A stream is bounded per scheduling window; the world runtime supplies the window. */
    record Stream(TickSpan period, TickSpan phase) implements PlaybackPolicy {
        public Stream {
            if (period.ticks() == 0) throw new IllegalArgumentException("stream period must be positive");
            if (phase.ticks() >= period.ticks()) throw new IllegalArgumentException("stream phase must be inside period");
        }
    }
}
