package com.bitsson.gensokyou.danmaku.program;

/** Typed author-facing absolute times. Duration/offset remain separate TickSpan values. */
public sealed interface TimeSpec permits TimeSpec.CardTime, TimeSpec.CycleTime, TimeSpec.GroupTime, TimeSpec.MemberAge {
    TickSpan value();
    TimeDomain domain();
    default AtNode at(NodeId id,ProgramNode child) { return new AtNode(id,domain(),value(),child); }
    record CardTime(TickSpan value) implements TimeSpec { public TimeDomain domain() { return TimeDomain.CARD; } }
    record CycleTime(TickSpan value) implements TimeSpec { public TimeDomain domain() { return TimeDomain.CYCLE; } }
    record GroupTime(TickSpan value) implements TimeSpec { public TimeDomain domain() { return TimeDomain.GROUP; } }
    record MemberAge(TickSpan value) implements TimeSpec { public TimeDomain domain() { return TimeDomain.MEMBER_AGE; } }
    static CardTime card(long ticks) { return new CardTime(new TickSpan(ticks)); }
    static CycleTime cycle(long ticks) { return new CycleTime(new TickSpan(ticks)); }
    static GroupTime group(long ticks) { return new GroupTime(new TickSpan(ticks)); }
    static MemberAge member(long ticks) { return new MemberAge(new TickSpan(ticks)); }
}
