package com.bitsson.gensokyou.worldgen;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.registry.ModBlocks;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Places small wilderness POI structure templates (.nbt) across the Hakurei Shrine biome
 * on a deterministic chunk-grid so POIs are evenly spaced, never overlap the shrine/road,
 * sit solidly on hillsides with a mossy foundation skirt, and bind wilderness loot tables.
 */
public final class HakureiWildernessPoiFeature extends Feature<NoneFeatureConfiguration> {
    public static final HakureiWildernessPoiFeature INSTANCE =
            new HakureiWildernessPoiFeature(NoneFeatureConfiguration.CODEC);

    /** One candidate chunk per 5x5 chunk region (80x80 blocks), inset so adjacent cells never touch. */
    static final int GRID_CHUNKS = 5;

    public static final ResourceLocation WAYSIDE_HOKORA = Gensokyou.id("hakurei_wayside_hokora");
    public static final ResourceLocation BARRIER_STELE = Gensokyou.id("hakurei_barrier_stele");
    public static final ResourceLocation ANCIENT_RITUAL_RUIN = Gensokyou.id("hakurei_ancient_ritual_ruin");
    public static final ResourceLocation MOUNTAIN_TEAHOUSE = Gensokyou.id("hakurei_mountain_teahouse");

    public static final ResourceKey<LootTable> SHRINE_LOOT = ResourceKey.create(
            Registries.LOOT_TABLE, Gensokyou.id("chests/hakurei_wilderness_shrine"));
    public static final ResourceKey<LootTable> RUIN_LOOT = ResourceKey.create(
            Registries.LOOT_TABLE, Gensokyou.id("chests/hakurei_wilderness_ruin"));

    private HakureiWildernessPoiFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    /** Deterministic grid check: true for exactly one chunk inside each GRID_CHUNKS x GRID_CHUNKS cell. */
    public static boolean isCandidateChunk(long seed, int chunkX, int chunkZ) {
        int cellX = Math.floorDiv(chunkX, GRID_CHUNKS);
        int cellZ = Math.floorDiv(chunkZ, GRID_CHUNKS);
        long hash = mix(seed, cellX, cellZ);
        int offsetX = 1 + Math.floorMod((int) (hash >>> 8), GRID_CHUNKS - 2);
        int offsetZ = 1 + Math.floorMod((int) (hash >>> 16), GRID_CHUNKS - 2);
        return chunkX == cellX * GRID_CHUNKS + offsetX && chunkZ == cellZ * GRID_CHUNKS + offsetZ;
    }

    /** Deterministic POI selection with Great Barrier stele bias on the eastern frontier (X >= 3200). */
    public static ResourceLocation pickPoi(long seed, int worldX, int worldZ) {
        long hash = mix(seed ^ 0x517CC1B727220A95L, worldX >> 4, worldZ >> 4);
        int roll = Math.floorMod((int) (hash >>> 24), 100);
        if (worldX >= 3200) {
            if (roll < 55) return BARRIER_STELE;
            if (roll < 75) return WAYSIDE_HOKORA;
            if (roll < 90) return ANCIENT_RITUAL_RUIN;
            return MOUNTAIN_TEAHOUSE;
        }
        if (roll < 34) return WAYSIDE_HOKORA;
        if (roll < 56) return ANCIENT_RITUAL_RUIN;
        if (roll < 78) return MOUNTAIN_TEAHOUSE;
        return BARRIER_STELE;
    }

