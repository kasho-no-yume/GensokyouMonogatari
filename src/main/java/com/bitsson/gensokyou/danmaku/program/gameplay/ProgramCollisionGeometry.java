package com.bitsson.gensokyou.danmaku.program.gameplay;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/** Pure collision geometry. It has no world access and consumes no state. */
public final class ProgramCollisionGeometry {
    private static final double EPSILON = 1.0E-9D;

    private ProgramCollisionGeometry() {}

    /** First intersection of a segment and an expanded AABB. */
    public static Optional<Vec3> segmentAabb(Vec3 start, Vec3 end, AABB box, double radius) {
        if (radius < 0) throw new IllegalArgumentException("negative radius");
        Vec3 delta = end.subtract(start);
        AABB expanded = box.inflate(radius);
        double[] span = {0.0D, 1.0D};
        if (clip(start.x, delta.x, expanded.minX, expanded.maxX, span)
                || clip(start.y, delta.y, expanded.minY, expanded.maxY, span)
                || clip(start.z, delta.z, expanded.minZ, expanded.maxZ, span)) {
            return Optional.empty();
        }
        return Optional.of(start.add(delta.scale(span[0])));
    }

    /** Segment intersection used by a laser or other line-like member. */
    public static Optional<Vec3> rayAabb(Vec3 start, Vec3 end, AABB box, double radius) {
        return segmentAabb(start, end, box, radius);
    }

    /** Exact swept sphere/AABB test, including rounded edges and corners of the Minkowski sum. */
    public static Optional<Vec3> sphereAabb(Vec3 start,Vec3 end,AABB box,double radius) {
        if (!Double.isFinite(radius) || radius<0) throw new IllegalArgumentException("invalid radius");
        Vec3 delta=end.subtract(start);
        double[] origin={start.x,start.y,start.z}, direction={delta.x,delta.y,delta.z};
        double[] min={box.minX,box.minY,box.minZ}, max={box.maxX,box.maxY,box.maxZ};
        java.util.List<Double> cuts=new java.util.ArrayList<>(java.util.List.of(0D,1D));
        for (int axis=0;axis<3;axis++) if (Math.abs(direction[axis])>EPSILON)
            for (double plane:new double[]{min[axis],max[axis]}) {
                double t=(plane-origin[axis])/direction[axis];
                if (t>0 && t<1) cuts.add(t);
            }
        cuts.sort(Double::compare);
        for (int interval=0;interval<cuts.size()-1;interval++) {
            double lo=cuts.get(interval),hi=cuts.get(interval+1),mid=(lo+hi)*.5;
            double a=0,b=0,c=-radius*radius;
            for (int axis=0;axis<3;axis++) {
                double p=origin[axis]+direction[axis]*mid;
                if (p>=min[axis] && p<=max[axis]) continue;
                double offset=origin[axis]-(p<min[axis]?min[axis]:max[axis]);
                a+=direction[axis]*direction[axis]; b+=2*offset*direction[axis]; c+=offset*offset;
            }
            if ((a*lo+b)*lo+c<=EPSILON) return Optional.of(start.add(delta.scale(lo)));
            if (a<=EPSILON) continue;
            double discriminant=b*b-4*a*c;
            if (discriminant<0) continue;
            double enter=(-b-Math.sqrt(discriminant))/(2*a);
            if (enter>=lo-EPSILON && enter<=hi+EPSILON)
                return Optional.of(start.add(delta.scale(Math.max(lo,Math.min(hi,enter)))));
        }
        return Optional.empty();
    }

    private static boolean clip(double origin, double delta, double min, double max, double[] span) {
        if (Math.abs(delta) < EPSILON) return origin < min || origin > max;
        double inverse = 1.0D / delta;
        double enter = (min - origin) * inverse;
        double leave = (max - origin) * inverse;
        if (enter > leave) {
            double swap = enter;
            enter = leave;
            leave = swap;
        }
        span[0] = Math.max(span[0], enter);
        span[1] = Math.min(span[1], leave);
        return span[0] > span[1];
    }
}
