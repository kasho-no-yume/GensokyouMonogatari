package com.bitsson.gensokyou.worldgen;

import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/** Shares one world-specific layout through the noise router's continentalness channel. */
public final class GensokyoLayoutDensity implements DensityFunction.SimpleFunction {
    public static final KeyDispatchDataCodec<GensokyoLayoutDensity> CODEC = KeyDispatchDataCodec.of(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("seed_noise").forGetter(field -> field.seedNoise)
            ).apply(instance, GensokyoLayoutDensity::new)));

    private final DensityFunction seedNoise;
    private final GensokyoLayout layout;

    public GensokyoLayoutDensity(DensityFunction seedNoise) {
        this.seedNoise = seedNoise;
        long first = Double.doubleToLongBits(seedNoise.compute(new SinglePointContext(1024, 0, -2048)));
        long second = Double.doubleToLongBits(seedNoise.compute(new SinglePointContext(-8192, 0, 4096)));
        this.layout = new GensokyoLayout(first ^ Long.rotateLeft(second, 27));
    }

    public static GensokyoLayout layoutOf(DensityFunction function) {
        if (function instanceof GensokyoLayoutDensity field) {
            return field.layout;
        }
        if (function instanceof net.minecraft.world.level.levelgen.DensityFunctions.HolderHolder holder) {
            return layoutOf(holder.function().value());
        }
        throw new IllegalStateException("Gensokyo noise router is missing its layout field");
    }

    @Override
    public double compute(FunctionContext context) {
        double distance = layout.coastDistance(context.blockX(), context.blockZ());
        return Math.max(-1.0, Math.min(1.0, distance / 900.0));
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        DensityFunction wired = seedNoise.mapAll(visitor);
        return visitor.apply(wired.equals(seedNoise) ? this : new GensokyoLayoutDensity(wired));
    }

    @Override
    public double minValue() {
        return -1.0;
    }

    @Override
    public double maxValue() {
        return 1.0;
    }

    @Override
    public KeyDispatchDataCodec<GensokyoLayoutDensity> codec() {
        return CODEC;
    }
}
