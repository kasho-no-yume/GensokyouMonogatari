package com.bitsson.gensokyou.danmaku.program;

import java.util.List;
import java.util.Set;

public sealed interface ProgramNode permits OperationNode, AtNode, SequenceNode, ParallelNode,
        WaitNode, RepeatNode, CycleNode, EveryNode, ComposeNode, OffsetNode {
    NodeId id();
    TickSpan duration();
    Set<Channel> writes();
    List<ProgramNode> children();
    String kind();
}
