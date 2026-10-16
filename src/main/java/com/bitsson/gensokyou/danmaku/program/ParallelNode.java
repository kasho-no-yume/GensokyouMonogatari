package com.bitsson.gensokyou.danmaku.program;

import java.util.List;
import java.util.Set;

public record ParallelNode(NodeId id, List<ProgramNode> children) implements ProgramNode {
    public ParallelNode { children = List.copyOf(children); }
    @Override public TickSpan duration() { return new TickSpan(children.stream().mapToLong(n -> n.duration().ticks()).max().orElse(0)); }
    @Override public Set<Channel> writes() { return children.stream().flatMap(n -> n.writes().stream()).collect(java.util.stream.Collectors.toUnmodifiableSet()); }
    @Override public String kind() { return "parallel"; }
}
