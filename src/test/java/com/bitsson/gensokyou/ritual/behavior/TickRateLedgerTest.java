package com.bitsson.gensokyou.ritual.behavior;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 端点结算周期速率账本：周期内幂等、先到先得、定点零头跨周期守恒、周期折算正确。
 * 对应 spec ritual-power-attributes 的"端点每 tick 速率账本"要求（周期默认 20t = 1s）。
 */
class TickRateLedgerTest {

    @Test
    void sameTickIsIdempotent() {
        TickRateLedger led = new TickRateLedger();
        // period=1：1200/s → 每 tick 60 单位；同 tick 三笔各请求 100，合计不得超过 60
        assertEquals(60L, led.grant(100L, 1200L, 1, 100L));
        assertEquals(0L, led.grant(100L, 1200L, 1, 100L));
        assertEquals(0L, led.grant(100L, 1200L, 1, 100L));
    }

    @Test
    void firstComeFirstServedWithinTick() {
        TickRateLedger led = new TickRateLedger();
        // period=1、800/s → 每 tick 40；先到者吃满自身请求，后到者得剩余
        assertEquals(30L, led.grant(5L, 800L, 1, 30L));
        assertEquals(10L, led.grant(5L, 800L, 1, 30L));
        assertEquals(0L, led.grant(5L, 800L, 1, 30L));
    }

    @Test
    void periodScalesBudgetAndIsIdempotentWithinPeriod() {
        TickRateLedger led = new TickRateLedger();
        // period=20（1 秒）：1200/s → 1200 单位/周期
        assertEquals(1200L, led.grant(0L, 1200L, 20, Long.MAX_VALUE));
        // 同周期（gameTime 0..19）内再请求恒为 0
        assertEquals(0L, led.grant(5L, 1200L, 20, 1000L));
        assertEquals(0L, led.grant(19L, 1200L, 20, 1000L));
        // 下一周期（gameTime 20）恢复满额
        assertEquals(1200L, led.grant(20L, 1200L, 20, Long.MAX_VALUE));
    }

    @Test
    void subUnitRateAccumulatesWithoutTruncation() {
        TickRateLedger led = new TickRateLedger();
        long sum = 0L;
        for (long t = 0L; t < 20L; t++) {
            sum += led.grant(t, 1L, 1, Long.MAX_VALUE);
        }
        assertEquals(1L, sum);
    }

    @Test
    void oddRateConservesAcrossTwentyTicks() {
        TickRateLedger led = new TickRateLedger();
        long sum = 0L;
        for (long t = 0L; t < 20L; t++) {
            sum += led.grant(t, 3L, 1, Long.MAX_VALUE);
        }
        assertEquals(3L, sum);
    }

    @Test
    void integerBudgetDoesNotHoardAcrossTicks() {
        TickRateLedger led = new TickRateLedger();
        led.grant(0L, 1000L, 1, 0L); // t0 额度 50，全部未用
        // 下一 tick 全新 50，而非累加成 100
        assertEquals(50L, led.peek(1L, 1000L, 1));
    }

    @Test
    void directionsAreIndependent() {
        TickRateLedger in = new TickRateLedger();
        TickRateLedger out = new TickRateLedger();
        assertEquals(50L, in.grant(0L, 1000L, 1, 50L));
        assertEquals(50L, out.grant(0L, 1000L, 1, 50L));
    }

    @Test
    void clearDropsCarryAndBudget() {
        TickRateLedger led = new TickRateLedger();
        led.grant(0L, 1500L, 1, 0L); // 75 额度、零头结转
        led.clear();
        assertEquals(0L, led.peek(0L, 0L, 1));
    }
}
