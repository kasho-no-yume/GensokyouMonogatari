package com.bitsson.gensokyou.danmaku.program;

import java.util.List;

public record RepeatNode(NodeId id, int count, TickSpan period, ProgramNode child) implements ProgramNode {
    public RepeatNode {
        if (count < 0) throw new IllegalArgumentException("repeat count must be non-negative");
        if (count > 0 && period.ticks() == 0) throw new IllegalArgumentException("repeat period must be positive");
    }
    @Override public TickSpan duration() { return new TickSpan(count == 0 ? 0 : Math.addExact(Math.multiplyExact(count - 1L, period.ticks()), child.duration().ticks())); }
    @Override public java.util.Set<Channel> writes() { return child.writes(); }
    @Override public List<ProgramNode> children() { return List.of(child); }
    @Override public String kind() { return "repeat"; }
}
