package com.bitsson.gensokyou.client.resident;

import com.bitsson.gensokyou.support.MinecraftTestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ResidentGroundTest {
    @BeforeAll static void bootstrap() { MinecraftTestBootstrap.ensureStarted(); }

    @Test
    void resolvesDryFloorsAndHalfSlabsButRefusesUnprotectedNaturalGround() {
        TestWorld world = new TestWorld();
        ResidentGround ground = new ResidentGround(world);
        var expected = new ResidentRoute.Point(0.5, 72, 0.5);
        assertEquals(expected, ground.resolve(expected, 1, false));
        assertNull(ground.resolve(expected, 1, true));
        world.blocks.put(new BlockPos(0, 71, 0), Blocks.STONE_SLAB.defaultBlockState());
        assertEquals(71.5, ground.resolve(expected, 1, false).y());
    }

    @Test
    void waterLowCeilingsHolesAndMissingChunksNeverBecomeSpawns() {
        TestWorld world = new TestWorld();
        ResidentGround ground = new ResidentGround(world);
        var p = new ResidentRoute.Point(0.5, 72, 0.5);
        world.blocks.put(new BlockPos(0, 72, 0), Blocks.WATER.defaultBlockState());
        assertNull(ground.resolve(p, 1, false));
        world.blocks.put(new BlockPos(0, 72, 0), Blocks.AIR.defaultBlockState());
        world.blocks.put(new BlockPos(0, 73, 0), Blocks.STONE.defaultBlockState());
        assertNull(ground.resolve(p, 1, false));
        world.blocks.clear(); world.blocks.put(new BlockPos(0, 71, 0), Blocks.AIR.defaultBlockState());
        assertNull(ground.resolve(p, 1, false));
        world.blocks.clear(); world.loaded = false;
        assertNull(ground.resolve(p, 1, false));
        assertEquals(0, world.unloadedReads, "must check loaded status before accessing blocks");
    }

    @Test
    void sweptBodyCannotPassWallsButCanStepUpHalfABlock() {
        TestWorld world = new TestWorld();
        ResidentGround ground = new ResidentGround(world);
        var a = new ResidentRoute.Point(0.5, 72, 0.5);
        var b = new ResidentRoute.Point(1.5, 72, 0.5);
        assertTrue(ground.clear(a, b, 1));
        world.blocks.put(new BlockPos(1, 72, 0), Blocks.STONE.defaultBlockState());
        assertFalse(ground.clear(a, b, 1));
        world.blocks.put(new BlockPos(1, 72, 0), Blocks.STONE_SLAB.defaultBlockState());
        assertTrue(ground.clear(a, new ResidentRoute.Point(1.5, 72.5, 0.5), 1));
        var edge = ground.resolve(new ResidentRoute.Point(0.85, 72, 0.5), 1, false);
        assertNotNull(edge);
        assertEquals(72.5, edge.y(), "the back of the footprint still rests on the descending slab");
        var pastEdge = ground.resolve(new ResidentRoute.Point(0.65, 72, 0.5), 1, false);
        assertNotNull(pastEdge);
        assertEquals(72, pastEdge.y());
        assertTrue(ground.clear(edge, pastEdge, 1), "a descending step must not become a permanent blocker");
        world.loaded = false;
        assertFalse(ground.clear(a, b, 1));
    }

    private static final class TestWorld implements ResidentGround.World, BlockGetter {
        final Map<BlockPos, BlockState> blocks = new HashMap<>();
        boolean loaded = true;
        int unloadedReads;
        @Override public BlockGetter blocks() { return this; }
        @Override public boolean loaded(int x, int z) { return loaded; }
        @Override public int minY() { return 64; }
        @Override public int maxY() { return 96; }
        @Override public BlockEntity getBlockEntity(BlockPos p) { return null; }
        @Override public BlockState getBlockState(BlockPos p) {
            if (!loaded) unloadedReads++;
            return blocks.getOrDefault(p, p.getY() == 71 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
        @Override public FluidState getFluidState(BlockPos p) { return getBlockState(p).getFluidState(); }
        @Override public int getHeight() { return 32; }
        @Override public int getMinBuildHeight() { return 64; }
    }
}
