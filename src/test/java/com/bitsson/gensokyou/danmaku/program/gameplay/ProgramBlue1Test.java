package com.bitsson.gensokyou.danmaku.program.gameplay;

import com.bitsson.gensokyou.danmaku.program.*;
import com.bitsson.gensokyou.danmaku.program.math.*;
import com.bitsson.gensokyou.danmaku.program.ops.*;
import com.bitsson.gensokyou.danmaku.program.server.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProgramBlue1Test {
    @Test void blue4PreviewDistanceMustLeaveClearanceBeyondTheTenBlockNode() {
        for (int distance:new int[]{12,22}) {
            var world=new World(1); var player=world.players.getFirst();
            world.players.set(0,new DecisionProvider.Player(player.uuid(),new Vec3(0,0,distance),new Vec3(0,1,distance),2,true,false,false,.3));
            var session=session(world,ProgramPreviewPrograms.blue4("sphere"));
            for (int tick=0;tick<=90;tick++) session.advance(tick,256);
            var events=session.runtime().drainEvents();
            long lasers=events.stream().filter(e->e.kind()==DanmakuWorldRuntime.Kind.COMMIT && e.plan().program().collisionProfile() instanceof CollisionProfile.Laser).count();
            assertEquals(distance==12?0:1,lasers);
            if (distance==12) assertTrue(events.stream().anyMatch(e->e.kind()==DanmakuWorldRuntime.Kind.REJECT && e.reason().equals("clearance")));
        }
    }
    @Test void completeDefinitionsLateJoinThroughTheSharedRegistry() {
        for (var fixture:List.of(ProgramPreviewPrograms.blue1("sphere"),ProgramPreviewPrograms.blue4("sphere"),ProgramPreviewPrograms.blue6("star_prism"))) {
            var session=session(new World(1),fixture);
            for (int tick=0;tick<=80;tick++) session.advance(tick,256);
            var wire=new com.bitsson.gensokyou.danmaku.program.network.ProgramWire(com.bitsson.gensokyou.danmaku.program.network.NetworkLimits.defaults());
            var sent=new ArrayList<byte[]>();
            var sync=new com.bitsson.gensokyou.danmaku.program.network.ProgramSyncServer(wire,sent::add,20);
            var replica=new com.bitsson.gensokyou.danmaku.program.network.ProgramReplica(wire,ProgramPreviewPrograms.registry(),ProgramPreviewPrograms.limits());
            var projection=com.bitsson.gensokyou.danmaku.program.network.ReplicaState.capture(session.checkpoint(),wire);
            sync.publishSpatial(projection,ProgramPreviewBounds.groups(projection),ProgramPreviewBounds.members(projection),new Vec3(0,1,18),32,256,128);
            for (byte[] bytes:sent) replica.receive(bytes,0);
            assertEquals(0,replica.failures());
            assertFalse(replica.renderViews("test:blue1",80).isEmpty());
            if (fixture.definition().id().equals("gensokyou:preview_blue6"))
                assertTrue(replica.renderViews("test:blue1",88).stream().anyMatch(view->!view.guides().isEmpty()));
            if (fixture.definition().id().equals("gensokyou:preview_blue4")) {
                var id=session.checkpoint().runtime().groups().stream().flatMap(g->g.members().stream())
                        .filter(m->m.plan().program().collisionProfile() instanceof CollisionProfile.Laser).findFirst().orElseThrow().id();
                var image=com.bitsson.gensokyou.danmaku.program.network.ReplicaState.beam(projection.image(),id,0);
                sent.clear();
                sync.publishSpatial(new com.bitsson.gensokyou.danmaku.program.network.ReplicaState.Projection(image,projection.descriptions(),projection.definitions()),
                        ProgramPreviewBounds.groups(projection),ProgramPreviewBounds.members(projection),new Vec3(0,1,18),32,256,128);
                for (byte[] bytes:sent) replica.receive(bytes,1);
                assertTrue(replica.renderViews("test:blue1",80).stream().flatMap(view->view.members().stream()).anyMatch(m->m.id().equals(id) && m.beamLength()==0));
            }
        }
    }
    @Test void blue1ClearanceFailureCancelsAndMissingSlotDoesNotRetarget() {
        var world=new World(2); var session=session(world,ProgramPreviewPrograms.blue1("sphere"));
        session.advance(0,256);
        UUID departed=world.players.getFirst().uuid(); world.players.removeFirst();
        for (int tick=1;tick<=44;tick++) session.advance(tick,256);
        var commits=session.runtime().drainEvents().stream().filter(e->e.kind()==DanmakuWorldRuntime.Kind.COMMIT).toList();
        assertEquals(4,commits.size());
        assertTrue(session.checkpoint().runtime().groups().stream().filter(g->g.targetSlot()==0).allMatch(g->g.members().isEmpty()));
        assertTrue(session.checkpoint().runtime().groups().stream().allMatch(g->g.targets().getFirst().equals(departed)));
        var near=new World(1);
        var player=near.players.getFirst(); near.players.set(0,new DecisionProvider.Player(player.uuid(),new Vec3(0,0,3),new Vec3(0,1,3),2,true,false,false,.3));
        var unsafe=session(near,ProgramPreviewPrograms.blue1("sphere"));
        for (int tick=0;tick<=44;tick++) unsafe.advance(tick,256);
        assertEquals(0,unsafe.runtime().liveMembers());
        assertTrue(unsafe.runtime().drainEvents().stream().anyMatch(e->e.kind()==DanmakuWorldRuntime.Kind.REJECT));
    }
    @Test void blue4HasPhysicalGapSharedFrameAndActivationCorridorCancellation() {
        for (boolean blocked:new boolean[]{false,true}) {
            World world=new World(1) {
                @Override public Block block(int x,int y,int z) { return new Block(true,!blocked,blocked,blocked?1:0); }
            };
            var session=session(world,ProgramPreviewPrograms.blue4("sphere"));
            List<DanmakuWorldRuntime.Event> events=new ArrayList<>();
            for (int tick=0;tick<=240;tick++) {
                session.advance(tick,256); events.addAll(session.runtime().drainEvents());
                if (tick==40) {
                    var projection=com.bitsson.gensokyou.danmaku.program.network.ReplicaState.capture(session.checkpoint(),new com.bitsson.gensokyou.danmaku.program.network.ProgramWire(com.bitsson.gensokyou.danmaku.program.network.NetworkLimits.defaults()));
                    assertFalse(ProgramPreviewBounds.groups(projection).isEmpty());
                }
            }
            var commits=events.stream().filter(e->e.kind()==DanmakuWorldRuntime.Kind.COMMIT).toList();
            assertEquals(54,commits.stream().filter(e->e.plan().program().collisionProfile() instanceof CollisionProfile.Projectile).mapToInt(e->e.plan().members().size()).sum());
            assertEquals(blocked?0:8,commits.stream().filter(e->e.plan().program().collisionProfile() instanceof CollisionProfile.Laser).mapToInt(e->e.plan().members().size()).sum());
            if (!blocked) for (var commit:commits) if (commit.plan().program().collisionProfile() instanceof CollisionProfile.Laser) {
                for (var member:commit.plan().members()) {
                    assertEquals(1.5,Math.abs(member.position().x()),1e-9);
                    assertTrue(member.position().x()*member.direction().x()>0);
                }
                assertEquals(2.64,3-2*commit.plan().members().getFirst().style().collisionRadius(),1e-9);
            }
            assertTrue(session.checkpoint().runtime().groups().isEmpty());
        }
    }
    @Test void blue6UsesSharedFrameAndKeepsPanelOffsetsAcrossBothJoins() {
        var session=session(new World(1),ProgramPreviewPrograms.blue6("sphere"));
        List<DanmakuWorldRuntime.Event> events=new ArrayList<>();
        for (int tick=0;tick<=240;tick++) {
            session.advance(tick,256); events.addAll(session.runtime().drainEvents());
            if (tick==92) for (var group:session.checkpoint().runtime().groups()) {
                if (group.members().size()<2) continue;
                var a=group.members().get(0); var b=group.members().stream().filter(m->m.id().batch()==a.id().batch() && m.id().index()!=a.id().index()).findFirst().orElseThrow();
                Vec3 difference=session.runtime().state(b.id(),a.plan().birthTick()).position().subtract(session.runtime().state(a.id(),a.plan().birthTick()).position());
                for (double age:new double[]{29.999,30,45,59.999,60,90})
                    assertTrue(difference.subtract(session.runtime().state(b.id(),a.plan().birthTick()+age).position().subtract(session.runtime().state(a.id(),a.plan().birthTick()+age).position())).length()<1e-9);
                var path=a.plan().program().path();
                assertTrue(path.at(30-1e-7).position().subtract(path.at(30).position()).length()<1e-6);
                assertTrue(path.at(60-1e-7).velocity().subtract(path.at(60).velocity()).length()<1e-6);
                assertEquals(Vec3.Z.multiply(.2),path.at(90).velocity());
                var parent=session.checkpoint().runtime().groups().stream().filter(g->g.id()==group.parent()).findFirst().orElseThrow();
                assertEquals(parent.frame().forward(),group.frame().forward());
            }
        }
        var commits=events.stream().filter(e->e.kind()==DanmakuWorldRuntime.Kind.COMMIT).toList();
        assertEquals(96,commits.stream().mapToInt(e->e.plan().members().size()).sum());
        assertEquals(Set.of(20L,32L,44L,56L,68L,80L,92L,104L),commits.stream().map(DanmakuWorldRuntime.Event::tick).collect(java.util.stream.Collectors.toSet()));
        assertTrue(session.checkpoint().runtime().groups().isEmpty());
    }
    @Test void tailPearlsUseBoundedDerivedBatchesAndParentLifetime() {
        var world=new World(1); var fixture=ProgramPreviewPrograms.tailPearls();
        var runtime=new DanmakuWorldRuntime("test:tail-pearls",new DanmakuWorldRuntime.Limits(8192,512,256,256,20000),world);
        var session=new ProgramSession(runtime,fixture.execution());
        session.install(runtime.startEncounter(OWNER),1,false,fixture.definition(),new ClockAnchors(0,0,null,null),0);
        List<DanmakuWorldRuntime.Event> events=new ArrayList<>();
        int peak=0;
        for (int tick=0;tick<=900;tick++) {
            session.advance(tick,4096);
            events.addAll(session.runtime().drainEvents());
            peak=Math.max(peak,session.runtime().liveMembers());
            if (tick>=40 && tick<100)
                assertTrue(session.runtime().liveMembers()>=6,"carrier parents remain during their lifetime");
        }
        long carrierCommits=events.stream().filter(e->e.kind()==DanmakuWorldRuntime.Kind.COMMIT)
                .filter(e->e.plan()!=null && e.plan().program().pattern().countBound()==6).count();
        long pearlCommits=events.stream().filter(e->e.kind()==DanmakuWorldRuntime.Kind.COMMIT)
                .filter(e->e.plan()!=null && e.plan().program().pattern().countBound()==1).count();
        assertEquals(20,carrierCommits);
        assertEquals(20*6*24,pearlCommits);
        assertTrue(peak>6,"derived members coexist with later carrier batches");
        assertTrue(events.stream().anyMatch(e->e.kind()==DanmakuWorldRuntime.Kind.REMOVE && e.reason().equals("lifetime-or-unloaded")));
        assertTrue(session.checkpoint().runtime().encounters().isEmpty());
        assertEquals(0,session.runtime().liveMembers());
    }
    static final UUID OWNER=new UUID(0,1);
    @Test void renderEntryUsesFactHistoryAcrossRebaseAndRemovalBoundaries() {
        var session=session(new World(1),ProgramPreviewPrograms.flowerSea());
        var wire=new com.bitsson.gensokyou.danmaku.program.network.ProgramWire(com.bitsson.gensokyou.danmaku.program.network.NetworkLimits.defaults());
        var replica=new com.bitsson.gensokyou.danmaku.program.network.ProgramReplica(wire,ProgramPreviewPrograms.registry(),ProgramPreviewPrograms.limits());
        var packets=new ArrayList<byte[]>();
        var sync=new com.bitsson.gensokyou.danmaku.program.network.ProgramSyncServer(wire,packets::add,20);
        DanmakuWorldRuntime.MemberId id=null;
        Vec3 before=null,after=null;
        for (int tick=0;tick<=102;tick++) {
            session.advance(tick,256); session.runtime().drainEvents();
            if (tick==99) {
                id=session.checkpoint().runtime().groups().getFirst().members().getFirst().id();
                before=session.runtime().state(id,99.5).position();
            }
            if (tick==101) after=session.runtime().state(id,101.5).position();
            if (tick==102) session.runtime().remove(id,"test-hit");
            var projection=com.bitsson.gensokyou.danmaku.program.network.ReplicaState.capture(session.checkpoint(),wire);
            sync.publishSpatial(projection,ProgramPreviewBounds.groups(projection),ProgramPreviewBounds.members(projection),new Vec3(0,1,18),32,256,128);
            for (byte[] packet:packets) replica.receive(packet,tick);
            packets.clear();
        }
        var stableId=id;
        var old=replica.renderViews("test:blue1",99.5).stream().flatMap(v->v.members().stream()).filter(m->m.id().equals(stableId)).findFirst().orElseThrow();
        assertEquals(before,old.position());
        var released=replica.renderViews("test:blue1",101.5).stream().flatMap(v->v.members().stream()).filter(m->m.id().equals(stableId)).findFirst().orElseThrow();
        assertEquals(after,released.position());
        assertTrue(replica.renderViews("test:blue1",102).stream().flatMap(v->v.members().stream()).noneMatch(m->m.id().equals(stableId)));
    }
    @Test void flowersPublishContinuouslyAcrossReleaseWithoutRecovery() {
        var fixture=ProgramPreviewPrograms.flowerSea();
        var session=session(new World(1) {
            @Override public Optional<Actions.Pose> entity(UUID id) {
                return id.equals(OWNER)?Optional.of(new Actions.Pose(Frame.along(new Vec3(-84.351,7.62,-162.063),new Vec3(.31,.17,.93),Vec3.Y),Vec3.ZERO)):Optional.empty();
            }
            @Override public List<Player> players() { return List.of(new Player(new UUID(0,2),new Vec3(-84,6,-144),new Vec3(-84,7.6,-144),2,true,false,false,.3)); }
        },fixture);
        var wire=new com.bitsson.gensokyou.danmaku.program.network.ProgramWire(com.bitsson.gensokyou.danmaku.program.network.NetworkLimits.defaults());
        var replica=new com.bitsson.gensokyou.danmaku.program.network.ProgramReplica(wire,ProgramPreviewPrograms.registry(),ProgramPreviewPrograms.limits());
        var packets=new ArrayList<byte[]>();
        var sync=new com.bitsson.gensokyou.danmaku.program.network.ProgramSyncServer(wire,packets::add,20);
        var scope=new com.bitsson.gensokyou.danmaku.program.network.ProgramReplica.Scope("test:blue1",OWNER);
        for (int tick=0;tick<=420;tick++) {
            session.advance(tick,256); session.runtime().drainEvents();
            if (tick==110) {
                var member=session.checkpoint().runtime().groups().getFirst().members().getFirst();
                session.runtime().remove(member.id(),"test-collision");
            }
            var projection=com.bitsson.gensokyou.danmaku.program.network.ReplicaState.capture(session.checkpoint(),wire);
            sync.publishSpatial(projection,ProgramPreviewBounds.groups(projection),ProgramPreviewBounds.members(projection),new Vec3(-84+tick*.03,7.6,-144),32,256,128);
            for (byte[] packet:packets) replica.receive(packet,tick);
            packets.clear();
            assertEquals(0,replica.failures(),"digest at "+tick);
            assertTrue(replica.recoveryRequests(tick).isEmpty(),"recovery at "+tick);
            for (var view:replica.renderViews("test:blue1",tick+.5)) for (var rendered:view.members()) {
                var expected=session.runtime().state(rendered.id(),tick+.5).position();
                assertTrue(expected.subtract(rendered.position()).length()<1e-9,"render position at "+tick);
            }
            for (var group:session.checkpoint().runtime().groups()) for (var member:group.members())
                assertTrue(session.runtime().state(member.id(),tick).position().subtract(replica.state(scope,member.id(),tick).position()).length()<1e-9,"position at "+tick);
        }
        assertEquals(0,sync.stats().logicalBytes().getOrDefault(com.bitsson.gensokyou.danmaku.program.network.ProgramWire.Kind.CHECKPOINT,0L));
    }
    @Test void secondPreviewAfterIdleReanchorsClockAndRendersNewBirths() {
        for (boolean release:List.of(false,true)) {
            var fixture=ProgramPreviewPrograms.tailPearls();
            var wire=new com.bitsson.gensokyou.danmaku.program.network.ProgramWire(com.bitsson.gensokyou.danmaku.program.network.NetworkLimits.defaults());
            var replica=new com.bitsson.gensokyou.danmaku.program.network.ProgramReplica(wire,ProgramPreviewPrograms.registry(),ProgramPreviewPrograms.limits());
            var first=session(new World(1),fixture);
            for (int tick=0;tick<=8;tick++) first.advance(tick,256);
            var sent=new ArrayList<byte[]>();
            var sync=new com.bitsson.gensokyou.danmaku.program.network.ProgramSyncServer(wire,sent::add,20);
            var projection=com.bitsson.gensokyou.danmaku.program.network.ReplicaState.capture(first.checkpoint(),wire);
            sync.publishSpatial(projection,ProgramPreviewBounds.groups(projection),ProgramPreviewBounds.members(projection),new Vec3(0,1,18),32,256,128);
            for (byte[] bytes:sent) replica.receive(bytes,8);
            assertFalse(replica.renderViews("test:blue1",replica.renderTime("test:blue1",8)).isEmpty());
            sent.clear();
            if (release) sync.publish(projection,Map.of(),new Vec3(0,1,18),0);
            else sync.end("test:blue1",first.checkpoint().runtime().encounters().getFirst(),9,first.checkpoint().runtime().revision());
            for (byte[] bytes:sent) replica.receive(bytes,9);
            for (int tick=10;tick<2000;tick++) replica.noteTick(tick);
            var runtime=new DanmakuWorldRuntime("test:blue1",LIMITS,new World(1));
            var encounter=runtime.startEncounter(OWNER);
            runtime.endEncounter(encounter,"old-epoch"); encounter=runtime.startEncounter(OWNER);
            var second=new ProgramSession(runtime,fixture.execution());
            second.install(encounter,1,false,fixture.definition(),new ClockAnchors(2000,2000,null,null),2000);
            second.advance(2000,256);
            sent.clear();
            var secondSync=new com.bitsson.gensokyou.danmaku.program.network.ProgramSyncServer(wire,sent::add,20);
            var next=com.bitsson.gensokyou.danmaku.program.network.ReplicaState.capture(second.checkpoint(),wire);
            secondSync.publishSpatial(next,ProgramPreviewBounds.groups(next),ProgramPreviewBounds.members(next),new Vec3(0,1,18),32,256,128);
            for (byte[] bytes:sent) replica.receive(bytes,2000);
            double renderTime=replica.renderTime("test:blue1",2000);
            assertEquals(2000,renderTime,1e-9);
            assertEquals(6,replica.renderViews("test:blue1",renderTime).stream().mapToInt(v->v.members().size()).sum());
            assertEquals(0,replica.failures());
        }
    }
    @Test void continuousDerivedPublicationUsesDecisionsWithoutCheckpointStormOrGhosts() {
        var fixture=ProgramPreviewPrograms.tailPearls();
        var runtime=new DanmakuWorldRuntime("test:blue1",new DanmakuWorldRuntime.Limits(4096,256,128,128,20000),new World(1));
        var session=new ProgramSession(runtime,fixture.execution());
        session.install(runtime.startEncounter(OWNER),1,false,fixture.definition(),new ClockAnchors(0,0,null,null),0);
        var wire=new com.bitsson.gensokyou.danmaku.program.network.ProgramWire(com.bitsson.gensokyou.danmaku.program.network.NetworkLimits.defaults());
        var sent=new ArrayList<byte[]>();
        var sync=new com.bitsson.gensokyou.danmaku.program.network.ProgramSyncServer(wire,sent::add,20);
        var replica=new com.bitsson.gensokyou.danmaku.program.network.ProgramReplica(wire,ProgramPreviewPrograms.registry(),ProgramPreviewPrograms.limits());
        var scope=new com.bitsson.gensokyou.danmaku.program.network.ProgramReplica.Scope("test:blue1",OWNER);
        for (int tick=0;tick<=879;tick++) {
            session.advance(tick,256); session.runtime().drainEvents();
            if (tick==40) {
                var id=session.checkpoint().runtime().groups().getFirst().members().getFirst().id();
                session.runtime().remove(id,"test-authority-removal");
            }
            var projection=com.bitsson.gensokyou.danmaku.program.network.ReplicaState.capture(session.checkpoint(),wire);
            sync.publishSpatial(projection,ProgramPreviewBounds.groups(projection),ProgramPreviewBounds.members(projection),new Vec3(0,1,18),32,256,128);
            for (byte[] bytes:sent) replica.receive(bytes,tick);
            sent.clear();
            // Exercise the real display clock after emission ceases and only sparse facts remain.
            for (double partial:replica.active("test:blue1")?new double[]{0,.5,.99}:new double[0]) {
                double display=replica.renderTime("test:blue1",tick+partial);
                assertTrue(replica.clock("test:blue1").renderable(),"blank frame at "+tick);
                assertEquals(tick+partial,display,1e-6);
            }
            if (tick==360) {
                // The crash occurred while digesting this high-occupancy image; late join must also fit.
                byte[] checkpoint=assertDoesNotThrow(()->wire.image(projection.image()));
                assertTrue(checkpoint.length<wire.limits().snapshotBytes());
                var lateSent=new ArrayList<byte[]>();
                var lateSync=new com.bitsson.gensokyou.danmaku.program.network.ProgramSyncServer(wire,lateSent::add,20);
                lateSync.publishSpatial(projection,ProgramPreviewBounds.groups(projection),ProgramPreviewBounds.members(projection),new Vec3(0,1,18),32,256,128);
                var lateReplica=new com.bitsson.gensokyou.danmaku.program.network.ProgramReplica(wire,ProgramPreviewPrograms.registry(),ProgramPreviewPrograms.limits());
                for (byte[] bytes:lateSent) lateReplica.receive(bytes,tick);
                assertEquals(session.runtime().liveMembers(),lateReplica.renderViews("test:blue1",tick).stream().mapToInt(v->v.members().size()).sum());
                assertEquals(0,lateReplica.failures());
            }
            assertEquals(0,replica.failures(),"tick "+tick);
            assertTrue(replica.recoveryRequests(tick).isEmpty(),"no recovery storm at "+tick);
            assertEquals(session.runtime().liveMembers(),replica.renderViews("test:blue1",tick).stream().mapToInt(v->v.members().size()).sum());
        }
        assertEquals(0,sync.stats().logicalBytes().getOrDefault(com.bitsson.gensokyou.danmaku.program.network.ProgramWire.Kind.CHECKPOINT,0L));
        assertTrue(sync.stats().logicalBytes().containsKey(com.bitsson.gensokyou.danmaku.program.network.ProgramWire.Kind.DECISIONS));
        var encounter=session.checkpoint().runtime().encounters().getFirst();
        sync.end("test:blue1",encounter,880,session.checkpoint().runtime().revision());
        for (byte[] bytes:sent) replica.receive(bytes,880);
        assertTrue(replica.image(scope).isEmpty());
        assertTrue(replica.renderViews("test:blue1",880).isEmpty());
    }
    @Test void flowerSeaSeparatesCorePetalsSamplesAndRetainsAcrossCycle() {
        var fixture=ProgramPreviewPrograms.flowerSea(); var world=new World(1);
        var runtime=new DanmakuWorldRuntime("test:flowers",new DanmakuWorldRuntime.Limits(4096,256,128,128,20000),world);
        var session=new ProgramSession(runtime,fixture.execution());
        session.install(runtime.startEncounter(OWNER),1,false,fixture.definition(),new ClockAnchors(0,0,null,null),0);
        long births=0,samples=0;
        for (int tick=0;tick<=701;tick++) {
            session.advance(tick,4096);
            var events=runtime.drainEvents();
            births+=events.stream().filter(e->e.kind()==DanmakuWorldRuntime.Kind.COMMIT).mapToLong(e->e.plan().members().size()).sum();
            samples+=events.stream().filter(e->e.kind()==DanmakuWorldRuntime.Kind.TARGET_SAMPLE).count();
            if (tick==99) assertTrue(session.checkpoint().runtime().groups().stream().flatMap(g->g.members().stream()).noneMatch(m->runtime.damaging(m.id(),99)));
            if (tick==100) {
                var group=session.checkpoint().runtime().groups().getFirst();
                var core=group.members().stream().filter(m->m.template().tags().contains("core")).findFirst().orElseThrow();
                assertEquals(4,core.template().style().damageScale());
                Vec3 center=runtime.state(core.id(),100).position();
                assertEquals(.3,runtime.state(core.id(),101).position().subtract(center).length(),1e-9);
                for (var petal:group.members()) if (petal.template().tags().contains("petals")) {
                    Vec3 offset=runtime.state(petal.id(),100).position().subtract(center);
                    Vec3 velocity=runtime.state(petal.id(),101).velocity();
                    assertEquals(.2,velocity.length(),1e-9);
                    assertTrue(offset.unit().subtract(velocity.unit()).length()<1e-9);
                }
            }
            if (tick==240) assertEquals(17*46,runtime.liveMembers());
            if (tick==120) {
                var wire=new com.bitsson.gensokyou.danmaku.program.network.ProgramWire(com.bitsson.gensokyou.danmaku.program.network.NetworkLimits.defaults());
                var projection=com.bitsson.gensokyou.danmaku.program.network.ReplicaState.capture(session.checkpoint(),wire);
                var bounds=ProgramPreviewBounds.groups(projection);
                for (var group:session.checkpoint().runtime().groups()) for (var member:group.members()) {
                    assertTrue(bounds.get(group.id()).near(runtime.state(member.id(),member.plan().birthTick()+399).position(),0));
                }
                var sent=new ArrayList<byte[]>();
                var sync=new com.bitsson.gensokyou.danmaku.program.network.ProgramSyncServer(wire,sent::add,20);
                sync.publishSpatial(projection,bounds,ProgramPreviewBounds.members(projection),new Vec3(0,1,18),32,256,128);
                var replica=new com.bitsson.gensokyou.danmaku.program.network.ProgramReplica(wire,ProgramPreviewPrograms.registry(),ProgramPreviewPrograms.limits());
                for (byte[] packet:sent) replica.receive(packet,0);
                assertEquals(0,replica.failures());
                var scope=new com.bitsson.gensokyou.danmaku.program.network.ProgramReplica.Scope("test:flowers",OWNER);
                for (var group:session.checkpoint().runtime().groups()) for (var member:group.members())
                    assertEquals(runtime.state(member.id(),120).position(),replica.state(scope,member.id(),120).position());
                // A newly received rebase must not evaluate its future path at negative age.
                assertDoesNotThrow(()->replica.renderViews("test:flowers",99.5));
            }
        }
        assertEquals(32*46,births); assertEquals(32,samples);
        assertEquals(0,runtime.liveMembers()); assertTrue(session.checkpoint().instances().isEmpty());
    }
    @Test void tailPearlsRecoverThroughNetworkAndRejectWholeDerivedBatchAtCapacity() {
        var fixture=ProgramPreviewPrograms.tailPearls(); var world=new World(1);
        var session=session(world,fixture);
        for (int tick=0;tick<=48;tick++) session.advance(tick,256);
        var wire=new com.bitsson.gensokyou.danmaku.program.network.ProgramWire(com.bitsson.gensokyou.danmaku.program.network.NetworkLimits.defaults());
        var sent=new ArrayList<byte[]>();
        var sync=new com.bitsson.gensokyou.danmaku.program.network.ProgramSyncServer(wire,sent::add,20);
        var replica=new com.bitsson.gensokyou.danmaku.program.network.ProgramReplica(wire,ProgramPreviewPrograms.registry(),ProgramPreviewPrograms.limits());
        var projection=com.bitsson.gensokyou.danmaku.program.network.ReplicaState.capture(session.checkpoint(),wire);
        sync.publishSpatial(projection,ProgramPreviewBounds.groups(projection),ProgramPreviewBounds.members(projection),new Vec3(0,1,18),32,256,128);
        for (byte[] bytes:sent) replica.receive(bytes,0);
        assertEquals(0,replica.failures());
        var scope=new com.bitsson.gensokyou.danmaku.program.network.ProgramReplica.Scope("test:blue1",OWNER);
        for (var group:session.checkpoint().runtime().groups()) for (var member:group.members()) {
            assertEquals(session.runtime().state(member.id(),48).position(),replica.state(scope,member.id(),48).position());
        }
        var limited=new DanmakuWorldRuntime("test:limited-tail",new DanmakuWorldRuntime.Limits(6,256,128,128,4096),world);
        var bounded=new ProgramSession(limited,fixture.execution());
        bounded.install(limited.startEncounter(OWNER),1,false,fixture.definition(),new ClockAnchors(0,0,null,null),0);
        for (int tick=0;tick<=8;tick++) bounded.advance(tick,256);
        assertEquals(6,limited.liveMembers());
        assertEquals(12,limited.drainEvents().stream().filter(e->e.kind()==DanmakuWorldRuntime.Kind.REJECT && e.reason().equals("budget")).count());
    }
    @Test void typedTargetRebaseSamplesOnceAndDoesNotTrackLaterMovement() {
        var codec=ServerControlOperations.dataRegistry().freeze();
        var rebase=ServerControlOperations.type("rebase_target",ServerControlOperations.RebaseTarget.class,codec);
        var description=new ServerControlOperations.RebaseTarget("grid",0,AuthorityDescriptions.TargetPoint.EYES,new Selection.IndexRange(0,1),.3);
        assertEquals(description,rebase.decode(rebase.encode(description)));
        var world=new World(1); var session=session(world,ProgramPreviewPrograms.blue1Slice());
        for (int tick=0;tick<=30;tick++) session.advance(tick,256);
        var group=session.checkpoint().runtime().groups().getFirst();
        var member=group.members().getFirst(); var origin=session.runtime().state(member.id(),30).position();
        var target=session.runtime().sampleTarget(group.id(),0,AuthorityDescriptions.TargetPoint.EYES).orElseThrow();
        session.runtime().rebase(member.id(),new Paths.Linear(target.subtract(origin),.3),true);
        var snapshot=session.checkpoint();
        world.players.clear();
        var restored=ProgramSession.restore(snapshot,LIMITS,world,ProgramPreviewPrograms.blue1Slice().execution());
        assertEquals(session.runtime().state(member.id(),40),restored.runtime().state(member.id(),40));
        assertEquals(3,restored.runtime().state(member.id(),40).position().subtract(origin).length(),1e-9);
    }
    @Test void removingCarrierStopsFuturePearlsButRetainsBornChildren() {
        var session=session(new World(1),ProgramPreviewPrograms.tailPearls());
        for (int tick=0;tick<=8;tick++) session.advance(tick,256);
        var group=session.checkpoint().runtime().groups().getFirst();
        var carrier=group.members().stream().filter(m->m.template().tags().contains("carrier")).findFirst().orElseThrow();
        var pearls=group.members().stream().filter(m->m.template().tags().contains("pearl")).toList();
        assertEquals(12,pearls.size());
        session.runtime().remove(carrier.id(),"test-parent-death");
        session.runtime().drainEvents();
        for (int tick=9;tick<=16;tick++) session.advance(tick,256);
        var commits=session.runtime().drainEvents().stream().filter(e->e.kind()==DanmakuWorldRuntime.Kind.COMMIT).toList();
        assertEquals(10,commits.size());
        assertTrue(pearls.stream().allMatch(m->session.runtime().member(m.id()).isPresent()));
        var pearl=pearls.getFirst();
        assertEquals(session.runtime().state(pearl.id(),pearl.plan().birthTick()).position(),
                session.runtime().state(pearl.id(),pearl.plan().birthTick()+39.9).position());
        assertEquals(.06,session.runtime().state(pearl.id(),pearl.plan().birthTick()+41).position()
                .subtract(session.runtime().state(pearl.id(),pearl.plan().birthTick()+40).position()).length(),1e-9);
    }
    static final DanmakuWorldRuntime.Limits LIMITS=new DanmakuWorldRuntime.Limits(1024,256,128,128,20000);
    static class World implements DecisionProvider {
        List<Player> players=new ArrayList<>();
        World(int count) {
            for (int i=0;i<count;i++) {
                Vec3 feet=new Vec3(i*2,0,18);
                players.add(new Player(new UUID(0,i+2),feet,feet.add(Vec3.Y),2,true,false,false,.3));
            }
        }
        public List<Player> players() { return List.copyOf(players); }
        public Optional<Actions.Pose> entity(UUID id) { return id.equals(OWNER)?Optional.of(new Actions.Pose(Frame.IDENTITY,Vec3.ZERO)):Optional.empty(); }
        public boolean loaded(Vec3 point) { return true; }
        public Block block(int x,int y,int z) { return new Block(true,true,false,0); }
    }
    static ProgramSession session(World world,ProgramPreviewPrograms.Installed definition) {
        var runtime=new DanmakuWorldRuntime("test:blue1",LIMITS,world);
        var session=new ProgramSession(runtime,definition.execution()); var encounter=runtime.startEncounter(OWNER);
        session.install(encounter,1,false,definition.definition(),new ClockAnchors(0,0,null,null),0);
        return session;
    }
    @Test void manifestCountsAndTimesForOneTwoFivePlayersAndTailCleanup() {
        for (int players:new int[]{1,2,5}) {
            var session=session(new World(players),ProgramPreviewPrograms.blue1("sphere"));
            List<DanmakuWorldRuntime.Event> events=new ArrayList<>();
            for (int tick=0;tick<=240;tick++) {
                session.advance(tick,256); events.addAll(session.runtime().drainEvents());
                if (tick==163) assertTrue(session.runtime().movementLocked(session.checkpoint().runtime().encounters().getFirst()));
                if (tick==164) assertFalse(session.runtime().movementLocked(session.checkpoint().runtime().encounters().getFirst()));
                if (tick==220) {
                    assertEquals(0,session.runtime().stats().plans());
                    assertTrue(session.checkpoint().runtime().groups().stream().flatMap(g->g.members().stream()).noneMatch(DanmakuWorldRuntime.MemberSnapshot::harmful));
                }
                if (tick==230) assertEquals(0,session.runtime().liveMembers());
            }
            var commits=events.stream().filter(e->e.kind()==DanmakuWorldRuntime.Kind.COMMIT).toList();
            assertEquals(12*players,commits.size());
            assertEquals(108*players,commits.stream().mapToInt(e->e.plan().members().size()).sum());
            assertEquals(Set.of(20L,28L,36L,44L,80L,88L,96L,104L,140L,148L,156L,164L),
                    commits.stream().map(DanmakuWorldRuntime.Event::tick).collect(java.util.stream.Collectors.toSet()));
            assertEquals(new DanmakuWorldRuntime.RuntimeStats(0,0,0,0,0,0,0),session.runtime().stats());
            assertTrue(session.checkpoint().instances().isEmpty());
        }
    }
    @Test void capturedSlotsAndDirectionsStayFixedDespitePlayerReorderingAndRecovery() {
        var world=new World(2); var fixture=ProgramPreviewPrograms.blue1("star_prism"); var session=session(world,fixture);
        session.advance(0,256); var snapshot=session.checkpoint();
        var frames=snapshot.runtime().groups().stream().map(DanmakuWorldRuntime.GroupSnapshot::frame).toList();
        Collections.reverse(world.players);
        world.players.replaceAll(p->new DecisionProvider.Player(p.uuid(),p.feet().add(Vec3.X.multiply(10)),p.eyes().add(Vec3.X.multiply(10)),2,true,false,false,.3));
        var restored=ProgramSession.restore(snapshot,LIMITS,world,fixture.execution());
        for (int tick=1;tick<=44;tick++) { session.advance(tick,256); restored.advance(tick,256); assertEquals(session.checkpoint(),restored.checkpoint()); }
        assertEquals(frames,session.checkpoint().runtime().groups().stream().map(DanmakuWorldRuntime.GroupSnapshot::frame).toList());
        for (var group:session.checkpoint().runtime().groups()) for (var member:group.members())
            assertEquals(group.frame(),member.plan().source().frame());
    }
}
