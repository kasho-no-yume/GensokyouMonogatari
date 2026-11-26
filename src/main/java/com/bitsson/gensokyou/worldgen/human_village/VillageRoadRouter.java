package com.bitsson.gensokyou.worldgen.human_village;

import com.bitsson.gensokyou.worldgen.GensokyoLayout;
import com.bitsson.gensokyou.worldgen.human_village.HumanVillagePlan.RoadCell;
import com.bitsson.gensokyou.worldgen.human_village.HumanVillagePlan.RoadType;

import java.util.*;

import static com.bitsson.gensokyou.worldgen.human_village.HumanVillagePlan.*;

/** Public performance clearances and small paving patches; houses define the lanes, not a road graph. */
public final class VillageRoadRouter {
    private static final int[][] DIRECTIONS = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
    private VillageRoadRouter() {}

    public static Map<Long, RoadCell> skeleton(GensokyoLayout layout) {
        Map<Long, RoadCell> roads = new LinkedHashMap<>();
        for (int x = -380; x <= 40; x++) {
            int halfWidth = x > 20 ? 1 : 2;
            for (int z = 100 - halfWidth; z <= 100 + halfWidth; z++) add(roads, layout, x, z, RoadType.L1_MAIN, true);
        }
        line(roads, layout, -200, 20, -200, 230, 2, RoadType.L1_MAIN);
        // The northern exit goes around the east wall of the expanded Hieda estate.
        line(roads, layout, -200, 34, -155, 34, 2, RoadType.L1_MAIN);
        line(roads, layout, -155, 34, -155, -82, 2, RoadType.L1_MAIN);
        line(roads, layout, -155, -82, -200, -82, 2, RoadType.L1_MAIN);
        line(roads, layout, -200, -82, -200, -65, 2, RoadType.L1_MAIN);
        line(roads, layout, -256, 48, -144, 48, 1, RoadType.L2_RING);
        line(roads, layout, -256, 48, -256, 140, 1, RoadType.L2_RING);
        line(roads, layout, -144, 48, -144, 140, 1, RoadType.L2_RING);
        // Connect the fixed bookshop door, market stalls, and both canal promenades before random housing.
        line(roads, layout, -215, 131, -200, 131, 1, RoadType.L2_RING);
        for (int x = -256; x <= -109; x++) {
            int z = (int) Math.round(GensokyoLayout.villageStreamCenterZ(x));
            for (int dz = -1; dz <= 1; dz++) {
                add(roads, layout, x, z - 6 + dz, RoadType.L2_RING, true);
                if (x >= -165) add(roads, layout, x, z + 5 + dz, RoadType.L2_RING, true);
            }
            if (x > -256) {
                int previous = (int) Math.round(GensokyoLayout.villageStreamCenterZ(x - 1));
                line(roads, layout, x, previous - 6, x, z - 6, 1, RoadType.L2_RING);
                if (x >= -164) line(roads, layout, x, previous + 5, x, z + 5, 1, RoadType.L2_RING);
            }
        }
        line(roads, layout, -144, 140, -144, 163, 1, RoadType.L2_RING);
        // Authored public paths around the statue; its pedestal/pools are never treated as road.
        roads.entrySet().removeIf(e -> inPlaza(e.getValue().x(), e.getValue().z()));
        for (int x = -214; x <= -186; x++) for (int z = 86; z <= 114; z++) {
            if (plazaWalkable(x, z)) add(roads, layout, x, z, RoadType.L2_RING, true);
        }
        // Noticeboard apron, behind its fence but clear of the corner lantern.
        for (int x = -71; x <= -66; x++) for (int z = 95; z <= 97; z++) add(roads, layout, x, z, RoadType.L2_RING, true);
        return roads;
    }

    public static void reserve(OccupancyGrid grid, GensokyoLayout layout) {
        for (RoadCell cell : skeleton(layout).values()) {
            grid.set(cell.x(), cell.z(), OccupancyGrid.MAIN_ROAD);
            for (int[] d : DIRECTIONS) {
                int x = cell.x() + d[0], z = cell.z() + d[1];
                if (grid.get(x, z) == OccupancyGrid.EMPTY) grid.set(x, z, OccupancyGrid.MAIN_ROAD);
            }
        }
    }

