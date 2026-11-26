package com.bitsson.gensokyou.worldgen;

import com.bitsson.gensokyou.entity.FairyEntity;
import com.bitsson.gensokyou.landmark.HakureiOuterGrounds;
import com.bitsson.gensokyou.landmark.HakureiShrineLandmark;
import com.bitsson.gensokyou.registry.ModEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;

/** Natural fairy encounters in Gensokyo: sparse wilderness populations and a quiet shrine approach. */
public final class GensokyoFairySpawns {
    private static final int SPAWN_CHANCE_DIVISOR = 4;
    private static final int POPULATION_RADIUS = 96;
    private static final int MAX_NEARBY_FAIRIES = 6;
    private static final int SHRINE_BUFFER = 32;
    private static final int ROAD_BUFFER = 32;

    private GensokyoFairySpawns() {}

    public static boolean canSpawn(ServerLevelAccessor accessor, MobSpawnType spawnType,
                                   BlockPos pos, RandomSource random) {
        var level = accessor.getLevel();
        if ((spawnType != MobSpawnType.NATURAL && spawnType != MobSpawnType.CHUNK_GENERATION)
                || !level.dimension().equals(HakureiShrineLandmark.GENSOKYO_DIMENSION)) {
            return true;
        }
        if (isProtected(new GensokyoLayout(level.getSeed()), pos.getX(), pos.getZ())
                || random.nextInt(SPAWN_CHANCE_DIVISOR) != 0) {
            return false;
        }

        // Weight alone cannot limit a biome whose only monster is the fairy. Count the whole
        // vertical column so flying fairies still occupy their local population allowance.
        double centerX = pos.getX() + 0.5D;
        double centerZ = pos.getZ() + 0.5D;
        AABB bounds = new AABB(centerX - POPULATION_RADIUS, level.getMinBuildHeight(),
                centerZ - POPULATION_RADIUS, centerX + POPULATION_RADIUS,
                level.getMaxBuildHeight(), centerZ + POPULATION_RADIUS);
        return level.getEntitiesOfClass(FairyEntity.class, bounds, fairy -> {
            double dx = fairy.getX() - centerX;
            double dz = fairy.getZ() - centerZ;
            return fairy.getType() == ModEntityTypes.FAIRY.get() && fairy.isAlive()
                    && dx * dx + dz * dz <= POPULATION_RADIUS * POPULATION_RADIUS;
        }).size() < MAX_NEARBY_FAIRIES;
    }

    /** Horizontal bounds deliberately cover roofs and the air above the buildings as well. */
    static boolean isProtected(GensokyoLayout layout, int x, int z) {
        if (nearFootprint(x, z, HakureiShrineLandmark.ORIGIN, HakureiShrineLandmark.SIZE)) {
            return true;
        }
        for (var grounds : HakureiOuterGrounds.ALL) {
            if (nearFootprint(x, z, grounds.origin(), grounds.size())) {
                return true;
            }
        }

        // The feature uses the world seed for its winding centerline. Clamp X to include a
        // round buffer beyond both ends instead of leaving a spawn seam at the village gate.
        int roadX = Math.clamp(x, GensokyoLayout.HAKUREI_ROAD_END_X, GensokyoLayout.HAKUREI_ROAD_START_X);
        return Math.hypot((double) x - roadX, z - layout.hakureiRoadCenterZ(roadX)) <= ROAD_BUFFER;
    }

    private static boolean nearFootprint(int x, int z, BlockPos origin, BlockPos size) {
        return x >= origin.getX() - SHRINE_BUFFER
                && x <= origin.getX() + size.getX() - 1 + SHRINE_BUFFER
                && z >= origin.getZ() - SHRINE_BUFFER
                && z <= origin.getZ() + size.getZ() - 1 + SHRINE_BUFFER;
    }
}
