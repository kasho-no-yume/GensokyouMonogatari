package com.bitsson.gensokyou.client.resident;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResidentSpawnVisibilityTest {
    private final ResidentRoute.Point viewer = new ResidentRoute.Point(0, 72, 0);
    @Test void firstArrivalFillsNearbyAndOrdinaryAdmissionUsesTheOuterRing() {
        assertTrue(ResidentSpawnVisibility.mayAppear(new ResidentRoute.Point(12, 72, 0), viewer, true));
        assertFalse(ResidentSpawnVisibility.mayAppear(new ResidentRoute.Point(12, 72, 0), viewer, false));
        assertTrue(ResidentSpawnVisibility.mayAppear(new ResidentRoute.Point(40, 72, 0), viewer, false));
        assertTrue(ResidentSpawnVisibility.mayAppear(new ResidentRoute.Point(-40, 72, 0), viewer, false));
        assertFalse(ResidentSpawnVisibility.mayAppear(new ResidentRoute.Point(50, 72, 0), viewer, true));
        assertTrue(ResidentSpawnVisibility.DESPAWN_RADIUS > ResidentSpawnVisibility.SPAWN_RADIUS);
    }
}
