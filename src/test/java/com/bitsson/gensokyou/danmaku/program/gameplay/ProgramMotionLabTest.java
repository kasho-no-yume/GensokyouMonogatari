package com.bitsson.gensokyou.danmaku.program.gameplay;

import com.bitsson.gensokyou.danmaku.program.math.*;
import com.bitsson.gensokyou.danmaku.program.network.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProgramMotionLabTest {
    @Test void roamingTowerEmitsEightRoundsFromThreeTopNodesAndChildrenStayIndependent() {
        var session=ProgramBlue1Test.session(new ProgramBlue1Test.World(1),ProgramMotionPreviewPrograms.movingTornado(true));
        int rings=0;
        for (int tick=0;tick<=281;tick++) {
            session.advance(tick,256);
            for (var event:session.runtime().drainEvents()) if (event.kind()==com.bitsson.gensokyou.danmaku.program.server.DanmakuWorldRuntime.Kind.COMMIT
                    && event.plan()!=null && event.plan().members().getFirst().tags().contains("top-ring")) {
                rings++; assertEquals(8,event.plan().members().size());
                assertEquals(0,(event.tick()-40)%20);
                var plan=event.plan(); var template=plan.members().getFirst();
                var origin=plan.source().frame().origin();
                assertEquals(origin,plan.state(template,event.tick()).position());
                assertEquals(1.2,plan.state(template,event.tick()+10).position().subtract(origin).length(),1e-9);
            }
        }
        assertEquals(24,rings); assertEquals(0,session.runtime().liveMembers()); assertEquals(0,session.runtime().stats().plans());
    }
    @Test void dynamicWarningCancellationOrOwnerLossNeverCommitsFutureMembers() {
        for (boolean ownerLoss:new boolean[]{false,true}) {
            boolean[] available={true};
            var world=new ProgramBlue1Test.World(1) {
                @Override public Optional<com.bitsson.gensokyou.danmaku.program.ops.Actions.Pose> entity(UUID id) {
                    return id.equals(ProgramBlue1Test.OWNER) && !available[0]?Optional.empty():super.entity(id);
                }
            };
            var session=ProgramBlue1Test.session(world,ProgramMotionPreviewPrograms.rotatingLaser(true));
            for (int tick=0;tick<=19;tick++) session.advance(tick,256);
            if (ownerLoss) available[0]=false;
            else session.runtime().cancel(session.checkpoint().runtime().groups().getFirst().id(),0,"warning-cancel");
            for (int tick=20;tick<=45;tick++) session.advance(tick,256);
            assertEquals(0,session.runtime().liveMembers()); assertEquals(0,session.runtime().stats().plans());
            assertTrue(session.runtime().drainEvents().stream().noneMatch(e->e.kind()==com.bitsson.gensokyou.danmaku.program.server.DanmakuWorldRuntime.Kind.COMMIT));
        }
    }
    @Test void emitterBudgetRejectDoesNotAccumulateShotsAndTwentyRestartsReleaseAllState() {
        for (int repeat=0;repeat<20;repeat++) {
            var installed=ProgramMotionPreviewPrograms.highFrequencyCircle();
            var runtime=new com.bitsson.gensokyou.danmaku.program.server.DanmakuWorldRuntime("test:blue1",
                    new com.bitsson.gensokyou.danmaku.program.server.DanmakuWorldRuntime.Limits(12,6,16,16,8192),new ProgramBlue1Test.World(1));
            var encounter=runtime.startEncounter(ProgramBlue1Test.OWNER);
            var session=new com.bitsson.gensokyou.danmaku.program.server.ProgramSession(runtime,installed.execution());
            session.install(encounter,1,false,installed.definition(),new com.bitsson.gensokyou.danmaku.program.ClockAnchors(0,0,null,null),0);
            int rejects=0;
            for (int tick=0;tick<=200;tick++) {
                session.advance(tick,256);
                for (var event:runtime.drainEvents()) if (event.kind()==com.bitsson.gensokyou.danmaku.program.server.DanmakuWorldRuntime.Kind.REJECT) rejects++;
                assertTrue(runtime.liveMembers()<=12);
                assertTrue(runtime.stats().plans()<=2);
            }
            assertTrue(rejects>0); assertEquals(0,runtime.stats().groups()); assertEquals(0,runtime.stats().members());
            assertEquals(0,runtime.stats().emitters()); assertEquals(0,runtime.stats().plans()); assertEquals(0,runtime.stats().locks());
        }
    }
    @Test void movingCarrierRecipeHasThreeTimesFourTimesEightIndependentChildren() {
        var session=ProgramBlue1Test.session(new ProgramBlue1Test.World(1),ProgramMotionPreviewPrograms.movingCarriers());
        int children=0;
        for (int tick=0;tick<=201;tick++) {
            session.advance(tick,256);
            for (var event:session.runtime().drainEvents()) if (event.kind()==com.bitsson.gensokyou.danmaku.program.server.DanmakuWorldRuntime.Kind.COMMIT
                    && event.plan()!=null && event.plan().members().getFirst().tags().contains("spray")) {
                children+=event.plan().members().size(); assertTrue(Set.of(40L,60L,80L,100L).contains(event.tick()));
            }
            if (tick==101) {
                assertTrue(session.checkpoint().runtime().groups().getFirst().members().stream().noneMatch(m->m.template().tags().contains("carrier")));
                assertTrue(session.runtime().liveMembers()>0);
            }
        }
        assertEquals(96,children); assertEquals(0,session.runtime().liveMembers());
    }
    @Test void highFrequencyEmitterBirthsAreIntegerTickSnapshotsAndFxRestoreWithoutMembers() {
        var session=ProgramBlue1Test.session(new ProgramBlue1Test.World(1),ProgramMotionPreviewPrograms.highFrequencyCircle());
        var counts=new TreeMap<Long,Integer>(); var wire=new ProgramWire(NetworkLimits.defaults());
        for (int tick=0;tick<=200;tick++) {
            session.advance(tick,256);
            for (var event:session.runtime().drainEvents()) if (event.kind()==com.bitsson.gensokyou.danmaku.program.server.DanmakuWorldRuntime.Kind.COMMIT)
                counts.merge(event.tick(),event.plan().members().size(),Integer::sum);
            if (tick==10 || tick==50) {
                var p=ReplicaState.capture(session.checkpoint(),wire); var packets=new ArrayList<byte[]>();
                var sync=new ProgramSyncServer(wire,packets::add,20);
                var replica=new ProgramReplica(new ProgramWire(NetworkLimits.defaults()),ProgramPreviewPrograms.registry(),ProgramPreviewPrograms.limits());
                sync.publish(p,ProgramPreviewBounds.groups(p),new Vec3(0,1,18),64);
                for (var packet:packets) replica.receive(packet,tick);
                for (int fps:new int[]{30,60,144}) for (int frame=0;frame<fps;frame++) {
                    double time=tick+frame/(double)fps;
                    var view=replica.renderViews("test:blue1",time).getFirst();
                    assertEquals(2,view.billboards().size());
                    assertTrue(view.sounds().isEmpty());
                    if (tick==10) assertTrue(view.members().isEmpty());
                    for (var member:view.members()) assertEquals(session.runtime().state(member.id(),time).position(),member.position());
                }
            }
        }
        assertEquals(80,counts.size()); assertEquals(480,counts.values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(20L,counts.firstKey()); assertEquals(99L,counts.lastKey());
        assertTrue(counts.values().stream().allMatch(n->n==6));
        assertEquals(0,session.runtime().liveMembers()); assertEquals(0,session.runtime().stats().emitters());
        assertTrue(session.checkpoint().instances().isEmpty());
    }
    @Test void dynamicWarningAndCommittedLaserShareGroupClockAndSurviveLateJoin() {
        var session=ProgramBlue1Test.session(new ProgramBlue1Test.World(1),ProgramMotionPreviewPrograms.rotatingLaser(true));
        var wire=new ProgramWire(NetworkLimits.defaults());
        Vec3 first=null,last=null;
        for (int tick=0;tick<=40;tick++) {
            session.advance(tick,256);
            var projection=ReplicaState.capture(session.checkpoint(),wire);
            var packets=new ArrayList<byte[]>(); var sync=new ProgramSyncServer(wire,packets::add,20);
            var replica=new ProgramReplica(new ProgramWire(NetworkLimits.defaults()),ProgramPreviewPrograms.registry(),ProgramPreviewPrograms.limits());
            sync.publishSpatial(projection,ProgramPreviewBounds.groups(projection),ProgramPreviewBounds.members(projection),new Vec3(0,1,18),32,256,128);
            for (byte[] packet:packets) replica.receive(packet,tick);
            var view=replica.renderViews("test:blue1",tick).getFirst();
            var group=session.checkpoint().runtime().groups().getFirst();
            if (tick<40) {
                var plan=group.plans().getFirst(); var warning=view.warnings().getFirst();
                assertEquals(plan.state(plan.members().getFirst(),tick).position(),warning.source());
                assertEquals(plan.direction(plan.members().getFirst(),tick),warning.direction());
                assertTrue(view.members().isEmpty());
                if (tick==0) first=warning.source(); if (tick==39) last=warning.source();
            } else {
                var member=group.members().getFirst();
                assertEquals(member.plan().state(member.template(),40).position(),view.members().getFirst().position());
                assertEquals(session.runtime().direction(member.id(),40),view.members().getFirst().direction());
                assertTrue(last.subtract(view.members().getFirst().position()).length()<.1);
            }
            for (var member:view.members()) assertTrue(ProgramPreviewBounds.groups(projection).get(group.id()).near(member.position(),0));
        }
        assertTrue(first.subtract(last).length()>.5);
        session.runtime().endEncounter(session.checkpoint().runtime().encounters().getFirst(),"test-cancel");
        assertEquals(0,session.runtime().liveMembers());
    }
    @Test void rotatingBeamUsesAuthoredAxisNotOrbitTangentAndWarningMatchesBirthPose() {
        var session=ProgramBlue1Test.session(new ProgramBlue1Test.World(1),ProgramMotionPreviewPrograms.rotatingLaser());
        session.advance(0,256);
        var wire=new ProgramWire(NetworkLimits.defaults());
        var packets=new ArrayList<byte[]>(); var sync=new ProgramSyncServer(wire,packets::add,20);
        var replica=new ProgramReplica(new ProgramWire(NetworkLimits.defaults()),ProgramPreviewPrograms.registry(),ProgramPreviewPrograms.limits());
        var projection=ReplicaState.capture(session.checkpoint(),wire);
        sync.publishSpatial(projection,ProgramPreviewBounds.groups(projection),ProgramPreviewBounds.members(projection),new Vec3(0,1,18),32,256,128);
        for (byte[] packet:packets) replica.receive(packet,0);
        var warning=replica.renderViews("test:blue1",0).getFirst().warnings().getFirst();
        for (int tick=1;tick<=40;tick++) session.advance(tick,256);
        var member=session.checkpoint().runtime().groups().getFirst().members().getFirst();
        assertEquals(session.runtime().state(member.id(),40).position(),warning.source());
        assertEquals(session.runtime().direction(member.id(),40),warning.direction());
        packets.clear(); projection=ReplicaState.capture(session.checkpoint(),wire);
        sync.publishSpatial(projection,ProgramPreviewBounds.groups(projection),ProgramPreviewBounds.members(projection),new Vec3(0,1,18),32,256,128);
        for (byte[] packet:packets) replica.receive(packet,40);
        for (double time:new double[]{40,50.5,80,140.25}) {
            var expected=member.plan().source().frame().direction(Vec3.Z.rotate(Vec3.Y,(time-40)*Math.PI/360)).unit();
            assertTrue(expected.subtract(session.runtime().direction(member.id(),time)).length()<1e-9);
            var rendered=replica.renderViews("test:blue1",time).getFirst().members().getFirst();
            assertTrue(expected.subtract(rendered.direction()).length()<1e-9);
            assertTrue(expected.cross(session.runtime().state(member.id(),time).velocity()).length()>1e-4);
        }
    }
    @Test void movingTornadoHasIndependentSharedTranslationAndRestoresAcrossBirth() {
        var session=ProgramBlue1Test.session(new ProgramBlue1Test.World(1),ProgramMotionPreviewPrograms.movingTornado());
        for (int tick=0;tick<=60;tick++) session.advance(tick,256);
        var group=session.checkpoint().runtime().groups().getFirst();
        assertEquals(48,group.members().size());
        var wire=new ProgramWire(NetworkLimits.defaults());
        var projection=ReplicaState.capture(session.checkpoint(),wire);
        var image=wire.image(wire.image(projection.image()));
        for (var member:group.members()) for (double t:new double[]{60,60.125,80,120.9,200}) {
            var position=session.runtime().state(member.id(),t).position();
            assertEquals(position,ReplicaState.state(image,projection.descriptions(),member.id(),t).position());
            assertTrue(ProgramPreviewBounds.groups(projection).get(group.id()).near(position,0));
            assertTrue(session.runtime().damaging(member.id(),t));
        }
    }
    @Test void staggeredBirthsAndMirroredBezierSurviveWireAndFrameRateChanges() {
        var session=ProgramBlue1Test.session(new ProgramBlue1Test.World(1),ProgramMotionPreviewPrograms.motionLab());
        var counts=new HashMap<Long,Integer>();
        for (int tick=0;tick<=80;tick++) {
            session.advance(tick,256);
            for (var event:session.runtime().drainEvents()) if (event.kind()==com.bitsson.gensokyou.danmaku.program.server.DanmakuWorldRuntime.Kind.COMMIT)
                counts.merge(event.tick(),event.plan().members().size(),Integer::sum);
        }
        assertEquals(Map.of(20L,9,30L,9,40L,9,50L,9,60L,9,70L,9),counts);
        var wire=new ProgramWire(NetworkLimits.defaults());
        var projection=ReplicaState.capture(session.checkpoint(),wire);
        var decoded=wire.image(wire.image(projection.image()));
        var groups=session.checkpoint().runtime().groups();
        var red=groups.stream().filter(g->g.members().stream().anyMatch(m->m.template().tags().contains("bezier"))).findFirst().orElseThrow();
        var gold=groups.stream().filter(g->g.members().stream().anyMatch(m->m.template().tags().contains("mirror"))).findFirst().orElseThrow();
        var r=red.members().getFirst(); var g=gold.members().getFirst();
        for (int fps:new int[]{30,60,144}) {
            for (int frame=0;frame<=fps;frame++) {
                double time=80+20D*frame/fps;
                var rp=session.runtime().state(r.id(),time).position();
                var gp=session.runtime().state(g.id(),time).position();
                Vec3 rd=rp.subtract(r.plan().source().frame().origin()),gd=gp.subtract(g.plan().source().frame().origin());
                var rf=r.plan().source().frame(); var gf=g.plan().source().frame();
                Vec3 rl=new Vec3(rd.dot(rf.right()),rd.dot(rf.up()),rd.dot(rf.forward()));
                Vec3 gl=new Vec3(gd.dot(gf.right()),gd.dot(gf.up()),gd.dot(gf.forward()));
                assertEquals(-rl.x(),gl.x(),1e-9); assertEquals(rl.y(),gl.y(),1e-9); assertEquals(rl.z(),gl.z(),1e-9);
                assertEquals(rp,ReplicaState.state(decoded,projection.descriptions(),r.id(),time).position());
                assertTrue(session.runtime().damaging(r.id(),time));
            }
        }
        for (int tick=81;tick<=231;tick++) session.advance(tick,256);
        assertEquals(0,session.runtime().liveMembers());
        assertTrue(session.checkpoint().instances().isEmpty());
    }
}
