package com.bitsson.gensokyou.worldgen;

import com.bitsson.gensokyou.registry.ModBlocks;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Rare landmark ancient sacred tree (2x2 sacred wood trunk with flared roots,
 * layered canopy, spirit soil ring, sakura petals, spirit herbs, and a small stone lantern).
 */
public final class HakureiAncientSacredTreeFeature extends Feature<NoneFeatureConfiguration> {
    public static final HakureiAncientSacredTreeFeature INSTANCE =
            new HakureiAncientSacredTreeFeature(NoneFeatureConfiguration.CODEC);

    private HakureiAncientSacredTreeFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource random = context.random();

        int ox = origin.getX();
        int oy = origin.getY();
        int oz = origin.getZ();

        // Validate 2x2 dry dirt/grass footprint with small slope tolerance
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                BlockPos below = new BlockPos(ox + dx, oy - 1, oz + dz);
                BlockState ground = level.getBlockState(below);
                if (!ground.is(BlockTags.DIRT) && !ground.is(ModBlocks.GENSOKYO_GRASS_BLOCK.get())) {
                    return false;
                }
                if (!level.getFluidState(below.above()).isEmpty()) {
                    return false;
                }
            }
        }

        int height = 10 + random.nextInt(4);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // Check vertical clearance for the trunk
        for (int dy = 1; dy <= height + 2; dy++) {
            for (int dx = 0; dx <= 1; dx++) {
                for (int dz = 0; dz <= 1; dz++) {
                    BlockState state = level.getBlockState(pos.set(ox + dx, oy + dy, oz + dz));
                    if (!state.isAir() && !state.canBeReplaced() && !state.is(BlockTags.LEAVES)) {
                        return false;
                    }
                }
            }
        }

        BlockState log = ModBlocks.SACRED_WOOD.get().defaultBlockState();
        BlockState leaves = ModBlocks.SACRED_LEAVES.get().defaultBlockState();
        BlockState spiritSoil = ModBlocks.SPIRIT_SOIL.get().defaultBlockState();
        BlockState sakura = ModBlocks.FALLEN_SAKURA_PETALS.get().defaultBlockState();
        BlockState spiritHerb = ModBlocks.SPIRIT_HERB.get().defaultBlockState();

        // 2x2 trunk + grounding roots
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                level.setBlock(pos.set(ox + dx, oy - 1, oz + dz), ModBlocks.GENSOKYO_DIRT.get().defaultBlockState(), 2);
                for (int dy = 0; dy < height; dy++) {
                    level.setBlock(pos.set(ox + dx, oy + dy, oz + dz), log, 2);
                }
            }
        }

        // Flared surface roots on cardinal sides
        int[][] rootOffsets = {{-1, 0}, {2, 1}, {0, -1}, {1, 2}};
        for (int[] ro : rootOffsets) {
            int rx = ox + ro[0];
            int rz = oz + ro[1];
            int ry = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, rx, rz) - 1;
            if (Math.abs(ry - (oy - 1)) <= 2) {
                level.setBlock(pos.set(rx, ry + 1, rz), log, 2);
                level.setBlock(pos.set(rx, ry, rz), log, 2);
            }
        }

        // Multi-tiered lush canopy
        int canopyStart = height - 4;
        for (int dy = canopyStart; dy <= height + 2; dy++) {
            int layer = dy - canopyStart;
            double radius = switch (layer) {
                case 0 -> 3.4;
                case 1, 2 -> 4.3;
                case 3 -> 3.7;
                case 4 -> 2.8;
                case 5 -> 1.9;
                default -> 1.1;
            };
            int rInt = (int) Math.ceil(radius);
            for (int dx = -rInt; dx <= rInt + 1; dx++) {
                for (int dz = -rInt; dz <= rInt + 1; dz++) {
                    double dist = Math.hypot(dx - 0.5, dz - 0.5);
                    if (dist > radius + (random.nextDouble() - 0.5) * 0.65) {
                        continue;
                    }
                    pos.set(ox + dx, oy + dy, oz + dz);
                    if (level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, leaves, 2);
                    }
                }
            }
        }

        // Ground ring of spirit soil, sakura petals, and spirit herbs
        for (int dx = -3; dx <= 4; dx++) {
            for (int dz = -3; dz <= 4; dz++) {
                double dist = Math.hypot(dx - 0.5, dz - 0.5);
                if (dist < 1.8 || dist > 4.2) {
                    continue;
                }
                int gx = ox + dx;
                int gz = oz + dz;
                int gy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, gx, gz) - 1;
                if (Math.abs(gy - (oy - 1)) > 2) {
                    continue;
                }
                BlockState ground = level.getBlockState(pos.set(gx, gy, gz));
                if (!ground.is(BlockTags.DIRT) && !ground.is(ModBlocks.GENSOKYO_GRASS_BLOCK.get())) {
                    continue;
                }
                if (random.nextInt(4) == 0) {
                    level.setBlock(pos, spiritSoil, 2);
                }
                pos.set(gx, gy + 1, gz);
                if (level.getBlockState(pos).isAir()) {
                    int roll = random.nextInt(10);
                    if (roll < 3) {
                        level.setBlock(pos, sakura, 2);
                    } else if (roll == 3) {
                        level.setBlock(pos, spiritHerb, 2);
                    }
                }
            }
        }

        // Small mossy stone lantern tucked near the southern base of the sacred tree
        int lx = ox - 1;
        int lz = oz + 2;
        int ly = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, lx, lz) - 1;
        if (Math.abs(ly - (oy - 1)) <= 2 && level.getFluidState(new BlockPos(lx, ly, lz)).isEmpty()) {
            BlockPos base = new BlockPos(lx, ly + 1, lz);
            level.setBlock(base, Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), 2);
            level.setBlock(base.above(1), Blocks.COBBLESTONE_WALL.defaultBlockState(), 2);
            level.setBlock(base.above(2), Blocks.LANTERN.defaultBlockState(), 2);
        }

        return true;
    }
}
