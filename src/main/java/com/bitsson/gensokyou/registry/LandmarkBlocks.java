package com.bitsson.gensokyou.registry;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;

public final class LandmarkBlocks {
    public static final DeferredBlock<Block> BRICK = ModBlocks.BLOCKS.registerBlock("landmark_brick", Block::new, solid(SoundType.STONE));
    public static final DeferredBlock<Block> WOOD = ModBlocks.BLOCKS.registerBlock("landmark_wood", Block::new, solid(SoundType.WOOD));
    public static final DeferredBlock<Block> TILE = ModBlocks.BLOCKS.registerBlock("landmark_tile", Block::new, solid(SoundType.STONE));
    public static final DeferredBlock<SlabBlock> SLAB = ModBlocks.BLOCKS.registerBlock("landmark_tile_slab", SlabBlock::new, solid(SoundType.STONE));
    public static final DeferredBlock<StairBlock> STAIR = ModBlocks.BLOCKS.registerBlock("landmark_tile_stairs",
            properties -> new StairBlock(TILE.get().defaultBlockState(), properties), solid(SoundType.STONE));
    public static final DeferredBlock<DoorBlock> DOOR = ModBlocks.BLOCKS.registerBlock("landmark_wood_door",
            properties -> new DoorBlock(BlockSetType.OAK, properties), solid(SoundType.WOOD).noOcclusion());

    private LandmarkBlocks() {}

    public static void register() {
        // Class initialization attaches the prototype blocks to the existing deferred register.
    }

    public static Block[] tintedBlocks() {
        return new Block[]{BRICK.get(), WOOD.get(), TILE.get(), SLAB.get(), STAIR.get(), DOOR.get()};
    }

    public static boolean contains(BlockState state) {
        return state.is(BRICK.get()) || state.is(WOOD.get()) || state.is(TILE.get())
                || state.is(SLAB.get()) || state.is(STAIR.get()) || state.is(DOOR.get());
    }

    private static BlockBehaviour.Properties solid(SoundType sound) {
        return BlockBehaviour.Properties.of().strength(-1.0F, 3_600_000.0F)
                .pushReaction(PushReaction.BLOCK).sound(sound).noLootTable();
    }
}
