package com.bitsson.gensokyou.danmaku.program.ops;

import com.bitsson.gensokyou.danmaku.program.math.*;

/** Pure member world motion, including hierarchy clocks and retained cancellation ownership. */
public final class MemberMotion {
    private MemberMotion() {}
    public record Ownership(String owner,String card,long cycle,String group) {
        public Ownership { if (owner==null || card==null || group==null) throw new IllegalArgumentException("ownership required"); }
    }
    public record Attached(Pattern.Member member, Frame frame, Path groupPath,
                           Transforms.Transform localTransform, double timeOrigin,
                           double delayStep, Ownership ownership, double groupOrigin,
                           com.bitsson.gensokyou.danmaku.program.TimeDomain transformClock,
                           java.util.List<Layer> parents) implements Path {
        public Attached(Pattern.Member member,Frame frame,Path groupPath,Transforms.Transform localTransform,
                        double timeOrigin,double delayStep,Ownership ownership) {
            this(member,frame,groupPath,localTransform,timeOrigin,delayStep,ownership,timeOrigin,
                    com.bitsson.gensokyou.danmaku.program.TimeDomain.MEMBER_AGE,java.util.List.of());
        }
        public Attached {
            java.util.Objects.requireNonNull(member); java.util.Objects.requireNonNull(frame);
            java.util.Objects.requireNonNull(groupPath); java.util.Objects.requireNonNull(localTransform);
            java.util.Objects.requireNonNull(ownership); Deterministic.finite(timeOrigin); Deterministic.nonnegative(delayStep);
            Deterministic.finite(groupOrigin); java.util.Objects.requireNonNull(transformClock);
            if (transformClock!=com.bitsson.gensokyou.danmaku.program.TimeDomain.GROUP && transformClock!=com.bitsson.gensokyou.danmaku.program.TimeDomain.MEMBER_AGE) throw new IllegalArgumentException("group/member transform clock required");
            parents=java.util.List.copyOf(parents); if (parents.size()>8) throw new IllegalArgumentException("parent depth limit");
        }
        public State at(double tick) {
            Deterministic.nonnegative(tick);
            double age=tick-timeOrigin-member.index()*delayStep;
            if (age<0) throw new IllegalArgumentException("member not born yet");
            double groupTime=tick-groupOrigin;
            double localTime=transformClock==com.bitsson.gensokyou.danmaku.program.TimeDomain.GROUP?groupTime:age;
            State local=localTransform.apply(new State(member.position(),Vec3.ZERO,false),localTime), group=groupPath.at(groupTime);
            for (Layer parent:parents) {
                State combined=parent.transform().apply(new State(group.position().add(local.position()),group.velocity().add(local.velocity()),group.ended()||local.ended()),tick-parent.timeOrigin());
                group=combined; local=new State(Vec3.ZERO,Vec3.ZERO,false);
            }
            return new State(frame.point(group.position().add(local.position())),frame.direction(group.velocity().add(local.velocity())),group.ended()||local.ended());
        }
        public Vec3 directionAt(double tick) {
            double age=tick-timeOrigin-member.index()*delayStep;
            Deterministic.nonnegative(age);
            Vec3 direction=localTransform.direction(member.direction(),transformClock==com.bitsson.gensokyou.danmaku.program.TimeDomain.GROUP?tick-groupOrigin:age);
            for (Layer parent:parents) direction=parent.transform().direction(direction,tick-parent.timeOrigin());
            return frame.direction(direction).unit();
        }
        public double radiusBound(double t) {
            double localOrigin=transformClock==com.bitsson.gensokyou.danmaku.program.TimeDomain.GROUP?groupOrigin:timeOrigin;
            double radius=groupPath.radiusBound(StrictMath.max(0,t-groupOrigin))+localTransform.radiusBound(member.position().length(),StrictMath.max(0,t-localOrigin));
            for (Layer parent:parents) radius=parent.transform().radiusBound(radius,StrictMath.max(0,t-parent.timeOrigin()));
            return frame.origin().length()+radius;
        }
    }
    /** Inner-to-outer immutable parent transform closure, each with its own group clock. */
    public record Layer(Transforms.Transform transform,double timeOrigin) {
        public Layer { java.util.Objects.requireNonNull(transform); Deterministic.finite(timeOrigin); }
    }
    public enum VelocityPolicy { TANGENT, NEW_DIRECTION }
    public record Detached(Attached source,Selection selection,double effectiveTick,
                           VelocityPolicy policy,Vec3 newVelocity,boolean discontinuousVelocity) implements Path {
        public Detached {
            java.util.Objects.requireNonNull(source); java.util.Objects.requireNonNull(selection); java.util.Objects.requireNonNull(policy); java.util.Objects.requireNonNull(newVelocity);
            Deterministic.nonnegative(effectiveTick);
            if (policy==VelocityPolicy.NEW_DIRECTION && !discontinuousVelocity
                    && source.at(effectiveTick).velocity().subtract(newVelocity).length()>1e-8) throw new IllegalArgumentException("velocity discontinuity must be declared");
        }
        public Ownership ownership() { return source.ownership(); }
        public State at(double t) {
            Deterministic.nonnegative(t);
            if (t<effectiveTick || !selection.includes(source.member())) return source.at(t);
            State boundary=source.at(effectiveTick);
            Vec3 v=policy==VelocityPolicy.TANGENT?boundary.velocity():newVelocity;
            return new State(boundary.position().add(v.multiply(t-effectiveTick)),v,false);
        }
        public double radiusBound(double t) {
            if (!selection.includes(source.member()) || t<effectiveTick) return source.radiusBound(t);
            return source.radiusBound(effectiveTick)+(policy==VelocityPolicy.TANGENT?source.at(effectiveTick).velocity().length():newVelocity.length())*(t-effectiveTick);
        }
    }
    public static Vec3 aimAtPoint(Vec3 actualSource,Vec3 sampledTarget) { return sampledTarget.subtract(actualSource).unit(); }
    public static Vec3 radialDirection(Vec3 center,Vec3 position,Vec3 centerFallback) {
        Vec3 delta=position.subtract(center); return delta.length()<1e-12?centerFallback.unit():delta.unit();
    }
}
