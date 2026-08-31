package com.bitsson.gensokyou.ritual;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

public record RitualMatch(ResourceLocation patternId, int level, BlockPos anchorPos,
                          Map<Character, List<BlockPos>> keyedPositions, int ritualTier) {

    /** 取某字符键的全部世界坐标（如所有祭品台位置）。 */
    public List<BlockPos> positionsOf(char key) {
        return keyedPositions.getOrDefault(key, List.of());
    }
}
