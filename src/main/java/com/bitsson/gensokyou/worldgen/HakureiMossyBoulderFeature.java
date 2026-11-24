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
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Natural mossy stone outcrop for Hakurei mountain slopes and ridges,
 * occasionally showing exposed cinnabar or spirit iron ore veins.
 */
public final class HakureiMossyBoulderFeature extends Feature<NoneFeatureConfiguration> {
    public static final HakureiMossyBoulderFeature INSTANCE = new HakureiMossyBoulderFeature(NoneFeatureConfiguration.CODEC);

    private HakureiMossyBoulderFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource random = context.random();

        BlockPos ground = origin.below();
        BlockState groundState = level.getBlockState(ground);
        if (!groundState.is(BlockTags.DIRT) && !groundState.is(ModBlocks.GENSOKYO_STONE.get())
                && !groundState.is(ModBlocks.GENSOKYO_GRASS_BLOCK.get())) {
            return false;
        }
        if (!level.getFluidState(origin).isEmpty() || !level.getFluidState(ground).isEmpty()) {
            return false;
        }

        double rx = 1.6 + random.nextDouble() * 1.4;
        double ry = 1.2 + random.nextDouble() * 1.1;
        double rz = 1.6 + random.nextDouble() * 1.4;
        int minX = (int) Math.floor(-rx);
        int maxX = (int) Math.ceil(rx);
        int minZ = (int) Math.floor(-rz);
        int maxZ = (int) Math.ceil(rz);
        int maxY = (int) Math.ceil(ry);

        BlockState gensokyoStone = ModBlocks.GENSOKYO_STONE.get().defaultBlockState();
        BlockState cinnabar = ModBlocks.CINNABAR.get().defaultBlockState();
        BlockState spiritIron = ModBlocks.SPIRIT_IRON_ORE.get().defaultBlockState();
        Block bellTuft = BuiltInRegistries.BLOCK.get(Gensokyou.id("bell_tuft"));

        int oreBudget = random.nextInt(3) == 0 ? 1 + random.nextInt(2) : 0;
        boolean preferCinnabar = random.nextBoolean();
        boolean placedAny = false;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int dx = minX; dx <= maxX; dx++) {
            for (int dz = minZ; dz <= maxZ; dz++) {
                double horiz = (dx * dx) / (rx * rx) + (dz * dz) / (rz * rz);
                if (horiz > 1.05) {
                    continue;
                }
                for (int dy = -1; dy <= maxY; dy++) {
                    double dist = horiz + (dy * dy) / (ry * ry);
                    double jitter = (random.nextDouble() - 0.5) * 0.25;
                    if (dist + jitter > 1.0) {
                        continue;
                    }
                    pos.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    BlockState existing = level.getBlockState(pos);
                    if (!existing.isAir() && !existing.canBeReplaced()
                            && !existing.is(BlockTags.DIRT)
                            && !existing.is(ModBlocks.GENSOKYO_GRASS_BLOCK.get())) {
                        continue;
                    }

                    BlockState chosen;
                    if (oreBudget > 0 && dy >= 0 && dy <= 1 && random.nextInt(7) == 0) {
                        chosen = preferCinnabar ? cinnabar : spiritIron;
                        oreBudget--;
                    } else {
                        int roll = random.nextInt(10);
                        if (roll < 4) {
                            chosen = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
                        } else if (roll < 8) {
                            chosen = gensokyoStone;
                        } else {
                            chosen = Blocks.COBBLESTONE.defaultBlockState();
                        }
                    }
                    level.setBlock(pos, chosen, 2);
                    placedAny = true;
                }
            }
        }

        // Light moss/herb accents on the boulder crown
        if (placedAny && bellTuft != Blocks.AIR) {
            for (int i = 0; i < 3; i++) {
                int dx = random.nextInt(3) - 1;
                int dz = random.nextInt(3) - 1;
                for (int dy = maxY + 1; dy >= 0; dy--) {
                    pos.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    BlockState below = level.getBlockState(pos.below());
                    if (level.getBlockState(pos).isAir()
                            && (below.is(Blocks.MOSSY_COBBLESTONE) || below.is(ModBlocks.GENSOKYO_STONE.get()))) {
                        if (random.nextInt(3) == 0) {
                            level.setBlock(pos, Blocks.MOSS_CARPET.defaultBlockState(), 2);
                        }
                        break;
                    }
                }
            }
        }

        return placedAny;
    }
}
