package com.bitsson.gensokyou.danmaku.program.gameplay;

import com.bitsson.gensokyou.danmaku.program.*;
import com.bitsson.gensokyou.danmaku.program.math.*;
import com.bitsson.gensokyou.danmaku.program.ops.*;
import com.bitsson.gensokyou.danmaku.program.server.*;
import java.util.*;

/** Motion composition fixtures kept separate from the reference-card catalogue. */
public final class ProgramMotionPreviewPrograms {
    private ProgramMotionPreviewPrograms() {}
    public static ProgramPreviewPrograms.Installed movingCarriers() {
        var codec=ServerControlOperations.dataRegistry().freeze();
        var capture=ServerControlOperations.type("capture",ServerControlOperations.Capture.class,codec);
        var group=ServerControlOperations.type("group",ServerControlOperations.Group.class,codec);
        var fire=ServerControlOperations.type("fire",ServerControlOperations.Fire.class,codec);
        var control=ServerControlOperations.type("control",ServerControlOperations.Control.class,codec);
        var execution=new ProgramExecution();
        ServerControlOperations.install(execution,capture); ServerControlOperations.install(execution,group);
        ServerControlOperations.install(execution,fire); ServerControlOperations.install(execution,control); execution.freeze();
        var child=new Actions.ChildProgram(new Patterns.Tagged(new Patterns.SphereDirections(8,Patterns.SphereDistribution.SEEDED,11),Set.of("spray"),
                new Pattern.Style("sphere","gold",.1,1,.2)),new Paths.Linear(Vec3.Z,.12),100,0,0,List.of(),
                new Presentation.Profile(List.of(new Presentation.Window(TimeDomain.MEMBER_AGE,0,100)),null,null,List.of()),new Transforms.Compose(List.of()),Actions.DirectionRule.TEMPLATE,0);
        var action=new Actions.ChildAction(Actions.ChildActionKind.EMIT,new Selection.Tag("carrier"),new Actions.AtAge(20),20,4,child,
                Actions.Binding.snapshot(),Actions.AtomicScope.PER_PARENT,Actions.OnReject.KEEP_PARENT,ScalarCurve.constant(0),Vec3.Y);
        var carriers=new Actions.ChildProgram(new Patterns.Tagged(new Patterns.ConeScatter(3,.4,17),Set.of("carrier"),new Pattern.Style("sphere","blue",.15,1,.3)),
                new Paths.Linear(Vec3.Z,.1),81,1,96,List.of(action),new Presentation.Profile(List.of(new Presentation.Window(TimeDomain.MEMBER_AGE,0,81)),null,null,List.of()),
                new Transforms.Compose(List.of()),Actions.DirectionRule.TEMPLATE,0);
        var b=new ProgramBuilder();
        var root=new SequenceNode(b.nextId(),List.of(
                b.operation(capture,new ServerControlOperations.Capture("targets",new AuthorityDescriptions.CaptureTargets(64,1,true,true)),new TickSpan(0)),
                b.operation(group,new ServerControlOperations.Group("carriers","targets",0,AuthorityDescriptions.TargetPoint.EYES,Vec3.ZERO,Vec3.Y,Vec3.Z,true,null),new TickSpan(0)),
                b.operation(fire,new ServerControlOperations.Fire("carriers",carriers,Actions.Binding.snapshot(),new Deterministic.Seed(601,List.of()),
                        new AuthorityDescriptions.Commit(new ProgramRef.Plan(new NodeId(0)),0,512,AuthorityDescriptions.Failure.REJECT_BATCH,true),20),new TickSpan(0)),
                new AtNode(b.nextId(),TimeDomain.CARD,new TickSpan(201),b.operation(control,new ServerControlOperations.Control("",Actions.LifecycleKind.END_ENCOUNTER,0,"preview-complete"),new TickSpan(0)))));
        var result=ProgramCompiler.compile("gensokyou:preview_moving_carriers",1,new PlaybackPolicy.Once(TailPolicy.RETAIN_UNTIL_LIFETIME),root,ProgramPreviewPrograms.limits(),ProgramPreviewPrograms.registry());
        if (!result.success()) throw new IllegalStateException("Invalid carrier preview: "+result.diagnostics());
        var definition=result.definition().orElseThrow(); execution.validate(definition); return new ProgramPreviewPrograms.Installed(definition,execution);
    }
    /** Two simultaneous nozzles, each firing three independent snapshot bullets every integer tick. */
    public static ProgramPreviewPrograms.Installed highFrequencyCircle() {
        var codec=ServerControlOperations.dataRegistry().freeze();
        var capture=ServerControlOperations.type("capture",ServerControlOperations.Capture.class,codec);
        var group=ServerControlOperations.type("group",ServerControlOperations.Group.class,codec);
        var bind=ServerControlOperations.type("bind_source",ServerControlOperations.BindSource.class,codec);
        var emitter=ServerControlOperations.type("emitter",ServerControlOperations.Emitter.class,codec);
        var control=ServerControlOperations.type("control",ServerControlOperations.Control.class,codec);
        var execution=new ProgramExecution();
        ServerControlOperations.install(execution,capture); ServerControlOperations.install(execution,group);
        ServerControlOperations.install(execution,bind); ServerControlOperations.install(execution,emitter);
        ServerControlOperations.install(execution,control); execution.freeze();
        var b=new ProgramBuilder(); var nodes=new ArrayList<ProgramNode>();
        nodes.add(b.operation(capture,new ServerControlOperations.Capture("targets",new AuthorityDescriptions.CaptureTargets(64,1,true,true)),new TickSpan(0)));
        for (int side:new int[]{-1,1}) {
            String name="circle_"+side; var reference=new ProgramRef.Group(b.nextId());
            var angle=new ScalarCurve(List.of(new ScalarCurve.Key(0,side*-.5),new ScalarCurve.Key(100,side*.5)));
            var child=new Actions.ChildProgram(new Patterns.Tagged(new Patterns.ConeScatter(3,.1,1),Set.of("spray"),
                    new Pattern.Style("sphere",side<0?"blue":"gold",.1,1,.2)),new Paths.Linear(Vec3.Z,.18),100,0,0,List.of(),
                    new Presentation.Profile(List.of(new Presentation.Window(TimeDomain.MEMBER_AGE,0,100)),null,null,List.of()),
                    new Transforms.Compose(List.of()),Actions.DirectionRule.TEMPLATE,0);
            nodes.add(b.operation(group,new ServerControlOperations.Group(name,"targets",0,AuthorityDescriptions.TargetPoint.EYES,new Vec3(side*3,0,0),Vec3.Y,Vec3.Z,true,null),new TickSpan(0)));
            nodes.add(b.operation(bind,new ServerControlOperations.BindSource(name,reference,null),new TickSpan(0)));
            var description=new Actions.CreateEmitter(new Actions.GroupPose(reference),angle,Vec3.Y,20,1,80,child,Actions.Binding.snapshot(),
                    new Presentation.Billboard("gensokyou:textures/entity/laser_magic_circle.png",1,side<0?0x5599ff:0xffcc55,angle));
            nodes.add(b.operation(emitter,new ServerControlOperations.Emitter(name,description,new Deterministic.Seed(503+side,List.of()),
                    new AuthorityDescriptions.Commit(new ProgramRef.Plan(new NodeId(0)),0,512,AuthorityDescriptions.Failure.REJECT_BATCH,true)),new TickSpan(0)));
        }
        nodes.add(new AtNode(b.nextId(),TimeDomain.CARD,new TickSpan(200),b.operation(control,new ServerControlOperations.Control("",Actions.LifecycleKind.END_ENCOUNTER,0,"preview-complete"),new TickSpan(0))));
        var result=ProgramCompiler.compile("gensokyou:preview_high_frequency_circle",1,new PlaybackPolicy.Once(TailPolicy.RETAIN_UNTIL_LIFETIME),
                new SequenceNode(b.nextId(),nodes),ProgramPreviewPrograms.limits(),ProgramPreviewPrograms.registry());
        if (!result.success()) throw new IllegalStateException("Invalid emitter preview: "+result.diagnostics());
        var definition=result.definition().orElseThrow(); execution.validate(definition); return new ProgramPreviewPrograms.Installed(definition,execution);
    }
    public static ProgramPreviewPrograms.Installed rotatingLaser() {
        return rotatingLaser(false);
    }
    public static ProgramPreviewPrograms.Installed rotatingLaser(boolean dynamicWarning) {
        var codec=ServerControlOperations.dataRegistry().freeze();
        var capture=ServerControlOperations.type("capture",ServerControlOperations.Capture.class,codec);
        var group=ServerControlOperations.type("group",ServerControlOperations.Group.class,codec);
        var fire=ServerControlOperations.type("fire",ServerControlOperations.Fire.class,codec);
        var control=ServerControlOperations.type("control",ServerControlOperations.Control.class,codec);
        var execution=new ProgramExecution();
        ServerControlOperations.install(execution,capture); ServerControlOperations.install(execution,group);
        ServerControlOperations.install(execution,fire); ServerControlOperations.install(execution,control); execution.freeze();
        var transform=new Transforms.Compose(List.of(
                new Transforms.Rotate(Vec3.Y,new ScalarCurve(List.of(new ScalarCurve.Key(0,0),new ScalarCurve.Key(180,Math.PI/2))),Vec3.ZERO),
                new Transforms.Orbit(Vec3.X.multiply(3),Vec3.Z,new ScalarCurve(List.of(new ScalarCurve.Key(0,0),new ScalarCurve.Key(180,Math.PI*2))))));
        var child=new Actions.ChildProgram(new Patterns.Tagged(new Patterns.Point(Vec3.ZERO,Vec3.Z),Set.of("beam"),new Pattern.Style("laser","blue",.18,1,.18)),
                new Paths.Hold(),180,0,0,List.of(),new Presentation.Profile(List.of(new Presentation.Window(TimeDomain.MEMBER_AGE,0,180)),null,null,List.of()),
                transform,Actions.DirectionRule.FIXED,0,new CollisionProfile.Laser(24,false,10));
        var b=new ProgramBuilder();
        var root=new SequenceNode(b.nextId(),List.of(
                b.operation(capture,new ServerControlOperations.Capture("targets",new AuthorityDescriptions.CaptureTargets(64,1,true,true)),new TickSpan(0)),
                 b.operation(group,new ServerControlOperations.Group("beam","targets",0,AuthorityDescriptions.TargetPoint.EYES,Vec3.ZERO,Vec3.Y,Vec3.Z,true,
                         dynamicWarning?new GroupMotion(Frame.along(Vec3.ZERO,Vec3.Z,Vec3.Y),new Paths.Linear(Vec3.X,.025),
                                 new Transforms.Rotate(Vec3.Y,new ScalarCurve(List.of(new ScalarCurve.Key(0,0),new ScalarCurve.Key(221,.8))),Vec3.ZERO),0,List.of()):null),new TickSpan(0)),
                 b.operation(fire,new ServerControlOperations.Fire("beam",child,dynamicWarning?new Actions.Binding(Actions.Attachment.FOLLOW_SOURCE,Actions.Velocity.ABSOLUTE,Actions.OnSourceLost.END):Actions.Binding.snapshot(),new Deterministic.Seed(401,List.of()),
                        new AuthorityDescriptions.Commit(new ProgramRef.Plan(new NodeId(0)),0,512,AuthorityDescriptions.Failure.REJECT_BATCH,true),40),new TickSpan(0)),
                new AtNode(b.nextId(),TimeDomain.CARD,new TickSpan(221),b.operation(control,new ServerControlOperations.Control("",Actions.LifecycleKind.END_ENCOUNTER,0,"preview-complete"),new TickSpan(0)))));
        var result=ProgramCompiler.compile("gensokyou:preview_"+(dynamicWarning?"dynamic_warning":"rotating_laser"),1,new PlaybackPolicy.Once(TailPolicy.RETAIN_UNTIL_LIFETIME),root,ProgramPreviewPrograms.limits(),ProgramPreviewPrograms.registry());
        if (!result.success()) throw new IllegalStateException("Invalid rotating laser: "+result.diagnostics());
        var definition=result.definition().orElseThrow(); execution.validate(definition);
        return new ProgramPreviewPrograms.Installed(definition,execution);
    }

