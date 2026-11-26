package com.bitsson.gensokyou.client.resident;

/** Admission is a distance AOI, independent of camera direction. Frusta only cull drawing. */
public final class ResidentSpawnVisibility {
    public static final double INITIAL_RADIUS = 32, SPAWN_RADIUS = 48, DESPAWN_RADIUS = 64;
    public static boolean mayAppear(ResidentRoute.Point feet, ResidentRoute.Point viewer, boolean initial) {
        double distance = feet.distance(viewer);
        return distance <= SPAWN_RADIUS && (initial || distance >= INITIAL_RADIUS);
    }
    private ResidentSpawnVisibility() {}
}
