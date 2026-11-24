package com.bitsson.gensokyou.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Small roadside torii used by the Hakurei pilgrimage approach.
 *
 * <p>The silhouette deliberately echoes the grand shrine torii: a wide stone footing, black-ended
 * nuki, a second beam below the kasagi, and raised roof tips. The three palettes age from lacquered
 * vermilion near the shrine to bare timber in the village outskirts, becoming narrower along the way.
 *
 * <p>Geometry is a pure {@link Map} so its proportions and chunk seams can be unit-tested without a
 * generated world. A gate is one block thick along the road (X); only its footing is three blocks wide.
 * The nuki caps a four-block air opening, keeping the doorway comfortably above the three-block minimum.
 */
final class HakureiToriiGenerator {
    static final int GROUND_Y = 0;
    static final int POST_BOTTOM_Y = 1;
    static final int NUKI_Y = 5;
    static final int SHIMAKI_Y = 6;
    static final int KASAGI_Y = 7;
    static final int TIP_CAP_Y = 8;
    private static final int FOUNDATION_DEPTH = 6;

    private static final Palette FORMAL_PALETTE = new Palette(
            Blocks.RED_CONCRETE,
            Blocks.RED_CONCRETE,
            Blocks.RED_NETHER_BRICK_STAIRS,
            Blocks.BLACK_CONCRETE,
            Blocks.DEEPSLATE_TILES,
            Blocks.DEEPSLATE_TILE_SLAB,
            Blocks.STONE_BRICKS,
            Blocks.STONE_BRICK_SLAB,
            Blocks.STONE_BRICKS,
            Blocks.DARK_OAK_PLANKS,
            Blocks.OCHRE_FROGLIGHT);
    private static final Palette WEATHERED_PALETTE = new Palette(
            Blocks.RED_TERRACOTTA,
            Blocks.RED_TERRACOTTA,
            Blocks.DARK_OAK_STAIRS,
            Blocks.DARK_OAK_LOG,
            Blocks.DARK_OAK_LOG,
            Blocks.DARK_OAK_SLAB,
            Blocks.MOSSY_STONE_BRICKS,
            Blocks.MOSSY_STONE_BRICK_SLAB,
            Blocks.MOSSY_STONE_BRICKS,
            Blocks.DARK_OAK_PLANKS,
            Blocks.DARK_OAK_PLANKS);
    private static final Palette RURAL_PALETTE = new Palette(
            Blocks.SPRUCE_LOG,
            Blocks.SPRUCE_LOG,
            Blocks.SPRUCE_STAIRS,
            Blocks.SPRUCE_LOG,
            Blocks.SPRUCE_LOG,
            Blocks.SPRUCE_SLAB,
            Blocks.COBBLESTONE,
            Blocks.COBBLESTONE_SLAB,
            Blocks.MOSSY_COBBLESTONE,
            Blocks.CHISELED_STONE_BRICKS,
            Blocks.CHISELED_STONE_BRICKS);

    enum Style {
        FORMAL(3, 4, 5, 6, true, FORMAL_PALETTE),
        WEATHERED(3, 4, 4, 5, false, WEATHERED_PALETTE),
        RURAL(2, 3, 3, 4, false, RURAL_PALETTE);

        private final int postHalfWidth;
        private final int nukiHalfWidth;
        private final int shimakiHalfWidth;
        private final int tipHalfWidth;
        private final boolean lanterns;
        private final Palette palette;

        Style(int postHalfWidth, int nukiHalfWidth, int shimakiHalfWidth, int tipHalfWidth,
              boolean lanterns, Palette palette) {
            this.postHalfWidth = postHalfWidth;
            this.nukiHalfWidth = nukiHalfWidth;
            this.shimakiHalfWidth = shimakiHalfWidth;
            this.tipHalfWidth = tipHalfWidth;
            this.lanterns = lanterns;
            this.palette = palette;
        }

        int postHalfWidth() {
            return postHalfWidth;
        }

        int nukiHalfWidth() {
            return nukiHalfWidth;
        }

        int tipHalfWidth() {
            return tipHalfWidth;
        }

        boolean hasLanterns() {
            return lanterns;
        }
    }

    private record Palette(Block pillar, Block beam, Block tipStair, Block trim, Block roof,
                           Block roofSlab, Block footing, Block footingSlab, Block foundation,
                           Block plaqueLower, Block plaqueUpper) {
    }

    private HakureiToriiGenerator() {
    }

    static Style styleFor(int x) {
        if (x >= 2500) {
            return Style.FORMAL;
        }
        if (x >= 1500) {
            return Style.WEATHERED;
        }
        return Style.RURAL;
    }

