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
    void reduceMultipliesShield() {
        // spec"减免与护盾乘算"：100 ×(1-0.5)×0.6 = 30，抵抗 0
        assertEquals(30F, AttributeMath.resolveIncoming(100F, 0.5F, 0.6F, 0F, 0.9F), 1e-4);
    }

    @Test
    void resistCanZeroOut() {
        // spec"抵抗减至零"：百分比阶段后 15，抵抗 20 → 0（无最低保留）
        assertEquals(0F, AttributeMath.resolveIncoming(100F, 0.7F, 0.5F, 20F, 0.9F), 1e-4);
    }

    @Test
    void globalCapBindsCombinedReduction() {
        // (1-0.9)×0.05 → 原始合并减免 0.955 > 0.9 → 按 0.9 封顶：100×0.1=10
        assertEquals(10F, AttributeMath.resolveIncoming(100F, 0.9F, 0.05F, 0F, 0.9F), 1e-4);
    }

    @Test
    void shieldImmunityBypassesCap() {
        // XI 级护盾（factor=0）短路：保持既有"完全免疫 0 点"，不吃 90% 封顶
        assertEquals(0F, AttributeMath.resolveIncoming(100F, 0F, 0F, 0F, 0.9F), 1e-6);
    }

    @Test
    void legacyShieldWithoutAttributesUnchanged() {
        // danmaku-combat 既有场景回归：III 级盾、玩家零属性 → 100→60
        assertEquals(60F, AttributeMath.resolveIncoming(100F, 0F, 0.6F, 0F, 0.9F), 1e-4);
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
