package com.bitsson.gensokyou.danmaku.program.gameplay;

import com.bitsson.gensokyou.danmaku.program.math.Vec3;
import com.bitsson.gensokyou.danmaku.program.network.*;
import com.bitsson.gensokyou.danmaku.program.server.DanmakuWorldRuntime;
import java.util.*;

/** Conservative whole-lifetime bounds for the linear, snapshot-only preview fixture. */
public final class ProgramPreviewBounds {
    private ProgramPreviewBounds() {}
    public static Map<Long,ProgramInterest.Box> groups(ReplicaState.Projection projection) {
        Map<Long,ProgramInterest.Box> result=new HashMap<>();
        boolean flowers=projection.definitions().values().stream().anyMatch(d->d.id().equals("gensokyou:preview_flower_sea"));
        boolean release=projection.definitions().values().stream().anyMatch(d->Set.of("gensokyou:preview_partial_release","gensokyou:preview_petal_release").contains(d.id()));
        for (Record entry:projection.image().entries().values()) if (entry instanceof ReplicaState.Emitter emitter && emitter.sourceGroup()!=null) {
            var source=(ReplicaState.Group)projection.image().entries().get(ReplicaState.groupKey(emitter.sourceGroup()));
            if (source==null) continue;
            double radius=emitter.description().billboard()==null?1:emitter.description().billboard().size();
            Vec3 origin=source.frame().origin();
            if (source.motion()!=null) {
                var motion=source.motion(); double duration=emitter.origin()+emitter.description().shotTick(emitter.description().count()-1)+1-motion.origin();
                radius=motion.transform().radiusBound(radius,duration)+motion.translation().radiusBound(duration);
                origin=motion.frame().origin();
            }
            Vec3 extent=new Vec3(radius,radius,radius);
            result.merge(emitter.group(),new ProgramInterest.Box(origin.subtract(extent),origin.add(extent)),ProgramPreviewBounds::union);
        }
        for (Record entry:projection.image().entries().values()) if (entry instanceof ReplicaState.Batch batch) {
            var program=projection.descriptions().get(batch.description());
            double radius=program.transform().radiusBound(program.pattern().radiusBound()+program.path().radiusBound(program.lifetimeTicks()),program.lifetimeTicks())
                    +batch.templates().stream().mapToDouble(m->Math.max(m.style().collisionRadius(),m.style().visualRadius())).max().orElse(0);
            if (program.collisionProfile() instanceof com.bitsson.gensokyou.danmaku.program.ops.CollisionProfile.Laser laser) radius+=laser.maxLength();
            // This fixture releases at age100: at most 7.2 travel + 3 petal offset
            // + 300*.3 after release. Reserve that space from birth, not at rebase.
            if (flowers) radius=Math.max(radius,102);
            // Partial-release fixture: reserve the full possible detached travel from birth.
            if (release) radius+=.15*program.lifetimeTicks();
            Vec3 origin=batch.source().frame().origin();
            if (batch.motion()!=null) {
                var motion=batch.motion(); double duration=batch.birth()+program.lifetimeTicks()-motion.origin();
                radius=motion.transform().radiusBound(radius,duration)+motion.translation().radiusBound(duration);
                for (var parent:motion.parents()) radius=parent.transform().radiusBound(radius,batch.birth()+program.lifetimeTicks()-parent.timeOrigin());
                origin=motion.frame().origin();
            }
            Vec3 extent=new Vec3(radius,radius,radius);
            var box=new ProgramInterest.Box(origin.subtract(extent),origin.add(extent));
            result.merge(batch.group(),box,ProgramPreviewBounds::union);
        }
        for (Record entry:projection.image().entries().values()) if (!flowers && !release && entry instanceof ReplicaState.Motion motion
                && motion.detached() instanceof DanmakuWorldRuntime.Rebased rebase) {
            double radius=rebase.path().radiusBound(rebase.end()-rebase.start())+2;
            Vec3 extent=new Vec3(radius,radius,radius);
            result.merge(motion.id().group(),new ProgramInterest.Box(rebase.origin().subtract(extent),rebase.origin().add(extent)),ProgramPreviewBounds::union);
        }
        return Map.copyOf(result);
    }
    public static Map<DanmakuWorldRuntime.MemberId,ProgramInterest.Box> members(ReplicaState.Projection projection) {
        Map<DanmakuWorldRuntime.MemberId,ProgramInterest.Box> result=new HashMap<>();
        var groups=groups(projection);
        for (Record entry:projection.image().entries().values()) if (entry instanceof ReplicaState.Batch batch)
            // Keep bounds for dead template slots too. A partial table makes spatial()
            // fall back to the entire batch and changes subscriptions on every hit.
            for (int i=0;i<batch.templates().size();i++)
                result.put(new DanmakuWorldRuntime.MemberId(batch.group(),batch.ordinal(),batch.templates().get(i).index()),groups.get(batch.group()));
        return Map.copyOf(result);
    }
    private static ProgramInterest.Box union(ProgramInterest.Box a,ProgramInterest.Box b) {
        return new ProgramInterest.Box(new Vec3(Math.min(a.min().x(),b.min().x()),Math.min(a.min().y(),b.min().y()),Math.min(a.min().z(),b.min().z())),
                new Vec3(Math.max(a.max().x(),b.max().x()),Math.max(a.max().y(),b.max().y()),Math.max(a.max().z(),b.max().z())));
    }
}
