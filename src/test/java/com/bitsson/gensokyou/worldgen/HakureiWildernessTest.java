package com.bitsson.gensokyou.worldgen;

import com.bitsson.gensokyou.support.MinecraftTestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HakureiWildernessTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.ensureStarted();
    }

    @Test
    void poiGridProducesExactlyOneNonAdjacentCandidatePerCell() {
        long seed = 424242L;
        int grid = HakureiWildernessPoiFeature.GRID_CHUNKS;
        Set<Long> candidates = new HashSet<>();

        for (int cellX = -6; cellX <= 6; cellX++) {
            for (int cellZ = -6; cellZ <= 6; cellZ++) {
                int hits = 0;
                for (int dx = 0; dx < grid; dx++) {
                    for (int dz = 0; dz < grid; dz++) {
                        int cx = cellX * grid + dx;
                        int cz = cellZ * grid + dz;
                        if (HakureiWildernessPoiFeature.isCandidateChunk(seed, cx, cz)) {
                            hits++;
                            candidates.add((((long) cx) << 32) ^ (cz & 0xffffffffL));
                        }
                    }
                }
                assertEquals(1, hits, "Cell (" + cellX + "," + cellZ + ") must have exactly one POI chunk");
            }
        }

        // No two candidate chunks should ever be cardinally or diagonally adjacent
        for (long key : candidates) {
            int cx = (int) (key >> 32);
            int cz = (int) key;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    long neighbor = (((long) (cx + dx)) << 32) ^ ((cz + dz) & 0xffffffffL);
                    assertFalse(candidates.contains(neighbor),
                            "Adjacent POI chunks at (" + cx + "," + cz + ") and (" + (cx + dx) + "," + (cz + dz) + ")");
                }
            }
        }
    }

    @Test
    void poiSelectionCoversAllFourVariantsAndBiasesEasternBarrierFrontier() {
        Map<ResourceLocation, Integer> general = new HashMap<>();
        Map<ResourceLocation, Integer> eastern = new HashMap<>();
        for (int i = 0; i < 400; i++) {
            ResourceLocation g = HakureiWildernessPoiFeature.pickPoi(12345L, 2400 + (i % 20) * 16, -800 + (i / 20) * 16);
            general.merge(g, 1, Integer::sum);
            ResourceLocation e = HakureiWildernessPoiFeature.pickPoi(12345L, 3240 + (i % 20) * 16, -800 + (i / 20) * 16);
            eastern.merge(e, 1, Integer::sum);
        }
        assertEquals(4, general.size(), "All 4 POI templates should appear in general Hakurei wilderness");
        assertTrue(eastern.getOrDefault(HakureiWildernessPoiFeature.BARRIER_STELE, 0) > 160,
                "Eastern barrier frontier (X>=3200) should favor barrier steles, got " + eastern);
    }

    @Test
    void rotationOriginMatchesTemplateBoundingBox() {
        Vec3i size = new Vec3i(5, 6, 7);
        int minX = 100;
        int baseY = 80;
        int minZ = 200;
        for (Rotation rot : Rotation.values()) {
            BlockPos anchor = HakureiWildernessPoiFeature.computeRotationOrigin(minX, baseY, minZ, size, rot);
            int minRx = Integer.MAX_VALUE;
            int maxRx = Integer.MIN_VALUE;
            int minRz = Integer.MAX_VALUE;
            int maxRz = Integer.MIN_VALUE;
            for (int x = 0; x < size.getX(); x++) {
                for (int z = 0; z < size.getZ(); z++) {
                    BlockPos worldPos = anchor.offset(new BlockPos(x, 0, z).rotate(rot));
                    minRx = Math.min(minRx, worldPos.getX());
                    maxRx = Math.max(maxRx, worldPos.getX());
                    minRz = Math.min(minRz, worldPos.getZ());
                    maxRz = Math.max(maxRz, worldPos.getZ());
                }
            }
            assertEquals(minX, minRx, "minX mismatch for " + rot);
            assertEquals(minZ, minRz, "minZ mismatch for " + rot);
            int expectedSx = (rot == Rotation.CLOCKWISE_90 || rot == Rotation.COUNTERCLOCKWISE_90) ? size.getZ() : size.getX();
            int expectedSz = (rot == Rotation.CLOCKWISE_90 || rot == Rotation.COUNTERCLOCKWISE_90) ? size.getX() : size.getZ();
            assertEquals(minX + expectedSx - 1, maxRx, "maxX mismatch for " + rot);
            assertEquals(minZ + expectedSz - 1, maxRz, "maxZ mismatch for " + rot);
        }
    }

    @Test
    void roadMilestonesAndRestStopsNeverOverlap() {
        int toriiCount = 0;
        int restCount = 0;
        for (int x = GensokyoLayout.HAKUREI_ROAD_END_X; x < GensokyoLayout.HAKUREI_ROAD_START_X; x++) {
            boolean torii = HakureiApproachRoadFeature.isToriiMilestone(x);
            boolean rest = HakureiApproachRoadFeature.isRestStopCenter(x);
            assertFalse(torii && rest, "Torii and rest stop overlap at x=" + x);
            if (torii) toriiCount++;
            if (rest) restCount++;
        }
        assertTrue(toriiCount >= 6, "Expected multiple milestone torii gates, got " + toriiCount);
        assertTrue(restCount >= 4, "Expected multiple roadside rest stops, got " + restCount);
    }

    @Test
    void toriiMilestonesLeaveRoomForThreeBlockWideFootingInsideTheirChunk() {
        for (int x = GensokyoLayout.HAKUREI_ROAD_END_X; x < GensokyoLayout.HAKUREI_ROAD_START_X; x++) {
            if (!HakureiApproachRoadFeature.isToriiMilestone(x)) {
                continue;
            }
            int localX = Math.floorMod(x, 16);
            assertTrue(localX >= 1 && localX <= 14,
                    "Torii at x=" + x + " leaves no room for its x-1..x+1 stone footing in one chunk");
        }
    }

    @Test
    void publishedTemplatesAndLootTablesExist() {
        Path resources = Path.of("src/main/resources/data/gensokyou");
        for (String name : new String[]{
                "hakurei_wayside_hokora",
                "hakurei_barrier_stele",
                "hakurei_ancient_ritual_ruin",
                "hakurei_mountain_teahouse"
        }) {
            Path nbt = resources.resolve("structure").resolve(name + ".nbt");
            assertTrue(Files.isRegularFile(nbt), "Missing published template: " + nbt);
        }
        assertTrue(Files.isRegularFile(resources.resolve("loot_table/chests/hakurei_wilderness_shrine.json")));
        assertTrue(Files.isRegularFile(resources.resolve("loot_table/chests/hakurei_wilderness_ruin.json")));
    }
}
