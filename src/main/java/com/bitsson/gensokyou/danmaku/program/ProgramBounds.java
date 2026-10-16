package com.bitsson.gensokyou.danmaku.program;

public record ProgramBounds(long nodes, long peakMembersPerBatch, long peakDerivedMembers,
                            long maxLifetimeTicks, long maxDepth, long windowTicks) {
    public static ProgramBounds empty() { return new ProgramBounds(0, 0, 0, 0, 0, 0); }
}
