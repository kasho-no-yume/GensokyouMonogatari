package com.bitsson.gensokyou.worldgen.human_village;

/** Bounded, registry-free 2D plan state. All searches stay inside this rectangle. */
public final class OccupancyGrid {
    public static final int MIN_X = -420, MAX_X = 40, MIN_Z = -120, MAX_Z = 320;
    public static final int WIDTH = MAX_X - MIN_X + 1, DEPTH = MAX_Z - MIN_Z + 1;
    public static final byte EMPTY = 0, STREAM = 1, MAIN_ROAD = 2, BUILDING = 3, BUFFER = 4;
    private final byte[] cells = new byte[WIDTH * DEPTH];
    public boolean inside(int x, int z) { return x >= MIN_X && x <= MAX_X && z >= MIN_Z && z <= MAX_Z; }
    public byte get(int x, int z) { return inside(x, z) ? cells[(z - MIN_Z) * WIDTH + x - MIN_X] : BUILDING; }
    public void set(int x, int z, byte value) { if (inside(x, z)) cells[(z - MIN_Z) * WIDTH + x - MIN_X] = value; }
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