    public static List<RoadCell> route(OccupancyGrid grid, List<BuildingInstance> buildings, GensokyoLayout layout) {
        var roads = skeleton(layout);
        // These fixed clearances keep authored performances valid. They are EARTH, not paved streets.
        roads.entrySet().removeIf(e -> blocked(buildings, e.getValue().x(), e.getValue().z()));
        roads.replaceAll((key,c) -> new RoadCell(c.x(),c.surfaceY(),c.z(),RoadType.EARTH,true));
        for (BuildingInstance b : buildings) {
            if (!b.hasDoorAnchor()) continue;
            int hash = b.minX()*31+b.minZ()*17;
            boolean stone = b.category()!=BuildingInstance.BuildingCategory.RESIDENTIAL || Math.floorMod(hash,5)==0;
            // A tiny apron replaces the surface near a door. No per-house path is drawn to the center.
            for(int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++) {
                int x=b.doorWorldX()+dx,z=b.doorWorldZ()+dz;
                if(grid.get(x,z)==OccupancyGrid.BUILDING || streamDistance(x,z)<=5) continue;
                int y = dx==0 && dz==0 ? b.doorWorldY() : (int)Math.ceil(layout.surfaceHeight(x,z,0))-1;
                var previous=roads.get(pack(x,z));
                boolean fixed=b.fixed() || (previous!=null && previous.fixed());
                roads.put(pack(x,z),new RoadCell(x,y,z,stone?RoadType.L3_BRANCH:RoadType.EARTH,fixed));
            }
        }
        roads.replaceAll((key, c) -> streamDistance(c.x(), c.z()) <= 4.2
                ? new RoadCell(c.x(), 71, c.z(), RoadType.L4_BRIDGE, c.fixed()) : c);
        return List.copyOf(roads.values());
    }

    public static boolean blocked(List<BuildingInstance> buildings, int x, int z) {
        for (var b : buildings) {
            if (!b.containsColumn(x, z)) continue;
            if (!b.publicPassage()) return true;
            String id = b.templateId().getPath();
            if (id.equals("hv_dragon_plaza")) return !plazaWalkable(x, z);
            if (id.equals("hv_gate_north")) return Math.abs(x + 200) > 2;
            if (id.equals("hv_gate_east") && x >= -71 && x <= -66 && z >= 95 && z <= 97) return false;
            return Math.abs(z - 100) > 2;
        }
        return false;
    }

    public static boolean inPlaza(int x, int z) { return x >= -214 && x <= -186 && z >= 86 && z <= 114; }
    public static boolean plazaWalkable(int x, int z) {
        int dx = Math.abs(x + 200), dz = Math.abs(z - 100);
        return inPlaza(x, z) && dx + dz <= 20 && Math.max(dx, dz) >= 6
                && !(dx == 8 && dz == 8) && !(dx == 10 && dz >= 3 && dz <= 5);
    }
    public static double streamDistance(int x, int z) {
        return x < -345 || x > 5 ? Double.POSITIVE_INFINITY : Math.abs(z - GensokyoLayout.villageStreamCenterZ(x));
    }
    public static int surfaceY(GensokyoLayout layout, int x, int z) {
        if (Math.abs(x + 200) <= 2 && z >= 20 && z <= 26) return 74 - (z - 20) / 2;
        if(x>=-257 && x<=-145 && z>=-62 && z<=34) {
            if(Math.abs(x+200)<=2 && z>=26) return 71;
            return (int)Math.ceil(layout.surfaceHeight(x,z,0))-1;
        }
        if (Math.hypot(x + 200, z - 100) <= 145 || (z >= 98 && z <= 102 && x >= -380 && x <= 40)) return 71;
        return (int) Math.ceil(layout.surfaceHeight(x, z, 0))-1;
    }
    private static void add(Map<Long, RoadCell> roads, GensokyoLayout layout, int x, int z, RoadType type, boolean fixed) {
        if (x < OccupancyGrid.MIN_X || x > OccupancyGrid.MAX_X || z < OccupancyGrid.MIN_Z || z > OccupancyGrid.MAX_Z) return;
        roads.putIfAbsent(pack(x, z), new RoadCell(x, surfaceY(layout, x, z), z, type, fixed));
    }
    private static void line(Map<Long, RoadCell> roads, GensokyoLayout layout, int x, int z, int endX, int endZ, int halfWidth, RoadType type) {
        if (x != endX && z != endZ) throw new IllegalArgumentException("Road segments must be orthogonal");
        int dx = Integer.compare(endX, x), dz = Integer.compare(endZ, z);
        for (;;) {
            for (int w = -halfWidth; w <= halfWidth; w++) add(roads, layout, x + (dz != 0 ? w : 0), z + (dx != 0 ? w : 0), type, true);
            if (x == endX && z == endZ) break;
            x += dx; z += dz;
        }
    }

    public static Set<Long> connectedColumns(List<RoadCell> cells) {
        Set<Long> available = new HashSet<>();
        for (var cell : cells) available.add(cell.key());
        var visited = new HashSet<Long>();
        var queue = new ArrayDeque<Long>();
        long start = pack(-185, 100);
        if (available.contains(start)) { queue.add(start); visited.add(start); }
        while (!queue.isEmpty()) {
            long p = queue.remove();
            for (int[] d : DIRECTIONS) {
                long next = pack(unpackX(p) + d[0], unpackZ(p) + d[1]);
                if (available.contains(next) && visited.add(next)) queue.add(next);
            }
        }
        return Set.copyOf(visited);
    }
}
