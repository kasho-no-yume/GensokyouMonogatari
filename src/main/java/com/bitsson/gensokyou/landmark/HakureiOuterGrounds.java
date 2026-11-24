package com.bitsson.gensokyou.landmark;

import com.bitsson.gensokyou.Gensokyou;
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
import org.slf4j.Logger;

import java.util.List;

/**
 * Deterministic placement and protection for the small buildings around the main Hakurei Shrine.
 *
 * <p>The main shrine template stays untouched. These three satellites form the first inhabited
 * ring: a roadside teahouse cluster, a northern guest courtyard, and the eastern bell/practice
 * grounds. Each has its own structure template and world-coordinate color pack.</p>
 */
public final class HakureiOuterGrounds {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String SAVED_DATA_NAME = "gensokyou_hakurei_outer_grounds";

    public static final Grounds FRONT = new Grounds(
            Gensokyou.id("hakurei_front_gateway"),
            Gensokyou.id("hakurei_front_gateway_gensokyo"),
            new BlockPos(2976, 78, 96),
            new BlockPos(46, 18, 60));
    public static final Grounds NORTH = new Grounds(
            Gensokyou.id("hakurei_north_guesthouse"),
            Gensokyou.id("hakurei_north_guesthouse_gensokyo"),
            new BlockPos(3040, 82, 62),
            new BlockPos(72, 26, 31));
    public static final Grounds EAST = new Grounds(
            Gensokyou.id("hakurei_east_grounds"),
            Gensokyou.id("hakurei_east_grounds_gensokyo"),
            new BlockPos(3118, 88, 100),
            new BlockPos(48, 32, 92));

    public static final List<Grounds> ALL = List.of(FRONT, NORTH, EAST);
    public static final ResourceKey<Level> GENSOKYO_DIMENSION = ResourceKey.create(
            Registries.DIMENSION, Gensokyou.id("gensokyo"));

    private HakureiOuterGrounds() {}

    public static boolean isGensokyo(LevelAccessor level) {
        return level instanceof Level world && world.dimension().equals(GENSOKYO_DIMENSION);
    }

    public static boolean contains(LevelAccessor level, BlockPos pos) {
        if (!isGensokyo(level)) {
            return false;
        }
        for (Grounds grounds : ALL) {
            if (grounds.contains(pos)) {
                return true;
            }
        }
        return false;
    }

    public static boolean intersectsChunk(int chunkX, int chunkZ) {
        int minX = chunkX << 4;
        int maxX = minX + 15;
        int minZ = chunkZ << 4;
        int maxZ = minZ + 15;
        for (Grounds grounds : ALL) {
            BlockPos end = grounds.end();
            if (maxX >= grounds.origin().getX() && minX <= end.getX()
                    && maxZ >= grounds.origin().getZ() && minZ <= end.getZ()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Places every missing satellite exactly once per world. Safe to call repeatedly; a world
     * created before these structures were added can still receive them when its chunks load.
     */
    public static boolean ensurePlaced(ServerLevel level) {
        if (!level.dimension().equals(GENSOKYO_DIMENSION)) {
            return false;
        }
        State state = level.getDataStorage().computeIfAbsent(State.FACTORY, SAVED_DATA_NAME);
        boolean complete = true;
        for (int index = 0; index < ALL.size(); index++) {
            int bit = 1 << index;
            if ((state.placedMask & bit) != 0) {
                continue;
            }
            Grounds grounds = ALL.get(index);
            StructureTemplate template = level.getStructureManager().get(grounds.id()).orElse(null);
            if (template == null || !template.getSize().equals(grounds.size())) {
                LOGGER.error("Could not load Hakurei outer-grounds structure {}", grounds.id());
                complete = false;
                continue;
            }
            preload(level, grounds);
            boolean placed = template.placeInWorld(
                    level, grounds.origin(), grounds.origin(), new StructurePlaceSettings(),
                    level.getRandom(), Block.UPDATE_ALL);
            if (placed) {
                state.markPlaced(index);
                LOGGER.info("Placed Hakurei outer grounds {} at {}", grounds.id(), grounds.origin().toShortString());
            } else {
                complete = false;
                LOGGER.error("Failed to place Hakurei outer grounds {} at {}",
                        grounds.id(), grounds.origin().toShortString());
            }
        }
        return complete;
    }

    private static void preload(ServerLevel level, Grounds grounds) {
        BlockPos end = grounds.end();
        int minCx = grounds.origin().getX() >> 4;
        int maxCx = end.getX() >> 4;
        int minCz = grounds.origin().getZ() >> 4;
        int maxCz = end.getZ() >> 4;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                level.getChunk(cx, cz);
            }
        }
    }

    public record Grounds(ResourceLocation id, ResourceLocation colorId, BlockPos origin, BlockPos size) {
        public BlockPos end() {
            return origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1);
        }

        public boolean contains(BlockPos pos) {
            BlockPos end = end();
            return pos.getX() >= origin.getX() && pos.getX() <= end.getX()
                    && pos.getY() >= origin.getY() && pos.getY() <= end.getY()
                    && pos.getZ() >= origin.getZ() && pos.getZ() <= end.getZ();
        }
    }

    private static final class State extends SavedData {
        static final Factory<State> FACTORY = new Factory<>(State::new, State::load, null);
        private int placedMask;

        private State() {}

        private static State load(CompoundTag tag, HolderLookup.Provider registries) {
            State state = new State();
            state.placedMask = tag.getInt("placedMask");
            return state;
        }

        private void markPlaced(int index) {
            placedMask |= 1 << index;
            setDirty();
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putInt("placedMask", placedMask);
            return tag;
        }
    }
}
