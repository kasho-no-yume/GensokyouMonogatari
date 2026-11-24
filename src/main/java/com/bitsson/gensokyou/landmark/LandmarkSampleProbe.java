package com.bitsson.gensokyou.landmark;

import com.bitsson.gensokyou.registry.LandmarkBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.util.List;

@EventBusSubscriber(modid = "gensokyou")
public final class LandmarkSampleProbe {
    private LandmarkSampleProbe() {}

    @SubscribeEvent
    public static void verify(ServerStartedEvent event) {
        if (!Boolean.getBoolean("gensokyou.landmarkSampleProbe")) {
            return;
        }
        var server = event.getServer();
        try {
            var level = server.getLevel(LandmarkSample.DIMENSION);
            check(level != null && LandmarkSampleEvents.placeSample(level), "Template could not be placed");
            BlockPos left = new BlockPos(15, 66, 5);
            BlockPos right = left.east();
            check(level.getBlockState(left).is(LandmarkBlocks.WOOD.get()) && level.getBlockState(right).is(LandmarkBlocks.WOOD.get()),
                    "Wall missing across chunk boundary");
            BlockPos door = LandmarkSample.ORIGIN.offset(5, 1, 1);
            LandmarkBlocks.DOOR.get().setOpen(null, level, level.getBlockState(door), door, true);
            check(level.getBlockState(door).getValue(DoorBlock.OPEN), "Door cannot open");
            LandmarkBlocks.DOOR.get().setOpen(null, level, level.getBlockState(door), door, false);
            check(!level.getBlockState(door).getValue(DoorBlock.OPEN), "Door cannot close");
            check(!new PistonStructureResolver(level, left.north(), Direction.SOUTH, true).resolve(), "Piston can push the wall");
            level.explode(null, left.getX() + 0.5, left.getY() + 0.5, left.getZ() - 0.5, 4, Level.ExplosionInteraction.BLOCK);
            check(level.getBlockState(left).is(LandmarkBlocks.WOOD.get()) && level.getBlockState(door).is(LandmarkBlocks.DOOR.get()),
                    "Explosion removed sample blocks");
            for (var block : LandmarkBlocks.tintedBlocks()) {
                var state = block.defaultBlockState();
                check(state.getDestroySpeed(level, left) < 0 && state.getPistonPushReaction() == PushReaction.BLOCK,
                        "Invalid gray block properties: " + block);
            }
            BlockPos interior = LandmarkSample.ORIGIN.offset(3, 2, 2);
            var inside = BlockSnapshot.create(level.dimension(), level, interior);
            var outside = BlockSnapshot.create(level.dimension(), level, LandmarkSample.ORIGIN.north());
            check(EventHooks.onBlockPlace(null, inside, Direction.UP), "Interior placement was allowed");
            check(!EventHooks.onBlockPlace(null, outside, Direction.UP), "Exterior placement was denied");
            check(EventHooks.onMultiBlockPlace(null, List.of(outside, inside), Direction.UP), "Multi-place skipped the second position");
            System.out.println("LANDMARK_SAMPLE_PROBE PASS template, door, blast, piston, placement");
        } finally {
            server.halt(false);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
