package com.bitsson.gensokyou.danmaku.program;

import com.bitsson.gensokyou.danmaku.program.math.*;
import com.bitsson.gensokyou.danmaku.program.ops.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProgramOperationsTest {
    private static final Deterministic.Seed SEED=new Deterministic.Seed(12345,List.of(7,2));
    private static void close(Vec3 expected,Vec3 actual) { assertTrue(expected.subtract(actual).length()<1e-8,expected+" != "+actual); }
    private static ScalarCurve curve(double end,double from,double to) { return new ScalarCurve(List.of(new ScalarCurve.Key(0,from),new ScalarCurve.Key(end,to))); }
    private static MemberMotion.Ownership ownership() { return new MemberMotion.Ownership("boss","card",3,"flower"); }

    @Test void numericalPolicyHasPortableGoldenFixtures() {
        assertEquals(0xe220a8397b1dcdafL,Deterministic.mix(0x9e3779b97f4a7c15L));
        assertEquals(new Vec3(1,0,-2),new Vec3(1+Deterministic.GRID*.49,-0.0,-2).quantized());
        close(Vec3.Y,Vec3.X.rotate(Vec3.Z,StrictMath.PI/2));
        var arc=new Paths.Arc(Vec3.X,Vec3.Z,StrictMath.PI/2,1).at(1);
        assertEquals(new Vec3(0.6366195678710938,0.6366195678710938,0),arc.position().quantized());
        assertThrows(IllegalArgumentException.class,()->new Vec3(Double.NaN,0,0));
        assertThrows(IllegalArgumentException.class,()->Vec3.ZERO.unit());
    }
    @Test void speedCurveAndSegmentsSeekWithoutHistoricalIntegration() {
        var path=new Paths.Segments(List.of(
                new Paths.Segment("slow",20,new Paths.SpeedCurve(Vec3.Z,curve(20,1,0))),
                new Paths.Segment("wait",10,new Paths.Hold()),
                new Paths.Segment("release",20,new Paths.Linear(Vec3.X,.5))),Paths.Tail.CONTINUE);
        close(new Vec3(0,0,10),path.at(20).position());
        close(new Vec3(0,0,10),path.at(30).position());
        close(new Vec3(15,0,10),path.at(60).position());
        assertEquals(20,path.endOf("slow"));
        var trigger=new Actions.AtSegmentEnd(path,"slow");
        assertTrue(trigger.due(20,21)); assertFalse(trigger.due(21,22));
        var times=new ArrayList<Double>(); for (int i=0;i<150;i++) times.add(i*.5);
        var expected=times.stream().map(path::at).toList();
        Collections.shuffle(times,new Random(4));
        for (double t:times) assertEquals(expected.get((int)(t*2)),path.at(t));
        assertFalse(new Paths.Segments(path.segments(),Paths.Tail.HOLD).at(100).ended());
        assertTrue(new Paths.Segments(path.segments(),Paths.Tail.END).at(50).ended());
        close(new Vec3(0,0,-10),new Paths.SpeedCurve(Vec3.Z,curve(20,0,-1)).at(20).position());
    }
    @Test void fortyEightBatchesShareTheSameCircleAndBlueTurnReturnsForward() {
        Path arc=new Paths.Arc(Vec3.Z,Vec3.Y,Deterministic.TAU/48,.2);
        double radius=.2/(Deterministic.TAU/48);
        for (int t=0;t<48;t++) {
            var s=arc.at(t);
            assertEquals(radius,s.position().subtract(new Vec3(radius,0,0)).length(),1e-9);
            assertEquals(.2,s.velocity().length(),1e-12);
        }
        double angle=StrictMath.toRadians(25);
        Path turn=new Paths.Arc(Vec3.Z.rotate(Vec3.Y,angle),Vec3.Y,-angle/30,.2);
        close(Vec3.Z.multiply(.2),turn.at(30).velocity());
        close(new Vec3(0,0,20),new Paths.Arc(Vec3.Z,Vec3.Y,0,.2).at(100).position());
        close(new Vec3(0,0,20),new Paths.Arc(Vec3.Z,Vec3.Y,1e-20,.2).at(100).position());
    }
    @Test void compositeRoseDetachesOnlySelectedMembersContinuouslyAndKeepsOwnership() {
        var pattern=new Patterns.Rose(5,6,2,1,0);
        var transform=new Transforms.Compose(List.of(new Transforms.Breathe(1,.4,40,0),new Transforms.Rotate(Vec3.Z,curve(100,0,4),Vec3.ZERO)));
        Path group=new Paths.Arc(Vec3.Z,Vec3.Y,.01,.2);
        for (Pattern.Member member:pattern.generate(SEED)) {
            var attached=new MemberMotion.Attached(member,Frame.IDENTITY,group,transform,0,0,ownership());
            var detached=new MemberMotion.Detached(attached,new Selection.IndexModulo(2,0),20,MemberMotion.VelocityPolicy.TANGENT,Vec3.ZERO,false);
            close(attached.at(20).position(),detached.at(20).position());
            close(attached.at(20).velocity(),detached.at(20).velocity());
            if (member.index()%2==0) close(attached.at(20).position().add(attached.at(20).velocity().multiply(10)),detached.at(30).position());
            else assertEquals(attached.at(30),detached.at(30));
            assertEquals(ownership(),detached.ownership());
            double epsilon=1e-5;
            close(attached.at(12+epsilon).position().subtract(attached.at(12-epsilon).position()).multiply(1/(2*epsilon)),attached.at(12).velocity());
        }
        var breathe=new Transforms.Breathe(1,.4,40,0);
        assertEquals(.6,breathe.apply(new Path.State(Vec3.X,Vec3.ZERO,false),30).position().x(),1e-10);
        assertEquals(1.4,breathe.apply(new Path.State(Vec3.X,Vec3.ZERO,false),10).position().x(),1e-10);
    }
    @Test void hierarchyOffsetMirrorBezierWaveAndRebaseAreAnalytic() {
        assertEquals(4,Transforms.stagger(10,3,2));
        assertEquals(-2,Transforms.stagger(4,3,2));
        assertEquals(1.5,Transforms.phaseOffset(0,3,.5));
        Path path=new Paths.Bezier(List.of(Vec3.ZERO,new Vec3(1,2,0),new Vec3(4,0,0)),20,Paths.TimeMap.LINEAR,Paths.Tail.HOLD);
        close(new Vec3(1.5,1,0),path.at(10).position());
        assertEquals(Vec3.ZERO,path.at(20).velocity());
        var wave=new Paths.WaveOffset(new Paths.Linear(Vec3.Z,1),Vec3.X,2,20,0);
        close(new Vec3(2,0,5),wave.at(5).position());
        close(new Vec3(0,0,20),wave.at(20).position());
        var rebase=new Paths.Rebase(wave,20,new Paths.Linear(Vec3.Y,.5),true);
        close(wave.at(20).position(),rebase.at(20).position());
        close(new Vec3(0,5,20),rebase.at(30).position());
        assertThrows(IllegalArgumentException.class,()->new Paths.Rebase(wave,20,new Paths.Hold(),false));
        var mirror=new Transforms.Mirror(Vec3.X,Transforms.VisualMirror.REFLECT);
        close(new Vec3(-1,2,3),mirror.apply(new Path.State(new Vec3(1,2,3),Vec3.X,false),0).position());
        assertEquals(Transforms.VisualMirror.REFLECT,mirror.visualOrientation());
        close(Vec3.Z,MemberMotion.radialDirection(Vec3.ZERO,Vec3.ZERO,Vec3.Z));
        close(new Vec3(-1,0,1).unit(),MemberMotion.aimAtPoint(Vec3.X,Vec3.Z));
        close(Vec3.X,Transforms.orientAlongPath(new Paths.Linear(Vec3.X,1),2,Vec3.Y,Frame.IDENTITY).forward());
    }
    @Test void templatesKeepRealCountsGapPhaseAndIndependentChannels() {
        var grid=new Patterns.ParallelGrid(3,7,1,1,Set.of(3),Vec3.ZERO);
        assertEquals(18,grid.generate(SEED).size()); assertEquals(18,grid.countBound());
        assertEquals(20,grid.generate(SEED).getLast().index());
        assertTrue(grid.generate(SEED).stream().noneMatch(m->m.position().x()==0));
        var nodes=new Patterns.AxisNodes(Vec3.Z,List.of(6.0,10.0),List.of(Vec3.X,Vec3.X.multiply(-1)),.2);
        assertEquals(4,nodes.generate(SEED).size());
        double phase=.8,gap=1;
        for (var m:new Patterns.Ring(48,3,gap,phase).generate(SEED)) {
            double delta=StrictMath.atan2(m.position().y(),m.position().x())-phase;
            delta=StrictMath.atan2(StrictMath.sin(delta),StrictMath.cos(delta));
            assertTrue(StrictMath.abs(delta)>=gap/2);
        }
        close(Vec3.X,new Patterns.Fan(1,1,StrictMath.PI/2).generate(SEED).getFirst().direction());
        var angular=new Patterns.AngularGrid(5,5,.1,.1).generate(SEED);
        assertEquals(25,angular.size()); assertTrue(angular.stream().allMatch(m->m.position().equals(Vec3.ZERO)));
        var style=new Pattern.Style("sphere","core",.8,4);
        var union=new Patterns.Union(List.of(new Patterns.Tagged(new Patterns.Point(Vec3.ZERO,Vec3.Z),Set.of("core"),style),new Patterns.Rose(5,4,2,1,0)));
        assertEquals(21,union.generate(SEED).size());
        assertTrue(new Selection.Tag("core").includes(union.generate(SEED).getFirst()));
        assertEquals(4,union.generate(SEED).getFirst().style().damageScale());
    }
    @Test void sphereLineageDoesNotDependOnSiblingExistenceAndIsNotLatitudeUniform() {
        Map<List<Integer>,Vec3> before=new HashMap<>();
        for (int p=0;p<4;p++) for (int c=0;c<2;c++) for (int d=0;d<2;d++) {
            var key=SEED.child(p).child(c).child(d);
            before.put(key.lineage(),Deterministic.seedDirection(key,5,0,Deterministic.Distribution.UNIFORM_SPHERE,Vec3.Z,StrictMath.PI));
        }
        for (int p:List.of(0,2,3)) for (int c=0;c<2;c++) for (int d=0;d<2;d++) {
            var key=SEED.child(p).child(c).child(d);
            assertEquals(before.get(key.lineage()),Deterministic.seedDirection(key,5,0,Deterministic.Distribution.UNIFORM_SPHERE,Vec3.Z,StrictMath.PI));
        }
        double z2=0;
        for (int i=0;i<10000;i++) { double z=Deterministic.seedDirection(SEED,3,i,Deterministic.Distribution.UNIFORM_SPHERE,Vec3.Z,StrictMath.PI).z(); z2+=z*z; }
        assertEquals(1.0/3,z2/10000,.015);
        for (var m:new Patterns.SphereSurface(48,3,Patterns.SphereDistribution.SEEDED,Patterns.Direction.OUTWARD,Vec3.Z,4).generate(SEED)) { assertEquals(3,m.position().length(),1e-10); close(m.position().unit(),m.direction()); }
        for (var m:new Patterns.SphereDirections(48,Patterns.SphereDistribution.FIBONACCI,4).generate(SEED)) assertEquals(Vec3.ZERO,m.position());
    }
    @Test void allPatternBoundsContainEveryTemplateAndGeneratedDataRoundTrips() {
        List<Pattern> patterns=List.of(new Patterns.Point(Vec3.ZERO,Vec3.Z),new Patterns.Ring(12,3,.5,.3),new Patterns.Fan(7,1,.4),
                new Patterns.AngularGrid(5,5,.2,.2),new Patterns.ParallelGrid(3,7,1,1,Set.of(3),Vec3.ZERO),
                new Patterns.AxisNodes(Vec3.Z,List.of(6.0,10.0),List.of(Vec3.X,Vec3.X.multiply(-1)),.2),
                new Patterns.SurfaceRays(List.of(Vec3.X,Vec3.Z),List.of(Vec3.Y)),new Patterns.Rose(5,6,2,1,.2),
                new Patterns.Spiral(30,3,1,4,-1,.2),new Patterns.ConeScatter(30,1,2),new Patterns.VolumeScatter(30,new Vec3(3,4,5),2),
                new Patterns.SphereSurface(30,3,Patterns.SphereDistribution.SEEDED,Patterns.Direction.INWARD,Vec3.Z,2),
                new Patterns.SphereDirections(30,Patterns.SphereDistribution.FIBONACCI,2),new Patterns.HelixColumn(10,3,2,20,ScalarCurve.constant(2),0),
                new Patterns.Radial(30,.5,.3),new Patterns.Shell(3,10,4,.5,.2),new Patterns.Cage(3,10,4,10,.5,.2),
                new Patterns.AroundPoint(30,Vec3.X,2,5,.3,1,2),new Patterns.Lattice(30,Vec3.X,2,5,.3,1,2),new Patterns.Rain(30,new Vec3(5,0,5),10,2));
        var codec=OperationLibrary.dataRegistry().freeze();
        for (Pattern pattern:patterns) {
            var parameters=new OperationLibrary.PatternParameters("g",100,pattern,SEED);
            assertEquals(parameters,codec.decode(codec.encode(parameters),OperationLibrary.PatternParameters.class));
            var members=pattern.generate(SEED);
            assertEquals(pattern.countBound(),members.size());
            for (var m:members) { assertTrue(m.position().length()<=pattern.radiusBound()+1e-9); assertEquals(1,m.direction().length(),1e-9); }
        }
        var helix=new Patterns.HelixColumn(10,3,2,20,ScalarCurve.constant(2),0);
        var rotation=new Transforms.Rotate(Vec3.Y,curve(100,0,3),Vec3.ZERO);
        for (var m:helix.generate(SEED)) assertEquals(m.position().y(),rotation.apply(new Path.State(m.position(),Vec3.ZERO,false),47).position().y(),1e-9);
        var aimed=new Patterns.Lattice(20,Vec3.ZERO,2,5,1,1,4);
        for (var m:aimed.generate(SEED)) close(m.position().multiply(-1).unit(),m.direction());
    }
    @Test void presentationWindowsAreHalfOpenAndHiddenMembersNeverDamage() {
        var profile=new Presentation.Profile(List.of(new Presentation.Window(TimeDomain.MEMBER_AGE,10,100)),
                new Presentation.VisibilityCycle(TimeDomain.MEMBER_AGE,20,10,0),
                new Presentation.Fade(TimeDomain.MEMBER_AGE,curve(100,1,0)),
                List.of(new Presentation.Flash(new Presentation.Window(TimeDomain.MEMBER_AGE,20,22),2,3)));
        assertFalse(profile.at(new Presentation.Clocks(0,0,0,15)).damaging());
        var hidden=profile.at(new Presentation.Clocks(0,0,0,15));
        assertTrue(hidden.visible());
        assertEquals(.85*Presentation.HIDDEN_ALPHA,hidden.alpha(),1e-12);
        assertEquals(1,hidden.brightness(),1e-12);
        var state=profile.at(new Presentation.Clocks(0,0,0,20));
        assertTrue(state.damaging()); assertEquals(2,state.sizeMultiplier()); assertEquals(.8,state.alpha(),1e-12);
        assertFalse(profile.at(new Presentation.Clocks(0,0,0,100)).damaging());
        var laser=new Presentation.LaserWindow(TimeDomain.GROUP,0,20,40);
        assertTrue(laser.at(new Presentation.Clocks(0,0,19,0)).warning());
        assertFalse(laser.at(new Presentation.Clocks(0,0,19,0)).damaging());
        assertTrue(laser.at(new Presentation.Clocks(0,0,20,0)).damaging());
        assertFalse(laser.at(new Presentation.Clocks(0,0,40,0)).visible());
    }
    @Test void movingEmitterUsesShotPoseAndSnapshotBulletsSurviveSourceLoss() {
        var source=new Actions.MemberPose(new ProgramRef.Group(new NodeId(1)),0);
        var child=new Actions.ChildProgram(new Patterns.SphereDirections(8,Patterns.SphereDistribution.SEEDED,0),new Paths.Linear(Vec3.Z,.2),100,0,0);
        var emitter=new Actions.CreateEmitter(source,curve(100,0,StrictMath.PI),Vec3.Y,20,20,3,child,Actions.Binding.snapshot(),new Presentation.Billboard("test:ring",2,-1,ScalarCurve.constant(0)));
        assertEquals(60,emitter.shotTick(2));
        Actions.ResolvedSources context=(s,t)->t>60?Optional.empty():Optional.of(new Actions.Pose(new Frame(Vec3.X.multiply(t),Vec3.X,Vec3.Y,Vec3.Z),Vec3.X));
        var spawn=new Actions.SpawnChild(source,child,new Actions.Binding(Actions.Attachment.WORLD_SNAPSHOT,Actions.Velocity.ADD_SOURCE_VELOCITY,Actions.OnSourceLost.END));
        var state=Actions.emittedState(spawn,context,20,80,Vec3.ZERO).orElseThrow();
        close(new Vec3(80,0,12),state.position());
        assertTrue(Actions.emittedState(spawn,context,80,90,Vec3.ZERO).isEmpty());
        var replacement=new Actions.ReplaceMembers(new Selection.IndexRange(0,4),new Actions.ChildProgram(new Patterns.SphereDirections(2,Patterns.SphereDistribution.SEEDED,2),new Paths.Hold(),20,0,0),Actions.AtomicScope.PER_PARENT,Actions.OnReject.END_PARENT);
        assertEquals(4,replacement.netMemberIncrease(4));
        assertThrows(IllegalArgumentException.class,()->new Actions.SpawnChild(new Actions.ExternalEntitySource(UUID.randomUUID()),child,new Actions.Binding(Actions.Attachment.FOLLOW_SOURCE,Actions.Velocity.ABSOLUTE,Actions.OnSourceLost.END)));
        var codec=OperationLibrary.dataRegistry().freeze();
        assertEquals(emitter,codec.decode(codec.encode(emitter),Actions.CreateEmitter.class));
        assertEquals(replacement,codec.decode(codec.encode(replacement),Actions.ReplaceMembers.class));
    }
    @Test void builtinTypesCompileAndRoundTripThroughUnchangedProgramEnvelope() {
        var codec=OperationLibrary.dataRegistry().freeze();
        var registry=new OperationRegistry(); var type=OperationLibrary.patternType(codec); registry.register(type); registry.freeze();
        var parameters=new OperationLibrary.PatternParameters("grid",100,new Patterns.ParallelGrid(3,7,1,1,Set.of(3),Vec3.ZERO),SEED);
        var op=new OperationNode(new NodeId(0),type.id(),1,new TickSpan(0),type.contract(parameters).writes(),type.encode(parameters));
        var bytes=ProgramCodec.encode("test:grid",new PlaybackPolicy.Once(TailPolicy.RETAIN_UNTIL_LIFETIME),op,ProgramLimits.defaults());
        var result=ProgramCodec.decode(bytes,ProgramLimits.defaults(),registry);
        assertTrue(result.success(),result.diagnostics().toString()); assertEquals(18,result.definition().orElseThrow().bounds().peakMembersPerBatch());
        var path=OperationLibrary.PathSpec.segments(List.of(new Paths.Segment("hold",20,new Paths.Hold()),new Paths.Segment("go",30,new Paths.Linear(Vec3.Z,.2))),Paths.Tail.CONTINUE);
        var pathParameters=new OperationLibrary.PathParameters("g",100,path);
        var decoded=codec.decode(codec.encode(pathParameters),OperationLibrary.PathParameters.class);
        assertEquals(pathParameters,decoded); assertEquals(path.evaluator().at(75),decoded.path().evaluator().at(75));
        var invalid=new HashMap<>(codec.encode(parameters)); invalid.put("v.pattern","future:unknown");
        assertThrows(IllegalArgumentException.class,()->codec.decode(invalid,OperationLibrary.PatternParameters.class));
        var noncanonical=new HashMap<>(codec.encode(parameters)); noncanonical.put("extra","ignored");
        assertThrows(IllegalArgumentException.class,()->codec.decode(noncanonical,OperationLibrary.PatternParameters.class));
    }
    @Test void nestedDescriptionsAndFollowSourceHaveCanonicalRecoveryData() {
        var codec=OperationLibrary.dataRegistry().freeze();
        var path=OperationLibrary.PathSpec.segments(List.of(new Paths.Segment("stop",20,new Paths.SpeedCurve(Vec3.Z,curve(20,1,0)))),Paths.Tail.HOLD);
        var trigger=new Actions.SegmentEndTrigger(path,"stop");
        assertEquals(trigger,codec.decode(codec.encode(trigger),Actions.SegmentEndTrigger.class));
        var child=new Actions.ChildProgram(new Patterns.Point(Vec3.ZERO,Vec3.Z),new Paths.Linear(Vec3.Z,.2),100,0,0);
        var source=new Actions.GroupPose(new ProgramRef.Group(new NodeId(7)));
        var spawn=new Actions.SpawnChild(source,child,new Actions.Binding(Actions.Attachment.FOLLOW_SOURCE,Actions.Velocity.ABSOLUTE,Actions.OnSourceLost.DETACH_AT_LAST_AUTHORITATIVE_POSE));
        Actions.ResolvedSources context=(s,t)->Optional.of(new Actions.Pose(
                new Frame(Vec3.X.multiply(t),Vec3.X.rotate(Vec3.Y,t*.1),Vec3.Y,Vec3.Z.rotate(Vec3.Y,t*.1)),Vec3.X,Vec3.Y.multiply(.1)));
        double t=12,epsilon=1e-5;
        var state=Actions.emittedState(spawn,context,0,t,Vec3.X).orElseThrow();
        Vec3 finiteDifference=Actions.emittedState(spawn,context,0,t+epsilon,Vec3.X).orElseThrow().position()
                .subtract(Actions.emittedState(spawn,context,0,t-epsilon,Vec3.X).orElseThrow().position()).multiply(1/(2*epsilon));
        close(finiteDifference,state.velocity());
        assertEquals(spawn,codec.decode(codec.encode(spawn),Actions.SpawnChild.class));
        var fields=new HashMap<>(codec.encode(child)); fields.put("v.pattern.count","513");
        assertThrows(IllegalArgumentException.class,()->codec.decode(fields,Actions.ChildProgram.class));
    }
}
