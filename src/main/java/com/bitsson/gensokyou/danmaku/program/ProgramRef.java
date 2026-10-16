package com.bitsson.gensokyou.danmaku.program;

/** Typed handles are intentionally distinct records, even though all serialize as a node id. */
public final class ProgramRef {
    private ProgramRef() {}
    public record Frame(NodeId node) {}
    public record TargetSet(NodeId node) {}
    public record Group(NodeId node) {}
    public record Plan(NodeId node) {}
    public record Selection(NodeId node) {}
}
