package com.bitsson.gensokyou.balance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpellAttributeMathTest {
    @Test
    void categoriesNeverCrossAmplify() {
        assertEquals(60D, SpellAttributeMath.amplified(10D, SpellAttributeMath.Kind.DAMAGE, 5D, .5D));
        assertEquals(15D, SpellAttributeMath.amplified(10D, SpellAttributeMath.Kind.HEALING, 5D, .5D));
        assertEquals(10D, SpellAttributeMath.amplified(10D, SpellAttributeMath.Kind.NONE, 5D, .5D));
    }

    @Test
    void controlIsNotAutomaticallyExtendedAndInfiniteDurationRemainsInfinite() {
        assertEquals(800, SpellAttributeMath.duration(200, true, 3D));
        assertEquals(200, SpellAttributeMath.duration(200, false, 3D));
        assertEquals(-1, SpellAttributeMath.duration(-1, true, 3D));
        assertEquals(5, SpellAttributeMath.capacity(3, .5D));
    }

    @Test
    void coverageAndCohortsAreDifferentQuantities() {
        assertEquals(1D, SpellAttributeMath.coverage(480D, 8D));
        assertEquals(60L, SpellAttributeMath.cohorts(480D, 8D));
        assertEquals(.5D, SpellAttributeMath.coverage(10D, 20D));
        assertThrows(IllegalArgumentException.class, () -> SpellAttributeMath.cohorts(10D, 0D));
    }

    @Test
    void oldSpeedMigrationPreservesTheoreticalFrequencyWithoutMultiplyingTwice() {
        assertEquals(3D, SpellAttributeMath.legacyIntervalReductionToSpeed(.75D));
        assertEquals(4D, SpellAttributeMath.legacyIntervalReductionToSpeed(99D), 1e-12);
        assertEquals(0D, SpellAttributeMath.legacyIntervalReductionToSpeed(-1D));
    }

    @Test
    void explicitParameterPolicyCapsFinalValueWithoutCappingUnrelatedParameters() {
        var shieldCount = new SpellAttributeMath.ParameterPolicy(true, false, 1D, 3D);
        var shieldDuration = new SpellAttributeMath.ParameterPolicy(false, true, 0D, -1D);
        assertEquals(3D, shieldCount.apply(2D, 5D, 3D));
        assertEquals(80D, shieldDuration.apply(20D, 5D, 3D));
        assertEquals(4D, SpellAttributeMath.ParameterPolicy.fixed().apply(4D, 5D, 3D));
        assertEquals(60D, SpellAttributeMath.ParameterPolicy.magnitude().apply(10D, 5D, 3D));
        assertEquals(300L, SpellAttributeMath.cooldown(300L, .6D, 300L));
        assertEquals(120L, SpellAttributeMath.cooldown(300L, .6D, -1L));
    }

    @Test
    void parameterListMakesMagnitudeDurationAndFixedFieldsUnambiguous() {
        assertEquals(9, SpellParameterPolicies.all().size());
        var shield = SpellParameterPolicies.card("flower_armor");
        assertEquals(2D, shield.parameter("petals").apply(1D, 5D, 3D));
        assertEquals(80D, shield.parameter("duration").apply(20D, 5D, 3D));
        assertEquals(4D, SpellParameterPolicies.card("healing_garden")
                .parameter("radius").apply(4D, 5D, 3D));
        assertEquals(10D, SpellParameterPolicies.card("demarcation")
                .parameter("duration").apply(10D, 5D, 3D));
        assertEquals(5D, SpellParameterPolicies.card("icicle_fall")
                .parameter("count").apply(5D, 5D, 3D));
        assertFalse(SpellParameterPolicies.card("unknown").parameter("damage").strength());
    }

    @Test
    void legacyGeneralBonusIsReadOnlyAndLimitedPerConsumer() {
        assertEquals(2D, SpellAttributeMath.combinedBonus(1D, 1D, true, 5D));
        assertEquals(.5D, SpellAttributeMath.combinedBonus(.2D, 1D, true, .5D));
        assertEquals(.2D, SpellAttributeMath.combinedBonus(.2D, 1D, false, 5D));
        assertEquals(0D, SpellAttributeMath.combinedBonus(0D, 1D, false, 5D));
    }
}
