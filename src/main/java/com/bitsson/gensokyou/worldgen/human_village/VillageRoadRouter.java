package com.bitsson.gensokyou.worldgen.human_village;

import com.bitsson.gensokyou.worldgen.GensokyoLayout;
import com.bitsson.gensokyou.worldgen.human_village.HumanVillagePlan.RoadCell;
import com.bitsson.gensokyou.worldgen.human_village.HumanVillagePlan.RoadType;

import java.util.*;

import static com.bitsson.gensokyou.worldgen.human_village.HumanVillagePlan.*;

/** Four-neighbor routing with direction in the search state, so turn penalties remain correct. */
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
        // Preserve templates: only their known public paving participates in the road graph.
        roads.entrySet().removeIf(e -> blocked(buildings, e.getValue().x(), e.getValue().z()));
        var ordered = new ArrayList<>(buildings);
        ordered.sort(Comparator.comparing((BuildingInstance b) -> !b.fixed()).thenComparingDouble(BuildingInstance::radius)
                .thenComparing(b -> b.templateId().toString()).thenComparingInt(BuildingInstance::minX));
        for (BuildingInstance b : ordered) {
            if (!b.hasDoorAnchor()) continue;
            var path = findPath(grid, roads, b.doorWorldX(), b.doorWorldZ(), layout);
            if (path.isEmpty()) continue;
            for (int i = 0; i < path.size(); i++) {
                long key = path.get(i);
                int x = unpackX(key), z = unpackZ(key);
                if (!roads.containsKey(key)) add(roads, layout, x, z, RoadType.L3_BRANCH, b.fixed());
                if (i + 1 < path.size()) {
                    int nx = unpackX(path.get(i + 1)), nz = unpackZ(path.get(i + 1));
                    int ex = x + (nz != z ? 1 : 0), ez = z + (nx != x ? 1 : 0);
                    if (grid.get(ex, ez) != OccupancyGrid.BUILDING && grid.get(ex, ez) != OccupancyGrid.STREAM
                            && !blocked(buildings, ex, ez)) add(roads, layout, ex, ez, RoadType.L3_BRANCH, b.fixed());
                }
            }
            // The final approach uses the exact template floor height, including the Hieda stair top.
            long door = pack(b.doorWorldX(), b.doorWorldZ());
            var cell = roads.get(door);
            if (cell != null) roads.put(door, new RoadCell(cell.x(), b.doorWorldY(), cell.z(), cell.type(), cell.fixed()));
        }
        roads.replaceAll((key, c) -> streamDistance(c.x(), c.z()) <= 4.2
                ? new RoadCell(c.x(), 72, c.z(), RoadType.L4_BRIDGE, c.fixed()) : c);
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
        if (Math.abs(x + 200) <= 2 && z >= 20 && z <= 26) return 75 - (z - 20) / 2;
        if(x>=-257 && x<=-145 && z>=-62 && z<=34) {
            if(Math.abs(x+200)<=2 && z>=26) return 72;
            return (int)Math.round(layout.surfaceHeight(x,z,0));
        }
        if (Math.hypot(x + 200, z - 100) <= 145 || (z >= 98 && z <= 102 && x >= -380 && x <= 40)) return 72;
        return (int) Math.round(layout.surfaceHeight(x, z, 0));
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

    private static List<Long> findPath(OccupancyGrid grid, Map<Long, RoadCell> roads, int startX, int startZ, GensokyoLayout layout) {
        if (roads.containsKey(pack(startX, startZ))) return List.of(pack(startX, startZ));
        if (roads.isEmpty()) return List.of();
        int[] distance = distanceField(roads);
        var open = new PriorityQueue<Node>(Comparator.comparingDouble(Node::estimate).thenComparingDouble(Node::cost)
                .thenComparingInt(Node::x).thenComparingInt(Node::z).thenComparingInt(Node::direction));
        var best = new HashMap<Long, Double>();
        Node start = new Node(startX, startZ, 4, 0, 0, null);
        open.add(start); best.put(start.stateKey(), 0.0);
        int expanded = 0;
        while (!open.isEmpty() && expanded < 2500) {
            Node node = open.remove();
            if (node.cost > best.getOrDefault(node.stateKey(), Double.POSITIVE_INFINITY)) continue;
            expanded++;
            if (roads.containsKey(pack(node.x, node.z))) {
                var path = new ArrayList<Long>();
                for (Node p = node; p != null; p = p.parent) path.add(pack(p.x, p.z));
                Collections.reverse(path); return path;
            }
            for (int d = 0; d < 4; d++) {
                int x = node.x + DIRECTIONS[d][0], z = node.z + DIRECTIONS[d][1];
                if (!grid.inside(x, z) || (grid.get(x, z) == OccupancyGrid.BUILDING && !roads.containsKey(pack(x, z)))) continue;
                double cost = node.cost + (roads.containsKey(pack(x, z)) ? 0.15 : grid.get(x, z) == OccupancyGrid.STREAM ? 5.5 : 1.0)
                        + (node.direction != 4 && node.direction != d ? 0.6 : 0)
                        + 2.5 * Math.abs(surfaceY(layout, x, z) - surfaceY(layout, node.x, node.z));
                // Exact Manhattan distance to the goal SET, minus the discounted final road step.
                // This is admissible and avoids exhausting 2500 states in a broad empty field.
                double heuristic = Math.max(0, distance[(z - OccupancyGrid.MIN_Z) * OccupancyGrid.WIDTH + x - OccupancyGrid.MIN_X] - 0.85);
                Node next = new Node(x, z, d, cost, cost + heuristic, node);
                if (cost < best.getOrDefault(next.stateKey(), Double.POSITIVE_INFINITY)) {
                    best.put(next.stateKey(), cost); open.add(next);
                }
            }
        }
        return List.of();
    }
    private static int[] distanceField(Map<Long, RoadCell> roads) {
        int width=OccupancyGrid.WIDTH,depth=OccupancyGrid.DEPTH;
        int[] distance=new int[width*depth];
        Arrays.fill(distance,width+depth);
        for(var cell:roads.values()) distance[(cell.z()-OccupancyGrid.MIN_Z)*width+cell.x()-OccupancyGrid.MIN_X]=0;
        for(int z=0;z<depth;z++) for(int x=0;x<width;x++) {
            int i=z*width+x;
            if(x>0) distance[i]=Math.min(distance[i],distance[i-1]+1);
            if(z>0) distance[i]=Math.min(distance[i],distance[i-width]+1);
        }
        for(int z=depth-1;z>=0;z--) for(int x=width-1;x>=0;x--) {
            int i=z*width+x;
            if(x+1<width) distance[i]=Math.min(distance[i],distance[i+1]+1);
            if(z+1<depth) distance[i]=Math.min(distance[i],distance[i+width]+1);
        }
        return distance;
    }
    private record Node(int x, int z, int direction, double cost, double estimate, Node parent) {
        long stateKey() { return (((long) (z - OccupancyGrid.MIN_Z) * OccupancyGrid.WIDTH + x - OccupancyGrid.MIN_X) * 5) + direction; }
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
