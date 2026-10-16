package com.bitsson.gensokyou.danmaku.program.math;

import java.util.List;

/** Version 1 numerical policy: StrictMath, 2^-20 block output grid, indexed SplitMix64. */
public final class Deterministic {
    public static final double TAU = 2 * StrictMath.PI;
    public static final double GRID = 0x1.0p-20;
    private Deterministic() {}
    public static void finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) throw new IllegalArgumentException("finite value required");
    }
    public static double nonnegative(double value) {
        finite(value);
        if (value < 0) throw new IllegalArgumentException("nonnegative value required");
        return value;
    }
    public static double positive(double value) {
        finite(value);
        if (value <= 0) throw new IllegalArgumentException("positive value required");
        return value;
    }
    public static double quantize(double value) {
        finite(value);
        double result = StrictMath.rint(value / GRID) * GRID;
        finite(result);
        return result == 0 ? 0 : result;
    }
    public static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
    /** Full immutable lineage; removing a sibling never advances another member's RNG. */
    public record Seed(long root, List<Integer> lineage) {
        public Seed {
            lineage = List.copyOf(lineage);
            if (lineage.size() > 64 || lineage.stream().anyMatch(i -> i < 0)) throw new IllegalArgumentException("invalid lineage");
        }
        public Seed child(int ordinal) {
            var next = new java.util.ArrayList<>(lineage);
            next.add(ordinal);
            return new Seed(root, next);
        }
        public long bits(long channel, long index) {
            long value = mix(root ^ 0x9e3779b97f4a7c15L);
            for (int generation = 0; generation < lineage.size(); generation++) {
                value = mix(value ^ mix(((long) generation << 32) | lineage.get(generation)));
            }
            return mix(value ^ mix(channel) ^ mix(index + 0x9e3779b97f4a7c15L));
        }
        public double unit(long channel, long index) { return (bits(channel, index) >>> 11) * 0x1.0p-53; }
    }
    public enum Distribution { UNIFORM_SPHERE, CONE, PLANE_CIRCLE }
    public static Vec3 seedDirection(Seed seed, long channel, long index, Distribution distribution,
                                     Vec3 axis, double coneRadians) {
        finite(coneRadians);
        if (coneRadians < 0 || coneRadians > StrictMath.PI) throw new IllegalArgumentException("cone in [0,pi]");
        double azimuth = TAU * seed.unit(channel, Math.multiplyExact(index, 2));
        double v = seed.unit(channel, Math.addExact(Math.multiplyExact(index, 2), 1));
        double z = switch (distribution) {
            case UNIFORM_SPHERE -> 1 - 2*v;
            case CONE -> 1 - v*(1-StrictMath.cos(coneRadians));
            case PLANE_CIRCLE -> 0;
        };
        double r = StrictMath.sqrt(StrictMath.max(0, 1-z*z));
        Frame frame = Frame.along(Vec3.ZERO, axis, Vec3.Y);
        return frame.direction(new Vec3(r*StrictMath.cos(azimuth), r*StrictMath.sin(azimuth), z));
    }
}
