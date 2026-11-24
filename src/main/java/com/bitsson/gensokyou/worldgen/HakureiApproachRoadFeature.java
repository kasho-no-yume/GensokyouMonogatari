package com.bitsson.gensokyou.worldgen;

import com.bitsson.gensokyou.registry.ModBlocks;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Generates the 3-stage winding pilgrimage road connecting Hakurei Shrine's First Torii (X=3022)
 * all the way west to Human Village (X=40):
 * 1. Near Shrine (X >= 2780): formal flagstone Sando with paired roadside stone lanterns every 24 blocks.
 * 2. Mid Hills (1500..2780): rustic stepping-stone & worn grass trail with occasional solitary lanterns.
 * 3. Village Outskirts (40..1500): natural countryside dirt path fading into Human Village meadows.
 * Also places milestone torii gates, roadside rest benches, and railed wooden footbridges.
 */
public final class HakureiApproachRoadFeature extends Feature<NoneFeatureConfiguration> {
    public static final HakureiApproachRoadFeature INSTANCE = new HakureiApproachRoadFeature(NoneFeatureConfiguration.CODEC);
    private static final int[] TORII_MILESTONES = {2900, 2580, 2260, 1940, 1620, 1220, 820, 420};

    private HakureiApproachRoadFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    static boolean isToriiMilestone(int x) {
        for (int m : TORII_MILESTONES) {
            if (x == m) {
                return true;
            }
        }
        return false;
    }

    static boolean isRestStopCenter(int x) {
        return x >= 1560 && x <= 2900 && Math.floorMod(x, 240) == 120 && !isToriiMilestone(x);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        int chunkMinX = origin.getX() & ~15;
        int chunkMinZ = origin.getZ() & ~15;
        int chunkMaxX = chunkMinX + 15;
        if (chunkMaxX < GensokyoLayout.HAKUREI_ROAD_END_X || chunkMinX > GensokyoLayout.HAKUREI_ROAD_START_X) {
            return false;
        }

        GensokyoLayout layout = new GensokyoLayout(level.getSeed());
        int midX = Math.max(GensokyoLayout.HAKUREI_ROAD_END_X,
                Math.min(GensokyoLayout.HAKUREI_ROAD_START_X, chunkMinX + 8));
        if (layout.hakureiRoadDistance(midX, chunkMinZ + 8) > 28.0) {
            return false;
        }

        RandomSource random = context.random();
        boolean placedAny = false;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int dx = 0; dx < 16; dx++) {
            int x = chunkMinX + dx;
            if (x < GensokyoLayout.HAKUREI_ROAD_END_X || x >= GensokyoLayout.HAKUREI_ROAD_START_X) {
                continue;
            }
            double centerZ = layout.hakureiRoadCenterZ(x);
            int centerZi = (int) Math.round(centerZ);

            // Determine road stage by distance from Hakurei Shrine
            boolean formalSando = x >= 2780;
            boolean midTrail = x >= 1500 && x < 2780;
            double halfWidth = formalSando ? 2.55 : (midTrail ? 2.05 : 1.75);

            for (int dz = 0; dz < 16; dz++) {
                int z = chunkMinZ + dz;
                double dist = Math.abs((double) z - centerZ);
                if (dist > halfWidth + 2.6) {
                    continue;
                }

                int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                if (surfaceY <= GensokyoLayout.SEA_LEVEL - 2) {
                    continue;
                }
                cursor.set(x, surfaceY, z);
                BlockState existing = level.getBlockState(cursor);

                // Roadside rest bench & signpost on north shoulder (z == centerZi - 4)
                if (z == centerZi - 4 && existing.getFluidState().isEmpty()) {
                    if (isRestStopCenter(x)) {
                        placeRestBench(level, x, surfaceY, z);
                        placedAny = true;
                        continue;
                    }
                    if (isRestStopCenter(x + 1)) {
                        placeRoadsideLantern(level, x, surfaceY, z);
                        placedAny = true;
                        continue;
                    }
                    if (isRestStopCenter(x - 1)) {
                        placeRestSignpost(level, x, surfaceY, z);
                        placedAny = true;
                        continue;
                    }
                }

                // Roadside stone lanterns at regular intervals
                if (formalSando && x % 24 == 0 && (z == centerZi - 3 || z == centerZi + 3)) {
                    placeRoadsideLantern(level, x, surfaceY, z);
                    placedAny = true;
                    continue;
                }
                if (midTrail && x % 56 == 0 && z == centerZi - 3) {
                    placeRoadsideLantern(level, x, surfaceY, z);
                    placedAny = true;
                    continue;
                }

                if (dist > halfWidth) {
                    continue;
                }
                // Natural ragged edges on rustic & countryside segments
                if (!formalSando && dist > halfWidth - 0.55 && random.nextInt(3) == 0) {
                    continue;
                }

                // Water crossing becomes a railed rustic wooden footbridge
                if (!existing.getFluidState().isEmpty()) {
                    level.setBlock(cursor, Blocks.SPRUCE_PLANKS.defaultBlockState(), 2);
                    clearAbove(level, x, surfaceY + 1, z);
                    if (dist > halfWidth - 0.85) {
                        level.setBlock(new BlockPos(x, surfaceY + 1, z), Blocks.SPRUCE_FENCE.defaultBlockState(), 2);
                    }
                    placedAny = true;
                    continue;
                }

                BlockState pave = pickPaving(formalSando, midTrail, dist, random);
                level.setBlock(cursor, pave, 2);
                clearAbove(level, x, surfaceY + 1, z);
                placedAny = true;
            }
        }

