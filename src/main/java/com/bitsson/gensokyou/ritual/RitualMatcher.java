package com.bitsson.gensokyou.ritual;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 仪式结构匹配器 v3：遍历展开后的稀疏偏移表，仅枚举 4 种旋转
 * （中心对称约定下镜像冗余）。条目已按 (y,z,x) 规范序排序，
 * 键位坐标收集顺序即祭品台 slot 寻址的规范序，且旋转不变。
 */
public final class RitualMatcher {

    private RitualMatcher() {
    }

    public static Optional<RitualMatch> matchAt(Level level, BlockPos anchorPos) {
        for (RitualPattern pattern : RitualPatternLoader.all()) {
            Optional<RitualMatch> match = tryPattern(level, anchorPos, pattern);
            if (match.isPresent()) {
                return match;
            }
        }
        return Optional.empty();
    }

    private static Optional<RitualMatch> tryPattern(Level level, BlockPos anchorPos,
                                                    RitualPattern pattern) {
        List<RitualPattern.LevelSlice> tiers = pattern.levels();
        for (int tierIndex = tiers.size() - 1; tierIndex >= 0; tierIndex--) {
            RitualPattern.LevelSlice tier = tiers.get(tierIndex);
            for (int rotation = 0; rotation < 4; rotation++) {
                Map<Character, List<BlockPos>> keyed = verifyTier(level, anchorPos, pattern, tier, rotation);
                if (keyed != null) {
                    return Optional.of(new RitualMatch(pattern.id(), tier.level(),
                            anchorPos.immutable(), keyed));
                }
            }
        }
        return Optional.empty();
    }

    /** 校验一个层级在给定旋转下是否成立；成功返回键位坐标表（规范序），失败返回 null。 */
    private static Map<Character, List<BlockPos>> verifyTier(Level level, BlockPos anchorWorld,
                                                             RitualPattern pattern,
                                                             RitualPattern.LevelSlice tier,
                                                             int rotation) {
        Map<Character, List<BlockPos>> keyed = new HashMap<>();
        for (RitualPattern.BlockEntry block : tier.blocks()) {
            long offset = orient(block.x(), block.z(), rotation);
            BlockPos target = anchorWorld.offset((int) (offset >> 32), block.y(), (int) offset);
            RitualPattern.Predicate predicate = pattern.palette().get(block.key());
            if (predicate == null || !predicate.test(level.getBlockState(target))) {
                return null;
            }
            keyed.computeIfAbsent(block.key(), k -> new ArrayList<>()).add(target);
        }
        return keyed;
    }

    /** 偏移的旋转变换，返回打包的 (dx << 32 | dz)。 */
    private static long orient(int x, int z, int rotation) {
        return switch (rotation % 4) {
            case 0 -> pack(x, z);
            case 1 -> pack(-z, x);
            case 2 -> pack(-x, -z);
            default -> pack(z, -x);
        };
    }

    private static long pack(int dx, int dz) {
        return ((long) dx << 32) | (dz & 0xFFFFFFFFL);
    }
}
