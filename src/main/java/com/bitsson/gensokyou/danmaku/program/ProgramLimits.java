package com.bitsson.gensokyou.danmaku.program;

public record ProgramLimits(int maxNodes, int maxDepth, int maxBatchMembers, int maxDerivedMembers,
                            int maxStringBytes, int maxPayloadBytes) {
    public ProgramLimits {
        if (maxNodes <= 0 || maxDepth <= 0 || maxBatchMembers <= 0 || maxDerivedMembers <= 0
                || maxStringBytes <= 0 || maxPayloadBytes <= 0) throw new IllegalArgumentException("limits must be positive");
    }
    public static ProgramLimits defaults() { return new ProgramLimits(4096, 8, 512, 4096, 256, 65536); }
}
