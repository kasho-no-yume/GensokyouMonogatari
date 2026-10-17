package com.bitsson.gensokyou.danmaku.program.gameplay;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.DoubleFunction;
import java.util.function.Function;

/** Bounded, world-free narrow phase. An unresolved curve is rejected, never replaced by its chord. */
public final class ProgramCollisionSweep {
    public record Segment(Vec3 from,Vec3 until) {}
    public record Hit<T>(T target,Vec3 point,double distance) {}
    private ProgramCollisionSweep() {}

    public static Optional<List<Segment>> sample(DoubleFunction<Vec3> path,double start,double end) {
        var segments=new ArrayList<Segment>(8);
        return subdivide(path,start,end,path.apply(start),path.apply(end),0,segments)
                ?Optional.of(List.copyOf(segments)):Optional.empty();
    }
    private static boolean subdivide(DoubleFunction<Vec3> path,double start,double end,Vec3 a,Vec3 b,
                                     int depth,List<Segment> segments) {
        double middle=(start+end)*.5;
        Vec3 m=path.apply(middle);
        // Quarter samples also detect an S-curve whose midpoint lies exactly on its chord.
        double error=Math.max(m.distanceTo(a.lerp(b,.5)),Math.max(
                path.apply(start+(end-start)*.25).distanceTo(a.lerp(b,.25)),
                path.apply(start+(end-start)*.75).distanceTo(a.lerp(b,.75))));
        if (error>.02) {
            if (depth>=3) return false;
            return subdivide(path,start,middle,a,m,depth+1,segments)
                    && subdivide(path,middle,end,m,b,depth+1,segments);
        }
        segments.add(new Segment(a,b)); return true;
    }
    public static AABB bounds(List<Segment> segments,double radius) {
        if (segments.isEmpty()) throw new IllegalArgumentException("empty sweep");
        AABB box=new AABB(segments.getFirst().from(),segments.getFirst().until());
        for (var segment:segments) box=box.minmax(new AABB(segment.from(),segment.until()));
        return box.inflate(radius);
    }
    public static <T> Optional<Hit<T>> nearest(List<Segment> segments,List<T> targets,
                                              Function<T,AABB> boxes,double radius,boolean laser) {
        double travelled=0;
        for (var segment:segments) {
            Hit<T> nearest=null;
            for (T target:targets) {
                var hit=laser?ProgramCollisionGeometry.rayAabb(segment.from(),segment.until(),boxes.apply(target),radius)
                        :ProgramCollisionGeometry.sphereAabb(segment.from(),segment.until(),boxes.apply(target),radius);
                if (hit.isEmpty()) continue;
                double distance=travelled+segment.from().distanceTo(hit.get());
                if (nearest==null || distance<nearest.distance()) nearest=new Hit<>(target,hit.get(),distance);
            }
            if (nearest!=null) return Optional.of(nearest);
            travelled+=segment.from().distanceTo(segment.until());
        }
        return Optional.empty();
    }
}
