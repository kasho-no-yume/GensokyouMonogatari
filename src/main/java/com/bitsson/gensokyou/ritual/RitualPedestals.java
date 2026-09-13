package com.bitsson.gensokyou.ritual;

import com.bitsson.gensokyou.Gensokyou;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 结构内祭品台枚举：pattern 的台面 key 不保证统一（加具土命 P，八方归元 a/b/c/d），
 * 以 palette 绑定 #gensokyou:ritual_pedestals 的全部 key 反查为准。
 */
public final class RitualPedestals {

    private static final ResourceLocation PEDESTAL_TAG = Gensokyou.id("ritual_pedestals");

    private RitualPedestals() {
    }

    /** 全部台位（规范序 y,z,x；跨 key 天然互斥，无重复）。 */
    public static List<BlockPos> positions(RitualMatch match) {
        return RitualPatternLoader.byId(match.patternId())
                .map(pattern -> collect(pattern, match))
                .orElse(List.of());
    }

    private static List<BlockPos> collect(RitualPattern pattern, RitualMatch match) {
        List<BlockPos> out = new ArrayList<>();
        for (var entry : pattern.palette().entrySet()) {
            RitualPattern.Predicate predicate = entry.getValue();
            if (predicate.kind() == RitualPattern.Kind.TAG && predicate.tag() != null
                    && predicate.tag().location().equals(PEDESTAL_TAG)) {
                out.addAll(match.positionsOf(entry.getKey()));
            }
        }
        out.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY)
                .thenComparingInt(BlockPos::getZ)
                .thenComparingInt(BlockPos::getX));
        return out;
    }
}
