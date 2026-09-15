package com.bitsson.gensokyou.ritual;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 火柱发射点布局纯函数：确定性、阶级放大、**从内到外铺开**（关键回归：
 * 台位数据缺失时也必须铺满结构半径，而不是全挤在中心）。对应 design D3。
 */
class RitualFxLayoutTest {

    private static final BlockPos CORE = new BlockPos(0, 64, 0);
    private static final long[] PEDS = {
            new BlockPos(3, 64, 0).asLong(),
            new BlockPos(-3, 64, 1).asLong(),
            new BlockPos(0, 64, -4).asLong(),
    };

    private static double maxRadius(List<RitualFxLayout.Pillar> pillars) {
        double max = 0.0D;
        for (RitualFxLayout.Pillar p : pillars) {
            max = Math.max(max, Math.hypot(p.x() - 0.5D, p.z() - 0.5D));
        }
        return max;
    }

    @Test
    void layoutIsDeterministic() {
        List<RitualFxLayout.Pillar> a = RitualFxLayout.flamePillars(CORE, PEDS, 3, 2, 12, 6);
        List<RitualFxLayout.Pillar> b = RitualFxLayout.flamePillars(CORE, PEDS, 3, 2, 12, 6);
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).x(), b.get(i).x(), 1e-9);
            assertEquals(a.get(i).y(), b.get(i).y(), 1e-9);
            assertEquals(a.get(i).z(), b.get(i).z(), 1e-9);
            assertEquals(a.get(i).seed(), b.get(i).seed());
        }
    }

    @Test
    void firstPillarIsCoreCenter() {
        RitualFxLayout.Pillar core = RitualFxLayout.flamePillars(CORE, PEDS, 5, 2, 12, 6).get(0);
        assertEquals(0.5D, core.x(), 1e-9);
        assertEquals(0.5D, core.z(), 1e-9);
        assertEquals(CORE.asLong(), core.seed());
    }

    @Test
    void countIncreasesWithTier() {
        int t0 = RitualFxLayout.flamePillars(CORE, PEDS, 0, 2, 12, 6).size();
        int t4 = RitualFxLayout.flamePillars(CORE, PEDS, 4, 2, 12, 6).size();
        assertTrue(t4 > t0, "tier4=" + t4 + " should exceed tier0=" + t0);
    }

    @Test
    void spreadsOutToStructureRadius() {
        List<RitualFxLayout.Pillar> pillars = RitualFxLayout.flamePillars(CORE, PEDS, 3, 2, 12, 8);
        assertTrue(maxRadius(pillars) > 8.0D * 0.7D,
                "max radius " + maxRadius(pillars) + " should reach the structure edge");
    }

    /** 回归：台位数据缺失（linkPos 为空）时不得退化成"全在中心"。 */
    @Test
    void emptyPedestalsStillSpreads() {
        List<RitualFxLayout.Pillar> pillars =
                RitualFxLayout.flamePillars(CORE, new long[0], 3, 2, 12, 8);
        assertTrue(maxRadius(pillars) > 8.0D * 0.7D,
                "max radius " + maxRadius(pillars) + " must not collapse to the centre");
    }

    /** 结构半径未知（0）时回落到最小半径 2，柱仍围绕核心可见但不会失控。 */
    @Test
    void unknownStructureRadiusFallsBackToMinimum() {
        List<RitualFxLayout.Pillar> pillars =
                RitualFxLayout.flamePillars(CORE, new long[0], 3, 2, 12, 0);
        assertTrue(maxRadius(pillars) <= 2.5D, "max radius " + maxRadius(pillars));
    }
}
