package com.bitsson.gensokyou.landmark;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.registry.ModEntityTypes;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * Deterministic placement, persistence, and protection bounds for the grand Hakurei Shrine
 * inside the {@code gensokyou:gensokyo} dimension.
 *
 * <p>Anchored at {@code (3022, 83, 93)} so that:
 * <ul>
 *   <li>Courtyard center surface {@code (+50, +12, +35)} aligns with {@code (3072, 95, 128)},
 *       giving standing height {@code Y = 96} ({@code GensokyoLayout.COURTYARD_Y}).</li>
 *   <li>North boundary stone dais {@code (+50, +12, +19)} aligns with {@code (3072, 95, 112)}
 *       for the Twin Sukima Gate at {@code (3072, 96, 112)}.</li>
 *   <li>First Torii west entrance {@code (+0, +0, +35)} aligns with {@code (3022, 83, 128)},
 *       joining seamlessly with the long pilgrimage approach road.</li>
 * </ul>
 */
public final class HakureiShrineLandmark {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final ResourceKey<Level> GENSOKYO_DIMENSION = ResourceKey.create(
            Registries.DIMENSION, Gensokyou.id("gensokyo"));
    public static final ResourceLocation COLOR_ID = Gensokyou.id("hakurei_shrine_gensokyo");
    public static final BlockPos ORIGIN = new BlockPos(3022, 83, 93);
    public static final BlockPos REIMU_SPAWN = ORIGIN.offset(48, 13, 35);
    public static final BlockPos SIZE = HakureiShrinePreview.SIZE;
    static final BlockPos END = ORIGIN.offset(SIZE.getX() - 1, SIZE.getY() - 1, SIZE.getZ() - 1);
    private static final String SAVED_DATA_NAME = "gensokyou_hakurei_shrine_landmark";

    private HakureiShrineLandmark() {}

    public static boolean isGensokyo(LevelAccessor level) {
        return level instanceof Level world && world.dimension().equals(GENSOKYO_DIMENSION);
    }

    public static boolean contains(LevelAccessor level, BlockPos pos) {
        return isGensokyo(level)
                && pos.getX() >= ORIGIN.getX() && pos.getX() <= END.getX()
                && pos.getY() >= ORIGIN.getY() && pos.getY() <= END.getY()
                && pos.getZ() >= ORIGIN.getZ() && pos.getZ() <= END.getZ();
    }

    public static boolean intersectsChunk(int chunkX, int chunkZ) {
        int minX = chunkX << 4;
        int maxX = minX + 15;
        int minZ = chunkZ << 4;
        int maxZ = minZ + 15;
        return maxX >= ORIGIN.getX() && minX <= END.getX()
                && maxZ >= ORIGIN.getZ() && minZ <= END.getZ();
    }

    /**
     * Places the Hakurei Shrine exactly once per world in {@code gensokyou:gensokyo}.
     * Safe to call repeatedly; returns immediately once persisted.
     */
    public static boolean ensurePlaced(ServerLevel level) {
        if (!level.dimension().equals(GENSOKYO_DIMENSION)) {
            return false;
        }
        State state = level.getDataStorage().computeIfAbsent(State.FACTORY, SAVED_DATA_NAME);
        if (state.placed) {
            ensureReimuSpawned(level, state);
            return true;
        }
        StructureTemplate template = level.getStructureManager().get(HakureiShrinePreview.ID).orElse(null);
        if (template == null || !template.getSize().equals(SIZE)) {
            LOGGER.error("Could not load Hakurei Shrine structure template {}", HakureiShrinePreview.ID);
            return false;
        }
        // Pre-load all chunks spanned by the shrine so placement never clips across chunk borders.
        int minCx = ORIGIN.getX() >> 4;
        int maxCx = END.getX() >> 4;
        int minCz = ORIGIN.getZ() >> 4;
        int maxCz = END.getZ() >> 4;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                level.getChunk(cx, cz);
            }
        }
        boolean placed = template.placeInWorld(
                level, ORIGIN, ORIGIN, new StructurePlaceSettings(), level.getRandom(), Block.UPDATE_ALL);
        if (placed) {
            state.markPlaced();
            ensureReimuSpawned(level, state);
            LOGGER.info("Placed Hakurei Shrine landmark in gensokyou:gensokyo at {}", ORIGIN.toShortString());
        } else {
            LOGGER.error("Failed to place Hakurei Shrine landmark at {}", ORIGIN.toShortString());
        }
        return placed;
    }

    /** 独立出生标记也为已生成神社的旧存档补上灵梦，重复加载区块不会重复生成。 */
    private static void ensureReimuSpawned(ServerLevel level, State state) {
        if (state.reimuSpawned) {
            return;
        }
        level.getChunk(REIMU_SPAWN.getX() >> 4, REIMU_SPAWN.getZ() >> 4);
        // 加载区块可能触发另一次确保出生的回调，落地前再核对一次。
        if (state.reimuSpawned) {
            return;
        }
        var reimu = ModEntityTypes.REIMU.get().create(level);
        if (reimu != null) {
            reimu.setAnchorPos(Vec3.atBottomCenterOf(REIMU_SPAWN));
            reimu.setYRot(90.0F);
            reimu.setYHeadRot(90.0F);
            if (level.addFreshEntity(reimu)) {
                state.reimuSpawned = true;
                state.setDirty();
                LOGGER.info("Spawned Reimu in the Hakurei Shrine courtyard at {}", REIMU_SPAWN.toShortString());
            }
        }
    }

    private static final class State extends SavedData {
        static final Factory<State> FACTORY = new Factory<>(State::new, State::load, null);
        private boolean placed;
        private boolean reimuSpawned;

        private State() {}

        private static State load(CompoundTag tag, HolderLookup.Provider registries) {
            State state = new State();
            state.placed = tag.getBoolean("placed");
            state.reimuSpawned = tag.getBoolean("reimuSpawned");
            return state;
        }

        private void markPlaced() {
            this.placed = true;
            setDirty();
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putBoolean("placed", placed);
            tag.putBoolean("reimuSpawned", reimuSpawned);
            return tag;
        }
    }
}
