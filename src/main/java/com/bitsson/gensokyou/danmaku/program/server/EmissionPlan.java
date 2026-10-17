package com.bitsson.gensokyou.danmaku.program.server;

import com.bitsson.gensokyou.danmaku.program.math.*;
import com.bitsson.gensokyou.danmaku.program.ops.*;
import java.util.*;

/** A warning and its eventual commit retain this same immutable geometry and seed. */
public record EmissionPlan(long group, long ordinal, long birthTick, Actions.Pose source,
                           Actions.ChildProgram program, Actions.Binding binding,
                            Deterministic.Seed seed, List<Pattern.Member> members, GroupMotion motion) {
    public EmissionPlan(long group,long ordinal,long birthTick,Actions.Pose source,Actions.ChildProgram program,
                        Actions.Binding binding,Deterministic.Seed seed,List<Pattern.Member> members) {
        this(group,ordinal,birthTick,source,program,binding,seed,members,null);
    }
    public EmissionPlan {
        if (group<1 || ordinal<0 || birthTick<0) throw new IllegalArgumentException("stable plan identity required");
        Objects.requireNonNull(source); Objects.requireNonNull(program); Objects.requireNonNull(binding); Objects.requireNonNull(seed);
        members=List.copyOf(members);
        if (members.size()>program.pattern().countBound() || members.size()>512
                || members.stream().map(Pattern.Member::index).distinct().count()!=members.size())
            throw new IllegalArgumentException("invalid expanded batch");
    }
    public Path.State state(Pattern.Member member, double tick) {
        if (motion!=null) return motion.apply(program.memberState(member,seed,Math.max(0,tick-birthTick)),tick);
        return state(program,binding,source,seed,member,tick-birthTick);
    }
    public Vec3 direction(Pattern.Member member,double tick) {
        Vec3 local=program.transform().direction(member.direction(),Math.max(0,tick-birthTick));
        return motion==null?source.frame().direction(local).unit():motion.direction(local,tick);
    }
    /** Beam orientation is an authored axis, independent of the emitter's translational velocity. */
    public static Vec3 beamDirection(Actions.ChildProgram program,Actions.Pose source,Pattern.Member member,double age) {
        return source.frame().direction(program.transform().direction(member.direction(),age)).unit();
    }
    /** Evaluate an already validated immutable batch without rebuilding its member table. */
    public static Path.State state(Actions.ChildProgram program,Actions.Binding binding,Actions.Pose source,
                                   Deterministic.Seed seed,Pattern.Member member,double age) {
        Path.State local=program.memberState(member,seed,age);
        Vec3 inherited=Vec3.ZERO;
        if (binding.velocity()==Actions.Velocity.ADD_SOURCE_VELOCITY) {
            inherited=source.velocity();
            if (source.angularVelocity().length()>0) {
                Vec3 offset=program.memberState(member,seed,0).position();
                inherited=inherited.add(source.angularVelocity().cross(source.frame().direction(offset)));
            }
        }
        return new Path.State(source.frame().point(local.position()).add(inherited.multiply(age)),
                source.frame().direction(local.velocity()).add(inherited),local.ended());
    }
}
