package com.bitsson.gensokyou.balance;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class BuildBudgetMathTest {
    @Test
    void fixedReferenceIncludesSphereMultiplierAndAllLaserPulses() {
        assertEquals(900D, BuildBudgetMath.referenceDps(90D, 2D, 2D, 2.5D));
        assertEquals(5D, BuildBudgetMath.damageFactor(2D, 1, 8D));
        assertEquals(8D, BuildBudgetMath.damageFactor(2D, 12, 60D));
    }

    @Test
    void frequencyAndCriticalTotalHaveExplicitSemantics() {
        assertEquals(3.25D, BuildBudgetMath.criticalExpectation(0.75D, 4D));
        assertEquals(28.6D, BuildBudgetMath.weaponDps(1D, 1.2D, 3D, 0.75D, 4D), 1e-12);
        assertEquals(4D, BuildBudgetMath.weaponDps(1D, 0D, 3D, 0D, 1.5D));
    }

    @Test
    void wardSpanIsAbsoluteAndDistinctFromHealthAndGraze() {
        double span = BuildBudgetMath.wardSpan(3D);
        assertEquals(3D, Math.pow(2D, span), 1e-12);
        assertEquals(600D, BuildBudgetMath.effectiveHealth(100D, span, 0.5D), 1e-9);
        assertEquals(30L, BuildBudgetMath.lethalHits(100D, 10D, span));
    }

    @Test
    void biasedDistributionHasRareUpperTailWithoutGuaranteedHighValue() {
        assertEquals(0.125D, BuildBudgetMath.lowBiasedQuantile(0.5D, 3D));
        assertEquals(0.03451061539437028D,
                BuildBudgetMath.upperTailProbability(0.9D, 3D), 1e-12);
        assertEquals(0D, BuildBudgetMath.lowBiasedQuantile(0D, 3D));
        assertEquals(1D, BuildBudgetMath.lowBiasedQuantile(1D, 3D));
    }

    @Test
    void fractionalCostsAreNotRoundedToFreeShots() {
        double cost = BuildBudgetMath.discountedCost(1D, 0.9D);
        var actions = IntStream.range(0, 10)
                .mapToObj(i -> new BuildBudgetMath.Action(i, cost, 1D, true)).toList();
        var result = BuildBudgetMath.simulate(
                new BuildBudgetMath.ResourcePlan(1D, 1D, 0D, 0D, 0D), actions, 10D);
        assertEquals(10, result.accepted());
        assertEquals(1D, result.spent(), 1e-12);
        assertEquals(0D, result.finalSpirit(), 1e-12);
    }

    @Test
    void starvationSkipsDamageAndRecoveryAllowsLaterShots() {
        var actions = List.of(new BuildBudgetMath.Action(0D, 2D, 10D, true),
                new BuildBudgetMath.Action(0.5D, 2D, 10D, true),
                new BuildBudgetMath.Action(2D, 2D, 10D, true));
        var result = BuildBudgetMath.simulate(
                new BuildBudgetMath.ResourcePlan(2D, 2D, 1D, 0D, 0D), actions, 3D);
        assertEquals(2, result.accepted());
        assertEquals(1, result.starved());
        assertEquals(20D / 3D, result.sustainedDps(), 1e-12);
        assertEquals(1D, result.finalSpirit());
        assertEquals(2D + result.regenerated() + result.leeched() - result.spent(),
                result.finalSpirit(), 1e-12);
    }

    @Test
    void leechIsWeaponOnlyAndBucketLimited() {
        var actions = List.of(new BuildBudgetMath.Action(0D, 2D, 100D, false),
                new BuildBudgetMath.Action(0.1D, 2D, 100D, true),
                new BuildBudgetMath.Action(0.2D, 2D, 100D, true),
                new BuildBudgetMath.Action(1D, 2D, 100D, true));
        var result = BuildBudgetMath.simulate(
                new BuildBudgetMath.ResourcePlan(20D, 20D, 0D, 0.5D, 1D), actions, 2D);
        assertEquals(2D, result.leeched());
        assertEquals(14D, result.finalSpirit());
    }

    @Test
    void invalidAndUnorderedInputsFailExplicitly() {
        assertThrows(IllegalArgumentException.class,
                () -> BuildBudgetMath.referenceDps(Double.NaN, 1D, 1D, 1D));
        assertThrows(IllegalArgumentException.class,
                () -> new BuildBudgetMath.ResourcePlan(2D, 1D, 0D, 0D, 0D));
        assertThrows(IllegalArgumentException.class, () -> BuildBudgetMath.simulate(
                new BuildBudgetMath.ResourcePlan(1D, 1D, 0D, 0D, 0D),
                List.of(new BuildBudgetMath.Action(2D, 0D, 0D, false),
                        new BuildBudgetMath.Action(1D, 0D, 0D, false)), 3D));
    }

    @Test
    void periodicDamageRequiresPaidCastAndCannotPrecreditFutureLifetime() {
        var actions = List.of(new BuildBudgetMath.Action(0D, 5D, 10D, false, 1D, 10D, false),
                new BuildBudgetMath.Action(1D, 5D, 100D, false, 1D, 10D, false));
        var result = BuildBudgetMath.simulate(
                new BuildBudgetMath.ResourcePlan(5D, 5D, 0D, 0D, 0D), actions, 3D);
        assertEquals(1, result.accepted());
        assertEquals(1, result.starved());
        assertEquals(30D, result.damage());
        assertEquals(5D, result.spent());
    }

    @Test
    void killTimeStopsAtDeliveredPulseNotAtFullLifetimeCredit() {
        var actions = List.of(new BuildBudgetMath.Action(0D, 5D, 10D, false, 1D, 10D, false));
        var result = BuildBudgetMath.simulateUntilDamage(
                new BuildBudgetMath.ResourcePlan(5D, 5D, 0D, 0D, 0D), actions, 10D, 25D);
        assertEquals(3D, result.durationSeconds());
        assertEquals(30D, result.damage());
        assertEquals(1, result.accepted());
    }

    @Test
    void survivalObserverDoesNotReceiveStarvedCastsOrPeriodicPulses() {
        var paid = new java.util.ArrayList<BuildBudgetMath.Action>();
        var actions = List.of(new BuildBudgetMath.Action(0D, 5D, 1D, false, 1D, 3D, false),
                new BuildBudgetMath.Action(1D, 5D, 0D, false));
        BuildBudgetMath.simulateUntilDamage(new BuildBudgetMath.ResourcePlan(5D, 5D, 0D, 0D, 0D),
                actions, 4D, Double.POSITIVE_INFINITY, paid::add);
        assertEquals(List.of(actions.get(0)), paid);
    }
}
