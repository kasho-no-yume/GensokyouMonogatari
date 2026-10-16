package com.bitsson.gensokyou.danmaku.program.math;

/** Registry-free world/local vector. Units are blocks and ticks. */
public record Vec3(double x, double y, double z) {
    public static final Vec3 ZERO = new Vec3(0, 0, 0);
    public static final Vec3 X = new Vec3(1, 0, 0);
    public static final Vec3 Y = new Vec3(0, 1, 0);
    public static final Vec3 Z = new Vec3(0, 0, 1);

    public Vec3 {
        Deterministic.finite(x, y, z);
        x=x==0?0:x; y=y==0?0:y; z=z==0?0:z;
    }
    public Vec3 add(Vec3 b) { return new Vec3(x + b.x, y + b.y, z + b.z); }
    public Vec3 subtract(Vec3 b) { return add(b.multiply(-1)); }
    public Vec3 multiply(double s) { return new Vec3(x * s, y * s, z * s); }
    public double dot(Vec3 b) { return x * b.x + y * b.y + z * b.z; }
    public Vec3 cross(Vec3 b) { return new Vec3(y*b.z-z*b.y, z*b.x-x*b.z, x*b.y-y*b.x); }
    public double length() { return StrictMath.sqrt(dot(this)); }
    public Vec3 unit() {
        double length = length();
        if (length < 1e-12) throw new IllegalArgumentException("nonzero direction required");
        if (StrictMath.abs(length-1)<1e-14) return this;
        return multiply(1 / length);
    }
    public Vec3 rotate(Vec3 axis, double radians) {
        Vec3 a = axis.unit();
        double c = StrictMath.cos(radians), s = StrictMath.sin(radians);
        return multiply(c).add(a.cross(this).multiply(s)).add(a.multiply(a.dot(this)*(1-c)));
    }
    public Vec3 quantized() {
        return new Vec3(Deterministic.quantize(x), Deterministic.quantize(y), Deterministic.quantize(z));
    }
}