        // Place torii after paving so their 3-block-wide stone footings can seat over the road shoulder.
        // Each call writes only this chunk's Z slices; the gate remains seamless across chunk borders.
        for (int milestone : TORII_MILESTONES) {
            if (milestone < chunkMinX || milestone > chunkMaxX) {
                continue;
            }
            int centerZi = (int) Math.round(layout.hakureiRoadCenterZ(milestone));
            int baseY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, milestone, centerZi) - 1;
            BlockPos ground = new BlockPos(milestone, baseY, centerZi);
            if (baseY <= GensokyoLayout.SEA_LEVEL || !level.getFluidState(ground).isEmpty()) {
                continue;
            }
            if (HakureiToriiGenerator.placeSlice(level, milestone, baseY, centerZi, chunkMinZ)) {
                placedAny = true;
            }
        }
        return placedAny;
    }

    private static BlockState pickPaving(boolean formalSando, boolean midTrail, double dist, RandomSource random) {
        if (formalSando) {
            if (dist >= 1.85) {
                return random.nextInt(3) == 0
                        ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState()
                        : Blocks.STONE_BRICKS.defaultBlockState();
            }
            int roll = random.nextInt(10);
            if (roll < 4) return ModBlocks.GENSOKYO_STONE.get().defaultBlockState();
            if (roll < 7) return Blocks.STONE_BRICKS.defaultBlockState();
            if (roll < 9) return Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
            return Blocks.COBBLESTONE.defaultBlockState();
        }
        if (midTrail) {
            int roll = random.nextInt(10);
            if (dist < 1.0 && roll < 4) {
                return ModBlocks.GENSOKYO_STONE.get().defaultBlockState();
            }
            if (roll < 6) return Blocks.DIRT_PATH.defaultBlockState();
            if (roll < 8) return ModBlocks.GENSOKYO_DIRT.get().defaultBlockState();
            return Blocks.COBBLESTONE.defaultBlockState();
        }
        // Countryside village dirt path
        int roll = random.nextInt(10);
        if (roll < 7) return Blocks.DIRT_PATH.defaultBlockState();
        if (roll < 9) return ModBlocks.GENSOKYO_DIRT.get().defaultBlockState();
        return ModBlocks.GENSOKYO_GRASS_BLOCK.get().defaultBlockState();
    }

    private static void clearAbove(WorldGenLevel level, int x, int y, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y, z);
        for (int dy = 0; dy < 3; dy++) {
            pos.setY(y + dy);
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            if (state.canBeReplaced() || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SMALL_FLOWERS)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }

    private static void placeRoadsideLantern(WorldGenLevel level, int x, int surfaceY, int z) {
        BlockPos base = new BlockPos(x, surfaceY + 1, z);
        if (!level.getBlockState(new BlockPos(x, surfaceY, z)).getFluidState().isEmpty()) {
            return;
        }
        level.setBlock(base, Blocks.STONE_BRICKS.defaultBlockState(), 2);
        level.setBlock(base.above(1), Blocks.COBBLESTONE_WALL.defaultBlockState(), 2);
        level.setBlock(base.above(2), Blocks.OCHRE_FROGLIGHT.defaultBlockState(), 2);
        level.setBlock(base.above(3), Blocks.STONE_BRICK_SLAB.defaultBlockState(), 2);
    }

    private static void placeRestBench(WorldGenLevel level, int x, int surfaceY, int z) {
        BlockPos base = new BlockPos(x, surfaceY, z);
        level.setBlock(base, Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), 2);
        level.setBlock(base.above(1),
                Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH), 2);
    }

    private static void placeRestSignpost(WorldGenLevel level, int x, int surfaceY, int z) {
        BlockPos base = new BlockPos(x, surfaceY, z);
        level.setBlock(base, Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), 2);
        level.setBlock(base.above(1), Blocks.SPRUCE_FENCE.defaultBlockState(), 2);
        level.setBlock(base.above(2), Blocks.SPRUCE_FENCE.defaultBlockState(), 2);
        level.setBlock(base.above(3), Blocks.LANTERN.defaultBlockState(), 2);
    }
}
