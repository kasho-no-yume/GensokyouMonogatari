package com.bitsson.gensokyou.danmaku.program.server;

import com.bitsson.gensokyou.danmaku.program.math.*;
import com.bitsson.gensokyou.danmaku.program.ops.*;
import java.util.*;

/** A warning and its eventual commit retain this same immutable geometry and seed. */
public record EmissionPlan(long group, long ordinal, long birthTick, Actions.Pose source,
                           Actions.ChildProgram program, Actions.Binding binding,
                            Deterministic.Seed seed, List<Pattern.Member> members, GroupMotion motion, Vec3 aimLocal) {
    public EmissionPlan(long group,long ordinal,long birthTick,Actions.Pose source,Actions.ChildProgram program,
                        Actions.Binding binding,Deterministic.Seed seed,List<Pattern.Member> members) {
        this(group,ordinal,birthTick,source,program,binding,seed,members,null,null);
    }
    public EmissionPlan(long group,long ordinal,long birthTick,Actions.Pose source,Actions.ChildProgram program,
                        Actions.Binding binding,Deterministic.Seed seed,List<Pattern.Member> members,GroupMotion motion) {
        this(group,ordinal,birthTick,source,program,binding,seed,members,motion,null);
    }
    public EmissionPlan {
        if (group<1 || ordinal<0 || birthTick<0) throw new IllegalArgumentException("stable plan identity required");
        Objects.requireNonNull(source); Objects.requireNonNull(program); Objects.requireNonNull(binding); Objects.requireNonNull(seed);
        members=List.copyOf(members);
        if (members.size()>512
                || members.stream().map(Pattern.Member::index).distinct().count()!=members.size())
            throw new IllegalArgumentException("invalid expanded batch");
    }
    public Path.State state(Pattern.Member member, double tick) {
        if (motion!=null) return motion.apply(program.memberState(member,seed,Math.max(0,tick-birthTick),aimLocal),tick);
        return state(program,binding,source,seed,member,tick-birthTick,aimLocal);
    }
    public Vec3 direction(Pattern.Member member,double tick) {
        double age=Math.max(0,tick-birthTick);
        Vec3 local=aimLocal==null?program.transform().direction(member.direction(),age)
                :program.transform().direction(Actions.ChildProgram.aimDirection(member,aimLocal),age);
        return motion==null?source.frame().direction(local).unit():motion.direction(local,tick);
    }
    /** Beam orientation is an authored axis, independent of the emitter's translational velocity. */
    public static Vec3 beamDirection(Actions.ChildProgram program,Actions.Pose source,Pattern.Member member,double age) {
        return beamDirection(program,source,member,age,null);
    }
    public static Vec3 beamDirection(Actions.ChildProgram program,Actions.Pose source,Pattern.Member member,double age,Vec3 aimLocal) {
        Vec3 local=aimLocal==null?program.transform().direction(member.direction(),age)
                :program.transform().direction(Actions.ChildProgram.aimDirection(member,aimLocal),age);
        return source.frame().direction(local).unit();
    }
    /** Evaluate an already validated immutable batch without rebuilding its member table. */
    public static Path.State state(Actions.ChildProgram program,Actions.Binding binding,Actions.Pose source,
                                   Deterministic.Seed seed,Pattern.Member member,double age) {
        return state(program,binding,source,seed,member,age,null);
    }
    public static Path.State state(Actions.ChildProgram program,Actions.Binding binding,Actions.Pose source,
                                   Deterministic.Seed seed,Pattern.Member member,double age,Vec3 aimLocal) {
        Path.State local=program.memberState(member,seed,age,aimLocal);
        Vec3 inherited=Vec3.ZERO;
        if (binding.velocity()==Actions.Velocity.ADD_SOURCE_VELOCITY) {
            inherited=source.velocity();
            if (source.angularVelocity().length()>0) {
                Vec3 offset=program.memberState(member,seed,0,aimLocal).position();
                inherited=inherited.add(source.angularVelocity().cross(source.frame().direction(offset)));
            }
        }
        return new Path.State(source.frame().point(local.position()).add(inherited.multiply(age)),
                source.frame().direction(local.velocity()).add(inherited),local.ended());
    }
}