    /** Shared local spiral spins and breathes while its center follows a world-frame arc. */
    public static ProgramPreviewPrograms.Installed movingTornado() {
        return movingTornado(false);
    }
    public static ProgramPreviewPrograms.Installed movingTornado(boolean roaming) {
        var codec=ServerControlOperations.dataRegistry().freeze();
        var capture=ServerControlOperations.type("capture",ServerControlOperations.Capture.class,codec);
        var group=ServerControlOperations.type("group",ServerControlOperations.Group.class,codec);
        var fire=ServerControlOperations.type("fire",ServerControlOperations.Fire.class,codec);
        var control=ServerControlOperations.type("control",ServerControlOperations.Control.class,codec);
        var execution=new ProgramExecution();
        ServerControlOperations.install(execution,capture); ServerControlOperations.install(execution,group);
        ServerControlOperations.install(execution,fire); ServerControlOperations.install(execution,control); execution.freeze();
        var transform=new Transforms.Compose(List.of(new Transforms.Breathe(1,.25,60,0),
                new Transforms.Rotate(Vec3.Y,new ScalarCurve(List.of(new ScalarCurve.Key(0,0),new ScalarCurve.Key(220,Math.PI*6))),Vec3.ZERO),
                new Transforms.Translate(roaming?new Paths.Bezier(List.of(Vec3.ZERO,new Vec3(8,0,4),new Vec3(-5,0,12),new Vec3(0,0,16)),220,Paths.TimeMap.SMOOTHSTEP,Paths.Tail.HOLD):new Paths.Arc(Vec3.Z,Vec3.Y,.008,.06))));
        var helix=new Patterns.Tagged(new Patterns.HelixColumn(5,2,3,16,
                new ScalarCurve(List.of(new ScalarCurve.Key(0,1),new ScalarCurve.Key(1,3))),0),Set.of("helix"),new Pattern.Style("sphere","blue",.12,1,.22));
        var ring=new Actions.ChildProgram(new Patterns.Tagged(new Patterns.Radial(8,0,0),Set.of("top-ring"),new Pattern.Style("sphere","gold",.1,1,.18)),
                new Paths.Linear(Vec3.Z,.12),100,0,0,List.of(),new Presentation.Profile(List.of(new Presentation.Window(TimeDomain.MEMBER_AGE,0,100)),null,null,List.of()),
                new Transforms.Compose(List.of()),Actions.DirectionRule.TEMPLATE,0);
        var topEmission=new Actions.ChildAction(Actions.ChildActionKind.EMIT,new Selection.IndexModulo(16,15),new Actions.AtAge(20),20,8,ring,
                Actions.Binding.snapshot(),Actions.AtomicScope.PER_PARENT,Actions.OnReject.KEEP_PARENT,ScalarCurve.constant(0),Vec3.Y);
        var child=new Actions.ChildProgram(helix,new Paths.Hold(),220,roaming?1:0,roaming?3072:0,roaming?List.of(topEmission):List.of(),
                new Presentation.Profile(List.of(new Presentation.Window(TimeDomain.MEMBER_AGE,0,220)),null,null,List.of()),transform);
        var b=new ProgramBuilder();
        var root=new SequenceNode(b.nextId(),List.of(
                b.operation(capture,new ServerControlOperations.Capture("targets",new AuthorityDescriptions.CaptureTargets(64,1,true,true)),new TickSpan(0)),
                b.operation(group,new ServerControlOperations.Group("tornado","targets",0,AuthorityDescriptions.TargetPoint.EYES,Vec3.ZERO,Vec3.Y,Vec3.Z,true,null),new TickSpan(0)),
                b.operation(fire,new ServerControlOperations.Fire("tornado",child,Actions.Binding.snapshot(),new Deterministic.Seed(307,List.of()),
                        new AuthorityDescriptions.Commit(new ProgramRef.Plan(new NodeId(0)),0,512,AuthorityDescriptions.Failure.REJECT_BATCH,true),20),new TickSpan(0)),
                 new AtNode(b.nextId(),TimeDomain.CARD,new TickSpan(roaming?281:241),b.operation(control,new ServerControlOperations.Control("",Actions.LifecycleKind.END_ENCOUNTER,0,"preview-complete"),new TickSpan(0)))));
        var result=ProgramCompiler.compile("gensokyou:preview_"+(roaming?"roaming_tower":"moving_tornado"),1,new PlaybackPolicy.Once(TailPolicy.RETAIN_UNTIL_LIFETIME),root,ProgramPreviewPrograms.limits(),ProgramPreviewPrograms.registry());
        if (!result.success()) throw new IllegalStateException("Invalid tornado: "+result.diagnostics());
        var definition=result.definition().orElseThrow(); execution.validate(definition);
        return new ProgramPreviewPrograms.Installed(definition,execution);
    }

