package com.bitsson.gensokyou.worldgen;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/** Leaves room for the landmark pad and for the overhang of nearby crowns. */
public final class LandmarkClearanceFilter extends PlacementFilter {
    private static final GensokyoLayout.Region[] REGIONS = GensokyoLayout.Region.values();
    public static final MapCodec<LandmarkClearanceFilter> CODEC = MapCodec.unit(new LandmarkClearanceFilter());
    public static final PlacementModifierType<LandmarkClearanceFilter> TYPE = () -> CODEC;

    @Override
    protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos pos) {
        for (var region : REGIONS) {
            int radius = region == GensokyoLayout.Region.HAKUREI ? 56 : 80;
            long dx = (long) pos.getX() - region.landmarkX();
            long dz = (long) pos.getZ() - region.landmarkZ();
            if (dx * dx + dz * dz <= radius * radius) return false;
        }
        return true;
    }

    @Override
    public PlacementModifierType<?> type() {
        return TYPE;
    }
}
