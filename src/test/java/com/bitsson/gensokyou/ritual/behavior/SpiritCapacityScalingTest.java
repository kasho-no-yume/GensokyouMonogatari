package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.ritual.SpiritCapacityScaling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpiritCapacityScalingTest {

    @Test
    void scaledMatchesPow4Loops() {
        assertEquals(10000L, SpiritCapacityScaling.scaled(10000L, 4L, 0));
        assertEquals(40000L, SpiritCapacityScaling.scaled(10000L, 4L, 1));
        assertEquals(160000L, SpiritCapacityScaling.scaled(10000L, 4L, 2));
        assertEquals(40000L * 20L * 20L, SpiritCapacityScaling.scaled(40000L, 20L, 2));
    }

    @Test
    void scaledSaturatingMatchesWujinzangLoop() {
        long base = 1024L;
        long mult = 4L;
        // 与旧 scaledWujinzang 逐位等值（不饱和区）
        long manual = base;
        for (int i = 0; i < 3; i++) {
            manual *= mult;
        }
        assertEquals(manual, SpiritCapacityScaling.scaledSaturating(base, mult, 3));
        // 饱和区钉 0 溢出
        assertEquals(Long.MAX_VALUE, SpiritCapacityScaling.scaledSaturating(Long.MAX_VALUE / 2L, 4L, 1));
    }
}
