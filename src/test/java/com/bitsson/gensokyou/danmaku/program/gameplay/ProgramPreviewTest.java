package com.bitsson.gensokyou.danmaku.program.gameplay;

import com.bitsson.gensokyou.danmaku.program.*;
import com.bitsson.gensokyou.danmaku.program.math.*;
import com.bitsson.gensokyou.danmaku.program.network.*;
import com.bitsson.gensokyou.danmaku.program.ops.*;
import com.bitsson.gensokyou.danmaku.program.server.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProgramPreviewTest {
    @Test void replacingSphereWithStarPreservesCollisionAndTrajectory() {
        var world=new World();
        var sphere=session(world,ProgramPreviewPrograms.blue1Slice());
        var star=session(world,ProgramPreviewPrograms.blue1Slice("star_prism"));
        for (int tick=0;tick<=30;tick++) { sphere.advance(tick,256); star.advance(tick,256); }
        var a=sphere.runtime().checkpoint().groups().getFirst().members().getFirst();
        var b=star.runtime().checkpoint().groups().getFirst().members().getFirst();
        assertEquals(a.template().style().collisionRadius(),b.template().style().collisionRadius());
        assertEquals(.1,b.template().style().collisionRadius());
        assertEquals(.2,b.template().style().visualRadius());
        assertEquals(sphere.runtime().state(a.id(),30),star.runtime().state(b.id(),30));
        var wire=new ProgramWire(NetworkLimits.defaults());
        assertEquals(b.plan().program(),wire.decodeData(wire.encodeData(b.plan().program()),Actions.ChildProgram.class));
    }
    private static final UUID OWNER=new UUID(0,1), TARGET=new UUID(0,2);
    private static final DanmakuWorldRuntime.Limits LIMITS=new DanmakuWorldRuntime.Limits(512,128,64,64,4096);
    private static final class World implements DecisionProvider {
        boolean owner=true;
        public List<Player> players() { return List.of(new Player(TARGET,new Vec3(0,0,12),new Vec3(0,1,12),2,true,false,false,.3)); }
        public Optional<Actions.Pose> entity(UUID id) { return owner && id.equals(OWNER)?Optional.of(new Actions.Pose(Frame.IDENTITY,Vec3.ZERO)):Optional.empty(); }
        public boolean loaded(Vec3 p) { return true; }
        public Block block(int x,int y,int z) { return new Block(true,true,false,0); }
    }
    private static ProgramSession session(World world,ProgramPreviewPrograms.Installed fixture) {
        var runtime=new DanmakuWorldRuntime("test:preview",LIMITS,world);
        var session=new ProgramSession(runtime,fixture.execution());
        var encounter=runtime.startEncounter(OWNER);
        session.install(encounter,1,false,fixture.definition(),new ClockAnchors(0,0,null,null),0);
        return session;
    }
    @Test void warningBirthRecoveryAndTerminalCleanupUseCompiledProgram() {
        var fixture=ProgramPreviewPrograms.blue1Slice(); var world=new World(); var session=session(world,fixture);
        session.advance(0,256);
        assertEquals(0,session.runtime().liveMembers());
        assertEquals(1,session.runtime().stats().plans());
        var plan=session.runtime().checkpoint().groups().getFirst().plans().getFirst();
        assertEquals(new Vec3(0,1,0),plan.source().frame().origin());
        assertEquals(Vec3.Z,plan.source().frame().forward());
        var restored=ProgramSession.restore(session.checkpoint(),LIMITS,world,fixture.execution());
        for (int tick=1;tick<=320;tick++) {
            session.advance(tick,256); restored.advance(tick,256);
            if (tick==20) assertEquals(18,session.runtime().liveMembers());
            assertEquals(session.checkpoint(),restored.checkpoint(),"boundary "+tick);
        }
        assertTrue(session.checkpoint().instances().isEmpty());
        assertTrue(session.checkpoint().runtime().encounters().isEmpty());
        assertEquals(new DanmakuWorldRuntime.RuntimeStats(0,0,0,0,0,0,0),session.runtime().stats());
        assertTrue(session.checkpoint().runtime().targetHandles().isEmpty());
        assertEquals(5,session.runtime().drainEvents().stream().filter(e->e.kind()==DanmakuWorldRuntime.Kind.COMMIT).count());
    }
    @Test void ownerLossRevokesWarningAndAllFutureOccurrences() {
        var fixture=ProgramPreviewPrograms.blue1Slice(); var world=new World(); var session=session(world,fixture);
        session.advance(0,256); world.owner=false;
        for (int tick=1;tick<=80;tick++) session.advance(tick,256);
        assertTrue(session.checkpoint().instances().isEmpty());
        assertEquals(0,session.runtime().stats().groups());
        assertEquals(0,session.runtime().liveMembers());
        assertFalse(session.runtime().drainEvents().stream().anyMatch(e->e.kind()==DanmakuWorldRuntime.Kind.COMMIT));
    }
    @Test void actualPreviewDefinitionLateJoinRenderAndEndRoundTrip() {
        var fixture=ProgramPreviewPrograms.blue1Slice(); var session=session(new World(),fixture);
        for (int tick=0;tick<=30;tick++) session.advance(tick,256);
        var wire=new ProgramWire(NetworkLimits.defaults()); var sent=new ArrayList<byte[]>();
        var sync=new ProgramSyncServer(wire,sent::add,20);
        var replica=new ProgramReplica(wire,ProgramPreviewPrograms.registry(),ProgramLimits.defaults());
        var projection=ReplicaState.capture(session.checkpoint(),wire);
        sync.publishSpatial(projection,ProgramPreviewBounds.groups(projection),ProgramPreviewBounds.members(projection),new Vec3(0,1,12),32,256,128);
        for (byte[] bytes:sent) replica.receive(bytes,0);
        var scope=new ProgramReplica.Scope("test:preview",OWNER);
        assertEquals(0,replica.failures());
        assertTrue(replica.image(scope).isPresent());
        // The authority has committed birth=20, but the client clock may still be before birth.
        for (double time:new double[]{0,19,19.999}) {
            var views=assertDoesNotThrow(()->replica.renderViews("test:preview",time));
            assertTrue(views.stream().allMatch(view->view.members().isEmpty()));
        }
        assertEquals(18,replica.renderViews("test:preview",20).stream().mapToInt(view->view.members().size()).sum());
        assertTrue(replica.renderViews("test:preview",120).stream().allMatch(view->view.members().isEmpty()));
        assertFalse(replica.renderViews("test:preview",30).isEmpty());
        var member=session.runtime().checkpoint().groups().getFirst().members().getFirst().id();
        assertEquals(session.runtime().state(member,30).position(),replica.state(scope,member,30).position());
        // Render frames between ticks must advance analytically, rather than holding at 20 Hz.
        double time0=replica.renderTime("test:preview",0);
        var position0=replica.state(scope,member,time0).position();
        for (double partial:new double[]{.1,.25,.5,.75,.9}) {
            double renderTime=replica.renderTime("test:preview",partial);
            assertEquals(time0+partial,renderTime,1e-9);
            assertEquals(ProgramPreviewPrograms.SPEED*partial,
                    replica.state(scope,member,renderTime).position().subtract(position0).length(),1e-9);
        }
        var old=List.copyOf(sent); sent.clear();
        var encounter=session.checkpoint().runtime().encounters().getFirst();
        session.runtime().endEncounter(encounter,"test-stop");
        sync.end("test:preview",encounter,31,session.checkpoint().runtime().revision());
        for (byte[] bytes:sent) replica.receive(bytes,1);
        for (byte[] bytes:old) replica.receive(bytes,2);
        assertTrue(replica.image(scope).isEmpty());
    }
    @Test void previewBoundsContainEveryLiveTrajectoryAndPendingWarning() {
        var fixture=ProgramPreviewPrograms.blue1Slice(); var session=session(new World(),fixture);
        var wire=new ProgramWire(NetworkLimits.defaults());
        for (int tick=0;tick<=280;tick++) {
            session.advance(tick,256);
            var projection=ReplicaState.capture(session.checkpoint(),wire);
            var bounds=ProgramPreviewBounds.groups(projection); var members=ProgramPreviewBounds.members(projection);
            for (var group:session.runtime().checkpoint().groups()) {
                for (var plan:group.plans()) assertTrue(bounds.get(group.id()).near(plan.source().frame().origin(),0));
                for (var member:group.members()) {
                    var box=members.get(member.id()); assertNotNull(box);
                    for (double age:new double[]{0,50,99.9}) assertTrue(box.near(session.runtime().state(member.id(),member.plan().birthTick()+age).position(),0));
                }
            }
        }
    }
}