    private static long mix(long seed, int a, int b) {
        long h = seed ^ (a * 341873128712L) ^ (b * 132897987541L);
        h ^= (h >>> 33);
        h *= 0xff51afd7ed558ccdL;
        h ^= (h >>> 33);
        h *= 0xc4ceb9fe1a85ec53L;
        return h ^ (h >>> 33);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        int chunkX = origin.getX() >> 4;
        int chunkZ = origin.getZ() >> 4;
        long seed = level.getSeed();

        if (!isCandidateChunk(seed, chunkX, chunkZ)) {
            return false;
        }

        int centerX = (chunkX << 4) + 8;
        int centerZ = (chunkZ << 4) + 8;

        // Extra safety clearance from the pilgrimage road and Hakurei Shrine complex
        if (centerX >= 2960 && centerX <= 3180 && centerZ >= 48 && centerZ <= 205) {
            return false;
        }
        GensokyoLayout layout = new GensokyoLayout(seed);
        if (layout.hakureiRoadDistance(centerX, centerZ) <= 14.0) {
            return false;
        }

        ResourceLocation templateId = pickPoi(seed, centerX, centerZ);
        StructureTemplate template = level.getLevel().getStructureManager().get(templateId).orElse(null);
        if (template == null) {
            return false;
        }

        RandomSource random = context.random();
        Rotation rotation = Rotation.values()[random.nextInt(Rotation.values().length)];
        Vec3i rotatedSize = template.getSize(rotation);
        int sx = rotatedSize.getX();
        int sy = rotatedSize.getY();
        int sz = rotatedSize.getZ();

        // Keep the entire footprint strictly inside the current 16x16 chunk
        int minX = (chunkX << 4) + Math.max(1, (16 - sx) / 2);
        int minZ = (chunkZ << 4) + Math.max(1, (16 - sz) / 2);
        int maxX = minX + sx - 1;
        int maxZ = minZ + sz - 1;

        int minSurface = Integer.MAX_VALUE;
        int maxSurface = Integer.MIN_VALUE;
        int sumSurface = 0;
        int samples = 0;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                if (y <= GensokyoLayout.SEA_LEVEL + 2) {
                    return false;
                }
                if (!level.getFluidState(new BlockPos(x, y, z)).isEmpty()) {
                    return false;
                }
                minSurface = Math.min(minSurface, y);
                maxSurface = Math.max(maxSurface, y);
                sumSurface += y;
                samples++;
            }
        }
        if (samples == 0 || maxSurface - minSurface > 4) {
            return false;
        }

        // Seat the Y=0 plinth at the median/average surface height
        int baseY = Math.round((float) sumSurface / samples);

        // Compute rotated placement origin so the template occupies [minX..maxX, baseY..baseY+sy-1, minZ..maxZ]
        BlockPos anchor = computeRotationOrigin(minX, baseY, minZ, template.getSize(), rotation);
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation);

        // Clear small vegetation above the footprint before placing
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = baseY + 1; y <= baseY + sy + 2; y++) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (state.is(BlockTags.LEAVES) || state.is(BlockTags.FLOWERS)
                            || state.is(BlockTags.SMALL_FLOWERS) || state.canBeReplaced()) {
                        level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }

        boolean placed = template.placeInWorld(level, anchor, anchor, settings, random, Block.UPDATE_CLIENTS);
        if (!placed) {
            return false;
        }

        // Fill a mossy stone / dirt foundation skirt under solid base blocks so hillsides never expose air gaps
        BlockState gensokyoDirt = ModBlocks.GENSOKYO_DIRT.get().defaultBlockState();
        BlockState gensokyoStone = ModBlocks.GENSOKYO_STONE.get().defaultBlockState();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockState plinth = level.getBlockState(cursor.set(x, baseY, z));
                if (plinth.isAir() || !plinth.isSolidRender(level, cursor)) {
                    continue;
                }
                for (int depth = 1; depth <= 6; depth++) {
                    cursor.set(x, baseY - depth, z);
                    BlockState below = level.getBlockState(cursor);
                    if (below.isSolidRender(level, cursor) && below.getFluidState().isEmpty()) {
                        break;
                    }
                    BlockState fill = depth == 1
                            ? (random.nextInt(3) == 0 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : gensokyoDirt)
                            : gensokyoStone;
                    level.setBlock(cursor, fill, 2);
                }
            }
        }

        // Bind loot tables to any chest/barrel placed by the template
        ResourceKey<LootTable> lootKey = templateId.equals(ANCIENT_RITUAL_RUIN) ? RUIN_LOOT : SHRINE_LOOT;
        for (int x = minX; x <= maxX; x++) {
            for (int y = baseY; y < baseY + sy; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockEntity be = level.getBlockEntity(cursor.set(x, y, z));
                    if (be instanceof RandomizableContainer container) {
                        container.setLootTable(lootKey, random.nextLong());
                    }
                }
            }
        }

        return true;
    }

    /**
     * Converts a desired world bounding-box min corner (minX, baseY, minZ) into the pivot origin
     * expected by {@link StructureTemplate#placeInWorld} for the given {@link Rotation}.
     */
    static BlockPos computeRotationOrigin(int minX, int baseY, int minZ, Vec3i unrotatedSize, Rotation rotation) {
        int sx = unrotatedSize.getX();
        int sz = unrotatedSize.getZ();
        return switch (rotation) {
            case NONE -> new BlockPos(minX, baseY, minZ);
            case CLOCKWISE_90 -> new BlockPos(minX + sz - 1, baseY, minZ);
            case CLOCKWISE_180 -> new BlockPos(minX + sx - 1, baseY, minZ + sz - 1);
            case COUNTERCLOCKWISE_90 -> new BlockPos(minX, baseY, minZ + sx - 1);
        };
    }
}
