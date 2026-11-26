package com.bitsson.gensokyou.client.resident;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ResidentMotionTest {
    private static final ResidentRoute ROUTE = new ResidentRoute(List.of(
            new ResidentRoute.Point(0, 72, 0), new ResidentRoute.Point(4, 72, 0), new ResidentRoute.Point(4, 72, 3)));

    @Test
    void routeWaitsAtCornersAndReturnsAlongTheSamePolyline() {
        assertFalse(ROUTE.sample(9, 1, 10, 0).walking());
        assertEquals(2, ROUTE.sample(12, 1, 10, 0).position().x(), 1e-6);
        assertEquals(new ResidentRoute.Point(4, 72, 0), ROUTE.sample(17, 1, 10, 0).position());
        assertFalse(ROUTE.sample(17, 1, 10, 0).walking());
        assertEquals(new ResidentRoute.Point(4, 72, 2), ROUTE.sample(26, 1, 10, 0).position());
        assertEquals(new ResidentRoute.Point(4, 72, 2), ROUTE.sample(38, 1, 10, 0).position());
        assertEquals(new ResidentRoute.Point(2, 72, 0), ROUTE.sample(52, 1, 10, 0).position());
        assertEquals(14, ROUTE.sample(54, 1, 10, 0).walked(), 1e-6);
        assertEquals(ROUTE.sample(12, 1, 10, 0).position(), ROUTE.sample(54 * 100_000.0 + 12, 1, 10, 0).position());
    }

    @Test
    void blockedMotionDoesNotAccumulateTimeOrResumeBehindTheWall() {
        ResidentWalker walker = new ResidentWalker(new ResidentScene.Slot("walk-test", ROUTE, 0, false));
        TestSurface surface = new TestSurface();
        walker.tick(surface);
        for (int i = 0; i < 500 && !walker.frame(1).walking(); i++) walker.tick(surface);
        ResidentWalker.Frame before = walker.frame(1);
        assertTrue(before.walking());
        surface.blocked = true;
        for (int i = 0; i < 500; i++) walker.tick(surface);
        assertEquals(before.position(), walker.frame(1).position());
        assertEquals(before.walked(), walker.frame(1).walked());
        assertFalse(walker.frame(1).walking());
        surface.blocked = false;
        walker.tick(surface);
        assertTrue(walker.frame(1).position().distance(before.position()) <= walker.appearance().speed() + 1e-6);
        assertEquals(walker.appearance().speed(), walker.frame(1).walked() - before.walked(), 1e-6);
    }

    @Test
    void unavailableGroundHidesResidentsAndInterpolationStaysWithinASingleStep() {
        ResidentWalker walker = new ResidentWalker(new ResidentScene.Slot("standing", ROUTE, 0, true));
        TestSurface surface = new TestSurface();
        surface.available = false; walker.tick(surface);
        assertNull(walker.frame(1));
        surface.available = true; walker.tick(surface); walker.tick(surface);
        var a = walker.frame(0); var b = walker.frame(1); var mid = walker.frame(0.5F);
        assertNotNull(mid);
        assertEquals(a.position().lerp(b.position(), 0.5), mid.position());
        surface.available = false; walker.tick(surface);
        assertNull(walker.frame(1));
        assertEquals(2, ResidentWalker.wrapDegrees(-358));
        assertEquals(-2, ResidentWalker.wrapDegrees(358));
    }

    private static final class TestSurface implements ResidentWalker.Surface {
        boolean available = true;
        boolean blocked;
        @Override public ResidentRoute.Point resolve(ResidentRoute.Point point, float scale, boolean protectedGround) {
            return available ? point : null;
        }
        @Override public boolean clear(ResidentRoute.Point from, ResidentRoute.Point to, float scale) { return !blocked; }
    }
}
