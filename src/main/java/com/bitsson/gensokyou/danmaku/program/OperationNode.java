package com.bitsson.gensokyou.danmaku.program;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record OperationNode(NodeId id, String operationId, int schemaVersion,
                            TickSpan duration, Set<Channel> writes,
                            Map<String, String> parameters) implements ProgramNode {
    public OperationNode {
        java.util.Objects.requireNonNull(id);
        java.util.Objects.requireNonNull(duration);
        if (operationId == null || operationId.isBlank()) throw new IllegalArgumentException("operation id is required");
        if (schemaVersion < 1) throw new IllegalArgumentException("schema version must be positive");
        writes = Set.copyOf(writes);
        parameters = Map.copyOf(parameters);
    }
    @Override public List<ProgramNode> children() { return List.of(); }
    @Override public String kind() { return "operation"; }
}
