package com.bitsson.gensokyou.danmaku.program.ops;

import com.bitsson.gensokyou.danmaku.program.math.*;
import java.util.ArrayList;
import java.util.List;

/** Closed-form paths. All angular inputs are radians, all times ticks. */
public final class Paths {
    private Paths() {}
    public record Hold() implements Path {
        public State at(double t) { Deterministic.nonnegative(t); return new State(Vec3.ZERO,Vec3.ZERO,false); }
        public double radiusBound(double t) { return Deterministic.nonnegative(t)*0; }
    }
    public record Linear(Vec3 direction, double speed) implements Path {
        public Linear { direction=direction.unit(); Deterministic.finite(speed); }
        public State at(double t) { Deterministic.nonnegative(t); Vec3 v=direction.multiply(speed); return new State(v.multiply(t),v,false); }
        public double radiusBound(double t) { return StrictMath.abs(speed)*Deterministic.nonnegative(t); }
    }
    public record SpeedCurve(Vec3 direction, ScalarCurve speed) implements Path {
        public SpeedCurve { direction=direction.unit(); java.util.Objects.requireNonNull(speed); }
        public State at(double t) { return new State(direction.multiply(speed.integral(t)),direction.multiply(speed.at(t)),false); }
        public double radiusBound(double t) { return speed.maxAbs()*Deterministic.nonnegative(t); }
    }
    public record Arc(Vec3 tangent, Vec3 axis, double radiansPerTick, double speed) implements Path {
        public Arc { tangent=tangent.unit(); axis=axis.unit(); Deterministic.finite(radiansPerTick,speed); }
        public State at(double t) {
            Deterministic.nonnegative(t);
            double angle=radiansPerTick*t;
            if (radiansPerTick == 0) return new Linear(tangent,speed).at(t);
            Vec3 parallel=axis.multiply(axis.dot(tangent)), perpendicular=tangent.subtract(parallel);
            // sinc form avoids cancellation for very small angles.
            double sinc=angle == 0 ? 1 : StrictMath.sin(angle)/angle;
            double halfSin=StrictMath.sin(angle/2);
            double cosc=angle == 0 ? 0 : 2*halfSin*halfSin/angle;
            Vec3 p=parallel.multiply(t).add(perpendicular.multiply(t*sinc)).add(axis.cross(tangent).multiply(t*cosc)).multiply(speed);
            return new State(p,tangent.rotate(axis,angle).multiply(speed),false);
        }
        public double radiusBound(double t) { return StrictMath.abs(speed)*Deterministic.nonnegative(t); }
    }
    public enum Tail { CONTINUE, HOLD, END }
    public record Segment(String label, double duration, Path path) {
        public Segment {
            if (label == null || label.isBlank()) throw new IllegalArgumentException("segment label required");
            Deterministic.positive(duration); java.util.Objects.requireNonNull(path);
            if (path.at(0).position().length() > 1e-9) throw new IllegalArgumentException("segment must start at zero");
        }
    }
    /** Prefix states are baked once; seeking never integrates previous ticks. */
    public static final class Segments implements Path {
        private final List<Segment> segments;
        private final Tail tail;
        private final double[] ends;
        private final Vec3[] starts;
        public Segments(List<Segment> segments, Tail tail) {
            this.segments=List.copyOf(segments); this.tail=java.util.Objects.requireNonNull(tail);
            if (segments.isEmpty() || segments.size()>64 || segments.stream().map(Segment::label).distinct().count()!=segments.size()) throw new IllegalArgumentException("1..64 uniquely labelled segments required");
            ends=new double[segments.size()]; starts=new Vec3[segments.size()+1]; starts[0]=Vec3.ZERO;
            for (int i=0;i<segments.size();i++) {
                Segment s=segments.get(i);
                if (s.path().at(s.duration()).ended()) throw new IllegalArgumentException("segment contains an ending path; use a non-ending inner tail and terminate the outer sequence");
                ends[i]=(i==0 ? 0 : ends[i-1])+s.duration();
                Deterministic.finite(ends[i]);
                starts[i+1]=starts[i].add(s.path().at(s.duration()).position());
            }
        }
        public List<Segment> segments() { return segments; }
        public Tail tail() { return tail; }
        public double duration() { return ends[ends.length-1]; }
        public double endOf(String label) {
            for (int i=0;i<segments.size();i++) if (segments.get(i).label().equals(label)) return ends[i];
            throw new IllegalArgumentException("unknown segment "+label);
        }
        public State at(double t) {
            Deterministic.nonnegative(t);
            for (int i=0;i<ends.length;i++) if (t<ends[i]) {
                State s=segments.get(i).path().at(t-(i==0?0:ends[i-1]));
                return new State(starts[i].add(s.position()),s.velocity(),s.ended());
            }
            Segment last=segments.getLast();
            Vec3 v=tail==Tail.CONTINUE ? last.path().at(last.duration()).velocity() : Vec3.ZERO;
            return new State(starts[segments.size()].add(v.multiply(t-duration())),v,tail==Tail.END);
        }
        public double radiusBound(double t) {
            Deterministic.nonnegative(t);
            double bound=0,elapsed=0;
            for (Segment s:segments) {
                double dt=StrictMath.min(s.duration(),StrictMath.max(0,t-elapsed));
                bound+=s.path().radiusBound(dt); elapsed+=s.duration();
            }
            if (t>duration() && tail==Tail.CONTINUE) bound+=segments.getLast().path().at(segments.getLast().duration()).velocity().length()*(t-duration());
            return bound;
        }
    }
    public enum TimeMap {
        LINEAR, SMOOTHSTEP;
        double value(double u) { return this==LINEAR ? u : u*u*(3-2*u); }
        double derivative(double u) { return this==LINEAR ? 1 : 6*u*(1-u); }
    }
    /** Parameter-time Bezier, deliberately not constant arc-length speed. */
    public record Bezier(List<Vec3> controls, double duration, TimeMap timeMap, Tail tail) implements Path {
        public Bezier {
            controls=List.copyOf(controls); Deterministic.positive(duration);
            if (controls.size()!=3 && controls.size()!=4) throw new IllegalArgumentException("quadratic/cubic controls required");
            java.util.Objects.requireNonNull(timeMap); java.util.Objects.requireNonNull(tail);
        }
        private static Vec3 interpolate(List<Vec3> points,double u) {
            var work=new ArrayList<>(points);
            for (int n=work.size()-1;n>0;n--) for (int i=0;i<n;i++) work.set(i,work.get(i).multiply(1-u).add(work.get(i+1).multiply(u)));
            return work.getFirst();
        }
        public State at(double t) {
            Deterministic.nonnegative(t);
            double raw=StrictMath.min(1,t/duration), u=timeMap.value(raw);
            var derivative=new ArrayList<Vec3>();
            for (int i=1;i<controls.size();i++) derivative.add(controls.get(i).subtract(controls.get(i-1)).multiply(controls.size()-1));
            Vec3 p=interpolate(controls,u), v=interpolate(derivative,u).multiply(timeMap.derivative(raw)/duration);
            if (t>=duration) {
                if (tail==Tail.CONTINUE) p=p.add(v.multiply(t-duration)); else v=Vec3.ZERO;
            }
            return new State(p,v,t>=duration && tail==Tail.END);
        }
        public double radiusBound(double t) {
            Deterministic.nonnegative(t);
            double max=controls.stream().mapToDouble(Vec3::length).max().orElseThrow();
            return max+(tail==Tail.CONTINUE ? at(duration).velocity().length()*StrictMath.max(0,t-duration):0);
        }
    }
    public record WaveOffset(Path base, Vec3 axis, double amplitude, double period, double phaseRadians) implements Path {
        public WaveOffset { java.util.Objects.requireNonNull(base); axis=axis.unit(); Deterministic.finite(amplitude,phaseRadians); Deterministic.positive(period); }
        public State at(double t) {
            State b=base.at(t); double rate=Deterministic.TAU/period,a=rate*t+phaseRadians;
            return new State(b.position().add(axis.multiply(amplitude*StrictMath.sin(a))),b.velocity().add(axis.multiply(amplitude*rate*StrictMath.cos(a))),b.ended());
        }
        public double radiusBound(double t) { return base.radiusBound(t)+StrictMath.abs(amplitude); }
    }
    /** Effective time uses the old analytic world pose, not last rendered coordinates. */
    public record Rebase(Path before, double effectiveTick, Path after, boolean discontinuousVelocity) implements Path {
        public Rebase {
            Deterministic.nonnegative(effectiveTick); java.util.Objects.requireNonNull(before); java.util.Objects.requireNonNull(after);
            if (!discontinuousVelocity && before.at(effectiveTick).velocity().subtract(after.at(0).velocity()).length()>1e-8) throw new IllegalArgumentException("velocity discontinuity must be declared");
        }
        public State at(double t) {
            Deterministic.nonnegative(t);
            if (t<effectiveTick) return before.at(t);
            State next=after.at(t-effectiveTick);
            return new State(before.at(effectiveTick).position().add(next.position().subtract(after.at(0).position())),next.velocity(),next.ended());
        }
        public double radiusBound(double t) { return before.radiusBound(StrictMath.min(t,effectiveTick))+(t>=effectiveTick?after.radiusBound(t-effectiveTick)+after.at(0).position().length():0); }
    }
}
