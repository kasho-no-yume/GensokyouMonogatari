package com.bitsson.gensokyou.danmaku.program.server;

import com.bitsson.gensokyou.danmaku.program.*;
import com.bitsson.gensokyou.danmaku.program.math.*;
import com.bitsson.gensokyou.danmaku.program.ops.*;
import java.util.*;

/** Concrete operations for world capture and group control. Handles resolve within a session occurrence. */
public final class ServerControlOperations {
    private ServerControlOperations() {}
    public record Capture(String name,AuthorityDescriptions.CaptureTargets targets) {}
    public record Group(String name,String targets,int slot,AuthorityDescriptions.TargetPoint point,
                        Vec3 sourceOffset,Vec3 up,Vec3 fallback,boolean retain,GroupMotion motion) {}
    public record Fire(String group,Actions.ChildProgram program,Actions.Binding binding,
                       Deterministic.Seed seed,AuthorityDescriptions.Commit policy,long warningTicks) {
        public Fire { if (warningTicks<0) throw new IllegalArgumentException("negative warning lead"); }
    }
    public record Control(String group,Actions.LifecycleKind kind,long fadeTicks,String reason) {}
    public record Sample(String group,int slot,AuthorityDescriptions.TargetPoint point) {}
    public record Surface(String group,String name,List<Vec3> points,AuthorityDescriptions.SurfaceMode mode,int steps,int queries) {
        public Surface { points=List.copyOf(points); }
    }
    public record Emitter(String group,Actions.CreateEmitter emitter,Deterministic.Seed seed,AuthorityDescriptions.Commit policy) {}
    public record Replace(String group,int maxParents,Actions.ReplaceMembers replacement,AuthorityDescriptions.Commit policy) {
        public Replace { if (maxParents<1 || maxParents>512) throw new IllegalArgumentException("bounded parent selection required"); }
    }
    public record BindSource(String group,ProgramRef.Group reference,Long batch) {}
    public record OffsetGroup(String name,String source,Vec3 offset) {}
    public record Corridor(String group,Vec3 from,Vec3 until,double halfWidth,int maxQueries,boolean cancelOnFailure,boolean local) {
        public Corridor(String group,Vec3 from,Vec3 until,double halfWidth,int maxQueries,boolean cancelOnFailure) {
            this(group,from,until,halfWidth,maxQueries,cancelOnFailure,false);
        }
    }
    public record RebaseTarget(String group,int slot,AuthorityDescriptions.TargetPoint point,Selection selection,double speed) {
        public RebaseTarget { if (slot< -1 || slot>4) throw new IllegalArgumentException("target slot"); Objects.requireNonNull(point); Objects.requireNonNull(selection); Deterministic.positive(speed); }
    }
    /** A shared boundary center is captured before any selected member changes path. */
    public record RadialRelease(String group,Selection center,Selection members,double speed) {
        public RadialRelease { Objects.requireNonNull(center); Objects.requireNonNull(members); Deterministic.positive(speed); }
    }
    public static OperationDataCodec dataRegistry() {
        return ServerOperations.dataRegistry().register("server:group_motion",GroupMotion.class)
                .register("server:capture",Capture.class).register("server:group",Group.class).register("server:fire",Fire.class)
                .register("server:control",Control.class).register("server:sample",Sample.class).register("server:surface",Surface.class)
                .register("server:emitter",Emitter.class).register("server:replace",Replace.class)
                .register("server:bind_source",BindSource.class).register("server:corridor",Corridor.class)
                .register("server:offset_group",OffsetGroup.class).register("server:rebase_target",RebaseTarget.class)
                .register("server:radial_release",RadialRelease.class);
    }
    public static <P extends Record> OperationType<P> type(String name,Class<P> clazz,OperationDataCodec codec) {
        return OperationLibrary.descriptionType("gensokyou:server_"+name,clazz,codec,p->{
            long members=0,derived=0,life=0; int generations=0;
            if (p instanceof Fire fire) { members=fire.program().pattern().countBound(); derived=fire.program().descendantBound(); generations=fire.program().maxGenerations(); life=Math.addExact(fire.warningTicks(),fire.program().treeLifetimeBound()); }
            if (p instanceof Emitter emitter) {
                members=Math.multiplyExact(emitter.emitter().count(),emitter.emitter().child().pattern().countBound());
                derived=Math.multiplyExact(emitter.emitter().count(),emitter.emitter().child().descendantBound());
                generations=emitter.emitter().child().maxGenerations();
                life=Math.addExact(emitter.emitter().shotTick(emitter.emitter().count()-1),emitter.emitter().child().treeLifetimeBound());
            }
            if (p instanceof Replace replace) {
                // A selection can address an entire maximum-sized batch. Do not pretend it selects one parent.
                members=Math.multiplyExact(replace.maxParents(),replace.replacement().child().pattern().countBound());
                derived=Math.multiplyExact(replace.maxParents(),replace.replacement().child().descendantBound());
                generations=replace.replacement().child().maxGenerations(); life=replace.replacement().child().treeLifetimeBound();
            }
            String scope=p instanceof Fire fire?fire.group():p instanceof Emitter emitter?emitter.group():p instanceof Replace replace?replace.group():"server_control";
            Set<Channel> writes=p instanceof Fire || p instanceof Emitter || p instanceof Replace?Set.of(Channel.EMISSION):Set.of();
            return new OperationType.OperationContract(scope,writes,Set.of(),List.of(),members,life,derived,generations);
        });
    }
    /** Operation records remain data; all mutable handle state resides in the runtime checkpoint. */
    public static <P extends Record> void install(ProgramExecution execution,OperationType<P> type) {
        execution.register(type,new ProgramExecution.Handler<>() {
            public DanmakuWorldRuntime.Phase phase() {
                Class<?> parameters=type instanceof OperationLibrary.RegisteredType<?> registered?registered.parameters():null;
                 if (parameters==Fire.class || parameters==Emitter.class || parameters==Replace.class || parameters==Corridor.class || parameters==RebaseTarget.class || parameters==RadialRelease.class)
                    return DanmakuWorldRuntime.Phase.PREPARE;
                if (parameters==Control.class) return DanmakuWorldRuntime.Phase.COMMIT;
                return DanmakuWorldRuntime.Phase.CAPTURE;
            }
            public void execute(DanmakuWorldRuntime r,ProgramSchedule.Event e,P value) { throw new IllegalStateException("program context required"); }
            @Override public void execute(DanmakuWorldRuntime r,ProgramExecution.Context c,ProgramSchedule.Event e,P value) {
                String prefix=c.instance()+":"+e.occurrence()+":";
                if (value instanceof Capture capture) {
                    List<UUID> targets=r.captureTargets(c.encounter(),r.ownerPose(c.encounter()).frame().origin(),capture.targets());
                    r.targetHandle(prefix+capture.name(),targets,capture.targets().radius());
                } else if (value instanceof Group group) {
                    String targetKey=r.resolveHandle(prefix,group.targets(),true);
                    List<UUID> targets=r.targetHandle(targetKey);
                    Actions.Pose owner=r.ownerPose(c.encounter()); Frame frame=owner.frame();
                    if (group.slot()>=0) {
                        Optional<Vec3> point=r.targetPoint(targets,group.slot(),group.point());
                        if (point.isEmpty()) return;
                        frame=DecisionProvider.captureFrame(owner.frame().point(group.sourceOffset()),point.orElseThrow(),group.up(),null,group.fallback());
                    }
                    long id=r.createGroup(c.encounter(),c.card(),e.occurrence().isEmpty()?0:e.occurrence().getFirst(),null,group.slot(),c.background(),group.retain(),frame,targets);
                    r.groupHandle(prefix+group.name(),id);
                    r.targetRadius(id,r.targetHandleRadius(targetKey));
                    r.clocks(id,new DanmakuWorldRuntime.Clocks(c.anchors().card(),c.anchors().cycle(),e.tick()));
                    if (group.motion()!=null) r.groupMotion(id,new GroupMotion(frame,group.motion().translation(),group.motion().transform(),e.tick(),group.motion().parents()));
                } else if (value instanceof OffsetGroup offset) {
                    r.groupHandle(r.resolveHandle(prefix,offset.source(),false)).ifPresent(source->{
                        long id=r.offsetGroup(source,prefix+offset.name(),offset.offset());
                        r.clocks(id,new DanmakuWorldRuntime.Clocks(c.anchors().card(),c.anchors().cycle(),e.tick()));
                    });
                } else if (value instanceof Fire fire) {
                    OptionalLong group=r.groupHandle(r.resolveHandle(prefix,fire.group(),false)); if (group.isEmpty()) return;
                    long id=group.getAsLong();
                    var seed=fire.seed().child(Math.toIntExact(c.instance())).child(Math.toIntExact(e.node().value()));
                    for (long n:e.occurrence()) seed=seed.child(Math.toIntExact(n));
                    var motion=fire.binding().attachment()==Actions.Attachment.FOLLOW_SOURCE?r.groupMotion(id):null;
                    var plan=r.prepare(id,Math.addExact(e.tick(),fire.warningTicks()),r.groupPose(id,e.tick()),fire.program(),fire.binding(),seed,motion);
                    r.commitAtBirth(plan,fire.policy(),e.ownershipEnd());
                } else if (value instanceof RadialRelease release) {
                    r.groupHandle(r.resolveHandle(prefix,release.group(),false)).ifPresent(id->{
                        var centers=r.members(id,release.center());
                        if (centers.isEmpty()) { r.cancel(id,0,"radial-center-unavailable"); return; }
                        if (centers.size()!=1) throw new IllegalArgumentException("radial release requires one live center");
                        Vec3 center=r.state(centers.getFirst(),e.tick()).position();
                        for (var member:r.members(id,release.members())) {
                            Vec3 delta=r.state(member,e.tick()).position().subtract(center);
                            r.rebase(member,delta.length()>1e-9?new Paths.Linear(delta,release.speed()):new Paths.Hold(),true);
                        }
                    });
                } else if (value instanceof RebaseTarget rebase) {
                    r.groupHandle(r.resolveHandle(prefix,rebase.group(),false)).ifPresent(id->{
                        var point=rebase.slot()<0?r.sampleRandomTarget(id,rebase.point()):r.sampleTarget(id,rebase.slot(),rebase.point());
                        if (point.isEmpty()) { r.cancel(id,0,"rebase-target-unavailable"); return; }
                        for (var member:r.members(id,rebase.selection())) {
                            Vec3 delta=point.orElseThrow().subtract(r.state(member,e.tick()).position());
                            r.rebase(member,delta.length()>1e-9?new Paths.Linear(delta,rebase.speed()):new Paths.Hold(),true);
                        }
                    });
                } else if (value instanceof Sample sample) {
                    r.groupHandle(r.resolveHandle(prefix,sample.group(),false)).ifPresent(id->r.sampleTarget(id,sample.slot(),sample.point()));
                } else if (value instanceof Surface surface) {
                    r.groupHandle(r.resolveHandle(prefix,surface.group(),false)).ifPresent(id->r.resolveSurface(id,surface.name(),surface.points(),
                            new AuthorityDescriptions.ResolveSurface(new ProgramRef.Plan(e.node()),surface.mode(),surface.steps(),surface.queries())));
                } else if (value instanceof Emitter emitter) {
                    r.groupHandle(r.resolveHandle(prefix,emitter.group(),false)).ifPresent(id->r.createEmitter(id,emitter.emitter(),emitter.seed(),emitter.policy()));
                } else if (value instanceof BindSource source) {
                    r.groupHandle(r.resolveHandle(prefix,source.group(),false)).ifPresent(id->{ if (source.batch()==null) r.bindSource(source.reference(),id); else r.bindSource(source.reference(),id,source.batch()); });
                } else if (value instanceof Replace replace) {
                    r.groupHandle(r.resolveHandle(prefix,replace.group(),false)).ifPresent(id->{
                        var parents=r.members(id,replace.replacement().parents());
                        if (parents.size()>replace.maxParents()) throw new IllegalArgumentException("parent selection exceeds compiled bound");
                        r.replace(id,parents,replace.replacement(),replace.policy(),"node:"+e.node()+":"+e.occurrence());
                    });
                } else if (value instanceof Corridor corridor) {
                    r.groupHandle(r.resolveHandle(prefix,corridor.group(),false)).ifPresent(id->{
                        Frame frame=r.groupPose(id,e.tick()).frame();
                        Vec3 from=corridor.local()?frame.point(corridor.from()):corridor.from();
                        Vec3 until=corridor.local()?frame.point(corridor.until()):corridor.until();
                        if (!r.corridor(id,from,until,corridor.halfWidth(),corridor.maxQueries()) && corridor.cancelOnFailure()) r.cancel(id,0,"corridor-blocked");
                    });
                } else if (value instanceof Control control) {
                    if (control.kind()==Actions.LifecycleKind.END_CARD) {
                        r.endCard(c.encounter(),c.card());
                        if (c.session()!=null) c.session().requestEndCard(c.encounter(),c.card());
                        return;
                    }
                    if (control.kind()==Actions.LifecycleKind.END_ENCOUNTER) { r.endEncounter(c.encounter(),control.reason()); return; }
                    r.groupHandle(r.resolveHandle(prefix,control.group(),false)).ifPresent(id->{
                        switch (control.kind()) {
                            case STOP_EMISSION -> r.stopEmission(id);
                            case COLLAPSE, CANCEL_SCOPE -> r.cancel(id,control.fadeTicks(),control.reason());
                            case END_MEMBERS -> r.endMembers(id,control.reason());
                            case LOCK_BOSS_MOVEMENT -> { if (control.fadeTicks()>0) r.lockUntil(id,Math.addExact(e.tick(),control.fadeTicks())); else r.movementLock(id,false); }
                            default -> throw new IllegalArgumentException("unsupported control; use explicit member condition: "+control.kind());
                        }
                    });
                } else throw new IllegalArgumentException("uninstalled authority description "+value.getClass());
            }
        });
    }
}
