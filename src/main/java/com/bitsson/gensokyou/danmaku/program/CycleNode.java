package com.bitsson.gensokyou.danmaku.program;

import java.util.List;

public record CycleNode(NodeId id, TickSpan length, TailPolicy tailPolicy, ProgramNode child) implements ProgramNode {
    public CycleNode {
        java.util.Objects.requireNonNull(tailPolicy);
        if (length.ticks() == 0) throw new IllegalArgumentException("cycle length must be positive");
    }
    @Override public TickSpan duration() { return length; }
    @Override public java.util.Set<Channel> writes() { return child.writes(); }
    @Override public List<ProgramNode> children() { return List.of(child); }
    @Override public String kind() { return "cycle"; }
}
