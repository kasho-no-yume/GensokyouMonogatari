package com.bitsson.gensokyou.worldgen;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GensokyoLayoutTest {
    private static final int STEP = 32;
    private static final int CELLS = GensokyoLayout.ISLAND_RADIUS * 2 / STEP;
    private final GensokyoLayout layout = new GensokyoLayout(424242);

    @Test
    void allEightRegionCellsAreNonEmptyAndConnectedOnTheSharedMapGrid() {
        for (long seed : new long[]{0, 1, -1, 424242, 987654321}) {
            assertConnected(new GensokyoLayout(seed));
        }
    }

    private static void assertConnected(GensokyoLayout layout) {
        GensokyoLayout.Region[][] grid = new GensokyoLayout.Region[CELLS][CELLS];
        Map<GensokyoLayout.Region, Integer> counts = new EnumMap<>(GensokyoLayout.Region.class);
        Map<GensokyoLayout.Region, Integer> components = new EnumMap<>(GensokyoLayout.Region.class);
        for (int x = 0; x < CELLS; x++) {
            for (int z = 0; z < CELLS; z++) {
                int worldX = -GensokyoLayout.ISLAND_RADIUS + x * STEP + STEP / 2;
                int worldZ = -GensokyoLayout.ISLAND_RADIUS + z * STEP + STEP / 2;
                if (layout.surfaceHeight(worldX, worldZ, 0.0) > GensokyoLayout.SEA_LEVEL) {
                    grid[x][z] = layout.regionAt(worldX, worldZ);
                }
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
            assertEquals(region, layout.regionAt(region.landmarkX(), region.landmarkZ()),
                    region + " landmark lies outside its region");
        }
    }

    @Test
    void seaAndInteriorLakeHaveStableBoundariesAndCourtyardPad() {
        assertNull(layout.regionAt(4600, 0));
        assertTrue(layout.surfaceHeight(4600, 0, 0.0) < GensokyoLayout.SEA_LEVEL);
        assertTrue(layout.isIsland(3600, 0));
        assertEquals(96.0, layout.surfaceHeight(
                GensokyoLayout.COURTYARD_X, GensokyoLayout.COURTYARD_Z, 0.0));
        assertEquals(GensokyoLayout.Region.SCARLET_MANSION, layout.regionAt(1500, -1750));
        assertTrue(layout.isMistyLake(1500, -1750));
        assertTrue(layout.surfaceHeight(1500, -1750, 0.0) < GensokyoLayout.SEA_LEVEL);
    }

    @Test
    void coastAndLakeCrossTheWaterlineAtTheirMappedBoundaries() {
        int seaLevel = GensokyoLayout.SEA_LEVEL;
        for (long seed : new long[]{0, 424242, 987654321}) {
            var seeded = new GensokyoLayout(seed);
            for (int direction : new int[]{-1, 1}) {
                int coastX = direction * GensokyoLayout.ISLAND_RADIUS;
                while (!seeded.isIsland(coastX, 0)) coastX -= direction;
                int lakeX = GensokyoLayout.MISTY_LAKE_X;
                int lakeZ = GensokyoLayout.MISTY_LAKE_Z;
                while (seeded.isMistyLake(lakeX, lakeZ)) lakeX += direction;
                for (double noise : new double[]{-1.0, 0.0, 1.0}) {
                    assertTrue(seeded.surfaceHeight(coastX + 8 * direction, 0, noise) < seaLevel - 1);
                    assertTrue(seeded.surfaceHeight(coastX, 0, noise) >= seaLevel);
                    assertTrue(seeded.surfaceHeight(lakeX - 8 * direction, lakeZ, noise) < seaLevel - 1);
                    assertTrue(seeded.surfaceHeight(lakeX, lakeZ, noise) >= seaLevel);
                }
            }
        }
    }

    @Test
    void landmarkPadsStayLevelAcrossAllSeeds() {
        for (GensokyoLayout.Region region : GensokyoLayout.Region.values()) {
            for (double noise : new double[]{-1.0, -0.5, 0.0, 0.5, 1.0}) {
                double center = layout.surfaceHeight(region.landmarkX(), region.landmarkZ(), noise);
                assertEquals(region.landmarkY(), center, region + " reserved pad center");
                if (region == GensokyoLayout.Region.HAKUREI) {
                    // Hakurei Shrine uses a stepped hillside: flat courtyard/twin-gate plateau (Y=96)
                    // and a 12-block west ramp down to the First Torii foot (3022, 128) at Y=84.
                    assertEquals(96.0, layout.surfaceHeight(GensokyoLayout.TWIN_GATE_X, GensokyoLayout.TWIN_GATE_Z, noise), 0.01);
                    assertEquals(96.0, layout.surfaceHeight(3108, 128, noise), 0.01);
                    assertEquals(84.0, layout.surfaceHeight(3022, 128, noise), 0.01);
                    continue;
                }
                for (int[] offset : new int[][]{{-48, 0}, {48, 0}, {0, -48}, {0, 48}}) {
                    double edge = layout.surfaceHeight(region.landmarkX() + offset[0],
                            region.landmarkZ() + offset[1], noise);
                    assertTrue(Math.abs(center - edge) <= 4.0,
                            region + " reserved pad edge slope is " + Math.abs(center - edge));
                }
            }
        }
    }

    @Test
    void hakureiPilgrimageRoadConnectsFirstToriiToHumanVillage() {
        assertEquals(128.0, layout.hakureiRoadCenterZ(GensokyoLayout.HAKUREI_ROAD_START_X), 0.01);
        assertEquals(100.0, layout.hakureiRoadCenterZ(GensokyoLayout.HAKUREI_ROAD_END_X), 0.01);
        for (int x = GensokyoLayout.HAKUREI_ROAD_END_X; x <= GensokyoLayout.HAKUREI_ROAD_START_X; x += 64) {
            int z = (int) Math.round(layout.hakureiRoadCenterZ(x));
            assertTrue(layout.surfaceHeight(x, z, 0.0) >= GensokyoLayout.SEA_LEVEL + 2.0,
                    "Pilgrimage road dipped into water at x=" + x);
        }
    }

    @Test
    void seedsChangeBothTheCoastAndInlandBoundaries() {
        GensokyoLayout other = new GensokyoLayout(987654321);
        int coastChanges = 0;
        int regionChanges = 0;
        for (int x = -4300; x <= 4300; x += 64) {
            for (int z = -4300; z <= 4300; z += 64) {
                var a = layout.regionAt(x, z);
                var b = other.regionAt(x, z);
                if ((a == null) != (b == null)) coastChanges++;
                else if (a != b) regionChanges++;
            }
        }
        assertTrue(coastChanges > 100);
        assertTrue(regionChanges > 100);
    }

    @Test
    void mountainAndTerracesHaveMoreReliefThanVillageLowlands() {
        assertTrue(reliefAround(GensokyoLayout.Region.MORIYA) > 70);
        assertTrue(reliefAround(GensokyoLayout.Region.HAKUGYOKUROU) > 40);
        assertTrue(reliefAround(GensokyoLayout.Region.HUMAN_VILLAGE) < 32);
    }

    private double reliefAround(GensokyoLayout.Region region) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (int x = region.anchorX() - 700; x <= region.anchorX() + 700; x += 25) {
            for (int z = region.anchorZ() - 700; z <= region.anchorZ() + 700; z += 25) {
                if (layout.regionAt(x, z) == region && layout.coastDistance(x, z) > 380) {
                    double height = layout.surfaceHeight(x, z, 0);
                    min = Math.min(min, height);
                    max = Math.max(max, height);
                }
            }
        }
        return max - min;
    }
}
