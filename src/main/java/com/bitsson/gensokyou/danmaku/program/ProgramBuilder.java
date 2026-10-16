package com.bitsson.gensokyou.danmaku.program;

import java.util.Map;

/** Deterministic lexical IDs. A fresh builder and the same construction order yield
 * the same identities. Resolved config is captured in the operation's immutable fields. */
public final class ProgramBuilder {
    private long next;
    public NodeId nextId() { return new NodeId(next++); }

    public <P> OperationNode operation(OperationType<P> type,P resolvedParameters,TickSpan duration) {
        Map<String,String> fields=Map.copyOf(type.encode(resolvedParameters));
        P detached=type.decode(fields);
        return new OperationNode(nextId(),type.id(),type.schemaVersion(),duration,
                type.contract(detached).writes(),fields);
    }
}
