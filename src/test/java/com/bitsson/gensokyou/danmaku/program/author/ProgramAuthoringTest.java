package com.bitsson.gensokyou.danmaku.program.author;

import com.bitsson.gensokyou.danmaku.program.*;
import com.bitsson.gensokyou.danmaku.program.gameplay.*;
import com.bitsson.gensokyou.danmaku.program.math.*;
import com.bitsson.gensokyou.danmaku.program.network.*;
import com.bitsson.gensokyou.danmaku.program.ops.*;
import com.bitsson.gensokyou.danmaku.program.server.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProgramAuthoringTest {
    @Test void hiddenPreviewHasWarningTwoTypesAndFiniteCleanup() {
        var fixture=AuthoringTemplates.hiddenState(); var session=session();
        for (var node:fixture.definition().nodes()) if (node instanceof OperationNode operation) {
            var value=StandardProgramOperations.decode(operation).orElse(null);
            if (value instanceof ServerControlOperations.Fire fire) assertEquals(0,fire.policy().minimumClearance());
        }
        var battle=new BossProgramController(session,OWNER); battle.card(fixture,0);
        for (int tick=0;tick<=441;tick++) {
            session.advance(tick,512); session.runtime().drainEvents();
            if (tick==39) assertEquals(0,session.runtime().liveMembers());
            if (tick==40 || tick==80 || tick==120) {
                assertEquals(5,session.runtime().liveMembers());
                for (var group:session.checkpoint().runtime().groups()) for (var member:group.members())
                    assertEquals(tick!=80,session.runtime().damaging(member.id(),tick));
            }
        }
        assertTrue(session.checkpoint().instances().isEmpty()); assertEquals(0,session.runtime().liveMembers()); battle.close();
    }
    @Test void hiddenProjectilesAndLasersStayVisibleAndNeverDispatchCollision() {
        for (String style:List.of("sphere","star_prism","laser")) {
            boolean laser=style.equals("laser");
            var members=MemberProgram.attack("hidden probe",MemberProgram.styled(new Patterns.Point(Vec3.ZERO,Vec3.Z),style,"blue",.1,1,.2),
                    new Paths.Linear(Vec3.Z,.1),40).fixedDirection().hiddenCycle(TimeDomain.MEMBER_AGE,20,10,0);
            if (laser) members.laser(12,false,1);
            var card=new CardProgram("test:hidden_"+laser).clearance(0);
            var targets=card.targets("players",64,1); var gun=card.aimed("gun",targets,0,Vec3.ZERO);
            card.fire(gun,members.build(),0,1);
            var session=session(); var battle=new BossProgramController(session,OWNER); battle.card(card.once(),0);
            var hits=new ArrayList<Long>();
            session.runtime().collisions((runtime,id,from,until)->hits.add(runtime.tick()));
            var wire=new ProgramWire(NetworkLimits.defaults());
            var replica=new ProgramReplica(wire,StandardProgramOperations.registry(),StandardProgramOperations.limits());
            var sync=new ProgramSyncServer(wire,bytes->replica.receive(bytes,0),20);
            for (int tick=0;tick<30;tick++) {
                session.advance(tick,512); session.runtime().drainEvents();
                var projection=ReplicaState.capture(session.checkpoint(),wire);
                sync.publishSpatial(projection,ProgramMotionBounds.groups(projection),ProgramMotionBounds.members(projection),Vec3.ZERO,1000,256,128);
                var rendered=replica.renderViews("test:author",tick).stream().flatMap(view->view.members().stream()).findFirst().orElseThrow();
                assertTrue(rendered.visible());
                assertEquals(tick<10 || tick>=20,rendered.damaging());
                double dim=laser?com.bitsson.gensokyou.danmaku.visual.DanmakuHiddenStyle.LASER_DIM:Presentation.HIDDEN_ALPHA;
                assertEquals(tick<10 || tick>=20?1:dim,rendered.alpha(),1e-12);
                assertEquals(tick<10 || tick>=20?1:laser?dim:1,rendered.brightness(),1e-12);
            }
            assertEquals(20,hits.size());
            assertFalse(hits.stream().anyMatch(t->t>=10 && t<20));
            assertEquals(0,replica.failures()); battle.close();
        }
    }
    @Test void hiddenModelOpacityUsesItsRegisteredProfileInsteadOfDefault() {
        var original=com.bitsson.gensokyou.danmaku.visual.DanmakuVisualProfile.STAR_PRISM;
        var custom=new com.bitsson.gensokyou.danmaku.visual.DanmakuVisualProfile.Profile(original.texture(),original.visualScale(),original.hitboxScale(),
                original.coreScale(),original.coreAlpha(),original.glowScale(),original.glowAlpha(),48,original.geometry(),original.colorMode(),
                original.colorCycleTicks(),original.tumbleAmpDeg(),original.tumblePeriod());
        ProgramVisuals.register("test:hidden_model",custom);
        var member=MemberProgram.attack("custom model",MemberProgram.styled(new Patterns.Point(Vec3.ZERO,Vec3.Z),"test:hidden_model","gold",.1,1,.3),
                new Paths.Hold(),40).fixedDirection().hiddenCycle(TimeDomain.MEMBER_AGE,20,10,0).build();
        var card=new CardProgram("test:profile").clearance(0); var targets=card.targets("players",64,1);
        card.fire(card.aimed("gun",targets,0,Vec3.ZERO),member,0,1);
        var session=session(); new BossProgramController(session,OWNER).card(card.once(),0);
        for (int t=0;t<=15;t++) { session.advance(t,512); session.runtime().drainEvents(); }
        var wire=new ProgramWire(NetworkLimits.defaults()); var replica=new ProgramReplica(wire,StandardProgramOperations.registry(),StandardProgramOperations.limits());
        var sync=new ProgramSyncServer(wire,bytes->replica.receive(bytes,0),20); var projection=ReplicaState.capture(session.checkpoint(),wire);
        sync.publishSpatial(projection,ProgramMotionBounds.groups(projection),ProgramMotionBounds.members(projection),Vec3.ZERO,1000,256,128);
        var view=replica.renderViews("test:author",15).getFirst().members().getFirst();
        assertEquals(48.0/255,view.alpha(),1e-12); assertEquals(1,view.brightness(),1e-12); assertFalse(view.damaging());
    }
    private static final UUID OWNER=new UUID(0,1),OTHER=new UUID(0,3),PLAYER=new UUID(0,2);
    private static final DanmakuWorldRuntime.Limits LIMITS=new DanmakuWorldRuntime.Limits(4096,512,256,256,65536);
    private static final class World implements DecisionProvider {
        public List<Player> players() { return List.of(new Player(PLAYER,new Vec3(0,0,20),new Vec3(0,1,20),2,true,false,false,.3)); }
        public Optional<Actions.Pose> entity(UUID uuid) { return Optional.of(new Actions.Pose(Frame.IDENTITY,Vec3.ZERO)); }
        public boolean loaded(Vec3 point) { return true; }
        public Block block(int x,int y,int z) { return new Block(true,true,false,0); }
    }
    private static ProgramSession session() { return new ProgramSession(new DanmakuWorldRuntime("test:author",LIMITS,new World()),StandardProgramOperations.execution()); }
    private static Actions.ChildProgram bullet(boolean attack) {
        var pattern=MemberProgram.styled(new Patterns.Point(Vec3.ZERO,Vec3.Z),"sphere","#123456",.1,1,.2);
        return (attack?MemberProgram.attack("bullet",pattern,new Paths.Linear(Vec3.Z,.1),40):MemberProgram.decoration("spark",pattern,new Paths.Hold(),40)).fixedDirection().build();
    }
    @Test void explicitSafetyAndNamedDiagnostics() {
        assertTrue(bullet(true).presentation().at(new Presentation.Clocks(0,0,0,1)).damaging());
        assertFalse(bullet(false).presentation().at(new Presentation.Clocks(0,0,0,1)).damaging());
        assertEquals(0x123456,ProgramVisuals.tint(new Pattern.Style("sphere","#123456",.1,1)));
        assertThrows(IllegalArgumentException.class,()->MemberProgram.styled(new Patterns.Point(Vec3.ZERO,Vec3.Z),"sphree","blue",.1,1,.2));
        assertThrows(IllegalArgumentException.class,()->MemberProgram.styled(new Patterns.Point(Vec3.ZERO,Vec3.Z),"sphere","bule",.1,1,.2));
        assertTrue(assertThrows(IllegalArgumentException.class,()->MemberProgram.attack("missing direction",new Patterns.Point(Vec3.ZERO,Vec3.Z),new Paths.Hold(),20).build()).getMessage().contains("missing direction"));
        var a=new CardProgram("test:first"); var b=new CardProgram("test:second");
        var targets=a.targets("players",64,1);
        assertTrue(assertThrows(IllegalArgumentException.class,()->b.aimed("gun",targets,0,Vec3.ZERO)).getMessage().contains("test:second"));
        var limited=new CardProgram("test:budget").limits(new ProgramLimits(4096,8,1,4096,256,262144));
        var t=limited.targets("players",64,1); var g=limited.aimed("gun",t,0,Vec3.ZERO);
        limited.fire(g,MemberProgram.attack("too many",new Patterns.Fan(3,.5,0),new Paths.Linear(Vec3.Z,.1),20).templateDirections().build(),10,1);
        assertTrue(assertThrows(IllegalArgumentException.class,limited::once).getMessage().contains("test:budget"));
    }
    @Test void templateDirectionsAndComputedDescendants() {
        var fan=MemberProgram.attack("fan",new Patterns.Fan(3,1,0),new Paths.Linear(Vec3.Z,.1),20).templateDirections().build();
        var seed=new Deterministic.Seed(0,List.of()); var members=fan.pattern().generate(seed);
        assertTrue(fan.memberState(members.get(0),seed,10).position().x()<0);
        assertTrue(fan.memberState(members.get(2),seed,10).position().x()>0);
        var template=AuthoringTemplates.carriers();
        var fire=template.definition().nodes().stream().filter(n->n instanceof OperationNode).map(n->StandardProgramOperations.decode((OperationNode)n).orElse(null))
                .filter(o->o instanceof ServerControlOperations.Fire).map(o->(ServerControlOperations.Fire)o).findFirst().orElseThrow();
        assertEquals(2,fire.program().maxGenerations()); assertEquals(135,fire.program().descendantBound());
    }
    @Test void templatesExecuteRoundTripBoundsAndCleanup() {
        for (var fixture:List.of(AuthoringTemplates.volley(),AuthoringTemplates.flower(),AuthoringTemplates.carriers())) {
            var session=session(); var battle=new BossProgramController(session,OWNER); battle.card(fixture,0);
            var wire=new ProgramWire(NetworkLimits.defaults()); long committed=0,derived=0;
            for (int tick=0;tick<=220;tick++) {
                session.advance(tick,512);
                var projection=ReplicaState.capture(session.checkpoint(),wire);
                var boxes=ProgramMotionBounds.groups(projection);
                for (var group:session.checkpoint().runtime().groups()) for (var member:group.members()) {
                    assertTrue(boxes.get(group.id()).near(session.runtime().state(member.id(),tick+.25).position(),0),fixture.definition().id()+" tick="+tick);
                    assertTrue(member.harmful());
                }
                if (tick==30 && fixture.definition().id().endsWith("flower")) {
                    var expected=ProgramSession.restore(session.checkpoint(),LIMITS,new World(),StandardProgramOperations.execution());
                    for (int future=31;future<=180;future++) {
                        expected.advance(future,512); expected.runtime().drainEvents();
                        for (var group:expected.checkpoint().runtime().groups()) for (var member:group.members())
                            assertTrue(boxes.get(group.id()).near(expected.runtime().state(member.id(),future).position(),0),"birth envelope covers future release");
                    }
                }
                if (tick==30 || tick==110) {
                    var packets=new ArrayList<byte[]>(); var sync=new ProgramSyncServer(wire,packets::add,20);
                    sync.publishSpatial(projection,boxes,ProgramMotionBounds.members(projection),new Vec3(0,1,20),1000,256,128);
                    var replica=new ProgramReplica(wire,StandardProgramOperations.registry(),StandardProgramOperations.limits());
                    for (byte[] packet:packets) replica.receive(packet,0);
                    assertEquals(0,replica.failures());
                    for (var group:session.checkpoint().runtime().groups()) for (var member:group.members())
                        assertEquals(session.runtime().state(member.id(),tick).position(),replica.state(new ProgramReplica.Scope("test:author",OWNER),member.id(),tick).position());
                }
                for (var event:session.runtime().drainEvents()) {
                    if (event.kind()==DanmakuWorldRuntime.Kind.COMMIT) committed+=event.members().size();
                    if (event.reason().equals("replace")) derived++;
                }
            }
            assertEquals(0,session.runtime().liveMembers());
            if (fixture.definition().id().endsWith("volley")) assertEquals(27,committed);
            if (fixture.definition().id().endsWith("flower")) assertEquals(26,committed);
            if (fixture.definition().id().endsWith("carriers")) { assertTrue(committed>=30); assertTrue(derived>0); }
            battle.close(); session.advance(221,512); session.runtime().drainEvents();
            assertTrue(session.checkpoint().instances().isEmpty());
            assertEquals(new DanmakuWorldRuntime.RuntimeStats(0,0,0,0,0,0,0),session.runtime().stats());
        }
    }
    @Test void twoOwnersAndBackgroundSurviveOtherCardAndEncounterEnd() {
        var session=session(); var a=new BossProgramController(session,OWNER); var b=new BossProgramController(session,OTHER);
        a.background(AuthoringTemplates.volley(),0); a.card(AuthoringTemplates.flower(),0); b.card(AuthoringTemplates.volley(),0);
        for (int tick=0;tick<=25;tick++) session.advance(tick,512);
        session.runtime().drainEvents();
        a.card(AuthoringTemplates.carriers(),26);
        assertTrue(session.checkpoint().instances().stream().anyMatch(i->i.encounter().equals(a.encounter()) && i.background()));
        a.close(); session.advance(26,512); session.runtime().drainEvents();
        assertTrue(b.active()); assertFalse(session.checkpoint().instances().stream().anyMatch(i->i.encounter().equals(a.encounter())));
        assertTrue(session.runtime().liveMembers()>0);
        b.close(); session.advance(27,512); session.runtime().drainEvents();
        assertEquals(0,session.runtime().liveMembers()); assertTrue(session.checkpoint().instances().isEmpty());
    }
    @Test void loopReanchorsAndEndCardRevokesFutureNodes() {
        var card=new CardProgram("test:loop").clearance(0);
        var targets=card.targets("players",64,1); var gun=card.aimed("gun",targets,0,Vec3.ZERO);
        card.fire(gun,bullet(true),0,7);
        var session=session(); var battle=new BossProgramController(session,OWNER);
        battle.card(card.loop(60,TailPolicy.END_AT_BOUNDARY),0);
        int commits=0;
        for (int tick=0;tick<=130;tick++) {
            session.advance(tick,512);
            commits+=session.runtime().drainEvents().stream().filter(e->e.kind()==DanmakuWorldRuntime.Kind.COMMIT).count();
        }
        assertEquals(3,commits);
        battle.close(); session.advance(131,512); session.runtime().drainEvents();
        var end=new CardProgram("test:end").clearance(0); var t=end.targets("players",64,1); var g=end.aimed("gun",t,0,Vec3.ZERO);
        end.fire(g,bullet(true),0,1); end.at(5,CardProgram::endCard); end.at(10,c->c.fire(g,bullet(true),0,2));
        var other=session(); var control=new BossProgramController(other,OWNER); control.card(end.once(),0);
        for (int tick=0;tick<=20;tick++) { other.advance(tick,512); other.runtime().drainEvents(); }
        assertTrue(other.checkpoint().instances().isEmpty()); assertEquals(0,other.runtime().liveMembers());
    }
    @Test void handlesCannotBeUsedBeforeCreationAndLaserMustBeExplicit() {
        var card=new CardProgram("test:time"); var targets=card.targets("players",64,1);
        CardProgram.Group[] group=new CardProgram.Group[1];
        card.at(40,c->group[0]=c.aimed("gun",targets,0,Vec3.ZERO));
        assertTrue(assertThrows(IllegalArgumentException.class,()->card.at(20,c->c.fire(group[0],bullet(true),0,1))).getMessage().contains("tick 20"));
        assertThrows(IllegalArgumentException.class,()->MemberProgram.attack("laser",MemberProgram.styled(new Patterns.Point(Vec3.ZERO,Vec3.Z),"laser","red",.1,1,.2),new Paths.Hold(),20).fixedDirection().build());
        var laser=MemberProgram.attack("laser",MemberProgram.styled(new Patterns.Point(Vec3.ZERO,Vec3.Z),"laser","red",.1,1,.2),new Paths.Hold(),20)
                .fixedDirection().laser(12,true,10).build();
        assertInstanceOf(CollisionProfile.Laser.class,laser.collisionProfile());
    }
    @Test void futureReaimEnvelopeAndMovementLockAreBattleLocal() {
        var card=new CardProgram("test:reaim").clearance(0);
        var targets=card.targets("players",64,1); var gun=card.aimed("gun",targets,0,Vec3.ZERO);
        card.fire(gun,bullet(true),0,1); card.lockMovement(gun,15);
        card.at(10,c->c.reaim(gun,0,new Selection.Tag("unused"),.5));
        var session=session(); var battle=new BossProgramController(session,OWNER); battle.card(card.once(),0);
        session.advance(0,512); session.runtime().drainEvents();
        assertTrue(battle.movementLocked());
        var projection=ReplicaState.capture(session.checkpoint(),new ProgramWire(NetworkLimits.defaults()));
        var bounds=ProgramMotionBounds.groups(projection);
        for (var group:session.checkpoint().runtime().groups())
            assertTrue(bounds.get(group.id()).near(group.frame().origin().add(new Vec3(20,0,0)),0));
        for (int tick=1;tick<=16;tick++) { session.advance(tick,512); session.runtime().drainEvents(); }
        assertFalse(battle.movementLocked()); battle.close();
    }
}
