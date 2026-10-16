package com.bitsson.gensokyou.danmaku.program;

import java.util.List;
import java.util.Set;

/** Ordered, explicit composition of one channel in one scope. */
public record ComposeNode(NodeId id, Channel channel, Mode mode, List<ProgramNode> children)
        implements ProgramNode {
    public enum Mode { COMPOSE_TRANSFORM, ADD_OFFSET, MULTIPLY_SCALE }

    public ComposeNode {
        java.util.Objects.requireNonNull(channel);
        java.util.Objects.requireNonNull(mode);
        children = List.copyOf(children);
        if (children.isEmpty()) throw new IllegalArgumentException("composition needs children");
        if (mode == Mode.ADD_OFFSET && channel != Channel.POSITION
                || mode == Mode.MULTIPLY_SCALE && channel != Channel.SCALE) {
            throw new IllegalArgumentException("composition mode/channel mismatch");
        }
        if (children.stream().anyMatch(n -> !n.writes().equals(Set.of(channel)))) {
            throw new IllegalArgumentException("composition children must write exactly its channel");
        }
    }

    @Override public TickSpan duration() {
        return new TickSpan(children.stream().mapToLong(n -> n.duration().ticks()).max().orElse(0));
    }
    @Override public Set<Channel> writes() { return Set.of(channel); }
    @Override public String kind() { return "compose"; }
}
