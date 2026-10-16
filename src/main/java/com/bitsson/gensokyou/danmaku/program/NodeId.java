package com.bitsson.gensokyou.danmaku.program;

/** Stable author supplied node identity. It is part of the canonical program hash. */
public record NodeId(long value) implements Comparable<NodeId> {
    public NodeId {
        if (value < 0) throw new IllegalArgumentException("node id must be non-negative");
    }

    @Override
    public int compareTo(NodeId other) {
        return Long.compare(value, other.value);
    }
}
