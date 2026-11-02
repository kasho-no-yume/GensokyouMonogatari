package com.bitsson.gensokyou.balance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BuildAttributeBoundsTest {
    @Test
    void sourcesAddBeforeBoundsAndOriginalSumIsRetained() {
        var result = BuildAttributeBounds.resolve(0D, 16D, 4D, 0D, 18D);
        assertEquals(20D, result.raw());
        assertEquals(18D, result.effective());
        assertEquals(2D, result.overflow());
        assertTrue(result.limited());
        assertEquals(16D, BuildAttributeBounds.resolve(0D, 16D, 0D, 0D, 18D).effective());
    }

    @Test
    void wardIsNotTreatedAsProbability() {
        var result = BuildAttributeBounds.resolve(13D, .2D, .5D, 12D, 14D);
        assertEquals(13.7D, result.effective(), 1e-12);
        assertFalse(result.limited());
    }

    @Test
    void lowerBoundDoesNotRequireRewritingLegacySource() {
        var result = BuildAttributeBounds.resolve(0D, -5D, 0D, .5D, 3D);
        assertEquals(-5D, result.raw());
        assertEquals(.5D, result.effective());
        assertEquals(0D, result.overflow());
        assertEquals(156.552D, BuildAttributeBounds.extraPowerLimit(6523D * .8D), 1e-9);
    }

    @Test
    void invalidOrOverflowingInputsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> BuildAttributeBounds.resolve(0D, 0D, 0D, 2D, 1D));
        assertThrows(IllegalArgumentException.class,
                () -> BuildAttributeBounds.resolve(Double.MAX_VALUE, Double.MAX_VALUE, 0D, 0D, -1D));
    }
}
