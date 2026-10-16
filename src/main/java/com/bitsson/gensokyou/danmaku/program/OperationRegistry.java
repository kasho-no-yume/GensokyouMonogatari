package com.bitsson.gensokyou.danmaku.program;

import java.util.LinkedHashMap;
import java.util.Map;

public final class OperationRegistry {
    private final Map<String, OperationType<?>> types = new LinkedHashMap<>();
    private boolean frozen;

    public void register(OperationType<?> type) {
        if (frozen) throw new IllegalStateException("registry is frozen");
        if (!type.id().matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || type.schemaVersion() < 1) {
            throw new IllegalArgumentException("invalid operation identity");
        }
        if (types.putIfAbsent(type.id(), type) != null) throw new IllegalArgumentException("duplicate operation " + type.id());
    }

    public OperationRegistry freeze() { frozen = true; return this; }

    public OperationType.OperationContract validate(OperationNode node) {
        if (!frozen) throw new IllegalStateException("freeze registry before compiling");
        OperationType<?> type = types.get(node.operationId());
        if (type == null) throw new IllegalArgumentException("unknown operation " + node.operationId());
        if (type.schemaVersion() != node.schemaVersion()) throw new IllegalArgumentException("unsupported schema " + node.operationId());
        return validateTyped(type, node);
    }

    private static <P> OperationType.OperationContract validateTyped(OperationType<P> type, OperationNode node) {
        P value = type.decode(node.parameters());
        if (!type.encode(value).equals(node.parameters())) throw new IllegalArgumentException("noncanonical parameters " + node.id());
        var contract = type.contract(value);
        if (!contract.writes().equals(node.writes())) throw new IllegalArgumentException("incorrect channel declaration " + node.id());
        return contract;
    }
}
