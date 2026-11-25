package com.bitsson.gensokyou.worldgen;

import com.bitsson.gensokyou.landmark.HakureiOuterGrounds;
import com.bitsson.gensokyou.landmark.HakureiShrineLandmark;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GensokyoFairySpawnsTest {
    private final GensokyoLayout layout = new GensokyoLayout(424242L);

    @Test
    void mainShrineAndAllOuterBuildingsHaveClearanceOnEverySide() {
        assertFootprintProtected(HakureiShrineLandmark.ORIGIN, HakureiShrineLandmark.SIZE);
        for (var grounds : HakureiOuterGrounds.ALL) {
            assertFootprintProtected(grounds.origin(), grounds.size());
        }
    }

    private void assertFootprintProtected(BlockPos origin, BlockPos size) {
        int minX = origin.getX();
        int minZ = origin.getZ();
        int maxX = minX + size.getX() - 1;
        int maxZ = minZ + size.getZ() - 1;
        for (int x : new int[]{minX - 32, minX, maxX, maxX + 32}) {
            for (int z : new int[]{minZ - 32, minZ, maxZ, maxZ + 32}) {
                assertTrue(GensokyoFairySpawns.isProtected(layout, x, z),
                        "Shrine building or buffer exposed at " + x + "," + z);
            }
        }
    }

    @Test
    void wholeSeededRoadAndBothShouldersAreProtected() {
        for (long seed : new long[]{0, 1, -1, 424242L, 987654321L}) {
            var seeded = new GensokyoLayout(seed);
            for (int x = GensokyoLayout.HAKUREI_ROAD_END_X;
                 x <= GensokyoLayout.HAKUREI_ROAD_START_X; x++) {
                int centerZ = (int) Math.round(seeded.hakureiRoadCenterZ(x));
                for (int dz : new int[]{-31, 0, 31}) {
                    assertTrue(GensokyoFairySpawns.isProtected(seeded, x, centerZ + dz),
                            "Road exposed at seed=" + seed + " x=" + x + " dz=" + dz);
                }
            }
        }
    }

    @Test
    void villageEndHasARoundBufferWithoutExtendingTheRoadIndefinitely() {
        int endX = GensokyoLayout.HAKUREI_ROAD_END_X;
        assertTrue(GensokyoFairySpawns.isProtected(layout, endX - 32, 100));
        assertTrue(GensokyoFairySpawns.isProtected(layout, endX - 20, 120));
        assertFalse(GensokyoFairySpawns.isProtected(layout, endX - 33, 100));
        assertFalse(GensokyoFairySpawns.isProtected(layout, endX - 32, 101));
    }

    @Test
    void wildernessOutsideTheBuffersStillAllowsFairyEncounters() {
        int roadX = 1500;
        int centerZ = (int) Math.round(layout.hakureiRoadCenterZ(roadX));
        assertFalse(GensokyoFairySpawns.isProtected(layout, roadX, centerZ - 33));
        assertFalse(GensokyoFairySpawns.isProtected(layout, roadX, centerZ + 33));
        assertFalse(GensokyoFairySpawns.isProtected(layout, 3072, 400));
        assertFalse(GensokyoFairySpawns.isProtected(layout, 3198, 170));
        assertFalse(GensokyoFairySpawns.isProtected(layout, -2500, -900));
    }
}
