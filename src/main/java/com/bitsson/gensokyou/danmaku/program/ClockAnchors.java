package com.bitsson.gensokyou.danmaku.program;

/** Immutable server-tick anchors. Absent group/member clocks are not inferred. */
public record ClockAnchors(long card, long cycle, Long group, Long member) {
    public long origin(TimeDomain domain) {
        return switch (domain) {
            case CARD -> card;
            case CYCLE -> cycle;
            case GROUP -> required(group, domain);
            case MEMBER_AGE -> required(member, domain);
        };
    }

    public ClockAnchors cycleAt(long tick) { return new ClockAnchors(card, tick, group, member); }

    private static long required(Long value, TimeDomain domain) {
        if (value == null) throw new IllegalArgumentException("missing clock anchor: " + domain);
        return value;
    }
}
