package com.bitsson.gensokyou.danmaku.program.gameplay;

import com.bitsson.gensokyou.danmaku.program.*;
import com.bitsson.gensokyou.danmaku.program.math.*;
import com.bitsson.gensokyou.danmaku.program.network.*;
import com.bitsson.gensokyou.danmaku.program.server.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProgramSplitPreviewTest {
    @Test void wholePetalReleaseSelectsThreeCompleteLobesAndPreservesTheOtherTwo() {
        var session=ProgramBlue1Test.session(new ProgramBlue1Test.World(1),ProgramPreviewPrograms.compositeMotion(true,true));
        for (int tick=0;tick<120;tick++) session.advance(tick,256);
        var original=new HashMap<DanmakuWorldRuntime.MemberId,Vec3>();
        for (var m:session.checkpoint().runtime().groups().getFirst().members()) original.put(m.id(),session.runtime().state(m.id(),150).position());
        session.advance(120,256);
        var checkpoint=session.checkpoint(); assertEquals(27,checkpoint.runtime().detached().size());
        var wire=new ProgramWire(NetworkLimits.defaults()); var p=ReplicaState.capture(checkpoint,wire); var decoded=wire.image(wire.image(p.image()));
        for (var m:checkpoint.runtime().groups().getFirst().members()) {
            int sample=m.id().index()-1;
            boolean selected=sample>=0 && Set.of(0,2,4).contains(Math.floorMod((sample+4)/9,5));
            assertEquals(selected,checkpoint.runtime().detached().containsKey(m.id()));
            if (!selected) assertEquals(original.get(m.id()),session.runtime().state(m.id(),150).position());
            assertEquals(session.runtime().state(m.id(),150).position(),ReplicaState.state(decoded,p.descriptions(),m.id(),150).position());
            assertTrue(ProgramPreviewBounds.groups(p).get(m.id().group()).near(session.runtime().state(m.id(),259).position(),0));
        }
    }
    @Test void sphericalRecipeDeceleratesOutwardsAndReplacesAtNamedSegmentBoundaries() {
        var session=ProgramBlue1Test.session(new ProgramBlue1Test.World(1),ProgramPreviewPrograms.splitGenerations(true,true));
        long births=0;
        for (int tick=0;tick<=251;tick++) {
            session.advance(tick,256);
            for (var event:session.runtime().drainEvents()) if (event.kind()==DanmakuWorldRuntime.Kind.COMMIT) {
                if (event.plan()!=null) births+=event.plan().members().size();
                for (var child:event.children()) births+=child.members().size();
            }
            if (tick==20) {
                assertEquals(6,session.runtime().liveMembers());
                for (var member:session.checkpoint().runtime().groups().getFirst().members()) {
                    var origin=member.plan().source().frame().origin();
                    var start=session.runtime().state(member.id(),20);
                    assertEquals(2,start.position().subtract(origin).length(),1e-9);
                    assertTrue(start.velocity().dot(start.position().subtract(origin))>0);
                    assertTrue(session.runtime().state(member.id(),54.99).velocity().length()<.001);
                }
            }
            if (tick==55) assertEquals(12,session.runtime().liveMembers());
            if (tick==90) assertEquals(24,session.runtime().liveMembers());
        }
        assertEquals(42,births); assertEquals(0,session.runtime().liveMembers());
    }
    @Test void partialReleaseDetachesOnlySelectedPetalsAndKeepsCoreAndOthersOnTheirOriginalPaths() {
        var fixture=ProgramPreviewPrograms.compositeMotion(true);
        var session=ProgramBlue1Test.session(new ProgramBlue1Test.World(1),fixture);
        for (int tick=0;tick<120;tick++) session.advance(tick,256);
        var members=session.checkpoint().runtime().groups().getFirst().members();
        var expected=new HashMap<DanmakuWorldRuntime.MemberId,Vec3>();
        for (var m:members) expected.put(m.id(),session.runtime().state(m.id(),150.5).position());
        session.advance(120,256);
        var wire=new ProgramWire(NetworkLimits.defaults());
        var projection=ReplicaState.capture(session.checkpoint(),wire);
        int detached=0;
        for (var m:members) {
            var state=session.runtime().state(m.id(),150.5);
            boolean selected=m.template().tags().contains("petals") && m.id().index()%2==0;
            if (selected) { detached++; assertEquals(.15,state.velocity().length(),1e-9); }
            else assertEquals(expected.get(m.id()),state.position());
            assertEquals(state.position(),ReplicaState.state(projection.image(),projection.descriptions(),m.id(),150.5).position());
            assertTrue(ProgramPreviewBounds.groups(projection).get(m.id().group()).near(session.runtime().state(m.id(),259).position(),0));
        }
        assertTrue(detached>0 && detached<45);
    }
    @Test void allNewPreviewsActuallyDispatchCollisionAndPublishContactRemoval() {
        for (var fixture:List.of(ProgramPreviewPrograms.splitGenerations(false),ProgramPreviewPrograms.splitGenerations(true),ProgramPreviewPrograms.compositeMotion())) {
            var session=ProgramBlue1Test.session(new ProgramBlue1Test.World(1),fixture);
            var samples=new HashSet<String>();
            session.runtime().collisions((runtime,id,from,until)->{
                var member=runtime.member(id).orElseThrow();
                samples.addAll(member.template().tags());
                assertTrue(runtime.damaging(id,runtime.tick()));
            });
            for (int tick=0;tick<=100;tick++) session.advance(tick,256);
            if (fixture.definition().id().contains("split")) assertTrue(samples.containsAll(Set.of("generation0","generation1","generation2")));
            else assertTrue(samples.containsAll(Set.of("core","petals")));
            var wire=new ProgramWire(NetworkLimits.defaults()); var packets=new ArrayList<byte[]>();
            var sync=new ProgramSyncServer(wire,packets::add,20);
            var replica=new ProgramReplica(new ProgramWire(NetworkLimits.defaults()),ProgramPreviewPrograms.registry(),ProgramPreviewPrograms.limits());
            var projection=ReplicaState.capture(session.checkpoint(),wire);
            sync.publishSpatial(projection,ProgramPreviewBounds.groups(projection),ProgramPreviewBounds.members(projection),new Vec3(0,1,18),32,256,128);
            for (byte[] bytes:packets) replica.receive(bytes,100);
            packets.clear();
            var id=session.checkpoint().runtime().groups().getFirst().members().getFirst().id();
            // Simulate the contact sink's removal without depending on a running Minecraft level.
            session.runtime().collisions((runtime,member,from,until)->{ if (member.equals(id)) runtime.remove(member,"entity-hit"); });
            session.advance(101,256);
            projection=ReplicaState.capture(session.checkpoint(),wire);
            sync.publishSpatial(projection,ProgramPreviewBounds.groups(projection),ProgramPreviewBounds.members(projection),new Vec3(0,1,18),32,256,128);
            for (byte[] bytes:packets) replica.receive(bytes,101);
            assertTrue(session.runtime().member(id).isEmpty());
            assertFalse(ReplicaState.hasMember(replica.image(new ProgramReplica.Scope("test:blue1",ProgramBlue1Test.OWNER)).orElseThrow(),id));
            assertEquals(0,replica.failures());
        }
    }
    @Test void compositeMotionKeepsCenterTravelSeparateFromBreathingAndRestoresAtArbitraryFrames() {
        var fixture=ProgramPreviewPrograms.compositeMotion();
        var session=ProgramBlue1Test.session(new ProgramBlue1Test.World(1),fixture);
        for (int tick=0;tick<=120;tick++) session.advance(tick,256);
        var group=session.checkpoint().runtime().groups().getFirst();
        assertEquals(46,group.members().size());
        var core=group.members().stream().filter(m->m.template().tags().contains("core")).findFirst().orElseThrow();
        var wire=new ProgramWire(NetworkLimits.defaults());
        var projection=ReplicaState.capture(session.checkpoint(),wire);
        var decoded=wire.image(wire.image(projection.image()));
        var frame=core.plan().source().frame();
        for (double time:new double[]{20,20.5,59.99,100.25,159.75,200}) {
            var center=session.runtime().state(core.id(),time).position();
            assertEquals(frame.point(new com.bitsson.gensokyou.danmaku.program.ops.Paths.Arc(Vec3.Z,Vec3.Y,.012,.10).at(time-20).position()),center);
            for (var member:group.members()) {
                var expected=session.runtime().state(member.id(),time).position();
                assertEquals(expected,ReplicaState.state(decoded,projection.descriptions(),member.id(),time).position());
                assertTrue(ProgramPreviewBounds.groups(projection).get(group.id()).near(expected,0));
            }
        }
    }
    @Test void twoGenerationPreviewsHaveSevenNAndFourNAndRestoreWithoutReplay() {
        for (boolean replace:new boolean[]{false,true}) {
            var fixture=ProgramPreviewPrograms.splitGenerations(replace);
            var session=ProgramBlue1Test.session(new ProgramBlue1Test.World(1),fixture);
            var wire=new ProgramWire(NetworkLimits.defaults()); var packets=new ArrayList<byte[]>();
            var sync=new ProgramSyncServer(wire,packets::add,20);
            var replica=new ProgramReplica(new ProgramWire(NetworkLimits.defaults()),ProgramPreviewPrograms.registry(),ProgramPreviewPrograms.limits());
            for (int tick=0;tick<=251;tick++) {
                session.advance(tick,256); session.runtime().drainEvents();
                if (tick==20) assertEquals(6,session.runtime().liveMembers());
                if (tick==55) assertEquals(replace?12:18,session.runtime().liveMembers());
                if (tick==90) assertEquals(replace?24:42,session.runtime().liveMembers());
                if (tick>=90 && tick<=100) {
                    var projection=ReplicaState.capture(session.checkpoint(),wire);
                    sync.publishSpatial(projection,ProgramPreviewBounds.groups(projection),ProgramPreviewBounds.members(projection),new Vec3(0,1,18),32,256,128);
                    for (byte[] packet:packets) replica.receive(packet,tick);
                    packets.clear();
                    var scope=new ProgramReplica.Scope("test:blue1",ProgramBlue1Test.OWNER);
                    for (var group:session.checkpoint().runtime().groups()) for (var member:group.members())
                        assertEquals(session.runtime().state(member.id(),tick+.5).position(),replica.state(scope,member.id(),tick+.5).position());
                    assertEquals(replace?24:42,replica.renderViews("test:blue1",tick+.5).stream().mapToInt(v->v.members().size()).sum());
                }
            }
            assertEquals(0,session.runtime().liveMembers());
            assertTrue(session.checkpoint().instances().isEmpty());
            assertEquals(0,replica.failures());
        }
    }
}
