package com.bitsson.gensokyou.danmaku.program.server;

import com.bitsson.gensokyou.danmaku.program.*;
import com.bitsson.gensokyou.danmaku.program.math.*;
import com.bitsson.gensokyou.danmaku.program.ops.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WorldRuntimeTest {
    static final UUID OWNER=new UUID(0,1);
    static final Deterministic.Seed SEED=new Deterministic.Seed(23,List.of());
    static final class World implements DecisionProvider {
        List<Player> players=new ArrayList<>();
        Map<UUID,Actions.Pose> entities=new HashMap<>();
        int queries;
        World() { entities.put(OWNER,new Actions.Pose(Frame.IDENTITY,Vec3.ZERO)); }
        public List<Player> players() { return List.copyOf(players); }
        public Optional<Actions.Pose> entity(UUID id) { return Optional.ofNullable(entities.get(id)); }
        public boolean loaded(Vec3 p) { return p.x()<100; }
        public Block block(int x,int y,int z) { queries++; return new Block(x<100,y>0,y<=0,1); }
    }
    static Actions.ChildProgram program(int n) {
        return new Actions.ChildProgram(new Patterns.SphereDirections(n,Patterns.SphereDistribution.FIBONACCI,1),new Paths.Linear(Vec3.Z,.2),100,0,0,List.of(),
                new Presentation.Profile(List.of(new Presentation.Window(TimeDomain.MEMBER_AGE,0,100)),null,null,List.of()),new Transforms.Compose(List.of()));
    }
    static AuthorityDescriptions.Commit policy(int quota,double clearance) {
        return new AuthorityDescriptions.Commit(new ProgramRef.Plan(new NodeId(0)),clearance,quota,AuthorityDescriptions.Failure.CANCEL_GROUP,true);
    }
    static DanmakuWorldRuntime runtime(World world,int cap) {
        return new DanmakuWorldRuntime("test:world",new DanmakuWorldRuntime.Limits(cap,1000,100,100,10000),world);
    }
    static long group(DanmakuWorldRuntime runtime,DanmakuWorldRuntime.Encounter encounter,List<UUID> targets,int slot,boolean retain) {
        return runtime.createGroup(encounter,1,0,null,slot,false,retain,Frame.IDENTITY,targets);
    }
    static EmissionPlan birth(DanmakuWorldRuntime r,long group,int n) {
        var p=r.prepare(group,r.tick(),new Actions.Pose(Frame.IDENTITY,Vec3.ZERO),program(n),Actions.Binding.snapshot(),SEED);
        assertTrue(r.commit(p,policy(4096,0))); return p;
    }
    @Test void replacementUsesNetBudgetAndConsumesRejectedTrigger() {
        World w=new World(); var r=runtime(w,2); var encounter=r.startEncounter(OWNER); long g=group(r,encounter,List.of(),-1,false);
        r.advance(0); var p=birth(r,g,1); var parent=new DanmakuWorldRuntime.MemberId(g,p.ordinal(),0);
        var split=new Actions.ReplaceMembers(new Selection.AllOf(List.of()),program(2),Actions.AtomicScope.PER_PARENT,Actions.OnReject.END_PARENT);
        assertTrue(r.replace(g,List.of(parent),split,policy(4096,0),"split"));
        assertEquals(2,r.liveMembers()); assertTrue(r.member(parent).isEmpty());
        assertFalse(r.replace(g,List.of(parent),split,policy(4096,0),"split")); assertEquals(2,r.liveMembers());
        var child=r.checkpoint().groups().getFirst().members().getFirst().id();
        assertFalse(r.replace(g,List.of(child),split,policy(4096,0),"reject"));
        assertEquals(1,r.liveMembers());
        assertFalse(r.replace(g,List.of(child),split,policy(4096,0),"reject")); assertEquals(1,r.liveMembers());
    }
    @Test void allSelectedParentsAreAtomicAndKeepParentDoesNotRetry() {
        World w=new World(); var r=runtime(w,3); var encounter=r.startEncounter(OWNER); long g=group(r,encounter,List.of(),-1,false);
        r.advance(0); birth(r,g,2);
        var parents=r.checkpoint().groups().getFirst().members().stream().map(DanmakuWorldRuntime.MemberSnapshot::id).toList();
        var split=new Actions.ReplaceMembers(new Selection.AllOf(List.of()),program(2),Actions.AtomicScope.ALL_SELECTED_PARENTS,Actions.OnReject.KEEP_PARENT);
        assertFalse(r.replace(g,parents,split,policy(4096,0),"all")); assertEquals(2,r.liveMembers());
        r.remove(parents.getFirst(),"collision");
        assertFalse(r.replace(g,parents,split,policy(4096,0),"all")); assertEquals(1,r.liveMembers());
    }
    @Test void laterClearanceFailureRevokesMembersWarningsAndFuturePlans() {
        World w=new World(); var r=runtime(w,100); var encounter=r.startEncounter(OWNER); long g=group(r,encounter,List.of(),-1,false);
        r.advance(0); birth(r,g,2);
        var second=r.prepare(g,1,new Actions.Pose(Frame.IDENTITY,Vec3.ZERO),program(2),Actions.Binding.snapshot(),SEED);
        r.prepare(g,20,new Actions.Pose(Frame.IDENTITY,Vec3.ZERO),program(2),Actions.Binding.snapshot(),SEED);
        w.players.add(new DecisionProvider.Player(new UUID(0,20),Vec3.ZERO,Vec3.Y,2,true,false,false,.3));
        r.advance(1); assertFalse(r.commit(second,policy(4096,6)));
        assertEquals(0,r.liveMembers()); assertTrue(r.checkpoint().groups().getFirst().plans().isEmpty());
        assertFalse(r.checkpoint().groups().getFirst().emitting());
    }
    @Test void targetsRemainFixedAndInvalidPersonalSlotStopsOnlyFutureShots() {
        World w=new World(); UUID a=new UUID(0,2), b=new UUID(0,3);
        w.players.add(new DecisionProvider.Player(a,Vec3.X,Vec3.Y,2,true,false,false,.3));
        w.players.add(new DecisionProvider.Player(b,Vec3.X.multiply(2),Vec3.Y,2,true,false,false,.3));
        var r=runtime(w,100); var encounter=r.startEncounter(OWNER);
        var targets=r.captureTargets(encounter,Vec3.ZERO,new AuthorityDescriptions.CaptureTargets(30,5,true,true));
        long ga=group(r,encounter,targets,0,false), gb=group(r,encounter,targets,1,false);
        r.advance(0); birth(r,ga,1); birth(r,gb,1);
        w.players.removeFirst(); r.advance(1);
        assertEquals(2,r.liveMembers()); var groups=r.checkpoint().groups();
        assertFalse(groups.getFirst().emitting()); assertTrue(groups.get(1).emitting());
        assertEquals(List.of(a,b),groups.get(1).targets());
        assertTrue(r.sampleTarget(ga,0,AuthorityDescriptions.TargetPoint.EYES).isEmpty());
    }
    @Test void tickPhasesAndCollisionChildrenWaitForNextBoundary() {
        World w=new World(); var r=runtime(w,100); var encounter=r.startEncounter(OWNER); long g=group(r,encounter,List.of(),-1,false);
        List<String> seen=new ArrayList<>();
        for (var phase:DanmakuWorldRuntime.Phase.values()) r.schedule(0,phase,()->seen.add(phase.name()));
        r.schedule(0,DanmakuWorldRuntime.Phase.COMMIT,()->birth(r,g,1));
        r.collisions((runtime,id,from,to)->runtime.afterCollision(()->{ seen.add("derived"); runtime.remove(id,"hit"); }));
        r.advance(0); assertEquals(Arrays.stream(DanmakuWorldRuntime.Phase.values()).map(Enum::name).toList(),seen);
        assertEquals(1,r.liveMembers()); r.advance(1); assertEquals("derived",seen.getLast()); assertEquals(0,r.liveMembers());
        assertThrows(IllegalArgumentException.class,()->r.advance(1));
    }
    @Test void cycleRetentionCardBackgroundAndTwentyEncounterReleases() {
        World w=new World(); var r=runtime(w,100);
        for (int i=0;i<20;i++) {
            var encounter=r.startEncounter(OWNER); long g=group(r,encounter,List.of(),-1,true);
            long bg=r.createGroup(encounter,0,0,null,-1,true,true,Frame.IDENTITY,List.of());
            r.advance(i); birth(r,g,1); birth(r,bg,1); r.movementLock(g,true);
            r.endCycle(encounter,1,0); assertEquals(2,r.liveMembers());
            r.endCard(encounter,1); assertFalse(r.movementLocked(encounter));
            // 旧卡弹幕改为淡出离场：仍在场，但不再发射也不再判伤
            assertEquals(2,r.liveMembers());
            assertTrue(r.checkpoint().groups().stream().filter(scope->scope.card()==1)
                            .flatMap(scope->scope.members().stream()).noneMatch(DanmakuWorldRuntime.MemberSnapshot::harmful),
                    "the ended card's danmaku stopped damaging");
            r.endEncounter(encounter,"test"); assertEquals(0,r.liveMembers()); assertEquals(0,r.frameReferences());
            assertEquals(i+1,encounter.epoch()); r.drainEvents();
        }
    }
    @Test void externalSourceSamplesEachShotAndLossKeepsBornChildren() {
        World w=new World(); UUID source=new UUID(0,40); w.entities.put(source,new Actions.Pose(Frame.IDENTITY,Vec3.X));
        var r=runtime(w,100); var encounter=r.startEncounter(OWNER); long g=group(r,encounter,List.of(),-1,false);
        var emitter=new Actions.CreateEmitter(new Actions.ExternalEntitySource(source),ScalarCurve.constant(0),Vec3.Y,0,1,3,program(1),Actions.Binding.snapshot(),null);
        r.createEmitter(g,emitter,SEED,policy(4096,0)); r.advance(0);
        w.entities.put(source,new Actions.Pose(new Frame(Vec3.X.multiply(10),Vec3.X,Vec3.Y,Vec3.Z),Vec3.X)); r.advance(1);
        w.entities.remove(source); r.advance(2); assertEquals(2,r.liveMembers());
        var members=r.checkpoint().groups().getFirst().members();
        assertEquals(0,r.state(members.getFirst().id(),2).position().x(),1e-9);
        assertEquals(10,r.state(members.get(1).id(),2).position().x(),1e-9);
        assertEquals(2,r.drainEvents().stream().filter(e->e.kind()==DanmakuWorldRuntime.Kind.SOURCE_SAMPLE).count());
        w.entities.remove(OWNER); r.advance(3); assertEquals(0,r.liveMembers()); assertEquals(0,r.frameReferences());
    }
    @Test void finiteWorldQueriesAndVerticalFrameFallback() {
        World w=new World();
        var surface=w.resolveSurface(List.of(new Vec3(0,3,0),new Vec3(100,3,0)),AuthorityDescriptions.SurfaceMode.DOWNWARD_SOLID_TOP,10,5);
        assertEquals(new Vec3(0,1,0),surface.points().getFirst().orElseThrow()); assertTrue(surface.points().get(1).isEmpty());
        assertEquals(5,w.queries);
        assertFalse(w.corridor(Vec3.ZERO,Vec3.Z.multiply(100),1,10));
        assertTrue(w.corridor(new Vec3(0,2,0),new Vec3(0,2,2),0,20));
        assertFalse(w.corridor(new Vec3(0,0,0),new Vec3(0,0,2),0,20));
        Frame frame=DecisionProvider.captureFrame(Vec3.ZERO,Vec3.Y,Vec3.Y,Vec3.Z,Vec3.X);
        assertEquals(Vec3.Z,frame.right()); assertEquals(Vec3.Y,frame.forward());
    }
    @Test void actualTwoGenerationTransactionsReachFourNAndPublishChildren() {
        World w=new World(); var r=runtime(w,100); var encounter=r.startEncounter(OWNER); long g=group(r,encounter,List.of(),-1,false);
        var leaves=program(2);
        var action=new Actions.ChildAction(Actions.ChildActionKind.REPLACE,new Selection.AllOf(List.of()),new Actions.AtAge(2),1,1,
                leaves,Actions.Binding.snapshot(),Actions.AtomicScope.PER_PARENT,Actions.OnReject.END_PARENT,ScalarCurve.constant(0),Vec3.Y);
        var middle=new Actions.ChildProgram(program(2).pattern(),new Paths.Hold(),10,1,4,List.of(action),
                new Presentation.Profile(List.of(),null,null,List.of()),new Transforms.Compose(List.of()));
        var rootAction=new Actions.ChildAction(Actions.ChildActionKind.REPLACE,new Selection.AllOf(List.of()),new Actions.AtAge(2),1,1,
                middle,Actions.Binding.snapshot(),Actions.AtomicScope.PER_PARENT,Actions.OnReject.END_PARENT,ScalarCurve.constant(0),Vec3.Y);
        var roots=new Actions.ChildProgram(program(3).pattern(),new Paths.Hold(),10,2,18,List.of(rootAction),
                new Presentation.Profile(List.of(),null,null,List.of()),new Transforms.Compose(List.of()));
        r.schedule(0,DanmakuWorldRuntime.Phase.COMMIT,()->{
            var p=r.prepare(g,0,new Actions.Pose(Frame.IDENTITY,Vec3.ZERO),roots,Actions.Binding.snapshot(),SEED);
            assertTrue(r.commit(p,policy(4096,0)));
        });
        r.advance(0); assertEquals(3,r.liveMembers()); r.advance(1); r.advance(2); assertEquals(6,r.liveMembers());
        var recovered=DanmakuWorldRuntime.restore(r.checkpoint(),new DanmakuWorldRuntime.Limits(100,1000,100,100,10000),w);
        r.advance(3); recovered.advance(3); r.advance(4); recovered.advance(4);
        assertEquals(12,r.liveMembers()); assertEquals(r.checkpoint(),recovered.checkpoint());
        assertEquals(18,r.drainEvents().stream().flatMap(e->e.children().stream()).mapToInt(p->p.members().size()).sum());
    }
    @Test void emitterCheckpointDoesNotReplayPreviousShots() {
        World w=new World(); UUID source=new UUID(0,50); w.entities.put(source,new Actions.Pose(Frame.IDENTITY,Vec3.ZERO));
        var r=runtime(w,100); var encounter=r.startEncounter(OWNER); long g=group(r,encounter,List.of(),-1,false);
        var emitter=new Actions.CreateEmitter(new Actions.ExternalEntitySource(source),ScalarCurve.constant(0),Vec3.Y,0,1,3,program(1),Actions.Binding.snapshot(),null);
        r.createEmitter(g,emitter,SEED,policy(4096,0)); r.advance(0);
        var recovered=DanmakuWorldRuntime.restore(r.checkpoint(),new DanmakuWorldRuntime.Limits(100,1000,100,100,10000),w);
        recovered.advance(1); assertEquals(2,recovered.liveMembers());
        assertEquals(1,recovered.drainEvents().stream().filter(e->e.kind()==DanmakuWorldRuntime.Kind.SOURCE_SAMPLE).count());
    }
    @Test void followingSourceLossDetachesAndCheckpointKeepsWorldBoundary() {
        World w=new World(); var r=runtime(w,100); var encounter=r.startEncounter(OWNER);
        long carrier=group(r,encounter,List.of(),-1,false), shots=group(r,encounter,List.of(),-1,false);
        var ref=new ProgramRef.Group(new NodeId(7)); r.bindSource(ref,carrier);
        r.schedule(0,DanmakuWorldRuntime.Phase.COMMIT,()->birth(r,carrier,1));
        var binding=new Actions.Binding(Actions.Attachment.FOLLOW_SOURCE,Actions.Velocity.ABSOLUTE,Actions.OnSourceLost.DETACH_AT_LAST_AUTHORITATIVE_POSE);
        var emitter=new Actions.CreateEmitter(new Actions.MemberPose(ref,0),ScalarCurve.constant(0),Vec3.Y,1,1,1,program(1),binding,null);
        r.createEmitter(shots,emitter,SEED,policy(4096,0)); r.advance(0); r.advance(1);
        var source=r.checkpoint().groups().stream().filter(g->g.id()==carrier).findFirst().orElseThrow().members().getFirst().id();
        var child=r.checkpoint().groups().stream().filter(g->g.id()==shots).findFirst().orElseThrow().members().getFirst().id();
        r.remove(source,"collision"); r.advance(2);
        var at2=r.state(child,2); var restored=DanmakuWorldRuntime.restore(r.checkpoint(),new DanmakuWorldRuntime.Limits(100,1000,100,100,10000),w);
        assertEquals(at2,restored.state(child,2)); assertEquals(at2.position().add(at2.velocity()),restored.state(child,3).position());
        assertTrue(r.member(child).isPresent()); r.cancel(shots,0,"scope"); assertEquals(0,r.liveMembers());
    }
    @Test void proximityIsConsumedOnceAndPlanUsesActualOffsetSource() {
        World w=new World(); var r=runtime(w,100); var encounter=r.startEncounter(OWNER); long g=group(r,encounter,List.of(),-1,false);
        r.advance(0);
        var description=new Actions.PrepareEmission(program(1).pattern(),Frame.IDENTITY,Vec3.X.multiply(5),Vec3.Z.multiply(10),0);
        var p=r.prepare(g,description,program(1),Actions.Binding.snapshot(),SEED); assertTrue(r.commit(p,policy(4096,0)));
        assertEquals(description.aimDirection(),p.source().frame().forward()); assertEquals(Vec3.X.multiply(5),p.source().frame().origin());
        var id=new DanmakuWorldRuntime.MemberId(g,p.ordinal(),0);
        w.players.add(new DecisionProvider.Player(new UUID(0,4),Vec3.X.multiply(5),Vec3.Y,2,true,false,false,.3));
        assertTrue(r.proximity(id,1,true,"mine")); assertFalse(r.proximity(id,1,true,"mine"));
    }
    @Test void movingGroupEmitterUsesGroupClockAndRecoveredClosure() {
        World w=new World(); var r=runtime(w,100); var encounter=r.startEncounter(OWNER);
        long carrier=group(r,encounter,List.of(),-1,false), shots=group(r,encounter,List.of(),-1,false);
        var ref=new ProgramRef.Group(new NodeId(12)); r.bindSource(ref,carrier);
        r.groupMotion(carrier,new GroupMotion(Frame.IDENTITY,new Paths.Linear(Vec3.X,2),
                new Transforms.Rotate(Vec3.Y,new ScalarCurve(List.of(new ScalarCurve.Key(0,0),new ScalarCurve.Key(10,1))),Vec3.ZERO),0,List.of()));
        r.createEmitter(shots,new Actions.CreateEmitter(new Actions.GroupPose(ref),ScalarCurve.constant(0),Vec3.Y,1,1,2,program(1),Actions.Binding.snapshot(),null),SEED,policy(4096,0));
        r.advance(0); r.advance(1);
        var recovered=DanmakuWorldRuntime.restore(r.checkpoint(),new DanmakuWorldRuntime.Limits(100,1000,100,100,10000),w);
        r.advance(2); recovered.advance(2); assertEquals(r.checkpoint(),recovered.checkpoint());
        var members=r.checkpoint().groups().stream().filter(g->g.id()==shots).findFirst().orElseThrow().members();
        assertEquals(2,members.getFirst().plan().source().frame().origin().x(),1e-9);
        assertEquals(4,members.get(1).plan().source().frame().origin().x(),1e-9);
        assertEquals(Vec3.Z.rotate(Vec3.Y,.2),members.get(1).plan().source().frame().forward());
    }
    @Test void retainedCarrierStillFiresAfterCycleButExplicitStopCancelsChildren() {
        World w=new World(); var r=runtime(w,100); var encounter=r.startEncounter(OWNER); long g=group(r,encounter,List.of(),-1,true);
        var action=new Actions.ChildAction(Actions.ChildActionKind.EMIT,new Selection.AllOf(List.of()),new Actions.AtAge(2),2,2,
                program(2),Actions.Binding.snapshot(),Actions.AtomicScope.PER_PARENT,Actions.OnReject.KEEP_PARENT,ScalarCurve.constant(0),Vec3.Y);
        var carrier=new Actions.ChildProgram(program(1).pattern(),new Paths.Hold(),10,1,4,List.of(action),new Presentation.Profile(List.of(),null,null,List.of()),new Transforms.Compose(List.of()));
        r.advance(0); var p=r.prepare(g,0,new Actions.Pose(Frame.IDENTITY,Vec3.ZERO),carrier,Actions.Binding.snapshot(),SEED); assertTrue(r.commit(p,policy(100,0)));
        r.endCycle(encounter,1,0); r.advance(1); r.advance(2); assertEquals(3,r.liveMembers());
        r.stopEmission(g); r.advance(3); r.advance(4); assertEquals(3,r.liveMembers());
    }
    @Test void nearDensityRejectsWholeBatchAndReplacementDeductsParents() {
        World w=new World(); w.players.add(new DecisionProvider.Player(new UUID(0,9),Vec3.X.multiply(3),Vec3.Y,2,true,false,false,.3));
        var r=runtime(w,100); r.admissionLimits(new DanmakuWorldRuntime.AdmissionLimits(10,2,1000));
        var encounter=r.startEncounter(OWNER); long g=group(r,encounter,List.of(),-1,false); r.advance(0); var p=birth(r,g,1);
        var split=new Actions.ReplaceMembers(new Selection.AllOf(List.of()),program(2),Actions.AtomicScope.PER_PARENT,Actions.OnReject.KEEP_PARENT);
        assertTrue(r.replace(g,List.of(new DanmakuWorldRuntime.MemberId(g,p.ordinal(),0)),split,policy(100,0),"net-density")); assertEquals(2,r.liveMembers());
        var extra=r.prepare(g,0,new Actions.Pose(Frame.IDENTITY,Vec3.ZERO),program(1),Actions.Binding.snapshot(),SEED);
        var rejectOnly=new AuthorityDescriptions.Commit(new ProgramRef.Plan(new NodeId(0)),0,100,AuthorityDescriptions.Failure.REJECT_BATCH,true);
        assertFalse(r.commit(extra,rejectOnly)); assertEquals(2,r.liveMembers());
        assertTrue(r.drainEvents().stream().anyMatch(e->e.reason().equals("near-density")));
    }
    @Test void compiledProgramWarningCommitAndFutureOccurrenceRecoverWithoutCallbacks() {
        World w=new World(); var r=runtime(w,100); var encounter=r.startEncounter(OWNER);
        var codec=ServerOperations.dataRegistry().freeze(); var type=ServerOperations.emissionType(codec);
        var registry=new OperationRegistry(); registry.register(type); registry.freeze();
        var emission=new ServerOperations.Emission("test",program(2),Actions.Binding.snapshot(),new Actions.Pose(Frame.IDENTITY,Vec3.ZERO),SEED,policy(100,0),2);
        var builder=new ProgramBuilder(); var op=builder.operation(type,emission,new TickSpan(0));
        var repeat=new RepeatNode(builder.nextId(),2,new TickSpan(5),op);
        var result=ProgramCompiler.compile("test:authority",1,new PlaybackPolicy.Once(TailPolicy.RETAIN_UNTIL_LIFETIME),repeat,ProgramLimits.defaults(),registry);
        assertTrue(result.success(),result.diagnostics().toString());
        var execution=new ProgramExecution(); ServerOperations.install(execution,type); execution.freeze();
        var session=new ProgramSession(r,execution);
        session.install(encounter,1,false,result.definition().orElseThrow(),new ClockAnchors(0,0,null,null),0);
        session.advance(0,100); assertEquals(0,r.liveMembers());
        var restored=ProgramSession.restore(session.checkpoint(),new DanmakuWorldRuntime.Limits(100,1000,100,100,10000),w,execution);
        for (int tick=1;tick<=7;tick++) { session.advance(tick,100); restored.advance(tick,100); }
        assertEquals(4,r.liveMembers()); assertEquals(session.checkpoint(),restored.checkpoint());
        assertEquals(2,restored.runtime().drainEvents().stream().filter(e->e.kind()==DanmakuWorldRuntime.Kind.COMMIT).count());
    }
    @Test void typedCollisionRequestIsIncludedInCheckpoint() {
        World w=new World(); var r=runtime(w,100); var encounter=r.startEncounter(OWNER); long g=group(r,encounter,List.of(),-1,false);
        r.schedule(0,DanmakuWorldRuntime.Phase.COMMIT,()->birth(r,g,1));
        r.collisions((runtime,id,from,to)->runtime.afterCollision(new AuthorityCommand.Remove(id,"collision")));
        r.advance(0);
        var restored=DanmakuWorldRuntime.restore(r.checkpoint(),new DanmakuWorldRuntime.Limits(100,1000,100,100,10000),w);
        restored.advance(1); assertEquals(0,restored.liveMembers());
    }
    @Test void eventCapacityFailureLeavesPlanAndParentsUntouched() {
        World w=new World();
        var r=new DanmakuWorldRuntime("test:events",new DanmakuWorldRuntime.Limits(100,100,100,100,4),w);
        var encounter=r.startEncounter(OWNER); long g=group(r,encounter,List.of(),-1,false); r.advance(0);
        var p=r.prepare(g,0,new Actions.Pose(Frame.IDENTITY,Vec3.ZERO),program(2),Actions.Binding.snapshot(),SEED);
        var onlyBatch=new AuthorityDescriptions.Commit(new ProgramRef.Plan(new NodeId(0)),0,100,AuthorityDescriptions.Failure.REJECT_BATCH,true);
        assertTrue(r.commit(p,onlyBatch));
        var extra=r.prepare(g,0,new Actions.Pose(Frame.IDENTITY,Vec3.ZERO),program(1),Actions.Binding.snapshot(),SEED);
        assertThrows(IllegalStateException.class,()->r.commit(extra,onlyBatch)); assertEquals(2,r.liveMembers());
        assertEquals(List.of(extra),r.checkpoint().groups().getFirst().plans());
        r.drainEvents(); assertTrue(r.commit(extra,onlyBatch)); assertEquals(3,r.liveMembers());
    }
    @Test void movementPoseHasAnalyticAngularVelocityAndNetDensityCannotIgnoreDistantPlayers() {
        var motion=new GroupMotion(Frame.IDENTITY,new Paths.Linear(Vec3.X,2),new Transforms.Rotate(Vec3.Y,
                new ScalarCurve(List.of(new ScalarCurve.Key(0,0),new ScalarCurve.Key(10,2))),Vec3.ZERO),0,List.of());
        assertTrue(motion.pose(3).angularVelocity().subtract(Vec3.Y.multiply(.2)).length()<1e-10);
        World w=new World(); var r=runtime(w,100); var encounter=r.startEncounter(OWNER); long g=group(r,encounter,List.of(),-1,false);
        r.advance(0); var p=birth(r,g,1); var id=new DanmakuWorldRuntime.MemberId(g,p.ordinal(),0);
        var ref=new ProgramRef.Group(new NodeId(88)); r.bindSource(ref,g,p.ordinal()); birth(r,g,1);
        r.createEmitter(g,new Actions.CreateEmitter(new Actions.MemberPose(ref,0),ScalarCurve.constant(0),Vec3.Y,1,1,1,program(1),Actions.Binding.snapshot(),null),SEED,policy(100,0));
        r.advance(1); assertEquals(3,r.liveMembers()); assertTrue(r.member(id).isPresent());
    }
    @Test void damageWindowIsHalfOpenAndHiddenMembersNeverReachCollisionSink() {
        World w=new World(); var r=runtime(w,100); var encounter=r.startEncounter(OWNER); long g=group(r,encounter,List.of(),-1,false);
        var profile=new Presentation.Profile(List.of(new Presentation.Window(TimeDomain.MEMBER_AGE,1,4)),
                new Presentation.VisibilityCycle(TimeDomain.MEMBER_AGE,4,2,0),null,List.of());
        var p=new Actions.ChildProgram(program(1).pattern(),new Paths.Hold(),10,0,0,List.of(),profile,new Transforms.Compose(List.of()));
        List<Long> hits=new ArrayList<>(); r.collisions((runtime,id,from,to)->hits.add(runtime.tick()));
        r.schedule(0,DanmakuWorldRuntime.Phase.COMMIT,()->{
            var plan=r.prepare(g,0,new Actions.Pose(Frame.IDENTITY,Vec3.ZERO),p,Actions.Binding.snapshot(),SEED); assertTrue(r.commit(plan,policy(100,0)));
        });
        for (int tick=0;tick<=4;tick++) r.advance(tick);
        assertEquals(List.of(1L),hits);
    }
    @Test void radiusLossInvalidatesSlotAndFrameResultSurvivesRecovery() {
        World w=new World(); UUID target=new UUID(0,3);
        w.players.add(new DecisionProvider.Player(target,Vec3.Z.multiply(5),Vec3.Z.multiply(5).add(Vec3.Y),2,true,false,false,.3));
        var r=runtime(w,100); var encounter=r.startEncounter(OWNER); long g=group(r,encounter,List.of(target),0,false); r.targetRadius(g,10);
        r.advance(0);
        var policy=new AuthorityDescriptions.CaptureFrame(new ProgramRef.TargetSet(new NodeId(1)),0,AuthorityDescriptions.TargetPoint.EYES,Vec3.X,Vec3.Y,null,Vec3.X);
        Frame frame=r.captureFrame(g,policy).orElseThrow(); assertEquals(Vec3.X,frame.origin());
        assertEquals(frame,r.checkpoint().capturedFrames().get(g));
        birth(r,g,1);
        w.players.set(0,new DecisionProvider.Player(target,Vec3.Z.multiply(20),Vec3.Z.multiply(20).add(Vec3.Y),2,true,false,false,.3));
        r.advance(1); assertFalse(r.checkpoint().groups().getFirst().emitting());
        assertTrue(r.sampleTarget(g,0,AuthorityDescriptions.TargetPoint.EYES).isEmpty());
    }
    @Test void compiledCaptureGroupAndTwoFireOperationsShareAuthorityStateAcrossCheckpoint() {
        World w=new World(); UUID target=new UUID(0,10);
        w.players.add(new DecisionProvider.Player(target,Vec3.Z.multiply(10),Vec3.Z.multiply(10).add(Vec3.Y),2,true,false,false,.3));
        var r=runtime(w,100); var encounter=r.startEncounter(OWNER);
        var codec=ServerControlOperations.dataRegistry().freeze();
        var capture=ServerControlOperations.type("capture",ServerControlOperations.Capture.class,codec);
        var group=ServerControlOperations.type("group",ServerControlOperations.Group.class,codec);
        var fire=ServerControlOperations.type("fire",ServerControlOperations.Fire.class,codec);
        var registry=new OperationRegistry(); registry.register(capture); registry.register(group); registry.register(fire); registry.freeze();
        var execution=new ProgramExecution(); ServerControlOperations.install(execution,capture); ServerControlOperations.install(execution,group); ServerControlOperations.install(execution,fire); execution.freeze();
        var builder=new ProgramBuilder();
        var c=builder.operation(capture,new ServerControlOperations.Capture("targets",new AuthorityDescriptions.CaptureTargets(30,5,true,true)),new TickSpan(0));
        var g=builder.operation(group,new ServerControlOperations.Group("attack","targets",0,AuthorityDescriptions.TargetPoint.EYES,Vec3.X,Vec3.Y,Vec3.X,true,null),new TickSpan(0));
        var first=builder.operation(fire,new ServerControlOperations.Fire("attack",program(1),Actions.Binding.snapshot(),SEED,policy(100,0),1),new TickSpan(0));
        var second=builder.operation(fire,new ServerControlOperations.Fire("attack",program(1),Actions.Binding.snapshot(),SEED,policy(100,0),1),new TickSpan(0));
        var root=new SequenceNode(builder.nextId(),List.of(c,g,first,new WaitNode(builder.nextId(),new TickSpan(3)),second));
        var compiled=ProgramCompiler.compile("test:controls",1,new PlaybackPolicy.Once(TailPolicy.RETAIN_UNTIL_LIFETIME),root,ProgramLimits.defaults(),registry);
        assertTrue(compiled.success(),compiled.diagnostics().toString());
        var session=new ProgramSession(r,execution); session.install(encounter,1,false,compiled.definition().orElseThrow(),new ClockAnchors(0,0,null,null),0);
        session.advance(0,100); session.advance(1,100); assertEquals(1,r.liveMembers());
        var recovered=ProgramSession.restore(session.checkpoint(),new DanmakuWorldRuntime.Limits(100,1000,100,100,10000),w,execution);
        for (int tick=2;tick<=4;tick++) { session.advance(tick,100); recovered.advance(tick,100); }
        assertEquals(2,r.liveMembers()); assertEquals(1,r.checkpoint().groups().size()); assertEquals(session.checkpoint(),recovered.checkpoint());
        session.endCard(encounter,1);
        // 旧卡弹幕以淡出离场：仍在场，但不再判伤
        assertEquals(2,r.liveMembers());
        assertTrue(r.checkpoint().groups().stream().flatMap(scope->scope.members().stream()).noneMatch(DanmakuWorldRuntime.MemberSnapshot::harmful));
        assertTrue(r.checkpoint().targetHandles().isEmpty());
    }
    @Test void followEmitterRetainsWorldNozzleRotationAndUnlockDeadlineRecovers() {
        World w=new World(); var r=runtime(w,100); var encounter=r.startEncounter(OWNER);
        long source=group(r,encounter,List.of(),-1,false), shots=group(r,encounter,List.of(),-1,false);
        var ref=new ProgramRef.Group(new NodeId(99)); r.bindSource(ref,source);
        var angle=new ScalarCurve(List.of(new ScalarCurve.Key(0,0),new ScalarCurve.Key(10,1)));
        var binding=new Actions.Binding(Actions.Attachment.FOLLOW_SOURCE,Actions.Velocity.ABSOLUTE,Actions.OnSourceLost.END);
        r.createEmitter(shots,new Actions.CreateEmitter(new Actions.GroupPose(ref),angle,Vec3.Y,0,1,1,program(1),binding,null),SEED,policy(100,0));
        r.lockUntil(shots,3); r.advance(0);
        var id=r.checkpoint().groups().stream().filter(g->g.id()==shots).findFirst().orElseThrow().members().getFirst().id();
        assertTrue(r.state(id,2).position().subtract(Vec3.Z.multiply(.4).rotate(Vec3.Y,.2)).length()<1e-9);
        var recovered=DanmakuWorldRuntime.restore(r.checkpoint(),new DanmakuWorldRuntime.Limits(100,1000,100,100,10000),w);
        recovered.advance(1); recovered.advance(2); assertTrue(recovered.movementLocked(encounter)); recovered.advance(3); assertFalse(recovered.movementLocked(encounter));
    }
    @Test void retargetRebaseKeepsBoundaryAndDoesNotReadFutureTarget() {
        World w=new World(); UUID target=new UUID(0,11);
        w.players.add(new DecisionProvider.Player(target,Vec3.X.multiply(10),Vec3.X.multiply(10).add(Vec3.Y),2,true,false,false,.3));
        var r=runtime(w,100); var encounter=r.startEncounter(OWNER); long g=group(r,encounter,List.of(target),0,false);
        r.advance(0); var plan=birth(r,g,1); var id=new DanmakuWorldRuntime.MemberId(g,plan.ordinal(),0); r.advance(1);
        Vec3 boundary=r.state(id,1).position(); Vec3 point=r.sampleTarget(g,0,AuthorityDescriptions.TargetPoint.FEET).orElseThrow();
        r.rebase(id,new Paths.Linear(MemberMotion.aimAtPoint(boundary,point),.5),true);
        assertEquals(boundary,r.state(id,1).position());
        var recovered=DanmakuWorldRuntime.restore(r.checkpoint(),new DanmakuWorldRuntime.Limits(100,1000,100,100,10000),w);
        w.players.clear(); assertEquals(r.state(id,4),recovered.state(id,4));
        assertTrue(r.state(id,4).position().x()>1);
    }
    @Test void collapseStopsDamageImmediatelyAndReturnsBudgetAfterFade() {
        World w=new World(); var r=runtime(w,100); var encounter=r.startEncounter(OWNER); long g=group(r,encounter,List.of(),-1,false);
        r.advance(0); var p=birth(r,g,1); var id=new DanmakuWorldRuntime.MemberId(g,p.ordinal(),0); r.movementLock(g,true);
        r.cancel(g,2,"collapse"); assertFalse(r.damaging(id,0)); assertFalse(r.movementLocked(encounter)); assertEquals(1,r.liveMembers());
        var cancel=r.drainEvents().stream().filter(e->e.kind()==DanmakuWorldRuntime.Kind.CANCEL).findFirst().orElseThrow();
        assertEquals(2,cancel.fadeEnd()); assertTrue(cancel.revokeWarnings());
        r.advance(1); assertEquals(1,r.liveMembers()); r.advance(2); assertEquals(0,r.liveMembers()); assertEquals(0,r.frameReferences());
    }
    @Test void workBudgetRejectsBeforeExpansionCommitAndResetsAtNextTick() {
        World w=new World(); var r=runtime(w,100); r.admissionLimits(new DanmakuWorldRuntime.AdmissionLimits(10,100,2));
        var encounter=r.startEncounter(OWNER); long g=group(r,encounter,List.of(),-1,false); r.advance(0); birth(r,g,1);
        var rejectOnly=new AuthorityDescriptions.Commit(new ProgramRef.Plan(new NodeId(0)),0,100,AuthorityDescriptions.Failure.REJECT_BATCH,true);
        var p=r.prepare(g,0,new Actions.Pose(Frame.IDENTITY,Vec3.ZERO),program(1),Actions.Binding.snapshot(),SEED);
        assertFalse(r.commit(p,rejectOnly)); assertEquals(1,r.liveMembers());
        r.advance(1); var next=r.prepare(g,1,new Actions.Pose(Frame.IDENTITY,Vec3.ZERO),program(1),Actions.Binding.snapshot(),SEED);
        assertTrue(r.commit(next,rejectOnly)); assertEquals(2,r.liveMembers());
    }
    @Test void dimensionUnloadClearsAllTransientResources() {
        World w=new World(); var host=new DimensionRuntimes(); var r=host.getOrCreate("test:world",()->runtime(w,100));
        var encounter=r.startEncounter(OWNER); long g=group(r,encounter,List.of(),-1,false); r.advance(0); birth(r,g,2); r.lockUntil(g,10);
        host.unload("test:world"); assertEquals(new DanmakuWorldRuntime.RuntimeStats(0,0,0,0,0,0,0),r.stats());
        assertFalse(r.active(encounter));
    }
    @Test void differentBirthsCanShareGroupClockAndFrameClosure() {
        World w=new World(); var r=runtime(w,100); var encounter=r.startEncounter(OWNER); long g=group(r,encounter,List.of(),-1,false);
        r.groupMotion(g,new GroupMotion(Frame.IDENTITY,new Paths.Linear(Vec3.X,.5),
                new Transforms.Rotate(Vec3.Y,new ScalarCurve(List.of(new ScalarCurve.Key(0,0),new ScalarCurve.Key(10,1))),Vec3.ZERO),0,List.of()));
        var child=new Actions.ChildProgram(new Patterns.Point(Vec3.Z,Vec3.Z),new Paths.Hold(),10,0,0);
        r.advance(0); var first=r.prepare(g,0,new Actions.Pose(Frame.IDENTITY,Vec3.ZERO),child,Actions.Binding.snapshot(),SEED); assertTrue(r.commit(first,policy(100,0)));
        var a=new DanmakuWorldRuntime.MemberId(g,first.ordinal(),0); r.attachToGroup(a);
        r.advance(1); var second=r.prepare(g,1,new Actions.Pose(Frame.IDENTITY,Vec3.ZERO),child,Actions.Binding.snapshot(),SEED); assertTrue(r.commit(second,policy(100,0)));
        var b=new DanmakuWorldRuntime.MemberId(g,second.ordinal(),0); r.attachToGroup(b);
        assertEquals(r.state(a,3),r.state(b,3));
        assertTrue(r.state(a,3).position().subtract(Vec3.X.multiply(1.5).add(Vec3.Z.rotate(Vec3.Y,.3))).length()<1e-9);
        var restored=DanmakuWorldRuntime.restore(r.checkpoint(),new DanmakuWorldRuntime.Limits(100,1000,100,100,10000),w);
        assertEquals(r.state(a,3.5),restored.state(a,3.5));
    }
    @Test void deadParentCancelsFutureShotsButKeepsPreviouslyBornChildren() {
        World w=new World(); var r=runtime(w,100); var encounter=r.startEncounter(OWNER); long g=group(r,encounter,List.of(),-1,false);
        var action=new Actions.ChildAction(Actions.ChildActionKind.EMIT,new Selection.AllOf(List.of()),new Actions.AtAge(1),2,2,
                program(2),Actions.Binding.snapshot(),Actions.AtomicScope.PER_PARENT,Actions.OnReject.KEEP_PARENT,ScalarCurve.constant(0),Vec3.Y);
        var carrier=new Actions.ChildProgram(program(1).pattern(),new Paths.Hold(),10,1,4,List.of(action),program(1).presentation(),new Transforms.Compose(List.of()));
        r.advance(0); var plan=r.prepare(g,0,new Actions.Pose(Frame.IDENTITY,Vec3.ZERO),carrier,Actions.Binding.snapshot(),SEED); assertTrue(r.commit(plan,policy(100,0)));
        r.advance(1); assertEquals(3,r.liveMembers()); r.remove(new DanmakuWorldRuntime.MemberId(g,plan.ordinal(),0),"hit");
        r.advance(2); r.advance(3); assertEquals(2,r.liveMembers());
        r.cancel(g,0,"card"); assertEquals(0,r.liveMembers());
    }
}
