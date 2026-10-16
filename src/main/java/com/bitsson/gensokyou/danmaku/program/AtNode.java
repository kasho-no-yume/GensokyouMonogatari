package com.bitsson.gensokyou.danmaku.program;

import java.util.List;
import java.util.Set;

public record AtNode(NodeId id, TimeDomain domain, TickSpan offset, ProgramNode child) implements ProgramNode {
    public AtNode {
        java.util.Objects.requireNonNull(id);
        java.util.Objects.requireNonNull(domain);
        java.util.Objects.requireNonNull(offset);
        java.util.Objects.requireNonNull(child);
    }
    @Override public TickSpan duration() { return new TickSpan(Math.addExact(offset.ticks(), child.duration().ticks())); }
    @Override public Set<Channel> writes() { return child.writes(); }
    @Override public List<ProgramNode> children() { return List.of(child); }
    @Override public String kind() { return "at:" + domain.name().toLowerCase(); }
}
