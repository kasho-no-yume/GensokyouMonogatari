package com.bitsson.gensokyou.spirit.attr;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 属性结算与受弹管线纯核（spec player-attribute-suite / danmaku-combat 全场景）。
 */
class AttributeMathTest {

    @Test
    void percentCapClampsSum() {
        // spec"百分比封顶"：base 0 + 贡献 0.7，cap 0.5 → 0.5
        assertEquals(0.5F, AttributeMath.finalFrom(0D, 0.7D, 0.5D), 1e-6);
    }

    @Test
    void noCapAllowsAnySum() {
        assertEquals(12.5F, AttributeMath.finalFrom(5D, 7.5D, -1D), 1e-6);
    }

    @Test
    void grazeRoll() {
        assertTrue(AttributeMath.grazeHit(0.3F, 0.2F));
        assertFalse(AttributeMath.grazeHit(0.3F, 0.3F));
        assertFalse(AttributeMath.grazeHit(0F, 0F));
    }

    @Test
    void wardMitigationHalvesPerPoint() {
        // 灵力护壁指数 P=1 → 受伤 ×0.5
        assertEquals(50F, AttributeMath.mitigate(100F, 1F, 1F), 1e-4);
        // P=7 → ×1/128
        assertEquals(100F / 128F, AttributeMath.mitigate(100F, 7F, 1F), 1e-4);
        // P=8.6 → ×2^-8.6，恒 > 0（不免疫）
        assertEquals((float) (100F * Math.pow(2D, -8.6D)), AttributeMath.mitigate(100F, 8.6F, 1F), 1e-5);
        assertTrue(AttributeMath.mitigate(100F, 8.6F, 1F) > 0F);
    }

    @Test
    void wardMultipliesShield() {
        // 100 ×2^-1 ×0.6 = 30
        assertEquals(30F, AttributeMath.mitigate(100F, 1F, 0.6F), 1e-4);
    }

    @Test
    void shieldImmunityShortCircuits() {
        // XI 级护盾（factor=0）短路：完全免疫 0 点
        assertEquals(0F, AttributeMath.mitigate(100F, 0F, 0F), 1e-6);
        assertEquals(0F, AttributeMath.mitigate(100F, 8.6F, 0F), 1e-6);
    }

    @Test
    void zeroWardOnlyAppliesShield() {
        // 无护壁（P=0）时退回护盾系数（既有场景回归：III 级盾、玩家零护壁 → 100→60）
        assertEquals(60F, AttributeMath.mitigate(100F, 0F, 0.6F), 1e-4);
    }

    @Test
    void wardDivisorDisplay() {
        assertEquals(2D, AttributeMath.wardDivisor(1F), 1e-6);
        assertEquals(128D, AttributeMath.wardDivisor(7F), 1e-6);
    }

    @Test
    void jumpStrengthDeltaMonotonicAndPlusThreeBlocks() {
        // 无馈赠 → 0；单调递增；+3 格 ≈ +0.39 强度（跳到约 4.1 格）
        assertEquals(0D, AttributeMath.jumpStrengthDelta(0F), 1e-9);
        assertTrue(AttributeMath.jumpStrengthDelta(1.5F) > AttributeMath.jumpStrengthDelta(0.5F));
        double plusThree = AttributeMath.jumpStrengthDelta(3F);
        assertTrue(plusThree > 0.36D && plusThree < 0.42D,
                "jump +3 格的强度增量应约 0.39, got " + plusThree);
    }

    @Test
    void cdrFoldsCooldown() {
        // spec skill-slots"冷却缩减生效"：20s=400t，CDR 30% → 280t
        assertEquals(280L, AttributeMath.effectiveCooldownTicks(400L, 0.3F));
        assertEquals(1L, AttributeMath.effectiveCooldownTicks(1L, 1F));
    }

    @Test
    void durationScaling() {
        // spec"增益延长"：20s=400t，+25% → 500t；韧性 30%：400t → 280t
        assertEquals(500, AttributeMath.scaledDuration(400, 1.25F));
        assertEquals(280, AttributeMath.scaledDuration(400, 0.7F));
        assertEquals(400, AttributeMath.scaledDuration(400, 1F));
    }

    @Test
    void leechQuotaBinds() {
        assertEquals(5F, AttributeMath.leechGain(50F, 0.1F, 10F));
        assertEquals(3F, AttributeMath.leechGain(50F, 0.1F, 3F));
        assertEquals(0F, AttributeMath.leechGain(50F, 0F, 10F));
        assertEquals(0F, AttributeMath.leechGain(-1F, 0.1F, 10F));
    }
}
