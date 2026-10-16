package com.bitsson.gensokyou.danmaku.program.server;

import com.bitsson.gensokyou.danmaku.program.math.*;
import com.bitsson.gensokyou.danmaku.program.ops.*;
import java.util.*;

/** Immutable inner-to-outer transform closure, with explicit group clock anchors. */
public record GroupMotion(Frame frame, Path translation, Transforms.Transform transform,
                          long origin, List<MemberMotion.Layer> parents) {
    public GroupMotion {
        Objects.requireNonNull(frame); Objects.requireNonNull(translation); Objects.requireNonNull(transform);
        parents=List.copyOf(parents);
        if (origin<0 || parents.size()>8) throw new IllegalArgumentException("bounded group closure required");
    }
    public static GroupMotion stationary(Frame frame,long origin) {
        return new GroupMotion(frame,new Paths.Hold(),new Transforms.Compose(List.of()),origin,List.of());
    }
    public Path.State apply(Path.State local,double tick) {
        double age=tick-origin; Deterministic.nonnegative(age);
        Path.State state=transform.apply(local,age), travel=translation.at(age);
        state=new Path.State(state.position().add(travel.position()),state.velocity().add(travel.velocity()),state.ended()||travel.ended());
        for (MemberMotion.Layer parent:parents) state=parent.transform().apply(state,tick-parent.timeOrigin());
        return new Path.State(frame.point(state.position()),frame.direction(state.velocity()),state.ended());
    }
    public Vec3 direction(Vec3 direction,double tick) {
        Vec3 result=transform.direction(direction,tick-origin);
        for (MemberMotion.Layer parent:parents) result=parent.transform().direction(result,tick-parent.timeOrigin());
        return frame.direction(result).unit();
    }
    public Actions.Pose pose(double tick) {
        Path.State center=apply(new Path.State(Vec3.ZERO,Vec3.ZERO,false),tick);
        Vec3 right=direction(Vec3.X,tick), forward=direction(Vec3.Z,tick);
        Vec3 up=forward.cross(right).unit();
        // Affine transforms already provide analytic derivatives. Remove translation
        // and the longitudinal scale derivative to obtain each unit basis derivative.
        Vec3 omega=Vec3.ZERO;
        Path.State zero=apply(new Path.State(Vec3.ZERO,Vec3.ZERO,false),tick);
        for (Vec3 axis:List.of(Vec3.X,Vec3.Y,Vec3.Z)) {
            Path.State tip=apply(new Path.State(axis,Vec3.ZERO,false),tick);
            Vec3 offset=tip.position().subtract(zero.position()), velocity=tip.velocity().subtract(zero.velocity());
            Vec3 basis=direction(axis,tick);
            Vec3 derivative=offset.length()<1e-12?Vec3.ZERO:velocity.subtract(basis.multiply(basis.dot(velocity))).multiply(1/offset.length());
            omega=omega.add(basis.cross(derivative).multiply(.5));
        }
        return new Actions.Pose(new Frame(center.position(),right,up,forward),center.velocity(),omega);
    }
}
