package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.ritual.FixedPointAccumulator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FixedPointAccumulatorTest {

    @Test
    void accumulateKeepsSubFixedRemainder() {
        FixedPointAccumulator acc = new FixedPointAccumulator();
        // 每 tick +333（×1000 口径），第 3 个 tick 才攒满 1 个整数份额
        assertEquals(0L, acc.accumulate(333L, 1000L));
        assertEquals(333L, acc.carry());
        assertEquals(0L, acc.accumulate(333L, 1000L));
        assertEquals(666L, acc.carry());
        assertEquals(0L, acc.accumulate(333L, 1000L));
        assertEquals(999L, acc.carry());
        assertEquals(1L, acc.accumulate(1L, 1000L));
        assertEquals(0L, acc.carry());
    }

    @Test
    void accumulateNeverLosesTotal() {
        FixedPointAccumulator acc = new FixedPointAccumulator();
        long total = 0L;
        for (int i = 0; i < 100; i++) {
            total += acc.accumulate(137L, 1000L);
        }
        assertEquals(13L, total, "100×137=13700，定点整除应恰好 13，零头 700");
        assertEquals(700L, acc.carry());
    }

    @Test
    void stepMatchesHandWrittenCarryFold() {
        long[] step = FixedPointAccumulator.step(250L, 800L, 1000L);
        assertEquals(1L, step[0]);
        assertEquals(50L, step[1]);
    }

    @Test
    void setCarryAndClearRoundTrip() {
        FixedPointAccumulator acc = new FixedPointAccumulator();
        acc.setCarry(42L);
        assertEquals(42L, acc.carry());
        acc.clear();
        assertEquals(0L, acc.carry());
    }
}
