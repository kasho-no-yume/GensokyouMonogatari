package com.bitsson.gensokyou.danmaku.program.gameplay;

import com.bitsson.gensokyou.danmaku.program.*;
import com.bitsson.gensokyou.danmaku.program.math.*;
import com.bitsson.gensokyou.danmaku.program.ops.*;
import com.bitsson.gensokyou.danmaku.program.server.*;
import java.util.*;

/** Small, bounded authority fixtures. These are previews, not migrated BOSS cards. */
public final class ProgramPreviewPrograms {
    public static final int WARNING_TICKS=20, LIFETIME_TICKS=100, SHOT_INTERVAL=40, SHOTS=5;
    public static final double SPEED=.2, TARGET_RADIUS=64;
    public static ProgramLimits limits() { return new ProgramLimits(4096,8,512,4096,256,262144); }
    public record Installed(ProgramDefinition definition,ProgramExecution execution) {}
    private ProgramPreviewPrograms() {}
    private static Presentation.Profile damagingFor(long lifetime) {
        return new Presentation.Profile(List.of(new Presentation.Window(TimeDomain.MEMBER_AGE,0,lifetime)),null,null,List.of());
    }
    /** Shared definition validation vocabulary; clients install no authority handlers. */
    public static OperationRegistry registry() {
        var codec=ServerControlOperations.dataRegistry().freeze();
        var registry=new OperationRegistry();
        registry.register(ServerControlOperations.type("capture",ServerControlOperations.Capture.class,codec));
        registry.register(ServerControlOperations.type("group",ServerControlOperations.Group.class,codec));
        registry.register(ServerControlOperations.type("fire",ServerControlOperations.Fire.class,codec));
        registry.register(ServerControlOperations.type("control",ServerControlOperations.Control.class,codec));
        registry.register(ServerControlOperations.type("offset_group",ServerControlOperations.OffsetGroup.class,codec));
        registry.register(ServerControlOperations.type("corridor",ServerControlOperations.Corridor.class,codec));
        registry.register(ServerControlOperations.type("rebase_target",ServerControlOperations.RebaseTarget.class,codec));
        registry.register(ServerControlOperations.type("radial_release",ServerControlOperations.RadialRelease.class,codec));
        registry.register(ServerControlOperations.type("emitter",ServerControlOperations.Emitter.class,codec));
        registry.register(ServerControlOperations.type("bind_source",ServerControlOperations.BindSource.class,codec));
        return registry.freeze();
    }

