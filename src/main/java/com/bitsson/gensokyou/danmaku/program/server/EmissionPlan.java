package com.bitsson.gensokyou.danmaku.program.server;

import com.bitsson.gensokyou.danmaku.program.math.*;
import com.bitsson.gensokyou.danmaku.program.ops.*;
import java.util.*;

/** A warning and its eventual commit retain this same immutable geometry and seed. */
public record EmissionPlan(long group, long ordinal, long birthTick, Actions.Pose source,
                           Actions.ChildProgram program, Actions.Binding binding,
                           Deterministic.Seed seed, List<Pattern.Member> members) {
    public EmissionPlan {
        if (group<1 || ordinal<0 || birthTick<0) throw new IllegalArgumentException("stable plan identity required");
        Objects.requireNonNull(source); Objects.requireNonNull(program); Objects.requireNonNull(binding); Objects.requireNonNull(seed);
        members=List.copyOf(members);
        if (members.size()>program.pattern().countBound() || members.size()>512
                || members.stream().map(Pattern.Member::index).distinct().count()!=members.size())
            throw new IllegalArgumentException("invalid expanded batch");
    }
    public Path.State state(Pattern.Member member, double tick) {
        double age=tick-birthTick;
        Path.State local=program.memberState(member,seed,age);
        Vec3 offset=program.memberState(member,seed,0).position();
        Vec3 inherited=binding.velocity()==Actions.Velocity.ADD_SOURCE_VELOCITY
                ?source.velocity().add(source.angularVelocity().cross(source.frame().direction(offset))):Vec3.ZERO;
        return new Path.State(source.frame().point(local.position()).add(inherited.multiply(age)),
                source.frame().direction(local.velocity()).add(inherited),local.ended());
    }
}
