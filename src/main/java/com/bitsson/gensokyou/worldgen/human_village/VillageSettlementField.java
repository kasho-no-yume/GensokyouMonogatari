package com.bitsson.gensokyou.worldgen.human_village;

import com.bitsson.gensokyou.worldgen.GensokyoLayout;
import java.util.ArrayDeque;
import java.util.Arrays;

/** Coarse biome-interior distance, computed once per plan. Never requests world chunks. */
public final class VillageSettlementField {
    public static final int STEP = 64, EDGE_BUFFER = 64;
    private static final int MIN = -4480, WIDTH = 141;
    private static final int[][] DIRECTIONS={{1,0},{-1,0},{0,1},{0,-1}};
    private final GensokyoLayout layout;
    private final int[] distance = new int[WIDTH * WIDTH];

    public VillageSettlementField(GensokyoLayout layout) {
        this.layout = layout;
        Arrays.fill(distance, Integer.MAX_VALUE);
        var queue = new ArrayDeque<Integer>();
        for (int z = 0; z < WIDTH; z++) for (int x = 0; x < WIDTH; x++) {
            if (x == 0 || z == 0 || x == WIDTH - 1 || z == WIDTH - 1
                    || layout.regionAt(MIN + x * STEP, MIN + z * STEP) != GensokyoLayout.Region.HUMAN_VILLAGE) {
                int i = z * WIDTH + x; distance[i] = 0; queue.add(i);
            }
        }
        while (!queue.isEmpty()) {
            int i = queue.remove(), x = i % WIDTH, z = i / WIDTH;
            for (int[] d : DIRECTIONS) {
                int nx = x + d[0], nz = z + d[1];
                if (nx < 0 || nz < 0 || nx >= WIDTH || nz >= WIDTH) continue;
                int n = nz * WIDTH + nx;
                if (distance[n] > distance[i] + 1) { distance[n] = distance[i] + 1; queue.add(n); }
            }
        }
    }

    public int edgeDistance(int x, int z) {
        int gx = Math.max(0, Math.min(WIDTH - 1, Math.floorDiv(x - MIN, STEP)));
        int gz = Math.max(0, Math.min(WIDTH - 1, Math.floorDiv(z - MIN, STEP)));
        return Math.max(0, distance[gz * WIDTH + gx] - 1) * STEP;
    }

    /** Zero at the center, one at the irregular biome edge. */
    public double outward(int x, int z) {
        double radius = Math.hypot(x + 200, z - 100);
        return radius / Math.max(1, radius + edgeDistance(x, z));
    }

    public boolean containsLot(int x, int z, int width, int depth) {
        if (edgeDistance(x + width / 2, z + depth / 2) < EDGE_BUFFER) return false;
        for (int px : new int[]{x - EDGE_BUFFER, x + width / 2, x + width - 1 + EDGE_BUFFER})
            for (int pz : new int[]{z - EDGE_BUFFER, z + depth / 2, z + depth - 1 + EDGE_BUFFER})
                if (layout.regionAt(px, pz) != GensokyoLayout.Region.HUMAN_VILLAGE) return false;
        return true;
    }
}
