package com.bitsson.gensokyou.worldgen;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/** Leaves room for landmark footprints, crown overhangs, and the Hakurei pilgrimage approach road. */
public final class LandmarkClearanceFilter extends PlacementFilter {
    private static final GensokyoLayout.Region[] REGIONS = GensokyoLayout.Region.values();
    public static final MapCodec<LandmarkClearanceFilter> CODEC = MapCodec.unit(new LandmarkClearanceFilter());
    public static final PlacementModifierType<LandmarkClearanceFilter> TYPE = () -> CODEC;

    @Override
    protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos pos) {
        int x = pos.getX();
        int z = pos.getZ();
        // Keep the full grand Hakurei Shrine bounding box (3022..3116, 93..163) + 10-block buffer clear of wild trees.
        if (x >= 3012 && x <= 3126 && z >= 83 && z <= 173) {
            return false;
        }
        // Keep the Hakurei-to-Human-Village pilgrimage road clear of blocking tree trunks.
        if (x >= GensokyoLayout.HAKUREI_ROAD_END_X && x <= GensokyoLayout.HAKUREI_ROAD_START_X) {
            long seed = context.getLevel().getSeed();
            GensokyoLayout layout = new GensokyoLayout(seed);
            if (layout.hakureiRoadDistance(x, z) <= 6.5) {
                return false;
            }
        }
        for (var region : REGIONS) {
            if (region == GensokyoLayout.Region.HAKUREI) {
                continue;
            }
            long dx = (long) x - region.landmarkX();
            long dz = (long) z - region.landmarkZ();
            if (dx * dx + dz * dz <= 80L * 80L) return false;
        }
        return true;
    }

    @Override
    public PlacementModifierType<?> type() {
        return TYPE;
    }
}
