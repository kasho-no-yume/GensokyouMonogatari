package com.bitsson.gensokyou.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

public final class HeightBandFilter extends PlacementFilter {
    public static final MapCodec<HeightBandFilter> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.fieldOf("min_y").forGetter(filter -> filter.minY),
            Codec.INT.fieldOf("max_y").forGetter(filter -> filter.maxY)
    ).apply(instance, HeightBandFilter::new));
    public static final PlacementModifierType<HeightBandFilter> TYPE = () -> CODEC;

    private final int minY;
    private final int maxY;

    private HeightBandFilter(int minY, int maxY) {
        this.minY = minY;
        this.maxY = maxY;
    }

    @Override
    protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos pos) {
        return pos.getY() >= minY && pos.getY() <= maxY;
    }

    @Override
    public PlacementModifierType<?> type() {
        return TYPE;
    }
}
