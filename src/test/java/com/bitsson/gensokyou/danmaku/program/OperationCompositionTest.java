package com.bitsson.gensokyou.danmaku.program;

import com.bitsson.gensokyou.danmaku.program.math.*;
import com.bitsson.gensokyou.danmaku.program.ops.*;
import com.bitsson.gensokyou.danmaku.program.ops.Paths;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OperationCompositionTest {
    private static final Deterministic.Seed SEED=new Deterministic.Seed(23,List.of());
    private static final Transforms.Compose IDENTITY=new Transforms.Compose(List.of());
    private static final Presentation.Profile PROFILE=new Presentation.Profile(List.of(),null,null,List.of());
    private static void close(Vec3 expected,Vec3 actual) { assertTrue(expected.subtract(actual).length()<1e-8,expected+" != "+actual); }
    private static Paths.Segments slow() {
        return new Paths.Segments(List.of(new Paths.Segment("slow",20,new Paths.SpeedCurve(Vec3.Z,
                new ScalarCurve(List.of(new ScalarCurve.Key(0,1),new ScalarCurve.Key(20,0)))))),Paths.Tail.HOLD);
    }
    private static Actions.ChildAction action(Actions.ChildProgram child,Actions.ChildActionKind kind,Actions.Trigger trigger,int count) {
        return new Actions.ChildAction(kind,new Selection.AllOf(List.of()),trigger,20,count,child,Actions.Binding.snapshot(),
                Actions.AtomicScope.PER_PARENT,Actions.OnReject.END_PARENT,ScalarCurve.constant(0),Vec3.Y);
    }
    @Test void twoGenerationGraphHasRealBoundsAndSurvivesEnvelopeRoundTrip() {
        var leaves=new Actions.ChildProgram(new Patterns.SphereDirections(2,Patterns.SphereDistribution.SEEDED,9),new Paths.Linear(Vec3.Z,.2),60,0,0);
        var children=new Actions.ChildProgram(new Patterns.SphereDirections(2,Patterns.SphereDistribution.SEEDED,8),slow(),21,1,4,
                List.of(action(leaves,Actions.ChildActionKind.REPLACE,new Actions.AtPathSegmentEnd("slow"),1)),PROFILE,IDENTITY);
        var roots=new Actions.ChildProgram(new Patterns.SphereSurface(3,2,Patterns.SphereDistribution.FIBONACCI,Patterns.Direction.OUTWARD,Vec3.Z,7),slow(),21,2,18,
                List.of(action(children,Actions.ChildActionKind.REPLACE,new Actions.AtPathSegmentEnd("slow"),1)),PROFILE,IDENTITY);
        assertEquals(21,roots.totalMembersBound()); // N + 2N + 4N
        assertEquals(100,roots.treeLifetimeBound());
        assertThrows(IllegalArgumentException.class,()->new Actions.ChildProgram(roots.pattern(),roots.path(),21,2,17,roots.actions(),PROFILE,IDENTITY));
        var codec=OperationLibrary.dataRegistry().freeze();
        var type=OperationLibrary.childProgramType(codec);
        var registry=new OperationRegistry(); registry.register(type); registry.freeze();
        var node=new OperationNode(new NodeId(0),type.id(),1,new TickSpan(0),type.contract(roots).writes(),type.encode(roots));
        var result=ProgramCodec.decode(ProgramCodec.encode("test:two_generations",new PlaybackPolicy.Once(TailPolicy.RETAIN_UNTIL_LIFETIME),node,ProgramLimits.defaults()),ProgramLimits.defaults(),registry);
        assertTrue(result.success(),result.diagnostics().toString());
        var restored=type.decode(((OperationNode)result.definition().orElseThrow().root()).parameters());
        assertEquals(21,restored.totalMembersBound());
        assertEquals(roots.path().at(20),restored.path().at(20));
        assertEquals(20,restored.actions().getFirst().trigger().tick(restored.path()));
    }
    @Test void movingCarriersDescribeEveryRoundAndRejectShotsAfterLifetime() {
        var scatter=new Actions.ChildProgram(new Patterns.SphereDirections(8,Patterns.SphereDistribution.SEEDED,3),new Paths.Linear(Vec3.Z,.2),100,0,0);
        var carrier=new Actions.ChildProgram(new Patterns.SphereDirections(3,Patterns.SphereDistribution.SEEDED,4),new Paths.Linear(Vec3.Z,.3),61,1,72,
                List.of(action(scatter,Actions.ChildActionKind.EMIT,new Actions.AtAge(20),3)),PROFILE,IDENTITY);
        assertEquals(75,carrier.totalMembersBound()); // 3 carriers + 3*3*8 shots
        assertEquals(160,carrier.treeLifetimeBound());
        assertThrows(IllegalArgumentException.class,()->new Actions.ChildProgram(carrier.pattern(),carrier.path(),60,1,72,carrier.actions(),PROFILE,IDENTITY));
        var codec=OperationLibrary.dataRegistry().freeze();
        var restored=codec.decode(codec.encode(carrier),Actions.ChildProgram.class);
        assertEquals(3,restored.actions().getFirst().count());
        assertEquals(carrier.totalMembersBound(),restored.totalMembersBound());
    }
    @Test void fortyEightDifferentBirthsUseSharedGroupClockAndRecoverIdentically() {
        var members=new Patterns.Ring(48,3,0,0).generate(SEED);
        var rotation=new Transforms.Rotate(Vec3.Z,new ScalarCurve(List.of(new ScalarCurve.Key(0,0),new ScalarCurve.Key(100,2))),Vec3.ZERO);
        var owner=new MemberMotion.Ownership("boss","card",0,"shared");
        var codec=OperationLibrary.dataRegistry().freeze();
        Frame frame=new Frame(new Vec3(10,2,5),Vec3.X,Vec3.Y,Vec3.Z);
        var parents=List.of(new MemberMotion.Layer(new Transforms.Translate(new Paths.Linear(Vec3.X,.1)),0));
        for (var member:members) {
            var attached=new MemberMotion.Attached(member,frame,new Paths.Linear(Vec3.Z,.2),rotation,member.index(),0,owner,0,TimeDomain.GROUP,parents);
            var recovered=codec.decode(codec.encode(attached),MemberMotion.Attached.class);
            for (double time:List.of(60.0,48.0,80.5)) {
                Vec3 center=frame.point(new Vec3(.1*time,0,.2*time));
                assertEquals(3,attached.at(time).position().subtract(center).length(),1e-8);
                close(attached.at(time).position(),recovered.at(time).position());
                close(frame.direction(Vec3.Z),attached.directionAt(time));
            }
        }
    }
    @Test void segmentedPathsComposeThroughChildTranslateRebaseAndPreserveTailContract() {
        var codec=OperationLibrary.dataRegistry().freeze();
        var child=new Actions.ChildProgram(new Patterns.Point(Vec3.ZERO,Vec3.Z),slow(),60,0,0);
        var restored=codec.decode(codec.encode(child),Actions.ChildProgram.class);
        assertEquals(child.path().at(35),restored.path().at(35));
        var translate=new Transforms.Translate(slow());
        assertEquals(translate.apply(new Path.State(Vec3.X,Vec3.ZERO,false),30),codec.decode(codec.encode(translate),Transforms.Translate.class).apply(new Path.State(Vec3.X,Vec3.ZERO,false),30));
        var rebase=new Paths.Rebase(slow(),20,new Paths.Linear(Vec3.X,.1),true);
        assertEquals(rebase.at(35),codec.decode(codec.encode(rebase),Paths.Rebase.class).at(35));
        var bezier=new Paths.Bezier(List.of(Vec3.ZERO,Vec3.X,Vec3.X.multiply(2)),10,Paths.TimeMap.LINEAR,Paths.Tail.END);
        assertThrows(IllegalArgumentException.class,()->new Paths.Segments(List.of(new Paths.Segment("bad",10,bezier)),Paths.Tail.CONTINUE));
        var openBezier=new Paths.Bezier(bezier.controls(),10,Paths.TimeMap.LINEAR,Paths.Tail.CONTINUE);
        close(new Vec3(2.2,0,0),new Paths.Segments(List.of(new Paths.Segment("good",10,openBezier)),Paths.Tail.CONTINUE).at(11).position());
    }
    @Test void parentRotationAndMirrorApplyToLaserDirectionWithoutUsingVelocity() {
        var m=new Patterns.Point(Vec3.X,Vec3.Z).generate(SEED).getFirst();
        var attached=new MemberMotion.Attached(m,Frame.IDENTITY,new Paths.Hold(),IDENTITY,0,0,
                new MemberMotion.Ownership("b","c",0,"g"),0,TimeDomain.GROUP,
                List.of(new MemberMotion.Layer(new Transforms.Rotate(Vec3.Y,ScalarCurve.constant(StrictMath.PI/2),Vec3.ZERO),0),
                        new MemberMotion.Layer(new Transforms.Mirror(Vec3.X,Transforms.VisualMirror.PRESERVE),0)));
        close(Vec3.X.multiply(-1),attached.directionAt(10));
        assertEquals(Vec3.ZERO,attached.at(10).velocity());
    }
    private record Birth(List<Integer> lineage,double tick,double end,Vec3 origin,Actions.ChildProgram program,Pattern.Member member) {}
    /** Deterministic accepted-plan expansion only: no world queries, budget decisions or damage. */
    private static void expand(Actions.ChildProgram program,Deterministic.Seed seed,double birth,Vec3 origin,List<Birth> output) {
        for (var member:program.pattern().generate(seed)) {
            var lineage=seed.child(member.index());
            double end=birth+program.lifetimeTicks();
            for (var action:program.actions()) if (action.selection().includes(member)) {
                double first=action.trigger().tick(program.path());
                for (int shot=0;shot<action.count();shot++) {
                    double age=first+shot*action.period();
                    Vec3 childOrigin=origin.add(program.memberState(member,seed,age).position());
                    expand(action.child(),lineage.child(shot),birth+age,childOrigin,output);
                }
                if (action.kind()==Actions.ChildActionKind.REPLACE) end=StrictMath.min(end,birth+first);
            }
            output.add(new Birth(lineage.lineage(),birth,end,origin,program,member));
        }
    }
    @Test void acceptedTwoGenerationPlanActuallyExpandsSevenNWithFourNFinalAndStableWorldOrigins() {
        var leaves=new Actions.ChildProgram(new Patterns.SphereDirections(2,Patterns.SphereDistribution.SEEDED,7),new Paths.Linear(Vec3.Z,.2),60,0,0,
                List.of(),PROFILE,IDENTITY,Actions.DirectionRule.SEEDED_SPHERE,20);
        var middle=new Actions.ChildProgram(new Patterns.SphereDirections(2,Patterns.SphereDistribution.SEEDED,6),slow(),21,1,4,
                List.of(action(leaves,Actions.ChildActionKind.REPLACE,new Actions.AtPathSegmentEnd("slow"),1)),PROFILE,IDENTITY,Actions.DirectionRule.SEEDED_SPHERE,19);
        var roots=new Actions.ChildProgram(new Patterns.SphereSurface(3,2,Patterns.SphereDistribution.FIBONACCI,Patterns.Direction.OUTWARD,Vec3.Z,5),slow(),21,2,18,
                List.of(action(middle,Actions.ChildActionKind.REPLACE,new Actions.AtPathSegmentEnd("slow"),1)),PROFILE,IDENTITY,Actions.DirectionRule.TEMPLATE,0);
        var births=new ArrayList<Birth>(); expand(roots,SEED,0,Vec3.ZERO,births);
        assertEquals(21,births.size());
        assertEquals(12,births.stream().filter(b->b.tick()<=40 && b.end()>40).count());
        assertEquals(6,births.stream().filter(b->b.tick()<=20 && b.end()>20).count());
        var codec=OperationLibrary.dataRegistry().freeze();
        var restored=codec.decode(codec.encode(roots),Actions.ChildProgram.class);
        var recovered=new ArrayList<Birth>(); expand(restored,SEED,0,Vec3.ZERO,recovered);
        for (int i=0;i<births.size();i++) {
            assertEquals(births.get(i).lineage(),recovered.get(i).lineage());
            close(births.get(i).origin(),recovered.get(i).origin());
        }
        for (var b:births) if (b.tick()==20) assertEquals(12,b.origin().length(),1e-8);
    }
    @Test void authorityPoliciesAndMovingStormEmitterRoundTripWithConcreteInputs() {
        var codec=OperationLibrary.dataRegistry().freeze();
        var capture=new AuthorityDescriptions.CaptureFrame(new ProgramRef.TargetSet(new NodeId(1)),0,
                AuthorityDescriptions.TargetPoint.CHEST,Vec3.X,Vec3.Y,null,Vec3.X);
        assertEquals(capture,codec.decode(codec.encode(capture),AuthorityDescriptions.CaptureFrame.class));
        var commit=new AuthorityDescriptions.Commit(new ProgramRef.Plan(new NodeId(2)),6,48,AuthorityDescriptions.Failure.CANCEL_GROUP,true);
        assertEquals(commit,codec.decode(codec.encode(commit),AuthorityDescriptions.Commit.class));
        var child=new Actions.ChildProgram(new Patterns.SphereDirections(6,Patterns.SphereDistribution.FIBONACCI,1),new Paths.Linear(Vec3.Z,.2),40,0,0);
        var storm=new Actions.ChildProgram(new Patterns.HelixColumn(10,3,2,12,ScalarCurve.constant(2),0),
                new Paths.Bezier(List.of(Vec3.ZERO,Vec3.X.multiply(4),Vec3.Z.multiply(8)),100,Paths.TimeMap.LINEAR,Paths.Tail.HOLD),81,1,288,
                List.of(action(child,Actions.ChildActionKind.EMIT,new Actions.AtAge(20),2)),PROFILE,
                new Transforms.Rotate(Vec3.Y,new ScalarCurve(List.of(new ScalarCurve.Key(0,0),new ScalarCurve.Key(100,4))),Vec3.ZERO));
        var restored=codec.decode(codec.encode(storm),Actions.ChildProgram.class);
        assertEquals(storm.totalMembersBound(),restored.totalMembersBound());
        var billboard=new Presentation.Billboard("test:circle",2,-1,ScalarCurve.constant(1));
        var emitter=new Actions.CreateEmitter(new Actions.GroupPose(new ProgramRef.Group(new NodeId(3))),
                new ScalarCurve(List.of(new ScalarCurve.Key(0,0),new ScalarCurve.Key(100,3))),Vec3.Y,0,1,80,child,Actions.Binding.snapshot(),billboard);
        assertEquals(emitter,codec.decode(codec.encode(emitter),Actions.CreateEmitter.class));
        close(emitter.nozzle(new Actions.Pose(Frame.IDENTITY,Vec3.ZERO),20).frame().forward(),Vec3.Z.rotate(Vec3.Y,.6));
    }
}
