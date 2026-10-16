package com.bitsson.gensokyou.danmaku.program.math;

/** Orthonormal right/up/forward frame; camera orientation never enters this value. */
public record Frame(Vec3 origin, Vec3 right, Vec3 up, Vec3 forward) {
    public static final Frame IDENTITY = new Frame(Vec3.ZERO, Vec3.X, Vec3.Y, Vec3.Z);
    public Frame {
        java.util.Objects.requireNonNull(origin);
        if (StrictMath.abs(right.length()-1) > 1e-8 || StrictMath.abs(up.length()-1) > 1e-8
                || StrictMath.abs(forward.length()-1) > 1e-8 || StrictMath.abs(right.dot(up)) > 1e-8
                || right.cross(up).subtract(forward).length() > 1e-8) throw new IllegalArgumentException("orthonormal frame required");
    }
    public static Frame along(Vec3 origin, Vec3 tangent, Vec3 preferredUp) {
        Vec3 f = tangent.unit(), reference = preferredUp.unit();
        if (StrictMath.abs(f.dot(reference)) > .999999) reference = StrictMath.abs(f.dot(Vec3.X)) < .9 ? Vec3.X : Vec3.Z;
        Vec3 r = reference.cross(f).unit();
        return new Frame(origin, r, f.cross(r).unit(), f);
    }
    public Vec3 direction(Vec3 local) { return right.multiply(local.x()).add(up.multiply(local.y())).add(forward.multiply(local.z())); }
    public Vec3 point(Vec3 local) { return origin.add(direction(local)); }
}
