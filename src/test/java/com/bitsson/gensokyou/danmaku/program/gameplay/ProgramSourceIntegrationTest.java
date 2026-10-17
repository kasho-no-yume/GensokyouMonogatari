package com.bitsson.gensokyou.danmaku.program.gameplay;

import com.bitsson.gensokyou.danmaku.program.*;
import com.bitsson.gensokyou.danmaku.program.math.*;
import com.bitsson.gensokyou.danmaku.program.ops.*;
import com.bitsson.gensokyou.danmaku.program.server.*;
import com.bitsson.gensokyou.danmaku.program.network.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProgramSourceIntegrationTest {
    @Test void followingLaserKeepsAuthoredAxisAndDetachesWithTheSameAxis() {
        var runtime=new DanmakuWorldRuntime("test:beam",ProgramBlue1Test.LIMITS,new ProgramBlue1Test.World(1));
        var encounter=runtime.startEncounter(ProgramBlue1Test.OWNER);
        long source=runtime.createGroup(encounter,1,0,null,-1,false,true,Frame.IDENTITY,List.of());
        long output=runtime.createGroup(encounter,1,0,null,-1,false,true,Frame.IDENTITY,List.of());
        runtime.advance(0);
        runtime.groupMotion(source,new GroupMotion(Frame.IDENTITY,new Paths.Linear(Vec3.X,.1),
                new Transforms.Rotate(Vec3.Y,new ScalarCurve(List.of(new ScalarCurve.Key(0,0),new ScalarCurve.Key(100,1))),Vec3.ZERO),0,List.of()));
        var ref=new ProgramRef.Group(new NodeId(0)); runtime.bindSource(ref,source);
        var child=new Actions.ChildProgram(new Patterns.Point(Vec3.ZERO,Vec3.Z),new Paths.Hold(),100,0,0,List.of(),
                new Presentation.Profile(List.of(new Presentation.Window(TimeDomain.MEMBER_AGE,0,100)),null,null,List.of()),new Transforms.Compose(List.of()),
                Actions.DirectionRule.FIXED,0,new CollisionProfile.Laser(10,false,10));
        runtime.createEmitter(output,new Actions.CreateEmitter(new Actions.GroupPose(ref),ScalarCurve.constant(.2),Vec3.Y,1,1,1,child,
                new Actions.Binding(Actions.Attachment.FOLLOW_SOURCE,Actions.Velocity.ABSOLUTE,Actions.OnSourceLost.DETACH_AT_LAST_AUTHORITATIVE_POSE),null),
                new Deterministic.Seed(5,List.of()),new AuthorityDescriptions.Commit(new ProgramRef.Plan(new NodeId(0)),0,512,AuthorityDescriptions.Failure.REJECT_BATCH,true));
        runtime.advance(1); runtime.advance(2);
        var id=runtime.members(output,new Selection.AllOf(List.of())).getFirst();
        Vec3 axis=runtime.direction(id,2);
        assertTrue(axis.subtract(Vec3.Z.rotate(Vec3.Y,.22)).length()<1e-9);
        assertTrue(axis.cross(runtime.state(id,2).velocity()).length()>.01);
        var wire=new ProgramWire(NetworkLimits.defaults());
        var projection=ReplicaState.capture(new ProgramSession(runtime,new ProgramExecution()).checkpoint(),wire);
        var image=wire.image(wire.image(projection.image()));
        assertEquals(axis,ReplicaState.direction(image,projection.descriptions(),id,2));
        runtime.cancel(source,0,"source-lost"); runtime.advance(3); runtime.advance(4);
        projection=ReplicaState.capture(new ProgramSession(runtime,new ProgramExecution()).checkpoint(),wire);
        image=wire.image(wire.image(projection.image()));
        assertEquals(runtime.direction(id,4),ReplicaState.direction(image,projection.descriptions(),id,4));
        assertEquals(runtime.direction(id,4),runtime.direction(id,20));
    }
    @Test void remoteSharedGroupUsesGroupClockAcrossStaggeredBirthsAndSiblingRemoval() {
        var runtime=new DanmakuWorldRuntime("test:remote",ProgramBlue1Test.LIMITS,new ProgramBlue1Test.World(1));
        var encounter=runtime.startEncounter(ProgramBlue1Test.OWNER);
        var frame=new Frame(new Vec3(500,20,-300),Vec3.X,Vec3.Y,Vec3.Z);
        long group=runtime.createGroup(encounter,1,0,null,-1,false,true,frame,List.of());
        runtime.advance(0);
        runtime.groupMotion(group,new GroupMotion(frame,new Paths.Linear(Vec3.Z,.1),
                new Transforms.Rotate(Vec3.Y,new ScalarCurve(List.of(new ScalarCurve.Key(0,0),new ScalarCurve.Key(200,2))),Vec3.ZERO),0,List.of()));
        var child=new Actions.ChildProgram(new Patterns.Point(Vec3.X,Vec3.Z),new Paths.Hold(),100,0,0);
        var policy=new AuthorityDescriptions.Commit(new ProgramRef.Plan(new NodeId(0)),0,512,AuthorityDescriptions.Failure.REJECT_BATCH,true);
        var ids=new ArrayList<DanmakuWorldRuntime.MemberId>();
        for (int tick=1;tick<=20;tick++) {
            runtime.advance(tick);
            if (tick!=1 && tick!=10) continue;
            var plan=runtime.prepare(group,tick,new Actions.Pose(frame,Vec3.ZERO),child,Actions.Binding.snapshot(),new Deterministic.Seed(7,List.of()));
            assertTrue(runtime.commit(plan,policy));
            var id=new DanmakuWorldRuntime.MemberId(group,plan.ordinal(),0); runtime.attachToGroup(id); ids.add(id);
        }
        assertEquals(runtime.state(ids.get(0),20.5).position(),runtime.state(ids.get(1),20.5).position());
        runtime.remove(ids.getFirst(),"remove-one-lane");
        var wire=new ProgramWire(NetworkLimits.defaults());
        var projection=ReplicaState.capture(new ProgramSession(runtime,new ProgramExecution()).checkpoint(),wire);
        var box=new ProgramInterest.Box(new Vec3(450,-30,-350),new Vec3(550,70,-250));
        var packets=new ArrayList<byte[]>(); var sync=new ProgramSyncServer(wire,packets::add,20);
        sync.publish(projection,Map.of(group,box),frame.origin(),0);
        var replica=new ProgramReplica(new ProgramWire(NetworkLimits.defaults()),new OperationRegistry().freeze(),ProgramLimits.defaults());
        for (byte[] bytes:packets) replica.receive(bytes,0);
        var scope=new ProgramReplica.Scope("test:remote",ProgramBlue1Test.OWNER);
        assertFalse(ReplicaState.hasMember(replica.image(scope).orElseThrow(),ids.getFirst()));
        assertEquals(runtime.state(ids.getLast(),25.5).position(),replica.state(scope,ids.getLast(),25.5).position());
        assertTrue(replica.renderViews("test:remote",25.5).stream().allMatch(v->v.sounds().isEmpty()));
        assertEquals(1,runtime.liveMembers());
    }
    @Test void movingExternalSourceSamplesEachShotOnceAndSourceLossKeepsExistingChildren() {
        UUID external=new UUID(0,99);
        var poses=new HashMap<UUID,Actions.Pose>();
        poses.put(external,new Actions.Pose(Frame.IDENTITY,Vec3.ZERO));
        var world=new ProgramBlue1Test.World(1) {
            @Override public Optional<Actions.Pose> entity(UUID id) { return id.equals(external)?Optional.ofNullable(poses.get(id)):super.entity(id); }
        };
        var runtime=new DanmakuWorldRuntime("test:source",ProgramBlue1Test.LIMITS,world);
        var encounter=runtime.startEncounter(ProgramBlue1Test.OWNER);
        long group=runtime.createGroup(encounter,1,0,null,-1,false,true,Frame.IDENTITY,List.of());
        runtime.advance(0);
        var child=new Actions.ChildProgram(new Patterns.Point(Vec3.ZERO,Vec3.Z),new Paths.Linear(Vec3.Z,.1),100,0,0,List.of(),
                new Presentation.Profile(List.of(new Presentation.Window(TimeDomain.MEMBER_AGE,0,100)),null,null,List.of()),new Transforms.Compose(List.of()));
        runtime.createEmitter(group,new Actions.CreateEmitter(new Actions.ExternalEntitySource(external),ScalarCurve.constant(0),Vec3.Y,2,2,6,child,Actions.Binding.snapshot(),null),
                new Deterministic.Seed(5,List.of()),new AuthorityDescriptions.Commit(new ProgramRef.Plan(new NodeId(0)),0,512,AuthorityDescriptions.Failure.REJECT_BATCH,true));
        for (int tick=1;tick<=14;tick++) {
            poses.put(external,new Actions.Pose(new Frame(new Vec3(tick,0,0),Vec3.X,Vec3.Y,Vec3.Z),Vec3.X));
            if (tick>=7) poses.remove(external);
            runtime.advance(tick);
        }
        var members=runtime.checkpoint().groups().getFirst().members();
        assertEquals(3,members.size());
        assertEquals(Set.of(2L,4L,6L),members.stream().map(m->m.plan().birthTick()).collect(java.util.stream.Collectors.toSet()));
        var wire=new ProgramWire(NetworkLimits.defaults());
        var projection=ReplicaState.capture(new ProgramSession(runtime,new ProgramExecution()).checkpoint(),wire);
        var decoded=wire.image(wire.image(projection.image()));
        for (var m:members) {
            assertEquals(m.plan().birthTick(),runtime.state(m.id(),30).position().x(),1e-9);
            assertEquals(runtime.state(m.id(),30).position(),ReplicaState.state(decoded,projection.descriptions(),m.id(),30).position());
        }
        assertEquals(0,runtime.stats().emitters());
        assertEquals(3,runtime.drainEvents().stream().filter(e->e.kind()==DanmakuWorldRuntime.Kind.SOURCE_SAMPLE).count());
    }
}