    public static Installed blue1Slice() {
        return blue1Slice("sphere");
    }
    /** Orthogonal transform probe: rotate and breathe the local flower, then move its center. */
    public static Installed compositeMotion() {
        return compositeMotion(false);
    }
    public static Installed compositeMotion(boolean partialRelease) {
        return compositeMotion(partialRelease,false);
    }
    public static Installed compositeMotion(boolean partialRelease,boolean wholePetals) {
        var codec=ServerControlOperations.dataRegistry().freeze();
        var capture=ServerControlOperations.type("capture",ServerControlOperations.Capture.class,codec);
        var group=ServerControlOperations.type("group",ServerControlOperations.Group.class,codec);
        var fire=ServerControlOperations.type("fire",ServerControlOperations.Fire.class,codec);
        var control=ServerControlOperations.type("control",ServerControlOperations.Control.class,codec);
        var release=ServerControlOperations.type("radial_release",ServerControlOperations.RadialRelease.class,codec);
        var execution=new ProgramExecution();
        ServerControlOperations.install(execution,capture); ServerControlOperations.install(execution,group);
        ServerControlOperations.install(execution,fire); ServerControlOperations.install(execution,control);
        ServerControlOperations.install(execution,release); execution.freeze();
        var rotation=new Transforms.Rotate(Vec3.Z,new ScalarCurve(List.of(new ScalarCurve.Key(0,0),new ScalarCurve.Key(240,Math.PI*2))),Vec3.ZERO);
        var travel=new Transforms.Translate(new Paths.Arc(Vec3.Z,Vec3.Y,.012,.10));
        var transform=new Transforms.Compose(List.of(new Transforms.Breathe(1,.3,80,0),rotation,travel));
        var flower=new Actions.ChildProgram(new Patterns.Union(List.of(
                new Patterns.Tagged(new Patterns.Point(Vec3.ZERO,Vec3.Z),Set.of("core"),new Pattern.Style("sphere","gold",.12,1,.3)),
                new Patterns.Tagged(new Patterns.Rose(5,9,2,1,0),Set.of("petals"),new Pattern.Style("sphere","blue",.12,1,.2)))),
                new Paths.Hold(),240,0,0,List.of(),damagingFor(240),transform);
        var b=new ProgramBuilder();
        var nodes=new ArrayList<ProgramNode>(List.of(
                b.operation(capture,new ServerControlOperations.Capture("targets",new AuthorityDescriptions.CaptureTargets(TARGET_RADIUS,1,true,true)),new TickSpan(0)),
                b.operation(group,new ServerControlOperations.Group("flower","targets",0,AuthorityDescriptions.TargetPoint.EYES,Vec3.ZERO,Vec3.Y,Vec3.Z,true,null),new TickSpan(0)),
                b.operation(fire,new ServerControlOperations.Fire("flower",flower,Actions.Binding.snapshot(),new Deterministic.Seed(103,List.of()),
                        new AuthorityDescriptions.Commit(new ProgramRef.Plan(new NodeId(0)),0,512,AuthorityDescriptions.Failure.REJECT_BATCH,true),20),new TickSpan(0))));
        if (partialRelease && !wholePetals) nodes.add(new AtNode(b.nextId(),TimeDomain.CARD,new TickSpan(120),b.operation(release,
                new ServerControlOperations.RadialRelease("flower",new Selection.Tag("core"),
                         new Selection.AllOf(List.of(new Selection.Tag("petals"),new Selection.IndexModulo(2,0))),.15),new TickSpan(0))));
        if (partialRelease && wholePetals) for (int petal:new int[]{0,2,4}) {
            // Rose uses nine angular samples per lobe, with the lobe center at 9*k.
            // Index zero is the core; Union assigns petal template indices 1..45.
            for (int sample=0;sample<45;sample++) if (Math.floorMod((sample+4)/9,5)==petal)
                nodes.add(new AtNode(b.nextId(),TimeDomain.CARD,new TickSpan(120),b.operation(release,
                        new ServerControlOperations.RadialRelease("flower",new Selection.Tag("core"),new Selection.IndexRange(sample+1,sample+2),.15),new TickSpan(0))));
        }
        nodes.add(new AtNode(b.nextId(),TimeDomain.CARD,new TickSpan(261),b.operation(control,new ServerControlOperations.Control("",Actions.LifecycleKind.END_ENCOUNTER,0,"preview-complete"),new TickSpan(0))));
        var root=new SequenceNode(b.nextId(),nodes);
        var result=ProgramCompiler.compile("gensokyou:preview_"+(wholePetals?"petal_release":partialRelease?"partial_release":"composite_motion"),1,new PlaybackPolicy.Once(TailPolicy.RETAIN_UNTIL_LIFETIME),root,limits(),registry());
        if (!result.success()) throw new IllegalStateException("Invalid motion preview: "+result.diagnostics());
        var definition=result.definition().orElseThrow(); execution.validate(definition); return new Installed(definition,execution);
    }
    /** Same two-generation graph, changing only EMIT/REPLACE lifetime semantics (7N versus 4N). */
    public static Installed splitGenerations(boolean replace) {
        return splitGenerations(replace,false);
    }
    public static Installed splitGenerations(boolean replace,boolean spherical) {
        var codec=ServerControlOperations.dataRegistry().freeze();
        var capture=ServerControlOperations.type("capture",ServerControlOperations.Capture.class,codec);
        var group=ServerControlOperations.type("group",ServerControlOperations.Group.class,codec);
        var fire=ServerControlOperations.type("fire",ServerControlOperations.Fire.class,codec);
        var control=ServerControlOperations.type("control",ServerControlOperations.Control.class,codec);
        var execution=new ProgramExecution();
        ServerControlOperations.install(execution,capture); ServerControlOperations.install(execution,group);
        ServerControlOperations.install(execution,fire); ServerControlOperations.install(execution,control); execution.freeze();
        var kind=replace?Actions.ChildActionKind.REPLACE:Actions.ChildActionKind.EMIT;
        var descendants=new Actions.ChildProgram(new Patterns.Tagged(spherical?new Patterns.SphereDirections(2,Patterns.SphereDistribution.SEEDED,1):new Patterns.ConeScatter(2,.35,1),Set.of("generation2"),
                new Pattern.Style("sphere","gold",.12,1,.2)),new Paths.Linear(Vec3.Z,.16),160,0,0,List.of(),
                damagingFor(160),new Transforms.Compose(List.of()),Actions.DirectionRule.TEMPLATE,0);
        Path slowed=new Paths.Segments(List.of(new Paths.Segment("decelerate",35,new Paths.SpeedCurve(Vec3.Z,new ScalarCurve(List.of(new ScalarCurve.Key(0,.16),new ScalarCurve.Key(35,0)))))),Paths.Tail.HOLD);
        Actions.Trigger trigger=spherical?new Actions.AtPathSegmentEnd("decelerate"):new Actions.AtAge(35);
        var secondAction=new Actions.ChildAction(kind,new Selection.Tag("generation1"),trigger,1,1,
                descendants,Actions.Binding.snapshot(),Actions.AtomicScope.ALL_SELECTED_PARENTS,spherical?Actions.OnReject.END_PARENT:Actions.OnReject.KEEP_PARENT,ScalarCurve.constant(0),Vec3.Y);
        var children=new Actions.ChildProgram(new Patterns.Tagged(spherical?new Patterns.SphereDirections(2,Patterns.SphereDistribution.SEEDED,1):new Patterns.ConeScatter(2,.45,1),Set.of("generation1"),
                new Pattern.Style("sphere","red",.12,1,.22)),spherical?slowed:new Paths.Linear(Vec3.Z,.12),160,1,4,List.of(secondAction),
                damagingFor(160),new Transforms.Compose(List.of()),Actions.DirectionRule.TEMPLATE,0);
        var firstAction=new Actions.ChildAction(kind,new Selection.Tag("generation0"),trigger,1,1,
                children,Actions.Binding.snapshot(),Actions.AtomicScope.ALL_SELECTED_PARENTS,spherical?Actions.OnReject.END_PARENT:Actions.OnReject.KEEP_PARENT,ScalarCurve.constant(0),Vec3.Y);
        var parents=new Actions.ChildProgram(new Patterns.Tagged(spherical?new Patterns.SphereSurface(6,2,Patterns.SphereDistribution.FIBONACCI,Patterns.Direction.OUTWARD,Vec3.Z,1):new Patterns.ConeScatter(6,.45,1),Set.of("generation0"),
                new Pattern.Style("sphere","blue",.12,1,.25)),spherical?slowed:new Paths.Linear(Vec3.Z,.10),160,2,36,List.of(firstAction),
                damagingFor(160),new Transforms.Compose(List.of()),Actions.DirectionRule.TEMPLATE,0);
        var b=new ProgramBuilder();
        var policy=new AuthorityDescriptions.Commit(new ProgramRef.Plan(new NodeId(0)),0,512,AuthorityDescriptions.Failure.REJECT_BATCH,true);
        var root=new SequenceNode(b.nextId(),List.of(
                b.operation(capture,new ServerControlOperations.Capture("targets",new AuthorityDescriptions.CaptureTargets(TARGET_RADIUS,1,true,true)),new TickSpan(0)),
                b.operation(group,new ServerControlOperations.Group("split","targets",0,AuthorityDescriptions.TargetPoint.EYES,Vec3.ZERO,Vec3.Y,Vec3.Z,true,null),new TickSpan(0)),
                b.operation(fire,new ServerControlOperations.Fire("split",parents,Actions.Binding.snapshot(),new Deterministic.Seed(101,List.of()),policy,20),new TickSpan(0)),
                new AtNode(b.nextId(),TimeDomain.CARD,new TickSpan(251),b.operation(control,new ServerControlOperations.Control("",Actions.LifecycleKind.END_ENCOUNTER,0,"preview-complete"),new TickSpan(0)))));
        var result=ProgramCompiler.compile("gensokyou:preview_"+(spherical?"sphere_split_":"split_")+(replace?"replace":"emit"),1,new PlaybackPolicy.Once(TailPolicy.RETAIN_UNTIL_LIFETIME),root,limits(),registry());
        if (!result.success()) throw new IllegalStateException("Invalid split preview: "+result.diagnostics());
        var definition=result.definition().orElseThrow(); execution.validate(definition); return new Installed(definition,execution);
    }
    /** Two manifest flower cycles; all members keep their original 400-tick lifetime. */
    public static Installed flowerSea() {
        var codec=ServerControlOperations.dataRegistry().freeze();
        var capture=ServerControlOperations.type("capture",ServerControlOperations.Capture.class,codec);
        var group=ServerControlOperations.type("group",ServerControlOperations.Group.class,codec);
        var fire=ServerControlOperations.type("fire",ServerControlOperations.Fire.class,codec);
        var release=ServerControlOperations.type("radial_release",ServerControlOperations.RadialRelease.class,codec);
        var rebase=ServerControlOperations.type("rebase_target",ServerControlOperations.RebaseTarget.class,codec);
        var control=ServerControlOperations.type("control",ServerControlOperations.Control.class,codec);
        var execution=new ProgramExecution();
        ServerControlOperations.install(execution,capture); ServerControlOperations.install(execution,group);
        ServerControlOperations.install(execution,fire); ServerControlOperations.install(execution,release);
        ServerControlOperations.install(execution,rebase); ServerControlOperations.install(execution,control); execution.freeze();
        var b=new ProgramBuilder(); var nodes=new ArrayList<ProgramNode>();
        var policy=new AuthorityDescriptions.Commit(new ProgramRef.Plan(new NodeId(0)),0,512,AuthorityDescriptions.Failure.REJECT_BATCH,true);
        var seed=new Deterministic.Seed(73,List.of());
        for (int cycle=0;cycle<2;cycle++) for (int flower=0;flower<16;flower++) {
            int ordinal=cycle*16+flower,birth=cycle*240+flower*4;
            String name="flower_"+ordinal,targets="flower_targets_"+ordinal;
            Vec3 axis=Deterministic.seedDirection(seed,3,ordinal,Deterministic.Distribution.UNIFORM_SPHERE,Vec3.Z,Math.PI);
            Vec3 plane=Deterministic.seedDirection(seed,4,ordinal,Deterministic.Distribution.UNIFORM_SPHERE,Vec3.Z,Math.PI);
            var rotation=new Transforms.Rotate(plane,ScalarCurve.constant(seed.unit(5,ordinal)*Deterministic.TAU),Vec3.ZERO);
            var pattern=new Patterns.Union(List.of(
                    new Patterns.Tagged(new Patterns.Point(Vec3.ZERO,Vec3.Z),Set.of("core"),new Pattern.Style("sphere","gold",.55,4,.55)),
                    new Patterns.Tagged(new Patterns.Rose(5,9,2,1,0),Set.of("petals"),new Pattern.Style("sphere","red",.275,1,.275))));
            var speed=new ScalarCurve(List.of(new ScalarCurve.Key(0,.24),new ScalarCurve.Key(60,0)));
            var transform=new Transforms.Compose(List.of(rotation,new Transforms.Translate(new Paths.SpeedCurve(axis,speed))));
            var members=new Actions.ChildProgram(pattern,new Paths.Hold(),400,0,0,List.of(),
                    new Presentation.Profile(List.of(new Presentation.Window(TimeDomain.MEMBER_AGE,100,400)),null,null,List.of()),transform);
            nodes.add(new AtNode(b.nextId(),TimeDomain.CARD,new TickSpan(birth),new SequenceNode(b.nextId(),List.of(
                    b.operation(capture,new ServerControlOperations.Capture(targets,new AuthorityDescriptions.CaptureTargets(TARGET_RADIUS,5,true,true)),new TickSpan(0)),
                    b.operation(group,new ServerControlOperations.Group(name,targets,-1,AuthorityDescriptions.TargetPoint.EYES,
                            Vec3.ZERO,Vec3.Y,Vec3.Z,true,null),new TickSpan(0)),
                    b.operation(fire,new ServerControlOperations.Fire(name,members,Actions.Binding.snapshot(),seed.child(ordinal),policy,0),new TickSpan(0))))));
            nodes.add(new AtNode(b.nextId(),TimeDomain.CARD,new TickSpan(birth+100),new SequenceNode(b.nextId(),List.of(
                    b.operation(release,new ServerControlOperations.RadialRelease(name,new Selection.Tag("core"),new Selection.Tag("petals"),.2),new TickSpan(0)),
                    b.operation(rebase,new ServerControlOperations.RebaseTarget(name,-1,AuthorityDescriptions.TargetPoint.EYES,new Selection.Tag("core"),.3),new TickSpan(0))))));
        }
        nodes.add(new AtNode(b.nextId(),TimeDomain.CARD,new TickSpan(701),b.operation(control,
                new ServerControlOperations.Control("",Actions.LifecycleKind.END_ENCOUNTER,0,"preview-complete"),new TickSpan(0))));
        var compiled=ProgramCompiler.compile("gensokyou:preview_flower_sea",1,new PlaybackPolicy.Once(TailPolicy.RETAIN_UNTIL_LIFETIME),
                new SequenceNode(b.nextId(),nodes),limits(),registry());
        if (!compiled.success()) throw new IllegalStateException("Invalid flower sea: "+compiled.diagnostics());
        execution.validate(compiled.definition().orElseThrow()); return new Installed(compiled.definition().orElseThrow(),execution);
    }
    /** Two complete 200-tick carrier cycles, followed by the last descendant's lifetime. */
    public static Installed tailPearls() {
        var codec=ServerControlOperations.dataRegistry().freeze();
        var capture=ServerControlOperations.type("capture",ServerControlOperations.Capture.class,codec);
        var group=ServerControlOperations.type("group",ServerControlOperations.Group.class,codec);
        var fire=ServerControlOperations.type("fire",ServerControlOperations.Fire.class,codec);
        var control=ServerControlOperations.type("control",ServerControlOperations.Control.class,codec);
        var execution=new ProgramExecution();
        ServerControlOperations.install(execution,capture); ServerControlOperations.install(execution,group);
        ServerControlOperations.install(execution,fire); ServerControlOperations.install(execution,control); execution.freeze();
        var drift=new Paths.Segments(List.of(new Paths.Segment("hold",40,new Paths.Hold()),
                new Paths.Segment("drift",360,new Paths.Linear(Vec3.Z,.06))),Paths.Tail.HOLD);
        var pearl=new Actions.ChildProgram(new Patterns.Tagged(new Patterns.Point(Vec3.ZERO,Vec3.Z),Set.of("pearl"),
                new Pattern.Style("sphere","gold",.275,1,.275)),drift,400,0,0,List.of(),
                new Presentation.Profile(List.of(new Presentation.Window(TimeDomain.MEMBER_AGE,0,400)),null,null,List.of()),
                new Transforms.Compose(List.of()),Actions.DirectionRule.SEEDED_SPHERE,17);
        var action=new Actions.ChildAction(Actions.ChildActionKind.EMIT,new Selection.Tag("carrier"),new Actions.AtAge(4),4,24,
                pearl,Actions.Binding.snapshot(),Actions.AtomicScope.PER_PARENT,Actions.OnReject.KEEP_PARENT,ScalarCurve.constant(0),Vec3.Y);
        var carrier=new Actions.ChildProgram(new Patterns.Tagged(new Patterns.ConeScatter(6,Math.toRadians(30),1),Set.of("carrier"),
                new Pattern.Style("sphere","blue",.275,1,.275)),new Paths.Linear(Vec3.Z,.70),100,1,144,List.of(action),
                new Presentation.Profile(List.of(new Presentation.Window(TimeDomain.MEMBER_AGE,0,100)),null,null,List.of()),
                new Transforms.Compose(List.of()),Actions.DirectionRule.TEMPLATE,0);
        var policy=new AuthorityDescriptions.Commit(new ProgramRef.Plan(new NodeId(0)),0,512,AuthorityDescriptions.Failure.REJECT_BATCH,true);
        var b=new ProgramBuilder(); var nodes=new ArrayList<ProgramNode>();
        for (int tick=0;tick<400;tick+=20) {
            String name="carriers_"+tick,targets="targets_"+tick;
            nodes.add(new AtNode(b.nextId(),TimeDomain.CARD,new TickSpan(tick),new SequenceNode(b.nextId(),List.of(
                    b.operation(capture,new ServerControlOperations.Capture(targets,new AuthorityDescriptions.CaptureTargets(TARGET_RADIUS,1,true,true)),new TickSpan(0)),
                    b.operation(group,new ServerControlOperations.Group(name,targets,0,AuthorityDescriptions.TargetPoint.EYES,
                            new Vec3(0,1,0),Vec3.Y,Vec3.Z,true,null),new TickSpan(0)),
                    b.operation(fire,new ServerControlOperations.Fire(name,carrier,Actions.Binding.snapshot(),new Deterministic.Seed(53,List.of()),policy,0),new TickSpan(0))))));
        }
        nodes.add(new AtNode(b.nextId(),TimeDomain.CARD,new TickSpan(880),b.operation(control,
                new ServerControlOperations.Control("",Actions.LifecycleKind.END_ENCOUNTER,0,"preview-complete"),new TickSpan(0))));
        var compiled=ProgramCompiler.compile("gensokyou:preview_tail_pearls",1,new PlaybackPolicy.Once(TailPolicy.RETAIN_UNTIL_LIFETIME),
                new SequenceNode(b.nextId(),nodes),limits(),registry());
        if (!compiled.success()) throw new IllegalStateException("Invalid tail pearls: "+compiled.diagnostics());
        execution.validate(compiled.definition().orElseThrow()); return new Installed(compiled.definition().orElseThrow(),execution);
    }
    /** One full 240-tick manifest cycle: three captures, fixed slots, four alternating shots. */
    public static Installed blue1(String projectile) {
        if (!Set.of("sphere","star_prism").contains(projectile)) throw new IllegalArgumentException("unknown preview style");
        var codec=ServerControlOperations.dataRegistry().freeze();
        var capture=ServerControlOperations.type("capture",ServerControlOperations.Capture.class,codec);
        var group=ServerControlOperations.type("group",ServerControlOperations.Group.class,codec);
        var fire=ServerControlOperations.type("fire",ServerControlOperations.Fire.class,codec);
        var control=ServerControlOperations.type("control",ServerControlOperations.Control.class,codec);
        var execution=new ProgramExecution();
        ServerControlOperations.install(execution,capture); ServerControlOperations.install(execution,group);
        ServerControlOperations.install(execution,fire); ServerControlOperations.install(execution,control); execution.freeze();
        var builder=new ProgramBuilder();
        Map<Integer,List<ProgramNode>> timeline=new TreeMap<>();
        var policy=new AuthorityDescriptions.Commit(new ProgramRef.Plan(new NodeId(0)),6,512,AuthorityDescriptions.Failure.CANCEL_GROUP,true);
        var members=new Actions.ChildProgram(new Patterns.Tagged(new Patterns.ParallelGrid(3,3,1.5,1.5,Set.of(),Vec3.ZERO),
                Set.of("blue1"),new Pattern.Style(projectile,"blue",.2,1,.2)),new Paths.Linear(Vec3.Z,.30),120,0,0,List.of(),
                new Presentation.Profile(List.of(new Presentation.Window(TimeDomain.MEMBER_AGE,0,120)),null,null,List.of()),new Transforms.Compose(List.of()));
        for (int base:new int[]{0,60,120}) {
            List<ProgramNode> shots=new ArrayList<>();
            String targets="targets_"+base;
            timeline.computeIfAbsent(base,k->new ArrayList<>()).add(builder.operation(capture,
                    new ServerControlOperations.Capture(targets,new AuthorityDescriptions.CaptureTargets(TARGET_RADIUS,5,true,true)),new TickSpan(0)));
            for (int slot=0;slot<5;slot++) for (int side:new int[]{-1,1}) {
                String name="grid_"+base+"_"+slot+"_"+side;
                timeline.get(base).add(builder.operation(group,new ServerControlOperations.Group(name,targets,slot,
                        AuthorityDescriptions.TargetPoint.EYES,new Vec3(side*2,1,0),Vec3.Y,Vec3.Z,false,null),new TickSpan(0)));
                timeline.get(base).add(builder.operation(control,new ServerControlOperations.Control(name,
                        Actions.LifecycleKind.LOCK_BOSS_MOVEMENT,164-base,"blue1-lock"),new TickSpan(0)));
                for (int shot=0;shot<2;shot++) if ((shot%2==0?-1:1)==side)
                    shots.add(new OffsetNode(builder.nextId(),new TickSpan(shot*8),builder.operation(fire,
                            new ServerControlOperations.Fire(name,members,Actions.Binding.snapshot(),new Deterministic.Seed(23,List.of()),policy,20),new TickSpan(0))));
                timeline.computeIfAbsent(220,k->new ArrayList<>()).add(builder.operation(control,
                        new ServerControlOperations.Control(name,Actions.LifecycleKind.COLLAPSE,10,"blue1-tail"),new TickSpan(0)));
            }
            timeline.get(base).add(new RepeatNode(builder.nextId(),2,new TickSpan(16),new ParallelNode(builder.nextId(),shots)));
        }
        timeline.computeIfAbsent(240,k->new ArrayList<>()).add(builder.operation(control,
                new ServerControlOperations.Control("",Actions.LifecycleKind.END_ENCOUNTER,0,"preview-complete"),new TickSpan(0)));
        List<ProgramNode> nodes=new ArrayList<>();
        for (var entry:timeline.entrySet()) {
            nodes.add(new AtNode(builder.nextId(),TimeDomain.CARD,new TickSpan(entry.getKey()),new SequenceNode(builder.nextId(),entry.getValue())));
        }
        var root=new SequenceNode(builder.nextId(),nodes);
        var compiled=ProgramCompiler.compile("gensokyou:preview_blue1",1,new PlaybackPolicy.Once(TailPolicy.RETAIN_UNTIL_LIFETIME),root,limits(),registry());
        if (!compiled.success()) throw new IllegalStateException("Invalid Blue1: "+compiled.diagnostics());
        execution.validate(compiled.definition().orElseThrow());
        return new Installed(compiled.definition().orElseThrow(),execution);
    }
    public static Installed blue6(String projectile) {
        var codec=ServerControlOperations.dataRegistry().freeze();
        var capture=ServerControlOperations.type("capture",ServerControlOperations.Capture.class,codec);
        var group=ServerControlOperations.type("group",ServerControlOperations.Group.class,codec);
        var fire=ServerControlOperations.type("fire",ServerControlOperations.Fire.class,codec);
        var control=ServerControlOperations.type("control",ServerControlOperations.Control.class,codec);
        var offset=ServerControlOperations.type("offset_group",ServerControlOperations.OffsetGroup.class,codec);
        var execution=new ProgramExecution();
        ServerControlOperations.install(execution,capture); ServerControlOperations.install(execution,group);
        ServerControlOperations.install(execution,fire); ServerControlOperations.install(execution,control);
        ServerControlOperations.install(execution,offset); execution.freeze();
        var b=new ProgramBuilder(); List<ProgramNode> nodes=new ArrayList<>(),timed=new ArrayList<>();
        nodes.add(b.operation(capture,new ServerControlOperations.Capture("targets",new AuthorityDescriptions.CaptureTargets(TARGET_RADIUS,1,true,true)),new TickSpan(0)));
        nodes.add(b.operation(group,new ServerControlOperations.Group("frame","targets",0,AuthorityDescriptions.TargetPoint.EYES,
                new Vec3(0,1,0),Vec3.Y,Vec3.Z,false,null),new TickSpan(0)));
        var policy=new AuthorityDescriptions.Commit(new ProgramRef.Plan(new NodeId(0)),6,512,AuthorityDescriptions.Failure.CANCEL_GROUP,true);
        for (int side:new int[]{-1,1}) {
            String name="panel_"+side;
            nodes.add(b.operation(offset,new ServerControlOperations.OffsetGroup(name,"frame",Vec3.X.multiply(side*4)),new TickSpan(0)));
            nodes.add(b.operation(control,new ServerControlOperations.Control(name,Actions.LifecycleKind.LOCK_BOSS_MOVEMENT,104,"blue6-lock"),new TickSpan(0)));
            double angle=side*Math.toRadians(25); Vec3 initial=Vec3.Z.rotate(Vec3.Y,angle);
            var path=new Paths.Segments(List.of(new Paths.Segment("out",30,new Paths.Linear(initial,.2)),
                    new Paths.Segment("turn",30,new Paths.Arc(initial,Vec3.Y,-angle/30,.2)),
                    new Paths.Segment("return",1,new Paths.Linear(Vec3.Z,.2))),Paths.Tail.CONTINUE);
            var members=new Actions.ChildProgram(new Patterns.Tagged(new Patterns.ParallelGrid(4,3,1.5,2,Set.of(),Vec3.ZERO),
                    Set.of("blue6"),new Pattern.Style(projectile,side<0?"blue":"gold",.2,1,.2)),path,120,0,0,List.of(),
                    new Presentation.Profile(List.of(new Presentation.Window(TimeDomain.MEMBER_AGE,0,120)),null,null,List.of()),new Transforms.Compose(List.of()));
            int prepare=side<0?0:12;
            var shot=b.operation(fire,new ServerControlOperations.Fire(name,members,Actions.Binding.snapshot(),new Deterministic.Seed(23,List.of()),policy,20),new TickSpan(0));
            timed.add(new AtNode(b.nextId(),TimeDomain.CARD,new TickSpan(prepare),new RepeatNode(b.nextId(),4,new TickSpan(24),shot)));
            timed.add(new AtNode(b.nextId(),TimeDomain.CARD,new TickSpan(220),b.operation(control,
                    new ServerControlOperations.Control(name,Actions.LifecycleKind.COLLAPSE,10,"blue6-tail"),new TickSpan(0))));
        }
        nodes.addAll(timed);
        nodes.add(new AtNode(b.nextId(),TimeDomain.CARD,new TickSpan(240),b.operation(control,
                new ServerControlOperations.Control("",Actions.LifecycleKind.END_ENCOUNTER,0,"preview-complete"),new TickSpan(0))));
        var compiled=ProgramCompiler.compile("gensokyou:preview_blue6",1,new PlaybackPolicy.Once(TailPolicy.RETAIN_UNTIL_LIFETIME),
                new SequenceNode(b.nextId(),nodes),limits(),registry());
        if (!compiled.success()) throw new IllegalStateException("Invalid Blue6: "+compiled.diagnostics());
        execution.validate(compiled.definition().orElseThrow()); return new Installed(compiled.definition().orElseThrow(),execution);
    }
    public static Installed blue4(String projectile) {
        var codec=ServerControlOperations.dataRegistry().freeze();
        var capture=ServerControlOperations.type("capture",ServerControlOperations.Capture.class,codec);
        var group=ServerControlOperations.type("group",ServerControlOperations.Group.class,codec);
        var offset=ServerControlOperations.type("offset_group",ServerControlOperations.OffsetGroup.class,codec);
        var fire=ServerControlOperations.type("fire",ServerControlOperations.Fire.class,codec);
        var control=ServerControlOperations.type("control",ServerControlOperations.Control.class,codec);
        var corridor=ServerControlOperations.type("corridor",ServerControlOperations.Corridor.class,codec);
        var execution=new ProgramExecution();
        ServerControlOperations.install(execution,capture); ServerControlOperations.install(execution,group);
        ServerControlOperations.install(execution,offset); ServerControlOperations.install(execution,fire);
        ServerControlOperations.install(execution,control); ServerControlOperations.install(execution,corridor); execution.freeze();
        var b=new ProgramBuilder(); List<ProgramNode> nodes=new ArrayList<>();
        var policy=new AuthorityDescriptions.Commit(new ProgramRef.Plan(new NodeId(0)),6,512,AuthorityDescriptions.Failure.CANCEL_GROUP,true);
        for (int base:new int[]{0,100}) {
            String targets="targets_"+base,frame="frame_"+base,balls="balls_"+base,lasers="lasers_"+base;
            List<ProgramNode> setup=new ArrayList<>();
            setup.add(b.operation(capture,new ServerControlOperations.Capture(targets,new AuthorityDescriptions.CaptureTargets(TARGET_RADIUS,1,true,true)),new TickSpan(0)));
            setup.add(b.operation(group,new ServerControlOperations.Group(frame,targets,0,AuthorityDescriptions.TargetPoint.EYES,new Vec3(0,1,0),Vec3.Y,Vec3.Z,false,null),new TickSpan(0)));
            setup.add(b.operation(offset,new ServerControlOperations.OffsetGroup(balls,frame,Vec3.ZERO),new TickSpan(0)));
            setup.add(b.operation(offset,new ServerControlOperations.OffsetGroup(lasers,frame,Vec3.ZERO),new TickSpan(0)));
            setup.add(b.operation(control,new ServerControlOperations.Control(frame,Actions.LifecycleKind.LOCK_BOSS_MOVEMENT,189-base,"blue4-lock"),new TickSpan(0)));
            var ballProgram=new Actions.ChildProgram(new Patterns.Tagged(new Patterns.ParallelGrid(3,3,1.5,1.5,Set.of(),Vec3.ZERO),Set.of("blue4-ball"),
                    new Pattern.Style(projectile,"blue",.2,1,.2)),new Paths.Linear(Vec3.Z,.3),70,0,0,List.of(),
                    new Presentation.Profile(List.of(new Presentation.Window(TimeDomain.MEMBER_AGE,0,70)),null,null,List.of()),new Transforms.Compose(List.of()));
            setup.add(new RepeatNode(b.nextId(),3,new TickSpan(8),b.operation(fire,new ServerControlOperations.Fire(balls,ballProgram,Actions.Binding.snapshot(),new Deterministic.Seed(23,List.of()),policy,20),new TickSpan(0))));
            nodes.add(new AtNode(b.nextId(),TimeDomain.CARD,new TickSpan(base),new SequenceNode(b.nextId(),setup)));
            var laserProgram=new Actions.ChildProgram(new Patterns.Tagged(new Patterns.AxisNodes(Vec3.Z,List.of(6D,10D),List.of(Vec3.X,Vec3.X.multiply(-1)),1.5),
                    Set.of("blue4-laser"),new Pattern.Style("laser","red",.18,1,.18)),new Paths.Hold(),20,0,0,List.of(),
                    new Presentation.Profile(List.of(new Presentation.Window(TimeDomain.MEMBER_AGE,0,20)),null,null,List.of()),new Transforms.Compose(List.of()),
                    Actions.DirectionRule.TEMPLATE,0,new CollisionProfile.Laser(24,false,10));
            nodes.add(new AtNode(b.nextId(),TimeDomain.CARD,new TickSpan(base+40),b.operation(fire,
                    new ServerControlOperations.Fire(lasers,laserProgram,Actions.Binding.snapshot(),new Deterministic.Seed(23,List.of()),policy,30),new TickSpan(0))));
            nodes.add(new AtNode(b.nextId(),TimeDomain.CARD,new TickSpan(base+70),b.operation(corridor,
                    new ServerControlOperations.Corridor(lasers,new Vec3(0,0,4),new Vec3(0,0,12),1.32,4096,true,true),new TickSpan(0))));
            for (String name:List.of(balls,lasers,frame)) nodes.add(new AtNode(b.nextId(),TimeDomain.CARD,new TickSpan(220),b.operation(control,
                    new ServerControlOperations.Control(name,Actions.LifecycleKind.COLLAPSE,10,"blue4-tail"),new TickSpan(0))));
        }
        nodes.add(new AtNode(b.nextId(),TimeDomain.CARD,new TickSpan(240),b.operation(control,
                new ServerControlOperations.Control("",Actions.LifecycleKind.END_ENCOUNTER,0,"preview-complete"),new TickSpan(0))));
        var compiled=ProgramCompiler.compile("gensokyou:preview_blue4",1,new PlaybackPolicy.Once(TailPolicy.RETAIN_UNTIL_LIFETIME),new SequenceNode(b.nextId(),nodes),limits(),registry());
        if (!compiled.success()) throw new IllegalStateException("Invalid Blue4: "+compiled.diagnostics());
        execution.validate(compiled.definition().orElseThrow()); return new Installed(compiled.definition().orElseThrow(),execution);
    }
    public static Installed blue1Slice(String projectile) {
        if (!Set.of("sphere","star_prism").contains(projectile)) throw new IllegalArgumentException("unknown preview style");
        var codec=ServerControlOperations.dataRegistry().freeze();
        var capture=ServerControlOperations.type("capture",ServerControlOperations.Capture.class,codec);
        var group=ServerControlOperations.type("group",ServerControlOperations.Group.class,codec);
        var fire=ServerControlOperations.type("fire",ServerControlOperations.Fire.class,codec);
        var control=ServerControlOperations.type("control",ServerControlOperations.Control.class,codec);
        var registry=new OperationRegistry();
        registry.register(capture); registry.register(group); registry.register(fire); registry.register(control); registry.freeze();
        var execution=new ProgramExecution();
        ServerControlOperations.install(execution,capture); ServerControlOperations.install(execution,group);
        ServerControlOperations.install(execution,fire); ServerControlOperations.install(execution,control); execution.freeze();
        var pattern=new Patterns.Tagged(new Patterns.ParallelGrid(3,7,1.5,1.5,Set.of(2),Vec3.ZERO),
                Set.of("blue1-slice"),new Pattern.Style(projectile,"blue",.1,1,.2));
        var members=new Actions.ChildProgram(pattern,new Paths.Linear(Vec3.Z,SPEED),LIFETIME_TICKS,0,0,List.of(),
                new Presentation.Profile(List.of(new Presentation.Window(TimeDomain.MEMBER_AGE,0,LIFETIME_TICKS)),null,null,List.of()),
                new Transforms.Compose(List.of()));
        var policy=new AuthorityDescriptions.Commit(new ProgramRef.Plan(new NodeId(0)),2,512,
                AuthorityDescriptions.Failure.CANCEL_GROUP,true);
        var b=new ProgramBuilder();
        var shot=new SequenceNode(b.nextId(),List.of(
                b.operation(capture,new ServerControlOperations.Capture("targets",new AuthorityDescriptions.CaptureTargets(TARGET_RADIUS,1,true,true)),new TickSpan(0)),
                b.operation(group,new ServerControlOperations.Group("grid","targets",0,AuthorityDescriptions.TargetPoint.EYES,
                        new Vec3(0,1,0),Vec3.Y,Vec3.Z,false,null),new TickSpan(0)),
                b.operation(fire,new ServerControlOperations.Fire("grid",members,Actions.Binding.snapshot(),
                        new Deterministic.Seed(23,List.of()),policy,WARNING_TICKS),new TickSpan(SHOT_INTERVAL))));
        var repeat=new RepeatNode(b.nextId(),SHOTS,new TickSpan(SHOT_INTERVAL),shot);
        // The last wave is allowed to finish before an explicit encounter end releases every handle.
        var root=new SequenceNode(b.nextId(),List.of(repeat,new WaitNode(b.nextId(),new TickSpan(WARNING_TICKS+LIFETIME_TICKS)),
                b.operation(control,new ServerControlOperations.Control("",Actions.LifecycleKind.END_ENCOUNTER,0,"preview-complete"),new TickSpan(0))));
        var result=ProgramCompiler.compile("gensokyou:preview_blue1_slice",1,new PlaybackPolicy.Once(TailPolicy.RETAIN_UNTIL_LIFETIME),root,ProgramLimits.defaults(),registry);
        if (!result.success()) throw new IllegalStateException("Invalid preview: "+result.diagnostics());
        var definition=result.definition().orElseThrow(); execution.validate(definition);
        return new Installed(definition,execution);
    }
}
