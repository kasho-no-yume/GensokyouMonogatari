package com.bitsson.gensokyou.balance;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BuildSurvivalMathTest {
    @Test
    void directHitsKillEvenWithTheoreticalOverhealingLater() {
        var result = BuildSurvivalMath.simulate(60D, List.of(
                new BuildSurvivalMath.Event(1D, 30D, 0D, 0),
                new BuildSurvivalMath.Event(2D, 30D, 0D, 0),
                new BuildSurvivalMath.Event(3D, 0D, 60D, 0)), 10D);
        assertFalse(result.survived());
        assertEquals(2D, result.livedSeconds());
        assertEquals(0D, result.effectiveHealing());
    }

    @Test
    void replacementShieldIsNotAdditiveAndOverhealingIsNotSurvivalCredit() {
        var result = BuildSurvivalMath.simulate(60D, List.of(
                new BuildSurvivalMath.Event(0D, 0D, 10D, 3),
                new BuildSurvivalMath.Event(1D, 10D, 0D, 0),
                new BuildSurvivalMath.Event(2D, 0D, 0D, 1),
                new BuildSurvivalMath.Event(3D, 10D, 0D, 0),
                new BuildSurvivalMath.Event(4D, 10D, 0D, 0)), 5D);
        assertEquals(2, result.blocked());
        assertEquals(50D, result.health());
        assertEquals(10D, result.overhealing());
    }

    @Test
    void proposedMaxHealingDoesNotEraseHighIncomingPressure() {
        List<BuildSurvivalMath.Event> timeline = new ArrayList<>();
        // T5 normal garden candidate .25 HP/s, +50% healing = .375 HP/s.
        for (int second = 1; second <= 1050; second++) {
            timeline.add(new BuildSurvivalMath.Event(second, 0D, .375D, 0));
        }
        for (int hit = 1; hit * 10 <= 1050; hit++) {
            timeline.add(new BuildSurvivalMath.Event(hit * 10D, 7.5D, 0D, 0));
        }
        var result = BuildSurvivalMath.simulate(60D, timeline, 1050D);
        assertFalse(result.survived());
        assertTrue(result.livedSeconds() < 200D);
    }

    @Test
    void expiredShieldCannotProtectFutureHits() {
        var result = BuildSurvivalMath.simulate(60D, List.of(
                new BuildSurvivalMath.Event(0D, 0D, 0D, 2, 20D),
                new BuildSurvivalMath.Event(19D, 30D, 0D, 0),
                new BuildSurvivalMath.Event(20D, 30D, 0D, 0),
                new BuildSurvivalMath.Event(21D, 30D, 0D, 0)), 30D);
        assertFalse(result.survived());
        assertEquals(1, result.blocked());
        assertEquals(21D, result.livedSeconds());
    }
}
