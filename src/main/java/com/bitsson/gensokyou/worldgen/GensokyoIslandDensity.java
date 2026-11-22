package com.bitsson.gensokyou.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

import java.util.LinkedHashMap;

/** Signed terrain field: positive below the island/ocean floor and negative above it. */
public final class GensokyoIslandDensity implements DensityFunction.SimpleFunction {
    public static final KeyDispatchDataCodec<GensokyoIslandDensity> CODEC = KeyDispatchDataCodec.of(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("terrain_noise")
                            .forGetter(GensokyoIslandDensity::terrainNoise)
            ).apply(instance, GensokyoIslandDensity::new)));

    private static final int COLUMN_CACHE_LIMIT = 2048;
    private final DensityFunction terrainNoise;
    private final ThreadLocal<LinkedHashMap<Long, Double>> columnHeights = ThreadLocal.withInitial(
            () -> new LinkedHashMap<>(COLUMN_CACHE_LIMIT, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<Long, Double> eldest) {
                    return size() > COLUMN_CACHE_LIMIT;
                }
            });

    public GensokyoIslandDensity(DensityFunction terrainNoise) {
        this.terrainNoise = terrainNoise;
    }

    public DensityFunction terrainNoise() {
        return terrainNoise;
    }

    @Override
    public double compute(FunctionContext context) {
        if (GensokyoLayout.isChireidenRiftVoid(context.blockX(), context.blockY(), context.blockZ())) {
            return -1.0;
        }
        int x = context.blockX();
        int z = context.blockZ();
        long key = ((long) x << 32) ^ (z & 0xFFFF_FFFFL);
        double height = columnHeights.get().computeIfAbsent(key,
                ignored -> GensokyoLayout.surfaceHeight(x, z, terrainNoise.compute(context)));
        return height - context.blockY();
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new GensokyoIslandDensity(terrainNoise.mapAll(visitor)));
    }

    @Override
    public double minValue() {
        return -512.0;
    }

    @Override
    public double maxValue() {
        return 512.0;
    }

    @Override
    public KeyDispatchDataCodec<GensokyoIslandDensity> codec() {
        return CODEC;
    }
}