    /** Builds the complete gate in world coordinates, independently of chunk borders or terrain. */
    static Map<BlockPos, BlockState> build(int x, int baseY, int centerZ) {
        Style style = styleFor(x);
        Palette palette = style.palette;
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();

        BlockState pillar = log(palette.pillar, Direction.Axis.Y);
        BlockState beam = log(palette.beam, Direction.Axis.Z);
        BlockState trim = log(palette.trim, Direction.Axis.Z);
        BlockState roof = log(palette.roof, Direction.Axis.Z);
        BlockState footing = palette.footing.defaultBlockState();
        BlockState footingSlab = palette.footingSlab.defaultBlockState()
                .setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        BlockState tipSlab = palette.roofSlab.defaultBlockState()
                .setValue(SlabBlock.TYPE, SlabType.BOTTOM);

        // Chamfered 3x3 stone pad: solid cross, half-height corners, and a one-block step around the post.
        for (int postDz : new int[]{-style.postHalfWidth, style.postHalfWidth}) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dp = -1; dp <= 1; dp++) {
                    int manhattan = Math.abs(dx) + Math.abs(dp);
                    BlockPos base = new BlockPos(x + dx, baseY + GROUND_Y, centerZ + postDz + dp);
                    blocks.put(base, manhattan <= 1 ? footing : footingSlab);
                    boolean innerStep = dx == 0 && Integer.signum(dp) == -Integer.signum(postDz);
                    if (manhattan == 1 && !innerStep) {
                        blocks.put(base.above(), footingSlab);
                    }
                }
            }
            for (int dy = POST_BOTTOM_Y; dy <= NUKI_Y; dy++) {
                blocks.put(new BlockPos(x, baseY + dy, centerZ + postDz), pillar);
            }
        }

        // Nuki projects one block past each post and finishes with a dark lashed/lacquered end.
        for (int dz = -style.nukiHalfWidth; dz <= style.nukiHalfWidth; dz++) {
            BlockState state = Math.abs(dz) == style.nukiHalfWidth ? trim : beam;
            blocks.put(new BlockPos(x, baseY + NUKI_Y, centerZ + dz), state);
        }

        // A second beam and a wider kasagi keep the roof from reading as a flat rectangular lintel.
        for (int dz = -style.shimakiHalfWidth; dz <= style.shimakiHalfWidth; dz++) {
            blocks.put(new BlockPos(x, baseY + SHIMAKI_Y, centerZ + dz), beam);
        }
        for (int tipDz : new int[]{-style.tipHalfWidth, style.tipHalfWidth}) {
            Direction facing = tipDz < 0 ? Direction.SOUTH : Direction.NORTH;
            blocks.put(new BlockPos(x, baseY + SHIMAKI_Y, centerZ + tipDz),
                    palette.tipStair.defaultBlockState()
                            .setValue(StairBlock.FACING, facing)
                            .setValue(StairBlock.HALF, Half.TOP));
        }
        for (int dz = -style.tipHalfWidth; dz <= style.tipHalfWidth; dz++) {
            blocks.put(new BlockPos(x, baseY + KASAGI_Y, centerZ + dz), roof);
        }
        // Only the ends get a raised cap; the center stays a clean, low roofline.
        for (int dz = -style.tipHalfWidth; dz <= style.tipHalfWidth; dz++) {
            if (Math.abs(dz) >= style.tipHalfWidth - 1) {
                blocks.put(new BlockPos(x, baseY + TIP_CAP_Y, centerZ + dz), tipSlab);
            }
        }

        // Hanging gakuzuka. The formal gate carries a warm paper panel; older gates use wood or stone.
        blocks.put(new BlockPos(x, baseY + NUKI_Y, centerZ), palette.plaqueLower.defaultBlockState());
        blocks.put(new BlockPos(x, baseY + SHIMAKI_Y, centerZ), palette.plaqueUpper.defaultBlockState());

        if (style.hasLanterns()) {
            int lanternDz = style.postHalfWidth - 1;
            BlockState lantern = Blocks.LANTERN.defaultBlockState()
                    .setValue(LanternBlock.HANGING, true);
            for (int dz : new int[]{-lanternDz, lanternDz}) {
                blocks.put(new BlockPos(x, baseY + NUKI_Y - 1, centerZ + dz), lantern);
            }
        }
        return blocks;
    }

    /** Places only the Z slices owned by the current chunk; neighboring chunks produce identical slices. */
    static boolean placeSlice(WorldGenLevel level, int x, int baseY, int centerZ, int chunkMinZ) {
        Style style = styleFor(x);
        Map<BlockPos, BlockState> blocks = build(x, baseY, centerZ);
        int chunkMaxZ = chunkMinZ + 15;
        int minGateZ = Math.max(chunkMinZ, centerZ - style.tipHalfWidth);
        int maxGateZ = Math.min(chunkMaxZ, centerZ + style.tipHalfWidth);
        if (minGateZ > maxGateZ) {
            return false;
        }

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int z = minGateZ; z <= maxGateZ; z++) {
            for (int dy = POST_BOTTOM_Y; dy <= TIP_CAP_Y + 1; dy++) {
                cursor.set(x, baseY + dy, z);
                BlockState state = level.getBlockState(cursor);
                if (state.canBeReplaced() || state.is(BlockTags.LEAVES)
                        || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SMALL_FLOWERS)) {
                    level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }

        boolean placed = false;
        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            BlockPos pos = entry.getKey();
            if (pos.getZ() < chunkMinZ || pos.getZ() > chunkMaxZ) {
                continue;
            }
            if (pos.getY() == baseY + GROUND_Y) {
                supportFooting(level, pos, style.palette.foundation.defaultBlockState());
            }
            level.setBlock(pos, entry.getValue(), 2);
            placed = true;
        }
        return placed;
    }

    private static void supportFooting(WorldGenLevel level, BlockPos base, BlockState foundation) {
        BlockPos.MutableBlockPos cursor = base.mutable();
        for (int depth = 1; depth <= FOUNDATION_DEPTH; depth++) {
            cursor.set(base.getX(), base.getY() - depth, base.getZ());
            BlockState state = level.getBlockState(cursor);
            if (state.isSolidRender(level, cursor) && state.getFluidState().isEmpty()) {
                return;
            }
            level.setBlock(cursor, foundation, 2);
        }
    }

    private static BlockState log(Block block, Direction.Axis axis) {
        BlockState state = block.defaultBlockState();
        return state.hasProperty(RotatedPillarBlock.AXIS)
                ? state.setValue(RotatedPillarBlock.AXIS, axis)
                : state;
    }
}
