package com.bitsson.gensokyou.ritual;

import com.bitsson.gensokyou.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

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
                VerifyResult result = verifyTier(level, anchorPos, pattern, tier, rotation);
                if (result != null) {
                    return Optional.of(new RitualMatch(pattern.id(), tier.level(),
                            anchorPos.immutable(), result.keyed(), result.maxTier()));
                }
            }
        }
        return Optional.empty();
    }

    /** 校验结果：键位坐标表（规范序）+ 结构内受阶方块（仪式石）的最高品阶。 */
    private record VerifyResult(Map<Character, List<BlockPos>> keyed, int maxTier) {
    }

    /** 校验一个层级在给定旋转下是否成立；成功返回校验结果，失败返回 null。 */
    private static VerifyResult verifyTier(Level level, BlockPos anchorWorld,
                                           RitualPattern pattern,
                                           RitualPattern.LevelSlice tier,
                                           int rotation) {
        Map<Character, List<BlockPos>> keyed = new HashMap<>();
        int maxTier = -1;
        for (RitualPattern.BlockEntry block : tier.blocks()) {
            long offset = orient(block.x(), block.z(), rotation);
            BlockPos target = anchorWorld.offset((int) (offset >> 32), block.y(), (int) offset);
            RitualPattern.Predicate predicate = pattern.palette().get(block.key());
            BlockState state = level.getBlockState(target);
            if (predicate == null || !predicate.test(state)) {
                return null;
            }
            if (block.orientation() != null
                    && !Orientation.matches(state, Orientation.rotateBy(block.orientation(), rotation))) {
                return null;
            }
            maxTier = Math.max(maxTier, ModBlocks.tierOf(state.getBlock()));
            keyed.computeIfAbsent(block.key(), k -> new ArrayList<>()).add(target);
        }
        return new VerifyResult(keyed, Math.max(maxTier, 0));
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
