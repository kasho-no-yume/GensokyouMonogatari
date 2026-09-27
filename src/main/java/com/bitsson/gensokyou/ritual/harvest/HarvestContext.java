package com.bitsson.gensokyou.ritual.harvest;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

public record HarvestContext(
        ServerLevel level,
        BlockPos corePos,
        BlockPos pedestalPos,
        int ritualLevel,
        RandomSource random) {
}
