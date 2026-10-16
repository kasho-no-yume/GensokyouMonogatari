package com.bitsson.gensokyou.danmaku.program.network;

import com.bitsson.gensokyou.danmaku.program.*;
import com.bitsson.gensokyou.danmaku.program.math.*;
import com.bitsson.gensokyou.danmaku.program.ops.*;
import com.bitsson.gensokyou.danmaku.program.server.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProgramNetworkTest {
    @Test void spatialPublisherSendsSelectedMembersAndReentryRestoresIdentity() {
        var f=new Fixture(); var plan=f.birth(2); var a=new DanmakuWorldRuntime.MemberId(f.group,plan.ordinal(),0); var b=new DanmakuWorldRuntime.MemberId(f.group,plan.ordinal(),1);
        var envelopes=Map.of(f.group,new ProgramInterest.Box(new Vec3(-1,-1,-1),new Vec3(101,1,1)));
        var members=Map.of(a,new ProgramInterest.Box(Vec3.ZERO,Vec3.X),b,new ProgramInterest.Box(new Vec3(100,0,0),new Vec3(101,0,0)));
        f.server.publishSpatial(f.projection(),envelopes,members,Vec3.ZERO,1,10,1); f.deliver(0);
        assertTrue(ReplicaState.hasMember(f.client.image(f.scope).orElseThrow(),a)); assertFalse(ReplicaState.hasMember(f.client.image(f.scope).orElseThrow(),b));
        f.server.publishSpatial(f.projection(),envelopes,members,new Vec3(100,0,0),1,10,1); f.deliver(1);
        assertFalse(ReplicaState.hasMember(f.client.image(f.scope).orElseThrow(),a)); assertTrue(ReplicaState.hasMember(f.client.image(f.scope).orElseThrow(),b));
        f.server.publishSpatial(f.projection(),envelopes,members,Vec3.ZERO,1,10,1); f.deliver(2);
        assertTrue(ReplicaState.hasMember(f.client.image(f.scope).orElseThrow(),a));
    }
    static final UUID OWNER=new UUID(0,1);
    static class World implements DecisionProvider {
        public List<Player> players() { return List.of(); }
        public Optional<Actions.Pose> entity(UUID id) { return Optional.of(new Actions.Pose(Frame.IDENTITY,Vec3.ZERO)); }
        public boolean loaded(Vec3 p) { return true; }
        public Block block(int x,int y,int z) { return new Block(true,true,false,0); }
    }
    static class Fixture {
        final ProgramWire wire=new ProgramWire(NetworkLimits.defaults());
        final DanmakuWorldRuntime runtime=new DanmakuWorldRuntime("test:world",new DanmakuWorldRuntime.Limits(4096,4096,100,1000,10000),new World());
        final ProgramSession session=new ProgramSession(runtime,new ProgramExecution());
        final DanmakuWorldRuntime.Encounter encounter=runtime.startEncounter(OWNER);
        final long group=runtime.createGroup(encounter,1,0,null,-1,false,false,Frame.IDENTITY,List.of());
        final List<byte[]> sent=new ArrayList<>();
        final ProgramSyncServer server=new ProgramSyncServer(wire,sent::add,40);
        final ProgramReplica client=new ProgramReplica(wire,new OperationRegistry().freeze(),ProgramLimits.defaults());
        final ProgramReplica.Scope scope=new ProgramReplica.Scope("test:world",OWNER);
        Fixture() { runtime.advance(0); }
        EmissionPlan birth(int n) {
            var program=new Actions.ChildProgram(new Patterns.SphereDirections(n,Patterns.SphereDistribution.FIBONACCI,1),new Paths.Linear(Vec3.Z,.2),100,0,0);
            var plan=runtime.prepare(group,runtime.tick(),new Actions.Pose(Frame.IDENTITY,Vec3.ZERO),program,Actions.Binding.snapshot(),new Deterministic.Seed(23,List.of()));
            assertTrue(runtime.commit(plan,new AuthorityDescriptions.Commit(new ProgramRef.Plan(new NodeId(0)),0,4096,AuthorityDescriptions.Failure.REJECT_BATCH,true))); return plan;
        }
        ReplicaState.Projection projection() { return ReplicaState.capture(session.checkpoint(),wire); }
        void publish(Vec3 viewer) { server.publish(projection(),Map.of(group,new ProgramInterest.Box(new Vec3(-100,-100,-100),new Vec3(100,100,100))),viewer,0); }
        void deliver(int tick) { for (byte[] bytes:List.copyOf(sent)) client.receive(bytes,tick); sent.clear(); }
    }
    @Test void actualRuntimeCheckpointAndSparseRemovalRoundTrip() {
        var f=new Fixture(); var plan=f.birth(48); f.publish(Vec3.ZERO); f.deliver(0);
        var id=new DanmakuWorldRuntime.MemberId(f.group,plan.ordinal(),7);
        assertEquals(f.runtime.state(id,12).position(),f.client.state(f.scope,id,12).position());
        f.runtime.remove(id,"hit"); f.runtime.advance(1); f.publish(Vec3.ZERO); f.deliver(4);
        assertFalse(ReplicaState.hasMember(f.client.image(f.scope).orElseThrow(),id));
        assertEquals(47,f.client.image(f.scope).orElseThrow().entries().values().stream().filter(r->r instanceof ReplicaState.Batch).map(r->(ReplicaState.Batch)r).mapToInt(b->b.alive().stream().mapToInt(Long::bitCount).sum()).sum());
    }
    @Test void partialCheckpointDoesNotInstallAndEndPreventsResurrection() {
        var f=new Fixture(); f.birth(480); f.publish(Vec3.ZERO);
        List<byte[]> starts=f.sent.stream().filter(b->f.wire.decode(b).kind()==ProgramWire.Kind.START).toList();
        assertTrue(starts.size()>1);
        for (byte[] b:f.sent) if (f.wire.decode(b).kind()==ProgramWire.Kind.DESCRIPTION) f.client.receive(b,0);
        f.client.receive(starts.getFirst(),0); assertTrue(f.client.image(f.scope).isEmpty());
        for (byte[] b:starts) f.client.receive(b,0); assertTrue(f.client.image(f.scope).isPresent());
        f.sent.clear(); f.server.end("test:world",f.encounter,1,100); f.deliver(1);
        for (byte[] b:starts) f.client.receive(b,2); assertTrue(f.client.image(f.scope).isEmpty());
    }
    @Test void missingDefinitionIsBoundedAndResolvedWithoutSideEffects() {
        var f=new Fixture(); f.birth(3); f.publish(Vec3.ZERO);
        for (byte[] b:f.sent) if (f.wire.decode(b).kind()==ProgramWire.Kind.START) f.client.receive(b,0);
        assertTrue(f.client.image(f.scope).isEmpty()); assertEquals(Set.of(f.scope),f.client.recoveryRequests(0));
        for (byte[] b:f.sent) if (f.wire.decode(b).kind()==ProgramWire.Kind.DESCRIPTION) f.client.receive(b,1);
        assertTrue(f.client.image(f.scope).isPresent()); assertTrue(f.client.recoveryRequests(20).isEmpty());
    }
    @Test void reentryUsesNewSubscriptionAndDuplicateDecisionsAreIdempotent() {
        var f=new Fixture(); f.birth(2); f.publish(Vec3.ZERO); f.deliver(0);
        f.publish(new Vec3(1000,0,0)); f.deliver(1); assertTrue(f.client.image(f.scope).isEmpty());
        f.publish(Vec3.ZERO); List<byte[]> reentry=List.copyOf(f.sent); f.deliver(2); assertTrue(f.client.image(f.scope).isPresent());
        for (byte[] b:reentry) f.client.receive(b,3); assertTrue(f.client.image(f.scope).isPresent()); assertTrue(f.client.duplicates()>0);
    }
    @Test void lateRebaseUsesEffectiveBoundaryAndDoesNotAdvanceAuthority() {
        var f=new Fixture(); var p=f.birth(2); var id=new DanmakuWorldRuntime.MemberId(f.group,p.ordinal(),0);
        f.publish(Vec3.ZERO); f.deliver(0); f.runtime.advance(1); f.runtime.rebase(id,new Paths.Linear(Vec3.X,.3),true);
        f.publish(Vec3.ZERO); f.client.renderTime("test:world",10); f.deliver(10);
        assertEquals(f.runtime.state(id,12).position(),f.client.state(f.scope,id,12).position());
        assertEquals(1,f.runtime.tick()); assertTrue(f.client.lateEvents()>0);
    }
    @Test void staleClockFreezesAndRequestsRecoveryAndVisualOffsetsExpire() {
        var f=new Fixture(); f.birth(1); f.publish(Vec3.ZERO); f.deliver(0);
        double trusted=f.client.renderTime("test:world",10); assertEquals(trusted,f.client.renderTime("test:world",201));
        assertEquals(trusted,f.client.renderTime("test:world",400)); assertEquals(Set.of(f.scope),f.client.recoveryRequests(400)); assertTrue(f.client.recoveryRequests(401).isEmpty());
        var offset=new VisualOffset(); offset.correct(new Vec3(.2,0,0),Vec3.ZERO,0,false); assertEquals(.1,offset.at(1).x(),1e-12); assertEquals(Vec3.ZERO,offset.at(2));
        offset.correct(Vec3.X,Vec3.ZERO,3,false); assertEquals(1,offset.snaps());
    }
    @Test void envelopeRejectsTrailingVersionAndOversizeBeforeAllocation() {
        var f=new Fixture(); f.birth(1); f.publish(Vec3.ZERO); byte[] valid=f.sent.getFirst();
        assertThrows(IllegalArgumentException.class,()->f.wire.decode(Arrays.copyOf(valid,valid.length+1)));
        byte[] bad=valid.clone(); bad[3]=2; assertThrows(IllegalArgumentException.class,()->f.wire.decode(bad));
        assertThrows(IllegalArgumentException.class,()->f.wire.decode(new byte[100000]));
    }
    @Test void remoteGroupAndInvisibleParentClosureAreSelected() {
        var f=new Fixture(); f.birth(2); long child=f.runtime.createGroup(f.encounter,1,0,f.group,-1,false,false,Frame.IDENTITY,List.of());
        var image=f.projection().image();
        var selected=ProgramInterest.select(image,Map.of(child,new ProgramInterest.Box(new Vec3(999,0,0),new Vec3(1001,2,2))),new Vec3(1000,1,1),0);
        assertEquals(Set.of(f.group,child),selected);
        var shards=ProgramInterest.shards(child,0,Map.of(7,new ProgramInterest.Box(Vec3.ZERO,Vec3.X),12,new ProgramInterest.Box(new Vec3(100,0,0),new Vec3(101,0,0))),10,10);
        assertEquals(2,shards.size()); assertEquals(List.of(7),shards.getFirst().indices()); assertEquals(List.of(12),shards.getLast().indices());
    }
    @Test void twoGenerationJoinRestoresOnlySurvivorsAndConsumedCursors() {
        var f=new Fixture(); f.birth(3);
        var program=new Actions.ChildProgram(new Patterns.SphereDirections(2,Patterns.SphereDistribution.FIBONACCI,1),new Paths.Linear(Vec3.Z,.2),100,0,0);
        var replace=new Actions.ReplaceMembers(new Selection.AllOf(List.of()),program,Actions.AtomicScope.ALL_SELECTED_PARENTS,Actions.OnReject.END_PARENT);
        var policy=new AuthorityDescriptions.Commit(new ProgramRef.Plan(new NodeId(0)),0,4096,AuthorityDescriptions.Failure.REJECT_BATCH,true);
        List<DanmakuWorldRuntime.MemberId> initial=f.runtime.members(f.group,new Selection.AllOf(List.of()));
        assertTrue(f.runtime.replace(f.group,initial,replace,policy,"generation-one"));
        List<DanmakuWorldRuntime.MemberId> second=f.runtime.members(f.group,new Selection.AllOf(List.of()));
        assertTrue(f.runtime.replace(f.group,second,replace,policy,"generation-two"));
        assertEquals(12,f.runtime.liveMembers()); f.publish(Vec3.ZERO); f.deliver(0);
        var image=f.client.image(f.scope).orElseThrow();
        assertTrue(initial.stream().noneMatch(id->ReplicaState.hasMember(image,id))); assertTrue(second.stream().noneMatch(id->ReplicaState.hasMember(image,id)));
        assertEquals(2,image.entries().values().stream().filter(r->r instanceof ReplicaState.Cursor).count());
        for (var id:f.runtime.members(f.group,new Selection.AllOf(List.of()))) assertEquals(f.runtime.state(id,10).position(),f.client.state(f.scope,id,10).position());
        for (int tick=1;tick<10;tick++) f.client.noteTick(tick);
        assertEquals(12,f.runtime.liveMembers()); assertEquals(0,f.runtime.tick());
    }
    @Test void memberMaskFactsStaySparseAndRecoveryIsAuthorizedAndRateLimited() {
        var f=new Fixture(); var plan=f.birth(480); f.publish(Vec3.ZERO); f.deliver(0);
        f.runtime.remove(new DanmakuWorldRuntime.MemberId(f.group,plan.ordinal(),7),"hit"); f.publish(Vec3.ZERO);
        var changes=f.sent.stream().filter(b->f.wire.decode(b).kind()==ProgramWire.Kind.DECISIONS).toList();
        assertEquals(1,changes.size()); assertTrue(changes.getFirst().length<2000);
        f.deliver(1);
        var request=f.client.request(f.scope,1).orElseThrow();
        assertTrue(f.server.recover(request,1)); assertFalse(f.server.recover(request,2));
        var unknown=new ProgramWire.Envelope(1,"test:other",OWNER,1,1,1,0,0,ProgramWire.Kind.RESYNC,1,0,1,new byte[0]);
        assertFalse(f.server.recover(unknown,100)); f.publish(Vec3.ZERO); f.deliver(2);
        assertEquals(1,f.server.stats().throttled());
    }
    @Test void beamLengthFactUsesSameImageAndExpiredChunksRequestRecovery() {
        var f=new Fixture(); var plan=f.birth(480); var id=new DanmakuWorldRuntime.MemberId(f.group,plan.ordinal(),0);
        var projection=f.projection(); var beamImage=ReplicaState.beam(projection.image(),id,3.5);
        var decoded=f.wire.image(f.wire.image(beamImage)); ReplicaState.validate(decoded,f.wire.limits(),projection.descriptions());
        assertEquals(3.5,((ReplicaState.Beam)decoded.entries().get("l/"+ReplicaState.motionKey(id))).length());
        f.publish(Vec3.ZERO);
        for (byte[] b:f.sent) if (f.wire.decode(b).kind()==ProgramWire.Kind.DESCRIPTION) f.client.receive(b,0);
        f.client.receive(f.sent.stream().filter(b->f.wire.decode(b).kind()==ProgramWire.Kind.START).findFirst().orElseThrow(),0);
        assertEquals(1,f.client.pendingTransfers()); assertEquals(Set.of(f.scope),f.client.recoveryRequests(201)); assertEquals(0,f.client.pendingTransfers());
    }
    @Test void steadyStateHasNoMemberTrafficAndDefinitionCacheIsShared() {
        var f=new Fixture(); f.birth(48); f.publish(Vec3.ZERO); f.deliver(0);
        f.runtime.advance(1); f.publish(Vec3.ZERO); assertTrue(f.sent.isEmpty());
        f.publish(new Vec3(1000,0,0)); f.deliver(2); f.publish(Vec3.ZERO);
        assertTrue(f.sent.stream().noneMatch(b->f.wire.decode(b).kind()==ProgramWire.Kind.DESCRIPTION));
    }
    @Test void compiledDefinitionsAndFutureWarningRestoreWithoutExecutingCommit() {
        var wire=new ProgramWire(NetworkLimits.defaults()); var codec=ServerOperations.dataRegistry().freeze();
        var type=ServerOperations.emissionType(codec); var registry=new OperationRegistry(); registry.register(type); registry.freeze();
        var execution=new ProgramExecution(); ServerOperations.install(execution,type); execution.freeze();
        var runtime=new DanmakuWorldRuntime("test:world",new DanmakuWorldRuntime.Limits(100,100,100,100,10000),new World());
        var encounter=runtime.startEncounter(OWNER); var session=new ProgramSession(runtime,execution);
        var program=new Actions.ChildProgram(new Patterns.SphereDirections(3,Patterns.SphereDistribution.FIBONACCI,1),new Paths.Linear(Vec3.Z,.2),100,0,0);
        var parameters=new ServerOperations.Emission("attack",program,Actions.Binding.snapshot(),new Actions.Pose(Frame.IDENTITY,Vec3.ZERO),new Deterministic.Seed(23,List.of()),new AuthorityDescriptions.Commit(new ProgramRef.Plan(new NodeId(0)),0,100,AuthorityDescriptions.Failure.REJECT_BATCH,true),10);
        var builder=new ProgramBuilder(); var root=builder.operation(type,parameters,new TickSpan(0));
        var compiled=ProgramCompiler.compile("test:network",1,new PlaybackPolicy.Once(TailPolicy.RETAIN_UNTIL_LIFETIME),root,ProgramLimits.defaults(),registry);
        assertTrue(compiled.success(),compiled.diagnostics().toString()); session.install(encounter,1,false,compiled.definition().orElseThrow(),new ClockAnchors(0,0,null,null),0); session.advance(0,100);
        List<byte[]> sent=new ArrayList<>(); var server=new ProgramSyncServer(wire,sent::add,40);
        var projection=ReplicaState.capture(session.checkpoint(),wire); long group=((ReplicaState.Group)projection.image().entries().values().stream().filter(r->r instanceof ReplicaState.Group).findFirst().orElseThrow()).id();
        server.publish(projection,Map.of(group,new ProgramInterest.Box(Vec3.ZERO,new Vec3(100,100,100))),Vec3.ZERO,0);
        var replica=new ProgramReplica(wire,registry,ProgramLimits.defaults()); for (byte[] bytes:sent) replica.receive(bytes,0);
        var image=replica.image(new ProgramReplica.Scope("test:world",OWNER)).orElseThrow();
        assertTrue(image.entries().values().stream().anyMatch(r->r instanceof ReplicaState.Pending));
        assertTrue(image.entries().values().stream().filter(r->r instanceof ReplicaState.Batch).map(r->(ReplicaState.Batch)r).allMatch(b->b.alive().stream().allMatch(w->w==0)));
        replica.noteTick(20); assertEquals(0,runtime.liveMembers()); assertEquals(0,runtime.tick());
        var incompatible=new ProgramReplica(wire,new OperationRegistry().freeze(),ProgramLimits.defaults());
        assertThrows(IllegalArgumentException.class,()->{ for (byte[] bytes:sent) incompatible.receive(bytes,0); });
    }
    @Test void followingSourceAndSharedGroupMotionUseAuthorityClosure() {
        var f=new Fixture(); var p=f.birth(2); var id=new DanmakuWorldRuntime.MemberId(f.group,p.ordinal(),0);
        f.runtime.groupMotion(f.group,new GroupMotion(Frame.IDENTITY,new Paths.Linear(Vec3.X,.4),new Transforms.Rotate(Vec3.Y,ScalarCurve.constant(.5),Vec3.ZERO),0,List.of()));
        f.runtime.attachToGroup(id); f.publish(Vec3.ZERO); f.deliver(0);
        assertEquals(f.runtime.state(id,10).position(),f.client.state(f.scope,id,10).position());
        long shots=f.runtime.createGroup(f.encounter,1,0,null,-1,false,false,Frame.IDENTITY,List.of()); var ref=new ProgramRef.Group(new NodeId(99)); f.runtime.bindSource(ref,f.group);
        var follow=new Actions.Binding(Actions.Attachment.FOLLOW_SOURCE,Actions.Velocity.ABSOLUTE,Actions.OnSourceLost.END);
        f.runtime.advance(1);
        f.runtime.createEmitter(shots,new Actions.CreateEmitter(new Actions.GroupPose(ref),ScalarCurve.constant(.1),Vec3.Y,1,2,2,p.program(),follow,null),new Deterministic.Seed(2,List.of()),new AuthorityDescriptions.Commit(new ProgramRef.Plan(new NodeId(0)),0,100,AuthorityDescriptions.Failure.REJECT_BATCH,true));
        f.runtime.advance(2); var projection=f.projection(); var image=projection.image();
        image=f.wire.image(f.wire.image(image)); ReplicaState.validate(image,f.wire.limits(),projection.descriptions());
        for (var member:f.runtime.members(shots,new Selection.AllOf(List.of()))) assertEquals(f.runtime.state(member,10).position(),ReplicaState.state(image,projection.descriptions(),member,10).position());
        assertTrue(image.entries().values().stream().anyMatch(r->r instanceof ReplicaState.Emitter));
        f.runtime.advance(3); var later=f.projection();
        var delta=f.wire.diff(projection.image(),later.image());
        assertTrue(delta.changed().values().stream().noneMatch(r->r instanceof ReplicaState.Motion));
    }
    @Test void digestMismatchRequestsRecoveryAndOldEpochDoesNotReplaceNewBattle() {
        var f=new Fixture(); f.birth(2); f.publish(Vec3.ZERO); List<byte[]> old=List.copyOf(f.sent); f.deliver(0);
        var header=f.wire.decode(old.stream().filter(b->f.wire.decode(b).kind()==ProgramWire.Kind.CLOCK_DIGEST).findFirst().orElseThrow());
        var bad=new ProgramWire.Envelope(1,header.dimension(),OWNER,header.epoch(),header.subscription(),header.sequence()+1,header.revision(),40,ProgramWire.Kind.CLOCK_DIGEST,999,0,1,new byte[64]);
        f.client.receive(f.wire.encode(bad),40); assertEquals(Set.of(f.scope),f.client.recoveryRequests(40));
        var next=f.runtime.startEncounter(OWNER); long group=f.runtime.createGroup(next,1,0,null,-1,false,false,Frame.IDENTITY,List.of());
        var p=f.projection(); f.server.publish(p,Map.of(group,new ProgramInterest.Box(Vec3.ZERO,Vec3.X)),Vec3.ZERO,0); f.deliver(41);
        for (byte[] bytes:old) f.client.receive(bytes,42);
        assertTrue(f.client.image(f.scope).orElseThrow().entries().values().stream().filter(r->r instanceof ReplicaState.Group).map(r->(ReplicaState.Group)r).allMatch(g->g.encounter().epoch()==next.epoch()));
    }
    @Test void absentSurfacePointsAreExplicitAndCorruptMaskCannotMutateInstalledState() {
        var f=new Fixture(); var p=f.birth(2); f.publish(Vec3.ZERO); List<byte[]> initial=List.copyOf(f.sent); f.deliver(0);
        var sample=new ReplicaState.Sample(f.group,"surface",null,List.of(new ReplicaState.SurfacePoint(null),new ReplicaState.SurfacePoint(Vec3.X)),2);
        assertEquals(sample,f.wire.decodeData(f.wire.encodeData(sample),ReplicaState.Sample.class));
        var before=f.client.image(f.scope).orElseThrow(); var batch=(ReplicaState.Batch)before.entries().get(ReplicaState.batchKey(f.group,p.ordinal()));
        var bad=new ProgramWire.Patch(before.revision(),List.of(),Map.of(ReplicaState.batchKey(f.group,p.ordinal()),new ReplicaState.Mask(f.group,p.ordinal(),List.of(7L),List.of(7L),false)));
        var start=f.wire.decode(initial.stream().filter(b->f.wire.decode(b).kind()==ProgramWire.Kind.START).findFirst().orElseThrow());
        var header=new ProgramWire.Envelope(1,start.dimension(),OWNER,1,start.subscription(),100,before.revision()+1,1,ProgramWire.Kind.DECISIONS,100,0,1,new byte[0]);
        assertThrows(IllegalArgumentException.class,()->{ for (var chunk:f.wire.split(header,f.wire.patch(bad))) f.client.receive(f.wire.encode(chunk),1); });
        assertEquals(before,f.client.image(f.scope).orElseThrow()); assertEquals(2,batch.templates().size());
    }
    @Test void increasingSequenceCannotResurrectRemovedMemberAndRejectedPatchIsAtomic() {
        var f=new Fixture(); var plan=f.birth(2); f.publish(Vec3.ZERO); List<byte[]> initial=List.copyOf(f.sent); f.deliver(0);
        var id=new DanmakuWorldRuntime.MemberId(f.group,plan.ordinal(),0);
        f.runtime.remove(id,"hit"); f.runtime.advance(1); f.publish(Vec3.ZERO); f.deliver(1);
        var before=f.client.image(f.scope).orElseThrow();
        var patch=new ProgramWire.Patch(before.revision(),List.of(),Map.of(ReplicaState.batchKey(f.group,plan.ordinal()),new ReplicaState.Mask(f.group,plan.ordinal(),List.of(3L),List.of(3L),false)));
        var start=f.wire.decode(initial.stream().filter(b->f.wire.decode(b).kind()==ProgramWire.Kind.START).findFirst().orElseThrow());
        var header=new ProgramWire.Envelope(1,"test:world",OWNER,1,start.subscription(),100,before.revision()+1,2,ProgramWire.Kind.DECISIONS,100,0,1,new byte[0]);
        assertThrows(IllegalArgumentException.class,()->{ for (var chunk:f.wire.split(header,f.wire.patch(patch))) f.client.receive(f.wire.encode(chunk),2); });
        assertEquals(before,f.client.image(f.scope).orElseThrow()); assertFalse(ReplicaState.hasMember(before,id));
    }
}
