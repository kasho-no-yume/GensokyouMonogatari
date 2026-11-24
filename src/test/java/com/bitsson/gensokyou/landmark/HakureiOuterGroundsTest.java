package com.bitsson.gensokyou.landmark;

import com.bitsson.gensokyou.worldgen.GensokyoLayout;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HakureiOuterGroundsTest {
    @Test
    void satelliteFootprintsDoNotOverlapTheMainShrine() {
        BlockPos mainEnd = HakureiShrineLandmark.END;
        assertTrue(HakureiOuterGrounds.FRONT.end().getX() < HakureiShrineLandmark.ORIGIN.getX());
        assertTrue(HakureiOuterGrounds.NORTH.end().getZ() < HakureiShrineLandmark.ORIGIN.getZ());
        assertTrue(HakureiOuterGrounds.EAST.origin().getX() > mainEnd.getX());
    }

    @Test
    void footprintsUseTheExportedTemplateSizes() {
        assertEquals(new BlockPos(46, 18, 60), HakureiOuterGrounds.FRONT.size());
        assertEquals(new BlockPos(72, 26, 31), HakureiOuterGrounds.NORTH.size());
        assertEquals(new BlockPos(48, 32, 92), HakureiOuterGrounds.EAST.size());
    }

    @Test
    void worldgenPadsMirrorTheExportedFootprints() {
        assertEquals(GensokyoLayout.HAKUREI_FRONT_X, HakureiOuterGrounds.FRONT.origin().getX());
        assertEquals(GensokyoLayout.HAKUREI_FRONT_Z, HakureiOuterGrounds.FRONT.origin().getZ());
        assertEquals(GensokyoLayout.HAKUREI_FRONT_WIDTH, HakureiOuterGrounds.FRONT.size().getX());
        assertEquals(GensokyoLayout.HAKUREI_FRONT_DEPTH, HakureiOuterGrounds.FRONT.size().getZ());
        assertEquals(GensokyoLayout.HAKUREI_NORTH_X, HakureiOuterGrounds.NORTH.origin().getX());
        assertEquals(GensokyoLayout.HAKUREI_NORTH_Z, HakureiOuterGrounds.NORTH.origin().getZ());
        assertEquals(GensokyoLayout.HAKUREI_NORTH_WIDTH, HakureiOuterGrounds.NORTH.size().getX());
        assertEquals(GensokyoLayout.HAKUREI_NORTH_DEPTH, HakureiOuterGrounds.NORTH.size().getZ());
        assertEquals(GensokyoLayout.HAKUREI_EAST_X, HakureiOuterGrounds.EAST.origin().getX());
        assertEquals(GensokyoLayout.HAKUREI_EAST_Z, HakureiOuterGrounds.EAST.origin().getZ());
        assertEquals(GensokyoLayout.HAKUREI_EAST_WIDTH, HakureiOuterGrounds.EAST.size().getX());
        assertEquals(GensokyoLayout.HAKUREI_EAST_DEPTH, HakureiOuterGrounds.EAST.size().getZ());
    }

    @Test
    void protectionBoundsIncludeEdgesButNotNeighbors() {
        assertTrue(HakureiOuterGrounds.FRONT.contains(HakureiOuterGrounds.FRONT.origin()));
        assertTrue(HakureiOuterGrounds.FRONT.contains(HakureiOuterGrounds.FRONT.end()));
        assertFalse(HakureiOuterGrounds.FRONT.contains(HakureiOuterGrounds.FRONT.end().east()));
        assertFalse(HakureiOuterGrounds.NORTH.contains(HakureiOuterGrounds.NORTH.origin().below()));
    }
}
