package com.bitsson.gensokyou.registry;

import com.bitsson.gensokyou.block.LandmarkCurtainBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;

public final class LandmarkBlocks {
    /** Finished-color village surface; deliberately excluded from tintedBlocks(). */
    public static final DeferredBlock<Block> PACKED_EARTH = ModBlocks.BLOCKS.registerBlock("landmark_packed_earth",
            Block::new, solid(SoundType.GRAVEL).mapColor(net.minecraft.world.level.material.MapColor.DIRT));
    public static final DeferredBlock<Block> BRICK = ModBlocks.BLOCKS.registerBlock("landmark_brick", Block::new, solid(SoundType.STONE));
    public static final DeferredBlock<Block> WOOD = ModBlocks.BLOCKS.registerBlock("landmark_wood", Block::new, solid(SoundType.WOOD));
    public static final DeferredBlock<Block> TILE = ModBlocks.BLOCKS.registerBlock("landmark_tile", Block::new, solid(SoundType.STONE));
    public static final DeferredBlock<SlabBlock> SLAB = ModBlocks.BLOCKS.registerBlock("landmark_tile_slab", SlabBlock::new, solid(SoundType.STONE));
    public static final DeferredBlock<StairBlock> STAIR = ModBlocks.BLOCKS.registerBlock("landmark_tile_stairs",
            properties -> new StairBlock(TILE.get().defaultBlockState(), properties), solid(SoundType.STONE));
    public static final DeferredBlock<DoorBlock> DOOR = ModBlocks.BLOCKS.registerBlock("landmark_wood_door",
            properties -> new DoorBlock(BlockSetType.OAK, properties), solid(SoundType.WOOD).noOcclusion());
    public static final DeferredBlock<TrapDoorBlock> TRAPDOOR = ModBlocks.BLOCKS.registerBlock("landmark_wood_trapdoor",
            properties -> new TrapDoorBlock(BlockSetType.OAK, properties), solid(SoundType.WOOD).noOcclusion());
    public static final DeferredBlock<FenceBlock> FENCE = ModBlocks.BLOCKS.registerBlock("landmark_wood_fence",
            FenceBlock::new, solid(SoundType.WOOD).noOcclusion());
    public static final DeferredBlock<WallBlock> WALL = ModBlocks.BLOCKS.registerBlock("landmark_stone_wall",
            WallBlock::new, solid(SoundType.STONE).noOcclusion());
    public static final DeferredBlock<Block> LAMP = ModBlocks.BLOCKS.registerBlock("landmark_lamp",
            Block::new, solid(SoundType.GLASS).lightLevel(state -> 15));

    // Extended Japanese machiya & village architectural blocks
    public static final DeferredBlock<SlabBlock> WOOD_SLAB = ModBlocks.BLOCKS.registerBlock("landmark_wood_slab",
            SlabBlock::new, solid(SoundType.WOOD));
    public static final DeferredBlock<StairBlock> WOOD_STAIRS = ModBlocks.BLOCKS.registerBlock("landmark_wood_stairs",
            properties -> new StairBlock(WOOD.get().defaultBlockState(), properties), solid(SoundType.WOOD));
    public static final DeferredBlock<SlabBlock> BRICK_SLAB = ModBlocks.BLOCKS.registerBlock("landmark_brick_slab",
            SlabBlock::new, solid(SoundType.STONE));
    public static final DeferredBlock<StairBlock> BRICK_STAIRS = ModBlocks.BLOCKS.registerBlock("landmark_brick_stairs",
            properties -> new StairBlock(BRICK.get().defaultBlockState(), properties), solid(SoundType.STONE));
    public static final DeferredBlock<FenceGateBlock> WOOD_FENCE_GATE = ModBlocks.BLOCKS.registerBlock("landmark_wood_fence_gate",
            properties -> new FenceGateBlock(WoodType.OAK, properties), solid(SoundType.WOOD).noOcclusion());
    public static final DeferredBlock<IronBarsBlock> SHOJI = ModBlocks.BLOCKS.registerBlock("landmark_shoji",
            IronBarsBlock::new, solid(SoundType.WOOD).noOcclusion());
    public static final DeferredBlock<LandmarkCurtainBlock> CURTAIN = ModBlocks.BLOCKS.registerBlock("landmark_curtain",
            LandmarkCurtainBlock::new, solid(SoundType.WOOL).noOcclusion().noCollission());
    public static final DeferredBlock<Block> BOOKSHELF = ModBlocks.BLOCKS.registerBlock("landmark_bookshelf",
            Block::new, solid(SoundType.WOOD));
    public static final DeferredBlock<LanternBlock> LANTERN = ModBlocks.BLOCKS.registerBlock("landmark_lantern",
            LanternBlock::new, solid(SoundType.LANTERN).noOcclusion().lightLevel(state -> 15));

    private LandmarkBlocks() {}

    public static void register() {
        // Class initialization attaches the prototype blocks to the existing deferred register.
    }

    public static Block[] tintedBlocks() {
        return new Block[]{
                BRICK.get(), WOOD.get(), TILE.get(), SLAB.get(), STAIR.get(), DOOR.get(),
                TRAPDOOR.get(), FENCE.get(), WALL.get(), LAMP.get(),
                WOOD_SLAB.get(), WOOD_STAIRS.get(), BRICK_SLAB.get(), BRICK_STAIRS.get(),
                WOOD_FENCE_GATE.get(), SHOJI.get(), CURTAIN.get(), BOOKSHELF.get(), LANTERN.get()
        };
    }

    public static boolean contains(BlockState state) {
        return state.is(PACKED_EARTH.get()) || state.is(BRICK.get()) || state.is(WOOD.get()) || state.is(TILE.get())
                || state.is(SLAB.get()) || state.is(STAIR.get()) || state.is(DOOR.get())
                || state.is(TRAPDOOR.get()) || state.is(FENCE.get()) || state.is(WALL.get())
                || state.is(LAMP.get())
                || state.is(WOOD_SLAB.get()) || state.is(WOOD_STAIRS.get())
                || state.is(BRICK_SLAB.get()) || state.is(BRICK_STAIRS.get())
                || state.is(WOOD_FENCE_GATE.get()) || state.is(SHOJI.get())
                || state.is(CURTAIN.get()) || state.is(BOOKSHELF.get()) || state.is(LANTERN.get());
    }

    private static BlockBehaviour.Properties solid(SoundType sound) {
        return BlockBehaviour.Properties.of().strength(-1.0F, 3_600_000.0F)
                .pushReaction(PushReaction.BLOCK).sound(sound).noLootTable();
    }
}
