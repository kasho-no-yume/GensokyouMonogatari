package com.bitsson.gensokyou.worldgen.human_village;

import java.util.List;
import java.util.Set;

public record HumanVillagePlan(long worldSeed, List<BuildingInstance> buildings,
                               List<RoadCell> roadCells, Set<Long> protectedColumns) {
    public HumanVillagePlan {
        buildings = List.copyOf(buildings);
        roadCells = List.copyOf(roadCells);
        protectedColumns = Set.copyOf(protectedColumns);
    }
    public enum RoadType { L1_MAIN, L2_RING, L3_BRANCH, L4_BRIDGE }
    /** surfaceY is the solid paving block; bridge half slabs may add half a block. */
    public record RoadCell(int x, int surfaceY, int z, RoadType type, boolean fixed) {
        public long key() { return pack(x, z); }
    }
    public static long pack(int x, int z) { return ((long) x << 32) | (z & 0xFFFFFFFFL); }
    public static int unpackX(long key) { return (int) (key >> 32); }
    public static int unpackZ(long key) { return (int) key; }
}
