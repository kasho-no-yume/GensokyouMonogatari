package com.bitsson.gensokyou.danmaku.program.gameplay;

import com.bitsson.gensokyou.danmaku.program.math.Vec3;
import com.bitsson.gensokyou.danmaku.program.network.*;
import com.bitsson.gensokyou.danmaku.program.server.DanmakuWorldRuntime;
import java.util.*;

/** Conservative lifetime envelopes. Standard future rebase/release speed is reserved from birth. */
public final class ProgramMotionBounds {
    private ProgramMotionBounds() {}
    private static final Map<String,Double> CONTROL_SPEEDS=Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<String,Double> TREE_RADII=Collections.synchronizedMap(new WeakHashMap<>());
    private static double controlSpeed(com.bitsson.gensokyou.danmaku.program.ProgramDefinition definition) {
        return CONTROL_SPEEDS.computeIfAbsent(definition.hash(),key->{
            double speed=0;
            for (var node:definition.nodes()) if (node instanceof com.bitsson.gensokyou.danmaku.program.OperationNode operation) {
                if (!operation.operationId().equals("gensokyou:server_radial_release") && !operation.operationId().equals("gensokyou:server_rebase_target")) continue;
                Object value=com.bitsson.gensokyou.danmaku.program.author.StandardProgramOperations.decode(operation).orElse(null);
                if (value instanceof com.bitsson.gensokyou.danmaku.program.server.ServerControlOperations.RadialRelease release) speed=Math.max(speed,release.speed());
                if (value instanceof com.bitsson.gensokyou.danmaku.program.server.ServerControlOperations.RebaseTarget target) speed=Math.max(speed,target.speed());
            }
            return speed;
        });
    }
    private static double treeRadius(com.bitsson.gensokyou.danmaku.program.ops.Actions.ChildProgram program) {
        double radius=program.transform().radiusBound(program.pattern().radiusBound()+program.path().radiusBound(program.lifetimeTicks()),program.lifetimeTicks());
        double child=0;
        for (var action:program.actions()) child=Math.max(child,treeRadius(action.child()));
        double body=program.pattern().generate(new com.bitsson.gensokyou.danmaku.program.math.Deterministic.Seed(0,List.of())).stream()
                .mapToDouble(m->Math.max(m.style().visualRadius(),m.style().collisionRadius())).max().orElse(0);
        if (program.collisionProfile() instanceof com.bitsson.gensokyou.danmaku.program.ops.CollisionProfile.Laser laser) body+=laser.maxLength();
        return radius+Math.max(body,child);
    }
    public static Map<Long,ProgramInterest.Box> groups(ReplicaState.Projection projection) {
        Map<Long,ProgramInterest.Box> result=new HashMap<>();
        double rebaseSpeed=0;
        for (var definition:projection.definitions().values()) rebaseSpeed=Math.max(rebaseSpeed,controlSpeed(definition));
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
            result.merge(emitter.group(),new ProgramInterest.Box(origin.subtract(extent),origin.add(extent)),ProgramMotionBounds::union);
        }
        for (Record entry:projection.image().entries().values()) if (entry instanceof ReplicaState.Batch batch) {
            var program=projection.descriptions().get(batch.description());
            double radius=TREE_RADII.computeIfAbsent(batch.description(),key->treeRadius(program))
                    +batch.templates().stream().mapToDouble(m->Math.max(m.style().collisionRadius(),m.style().visualRadius())).max().orElse(0);
            if (program.collisionProfile() instanceof com.bitsson.gensokyou.danmaku.program.ops.CollisionProfile.Laser laser) radius+=laser.maxLength();
            Vec3 origin=batch.source().frame().origin();
            if (batch.motion()!=null) {
                var motion=batch.motion(); double duration=batch.birth()+program.lifetimeTicks()-motion.origin();
                radius=motion.transform().radiusBound(radius,duration)+motion.translation().radiusBound(duration);
                for (var parent:motion.parents()) radius=parent.transform().radiusBound(radius,batch.birth()+program.lifetimeTicks()-parent.timeOrigin());
                origin=motion.frame().origin();
            }
            // A future control may detach at any boundary and travel in any world direction.
            // Original path bound covers that boundary; linear reserve covers the remainder.
            radius+=rebaseSpeed*program.lifetimeTicks();
            if (batch.binding().velocity()==com.bitsson.gensokyou.danmaku.program.ops.Actions.Velocity.ADD_SOURCE_VELOCITY) {
                double offset=program.transform().radiusBound(program.pattern().radiusBound(),0);
                radius+=(batch.source().velocity().length()+batch.source().angularVelocity().length()*offset)*program.lifetimeTicks();
            }
            Vec3 extent=new Vec3(radius,radius,radius);
            var box=new ProgramInterest.Box(origin.subtract(extent),origin.add(extent));
            result.merge(batch.group(),box,ProgramMotionBounds::union);
        }
        for (Record entry:projection.image().entries().values()) if (entry instanceof ReplicaState.Motion motion
                && motion.detached() instanceof DanmakuWorldRuntime.Rebased rebase) {
            double radius=rebase.path().radiusBound(rebase.end()-rebase.start())+2;
            Vec3 extent=new Vec3(radius,radius,radius);
            result.merge(motion.id().group(),new ProgramInterest.Box(rebase.origin().subtract(extent),rebase.origin().add(extent)),ProgramMotionBounds::union);
        }
        return Map.copyOf(result);
    }
    /** Escape hatch for custom authority operations whose future displacement is not standard. */
    public static Map<Long,ProgramInterest.Box> expanded(ReplicaState.Projection projection,double extraRadius) {
        if (!Double.isFinite(extraRadius) || extraRadius<0) throw new IllegalArgumentException("finite nonnegative envelope reserve required");
        var extent=new Vec3(extraRadius,extraRadius,extraRadius);
        Map<Long,ProgramInterest.Box> result=new HashMap<>();
        groups(projection).forEach((id,box)->result.put(id,new ProgramInterest.Box(box.min().subtract(extent),box.max().add(extent))));
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
