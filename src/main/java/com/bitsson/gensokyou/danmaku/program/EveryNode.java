package com.bitsson.gensokyou.danmaku.program;

import java.util.List;

public record EveryNode(NodeId id, TickSpan period, TickSpan phase, TickSpan window, ProgramNode child) implements ProgramNode {
    public EveryNode {
        if (period.ticks() == 0) throw new IllegalArgumentException("every period must be positive");
        if (phase.ticks() >= period.ticks()) throw new IllegalArgumentException("every phase must be inside period");
    }
    @Override public TickSpan duration() { return window; }
    @Override public java.util.Set<Channel> writes() { return child.writes(); }
    @Override public List<ProgramNode> children() { return List.of(child); }
    @Override public String kind() { return "every"; }
}
