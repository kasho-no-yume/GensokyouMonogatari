package com.bitsson.gensokyou.landmark;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

public final class HakureiShrinePreview {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("gensokyou", "hakurei_shrine_preview");
    public static final BlockPos ORIGIN = new BlockPos(128, 64, 128);
    public static final BlockPos SIZE = new BlockPos(95, 38, 71);
    static final BlockPos END = ORIGIN.offset(SIZE.getX() - 1, SIZE.getY() - 1, SIZE.getZ() - 1);

    private HakureiShrinePreview() {}

    public static boolean contains(LevelAccessor level, BlockPos pos) {
        return level instanceof Level world && world.dimension().equals(LandmarkSample.DIMENSION)
                && pos.getX() >= ORIGIN.getX() && pos.getX() <= END.getX()
                && pos.getY() >= ORIGIN.getY() && pos.getY() <= END.getY()
                && pos.getZ() >= ORIGIN.getZ() && pos.getZ() <= END.getZ();
    }
}
