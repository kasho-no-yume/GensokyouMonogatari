package com.bitsson.gensokyou.spirit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 三段式抽灵计划内核（世界无关）：槽内核→自身储灵→周围兜底的优先级、跨来源足额拼接、
 * 单段不超其存量。对应 spec core-socket-powering 的"三段式扣费来源"全有全无前置分配。
 */
class SpiritDrainPlanTest {

    @Test
    void prefersBatteryThenSelfThenSurround() {
        long[] plan = SpiritPowerHelper.drainPlan(5_000L, 5_000L, 5_000L, 12_000L);
        assertArrayEquals(new long[]{5_000L, 5_000L, 2_000L}, plan);
    }

    @Test
    void batteryCoversAloneWhenEnough() {
        long[] plan = SpiritPowerHelper.drainPlan(10_000L, 1_000L, 1_000L, 3_000L);
        assertArrayEquals(new long[]{3_000L, 0L, 0L}, plan);
    }

    @Test
    void spansSelfWhenBatteryShort() {
        long[] plan = SpiritPowerHelper.drainPlan(400L, 600L, 0L, 1_000L);
        assertArrayEquals(new long[]{400L, 600L, 0L}, plan);
    }

    @Test
    void clampsToAvailableWhenTotalShort() {
        long[] plan = SpiritPowerHelper.drainPlan(100L, 100L, 100L, 1_000L);
        // 合计仅 300，抽满来源但不足 want（原子性由调用方 canCover 事先保证）
        assertEquals(300L, plan[0] + plan[1] + plan[2]);
    }

    @Test
    void zeroWantYieldsEmptyPlan() {
        assertArrayEquals(new long[]{0L, 0L, 0L}, SpiritPowerHelper.drainPlan(9, 9, 9, 0));
        assertArrayEquals(new long[]{0L, 0L, 0L}, SpiritPowerHelper.drainPlan(9, 9, 9, -1));
    }
}
