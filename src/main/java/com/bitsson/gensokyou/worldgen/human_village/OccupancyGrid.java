package com.bitsson.gensokyou.worldgen.human_village;

import java.util.HashMap;
import java.util.Map;

/** Bounded, registry-free 2D plan state. All searches stay inside this rectangle. */
public final class OccupancyGrid {
    public static final int MIN_X = -4480, MAX_X = 4480, MIN_Z = -4480, MAX_Z = 4480;
    public static final int WIDTH = MAX_X - MIN_X + 1, DEPTH = MAX_Z - MIN_Z + 1;
    public static final byte EMPTY = 0, STREAM = 1, MAIN_ROAD = 2, BUILDING = 3, BUFFER = 4;
    // Allocate only occupied chunks, rather than a dense island-sized routing grid.
    private final Map<Long, byte[]> chunks = new HashMap<>();
    public boolean inside(int x, int z) { return x >= MIN_X && x <= MAX_X && z >= MIN_Z && z <= MAX_Z; }
    public byte get(int x, int z) {
        if (!inside(x, z)) return BUILDING;
        byte[] cells = chunks.get(HumanVillagePlan.pack(x >> 4, z >> 4));
        return cells == null ? EMPTY : cells[(z & 15) * 16 + (x & 15)];
    }
    public void set(int x, int z, byte value) {
        if (inside(x, z)) chunks.computeIfAbsent(HumanVillagePlan.pack(x >> 4, z >> 4), key -> new byte[256])[(z & 15) * 16 + (x & 15)] = value;
    }
    public boolean available(int x, int z, int width, int depth, int margin) {
        if (!inside(x - margin, z - margin) || !inside(x + width - 1 + margin, z + depth - 1 + margin)) return false;
        for (int px = x - margin; px < x + width + margin; px++) {
            for (int pz = z - margin; pz < z + depth + margin; pz++) {
                byte cell = get(px, pz);
                if (cell == BUILDING) return false;
                if (px >= x && px < x + width && pz >= z && pz < z + depth && cell != EMPTY) return false;
            }
        }
        return true;
    }
    public void occupy(BuildingInstance b) {
        for (int x = b.minX() - b.clearance(); x <= b.maxX() + b.clearance(); x++) {
            for (int z = b.minZ() - b.clearance(); z <= b.maxZ() + b.clearance(); z++) {
                if (b.containsColumn(x, z)) set(x, z, BUILDING);
                else if (get(x, z) == EMPTY) set(x, z, BUFFER);
            }
        }
    }
}