    /** Three shared-frame lanes, six staggered births each: wave, Bezier and mirrored Bezier. */
    public static ProgramPreviewPrograms.Installed motionLab() {
        var codec=ServerControlOperations.dataRegistry().freeze();
        var capture=ServerControlOperations.type("capture",ServerControlOperations.Capture.class,codec);
        var group=ServerControlOperations.type("group",ServerControlOperations.Group.class,codec);
        var offset=ServerControlOperations.type("offset_group",ServerControlOperations.OffsetGroup.class,codec);
        var fire=ServerControlOperations.type("fire",ServerControlOperations.Fire.class,codec);
        var control=ServerControlOperations.type("control",ServerControlOperations.Control.class,codec);
        var execution=new ProgramExecution();
        ServerControlOperations.install(execution,capture); ServerControlOperations.install(execution,group);
        ServerControlOperations.install(execution,offset); ServerControlOperations.install(execution,fire);
        ServerControlOperations.install(execution,control); execution.freeze();
        var bezier=new Paths.Bezier(List.of(Vec3.ZERO,new Vec3(5,2,4),new Vec3(-2,3,10),new Vec3(0,0,16)),160,Paths.TimeMap.SMOOTHSTEP,Paths.Tail.HOLD);
        List<Path> paths=List.of(new Paths.WaveOffset(new Paths.Linear(Vec3.Z,.1),Vec3.X,1.2,60,0),bezier,bezier);
        var b=new ProgramBuilder(); var nodes=new ArrayList<ProgramNode>();
        nodes.add(b.operation(capture,new ServerControlOperations.Capture("targets",new AuthorityDescriptions.CaptureTargets(64,1,true,true)),new TickSpan(0)));
        nodes.add(b.operation(group,new ServerControlOperations.Group("frame","targets",0,AuthorityDescriptions.TargetPoint.EYES,Vec3.ZERO,Vec3.Y,Vec3.Z,true,null),new TickSpan(0)));
        String[] names={"wave","bezier","mirror"},colors={"blue","red","gold"};
        for (int lane=0;lane<3;lane++) {
            nodes.add(b.operation(offset,new ServerControlOperations.OffsetGroup(names[lane],"frame",new Vec3((lane-1)*5,0,0)),new TickSpan(0)));
        }
        var policy=new AuthorityDescriptions.Commit(new ProgramRef.Plan(new NodeId(0)),0,512,AuthorityDescriptions.Failure.REJECT_BATCH,true);
        // The schedule supplies stagger; each batch keeps a separate, explicit birth/age clock.
        for (int shot=0;shot<6;shot++) {
            var simultaneous=new ArrayList<ProgramNode>();
            for (int lane=0;lane<3;lane++) {
                Transforms.Transform transform=lane==2?new Transforms.Mirror(Vec3.X,Transforms.VisualMirror.PRESERVE):new Transforms.Compose(List.of());
                var child=new Actions.ChildProgram(new Patterns.Tagged(new Patterns.ParallelGrid(1,3,1,1,Set.of(),Vec3.ZERO),Set.of(names[lane]),
                        new Pattern.Style("sphere",colors[lane],.12,1,.22)),paths.get(lane),160,0,0,List.of(),
                        new Presentation.Profile(List.of(new Presentation.Window(TimeDomain.MEMBER_AGE,0,160)),null,null,List.of()),transform);
                simultaneous.add(b.operation(fire,new ServerControlOperations.Fire(names[lane],child,Actions.Binding.snapshot(),new Deterministic.Seed(211+lane,List.of()),policy,20),new TickSpan(0)));
            }
            nodes.add(new AtNode(b.nextId(),TimeDomain.CARD,new TickSpan(shot*10),new SequenceNode(b.nextId(),simultaneous)));
        }
        nodes.add(new AtNode(b.nextId(),TimeDomain.CARD,new TickSpan(231),b.operation(control,new ServerControlOperations.Control("",Actions.LifecycleKind.END_ENCOUNTER,0,"preview-complete"),new TickSpan(0))));
        var result=ProgramCompiler.compile("gensokyou:preview_motion_lab",1,new PlaybackPolicy.Once(TailPolicy.RETAIN_UNTIL_LIFETIME),new SequenceNode(b.nextId(),nodes),ProgramPreviewPrograms.limits(),ProgramPreviewPrograms.registry());
        if (!result.success()) throw new IllegalStateException("Invalid motion lab: "+result.diagnostics());
        var definition=result.definition().orElseThrow(); execution.validate(definition);
        return new ProgramPreviewPrograms.Installed(definition,execution);
    }
}
