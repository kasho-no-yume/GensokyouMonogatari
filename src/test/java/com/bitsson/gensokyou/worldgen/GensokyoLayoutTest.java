package com.bitsson.gensokyou.worldgen;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GensokyoLayoutTest {
    private static final int STEP = 32;
    private static final int CELLS = GensokyoLayout.ISLAND_RADIUS * 2 / STEP;

    @Test
    void allEightRegionCellsAreNonEmptyAndConnectedOnTheSharedMapGrid() {
        GensokyoLayout.Region[][] grid = new GensokyoLayout.Region[CELLS][CELLS];
        Map<GensokyoLayout.Region, Integer> counts = new EnumMap<>(GensokyoLayout.Region.class);
        Map<GensokyoLayout.Region, Integer> components = new EnumMap<>(GensokyoLayout.Region.class);
        for (int x = 0; x < CELLS; x++) {
            for (int z = 0; z < CELLS; z++) {
                int worldX = -GensokyoLayout.ISLAND_RADIUS + x * STEP + STEP / 2;
                int worldZ = -GensokyoLayout.ISLAND_RADIUS + z * STEP + STEP / 2;
                grid[x][z] = GensokyoLayout.regionAt(worldX, worldZ);
                if (grid[x][z] != null) {
                    counts.merge(grid[x][z], 1, Integer::sum);
                }
            }
        }

        boolean[][] visited = new boolean[CELLS][CELLS];
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int x = 0; x < CELLS; x++) {
            for (int z = 0; z < CELLS; z++) {
                GensokyoLayout.Region region = grid[x][z];
                if (region == null || visited[x][z]) {
                    continue;
                }
                components.merge(region, 1, Integer::sum);
                ArrayDeque<int[]> queue = new ArrayDeque<>();
                queue.add(new int[]{x, z});
                visited[x][z] = true;
                while (!queue.isEmpty()) {
                    int[] cell = queue.removeFirst();
                    for (int[] direction : directions) {
                        int nx = cell[0] + direction[0];
                        int nz = cell[1] + direction[1];
                        if (nx >= 0 && nx < CELLS && nz >= 0 && nz < CELLS
                                && !visited[nx][nz] && grid[nx][nz] == region) {
                            visited[nx][nz] = true;
                            queue.addLast(new int[]{nx, nz});
                        }
                    }
                }
            }
        }

        for (GensokyoLayout.Region region : GensokyoLayout.Region.values()) {
            assertTrue(counts.getOrDefault(region, 0) > 0, region + " region is empty");
            assertEquals(1, components.getOrDefault(region, 0), region + " region is disconnected");
            assertEquals(region, GensokyoLayout.regionAt(region.landmarkX(), region.landmarkZ()),
                    region + " landmark lies outside its region");
        }
    }

    @Test
    void seaAndInteriorLakeHaveStableBoundariesAndCourtyardPad() {
        assertNull(GensokyoLayout.regionAt(4600, 0));
        assertTrue(GensokyoLayout.surfaceHeight(4096, 0, 0.0) < GensokyoLayout.SEA_LEVEL);
        assertEquals(96.0, GensokyoLayout.surfaceHeight(
                GensokyoLayout.COURTYARD_X, GensokyoLayout.COURTYARD_Z, 0.0));
        assertNotNull(GensokyoLayout.regionAt(1500, -1750));
        assertTrue(GensokyoLayout.isMistyLake(1500, -1750));
        assertTrue(GensokyoLayout.surfaceHeight(1500, -1750, 0.0) < GensokyoLayout.SEA_LEVEL);
    }

    @Test
    void landmarkPadsStayLevelAcrossAllSeeds() {
        for (GensokyoLayout.Region region : GensokyoLayout.Region.values()) {
            for (double noise : new double[]{-1.0, -0.5, 0.0, 0.5, 1.0}) {
                double center = GensokyoLayout.surfaceHeight(region.landmarkX(), region.landmarkZ(), noise);
                assertEquals(region.landmarkY(), center, region + " reserved pad center");
                for (int[] offset : new int[][]{{-48, 0}, {48, 0}, {0, -48}, {0, 48}}) {
                    double edge = GensokyoLayout.surfaceHeight(region.landmarkX() + offset[0],
                            region.landmarkZ() + offset[1], noise);
                    assertTrue(Math.abs(center - edge) <= 4.0,
                            region + " reserved pad edge slope is " + Math.abs(center - edge));
                }
            }
        }
    }
}
