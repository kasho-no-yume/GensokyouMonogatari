package com.bitsson.gensokyou.danmaku.program.ops;

import com.bitsson.gensokyou.danmaku.program.math.*;
import java.util.List;

/** Ordered position/velocity transforms. Collision radii and visual sizes are separate. */
public final class Transforms {
    private Transforms() {}
    public interface Transform {
        Path.State apply(Path.State local, double tick);
        double radiusBound(double localRadius, double untilTick);
        default Vec3 direction(Vec3 direction,double tick) { return direction; }
    }
    public record Rotate(Vec3 axis, ScalarCurve angleRadians, Vec3 pivot) implements Transform {
        public Vec3 direction(Vec3 direction,double tick) { return direction.rotate(axis,angleRadians.at(tick)); }
        public Rotate { axis=axis.unit(); java.util.Objects.requireNonNull(angleRadians); java.util.Objects.requireNonNull(pivot); }
        public Path.State apply(Path.State s,double t) {
            double a=angleRadians.at(t), omega=slope(angleRadians,t);
            Vec3 offset=s.position().subtract(pivot).rotate(axis,a);
            return new Path.State(pivot.add(offset),s.velocity().rotate(axis,a).add(axis.cross(offset).multiply(omega)),s.ended());
        }
        public double radiusBound(double r,double t) { return r+2*pivot.length(); }
    }
    public record Orbit(Vec3 radial, Vec3 axis, ScalarCurve angleRadians) implements Transform {
        public Orbit { axis=axis.unit(); java.util.Objects.requireNonNull(radial); java.util.Objects.requireNonNull(angleRadians); }
        public Path.State apply(Path.State s,double t) {
            Vec3 offset=radial.rotate(axis,angleRadians.at(t));
            return new Path.State(s.position().add(offset),s.velocity().add(axis.cross(offset).multiply(slope(angleRadians,t))),s.ended());
        }
        public double radiusBound(double r,double t) { return r+radial.length(); }
    }
    public record Scale(ScalarCurve scale) implements Transform {
        public Scale { java.util.Objects.requireNonNull(scale); }
        public Path.State apply(Path.State s,double t) { return new Path.State(s.position().multiply(scale.at(t)),s.velocity().multiply(scale.at(t)).add(s.position().multiply(slope(scale,t))),s.ended()); }
        public double radiusBound(double r,double t) { return r*scale.maxAbs(); }
    }
    public record Breathe(double base,double amplitude,double period,double phaseRadians) implements Transform {
        public Breathe { Deterministic.finite(base,amplitude,phaseRadians); Deterministic.positive(period); if (base<StrictMath.abs(amplitude)) throw new IllegalArgumentException("breathing scale cannot be negative"); }
        public Path.State apply(Path.State s,double t) {
            Deterministic.nonnegative(t); double rate=Deterministic.TAU/period,a=rate*t+phaseRadians;
            double scale=base+amplitude*StrictMath.sin(a), derivative=amplitude*rate*StrictMath.cos(a);
            return new Path.State(s.position().multiply(scale),s.velocity().multiply(scale).add(s.position().multiply(derivative)),s.ended());
        }
        public double radiusBound(double r,double t) { return r*(base+StrictMath.abs(amplitude)); }
    }
    public enum VisualMirror { PRESERVE, REFLECT }
    public record Mirror(Vec3 planeNormal, VisualMirror visualOrientation) implements Transform {
        public Vec3 direction(Vec3 direction,double tick) { return direction(direction); }
        public Mirror { planeNormal=planeNormal.unit(); java.util.Objects.requireNonNull(visualOrientation); }
        public Vec3 direction(Vec3 v) { return v.subtract(planeNormal.multiply(2*v.dot(planeNormal))); }
        public Path.State apply(Path.State s,double t) { Deterministic.nonnegative(t); return new Path.State(direction(s.position()),direction(s.velocity()),s.ended()); }
        public double radiusBound(double r,double t) { return r; }
    }
    /** List order is application order: [scale, rotate, translate]. */
    public record Compose(List<Transform> transforms) implements Transform {
        public Vec3 direction(Vec3 direction,double tick) { for (Transform transform:transforms) direction=transform.direction(direction,tick); return direction; }
        public Compose { transforms=List.copyOf(transforms); if (transforms.size()>64) throw new IllegalArgumentException("transform limit"); }
        public Path.State apply(Path.State s,double t) { for (Transform transform:transforms) s=transform.apply(s,t); return s; }
        public double radiusBound(double r,double t) { for (Transform transform:transforms) r=transform.radiusBound(r,t); return r; }
    }
    public record Translate(Path path) implements Transform {
        public Translate { java.util.Objects.requireNonNull(path); }
        public Path.State apply(Path.State s,double t) { Path.State p=path.at(t); return new Path.State(p.position().add(s.position()),p.velocity().add(s.velocity()),p.ended()||s.ended()); }
        public double radiusBound(double r,double t) { return r+path.radiusBound(t); }
    }
    public static double slope(ScalarCurve curve,double t) {
        Deterministic.nonnegative(t);
        var keys=curve.keys();
        for (int i=1;i<keys.size();i++) if (t<keys.get(i).tick()) return (keys.get(i).value()-keys.get(i-1).value())/(keys.get(i).tick()-keys.get(i-1).tick());
        return 0;
    }
    public static Frame orientAlongPath(Path path,double t,Vec3 up,Frame stationaryFallback) {
        Path.State s=path.at(t);
        return s.velocity().length()<1e-12 ? new Frame(s.position(),stationaryFallback.right(),stationaryFallback.up(),stationaryFallback.forward()) : Frame.along(s.position(),s.velocity(),up);
    }
    public static double phaseOffset(double base,int stableIndex,double phaseStep) {
        if (stableIndex<0) throw new IllegalArgumentException("stable index required");
        Deterministic.finite(base,phaseStep); return base+stableIndex*phaseStep;
    }
    public static double stagger(double tick,int stableIndex,double delayStep) {
        Deterministic.nonnegative(tick); Deterministic.nonnegative(delayStep);
        if (stableIndex<0) throw new IllegalArgumentException("stable index required");
        return tick-stableIndex*delayStep;
    }
}
