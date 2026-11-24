package com.bitsson.gensokyou.landmark;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

public final class LandmarkSample {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("gensokyou", "landmark_sample_v1");
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, ID);
    public static final BlockPos ORIGIN = new BlockPos(12, 64, 4);
    static final BlockPos END = ORIGIN.offset(11, 6, 7);

    private LandmarkSample() {}

    public static boolean contains(LevelAccessor level, BlockPos pos) {
        return level instanceof Level world && world.dimension().equals(DIMENSION)
                && pos.getX() >= ORIGIN.getX() && pos.getX() <= END.getX()
                && pos.getY() >= ORIGIN.getY() && pos.getY() <= END.getY()
                && pos.getZ() >= ORIGIN.getZ() && pos.getZ() <= END.getZ();
    }
}
