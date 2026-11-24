package com.bitsson.gensokyou.worldgen;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.registry.ModBlocks;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Small mountain spirit spring (mitarashi-style forest pool) with mossy stone rim,
 * spirit soil banks, misty reeds, spirit herbs, sakura petals, and an occasional lantern.
 */
public final class HakureiSpiritSpringFeature extends Feature<NoneFeatureConfiguration> {
    public static final HakureiSpiritSpringFeature INSTANCE = new HakureiSpiritSpringFeature(NoneFeatureConfiguration.CODEC);

    private HakureiSpiritSpringFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource random = context.random();

        int cx = origin.getX();
        int cz = origin.getZ();
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;

        // Require reasonably level dry ground across the 7x7 basin footprint
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx + dx, cz + dz) - 1;
                if (y <= GensokyoLayout.SEA_LEVEL + 2) {
                    return false;
                }
                if (!level.getFluidState(new BlockPos(cx + dx, y, cz + dz)).isEmpty()) {
                    return false;
                }
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
        }
        if (maxY - minY > 2) {
            return false;
        }

        int waterY = minY;
        double rx = 2.1 + random.nextDouble() * 0.6;
        double rz = 2.1 + random.nextDouble() * 0.6;

        BlockState water = Blocks.WATER.defaultBlockState();
        BlockState mossyCobble = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        BlockState gensokyoStone = ModBlocks.GENSOKYO_STONE.get().defaultBlockState();
        BlockState spiritSoil = ModBlocks.SPIRIT_SOIL.get().defaultBlockState();
        BlockState spiritHerb = ModBlocks.SPIRIT_HERB.get().defaultBlockState();
        BlockState sakuraPetals = ModBlocks.FALLEN_SAKURA_PETALS.get().defaultBlockState();
        BlockState mistyReed = ModBlocks.MISTY_REED.get().defaultBlockState();
        Block mistLilyBlock = BuiltInRegistries.BLOCK.get(Gensokyou.id("mist_lily"));

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int lanternX = cx + (random.nextBoolean() ? 3 : -3);
        int lanternZ = cz + (random.nextBoolean() ? 2 : -2);
        boolean placedLantern = false;

        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                double norm = (dx * dx) / (rx * rx) + (dz * dz) / (rz * rz);
                int x = cx + dx;
                int z = cz + dz;

                if (norm <= 1.0) {
                    // Pond interior: depth 1 near rim, depth 2 in the center
                    int depth = norm < 0.42 ? 2 : 1;
                    for (int dy = 1; dy <= 3; dy++) {
                        pos.set(x, waterY + dy, z);
                        BlockState above = level.getBlockState(pos);
                        if (above.canBeReplaced() || above.is(BlockTags.FLOWERS) || above.is(BlockTags.SMALL_FLOWERS)) {
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                        }
                    }
                    for (int d = 0; d < depth; d++) {
                        level.setBlock(pos.set(x, waterY - d, z), water, 2);
                    }
                    // Sealed stone/spirit-soil basin floor
                    BlockState floor = random.nextInt(4) == 0 ? spiritSoil
                            : (random.nextBoolean() ? mossyCobble : gensokyoStone);
                    level.setBlock(pos.set(x, waterY - depth, z), floor, 2);

                    // Occasional floating mist lily pad
                    if (mistLilyBlock != Blocks.AIR && norm < 0.7 && random.nextInt(8) == 0) {
                        pos.set(x, waterY + 1, z);
                        if (level.getBlockState(pos).isAir()) {
                            level.setBlock(pos, mistLilyBlock.defaultBlockState(), 2);
                        }
                    }
                } else if (norm <= 1.85) {
                    // Bank rim: seal side walls at waterY so water cannot spill
                    int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                    for (int y = waterY - 1; y <= waterY; y++) {
                        pos.set(x, y, z);
                        if (level.getBlockState(pos).isAir() || !level.getFluidState(pos).isEmpty()) {
                            level.setBlock(pos, mossyCobble, 2);
                        }
                    }
                    if (surfaceY <= waterY + 1) {
                        BlockState bankBlock;
                        int roll = random.nextInt(10);
                        if (roll < 4) {
                            bankBlock = spiritSoil;
                        } else if (roll < 8) {
                            bankBlock = mossyCobble;
                        } else {
                            bankBlock = ModBlocks.GENSOKYO_GRASS_BLOCK.get().defaultBlockState();
                        }
                        level.setBlock(pos.set(x, surfaceY, z), bankBlock, 2);

                        // Bankside flora
                        pos.set(x, surfaceY + 1, z);
                        if (level.getBlockState(pos).isAir()) {
                            if (x == lanternX && z == lanternZ && !placedLantern) {
                                placeSpringLantern(level, x, surfaceY, z);
                                placedLantern = true;
                            } else if (bankBlock.is(ModBlocks.SPIRIT_SOIL.get()) && random.nextInt(3) == 0) {
                                int reedHeight = 1 + random.nextInt(2);
                                for (int h = 0; h < reedHeight; h++) {
                                    pos.set(x, surfaceY + 1 + h, z);
                                    if (level.getBlockState(pos).isAir()) {
                                        level.setBlock(pos, mistyReed, 2);
                                    }
                                }
                            } else if (bankBlock.is(ModBlocks.GENSOKYO_GRASS_BLOCK.get())
                                    || bankBlock.is(ModBlocks.SPIRIT_SOIL.get())) {
                                int deco = random.nextInt(8);
                                if (deco == 0) {
                                    level.setBlock(pos, spiritHerb, 2);
                                } else if (deco <= 2) {
                                    level.setBlock(pos, sakuraPetals, 2);
                                }
                            }
                        }
                    }
                }
            }
        }
        return true;
    }

    private static void placeSpringLantern(WorldGenLevel level, int x, int surfaceY, int z) {
        BlockPos base = new BlockPos(x, surfaceY + 1, z);
        level.setBlock(base, Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), 2);
        level.setBlock(base.above(1), Blocks.MOSSY_COBBLESTONE_WALL.defaultBlockState(), 2);
        level.setBlock(base.above(2), Blocks.OCHRE_FROGLIGHT.defaultBlockState(), 2);
        level.setBlock(base.above(3), Blocks.MOSSY_STONE_BRICK_SLAB.defaultBlockState(), 2);
    }
}
