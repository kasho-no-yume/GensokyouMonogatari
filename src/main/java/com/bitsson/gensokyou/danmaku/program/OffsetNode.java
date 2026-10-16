package com.bitsson.gensokyou.danmaku.program;

import java.util.List;
import java.util.Set;

/** Local scheduling offset, rebased for each repeat occurrence. */
public record OffsetNode(NodeId id, TickSpan offset, ProgramNode child) implements ProgramNode {
    public OffsetNode {
        java.util.Objects.requireNonNull(id);
        java.util.Objects.requireNonNull(offset);
        java.util.Objects.requireNonNull(child);
    }
    @Override public TickSpan duration() { return new TickSpan(Math.addExact(offset.ticks(),child.duration().ticks())); }
    @Override public Set<Channel> writes() { return child.writes(); }
    @Override public List<ProgramNode> children() { return List.of(child); }
    @Override public String kind() { return "offset"; }
}
