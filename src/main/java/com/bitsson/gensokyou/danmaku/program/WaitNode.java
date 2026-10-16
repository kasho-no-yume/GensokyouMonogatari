package com.bitsson.gensokyou.danmaku.program;

import java.util.List;
import java.util.Set;

public record WaitNode(NodeId id, TickSpan duration) implements ProgramNode {
    @Override public Set<Channel> writes() { return Set.of(); }
    @Override public List<ProgramNode> children() { return List.of(); }
    @Override public String kind() { return "wait"; }
}
