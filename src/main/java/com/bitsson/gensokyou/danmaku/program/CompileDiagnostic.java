package com.bitsson.gensokyou.danmaku.program;

public record CompileDiagnostic(Severity severity, Code code, NodeId node, String message) {
    public enum Severity { ERROR, WARNING }
    public enum Code { INVALID_PROGRAM, DUPLICATE_NODE, UNKNOWN_OPERATION, UNKNOWN_SCHEMA, NODE_LIMIT, DEPTH_LIMIT,
        CHANNEL_CONFLICT, MISSING_COMPOSER, ZERO_DURATION_CYCLE, UNBOUNDED_DERIVATION,
        PAYLOAD_LIMIT, INVALID_ID, HASH_FAILURE }
}
