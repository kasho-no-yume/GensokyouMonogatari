package com.bitsson.gensokyou.danmaku.program.author;

import com.bitsson.gensokyou.danmaku.program.*;
import com.bitsson.gensokyou.danmaku.program.math.*;
import com.bitsson.gensokyou.danmaku.program.ops.*;
import com.bitsson.gensokyou.danmaku.program.server.*;
import java.util.*;
import java.util.function.Consumer;

/** Card-local timeline. Handles carry ownership; authors never allocate node IDs or handlers. */
public final class CardProgram {
    public record Compiled(ProgramDefinition definition,ProgramExecution execution) {}
    public record Targets(CardProgram owner,String name) {}
    public record Group(CardProgram owner,String name) {}
    private final String id;
    private final ProgramBuilder nodes=new ProgramBuilder();
    private final List<ProgramNode> timeline=new ArrayList<>();
    private final Set<String> names=new HashSet<>();
    private final Map<String,Long> createdAt=new HashMap<>();
    private final Set<String> movingGroups=new HashSet<>();
    private long time;
    private ProgramLimits limits=StandardProgramOperations.limits();
    private AuthorityDescriptions.Commit admission=new AuthorityDescriptions.Commit(new ProgramRef.Plan(new NodeId(0)),2,512,AuthorityDescriptions.Failure.REJECT_BATCH,true);
    public CardProgram(String id) { this.id=Objects.requireNonNull(id); }
    public CardProgram limits(ProgramLimits value) { limits=Objects.requireNonNull(value); return this; }
    public CardProgram clearance(double blocks) {
        admission=new AuthorityDescriptions.Commit(admission.plan(),blocks,admission.memberQuota(),admission.onFailure(),true); return this;
    }
    public CardProgram at(long tick,Consumer<CardProgram> body) {
        if (tick<0) throw error("negative card tick");
        long previous=time; time=tick;
        try { body.accept(this); } catch (IllegalArgumentException ex) { throw error("tick "+tick+": "+ex.getMessage()); }
        finally { time=previous; }
        return this;
    }
    public CardProgram every(long first,long period,int count,Consumer<CardProgram> body) {
        if (first<0 || period<1 || count<1) throw error("finite positive schedule required");
        if (count>limits.maxNodes()) throw error("schedule exceeds node budget");
        for (int i=0;i<count;i++) at(Math.addExact(first,Math.multiplyExact(period,i)),body);
        return this;
    }
    public Targets targets(String name,double radius,int maximum) {
        unique(name); add(new ServerControlOperations.Capture(name,new AuthorityDescriptions.CaptureTargets(radius,maximum,true,true)));
        return new Targets(this,name);
    }
    /** Fixed slot: absent/departed players are never silently reassigned. */
    public Group aimed(String name,Targets targets,int slot,Vec3 sourceOffset) {
        own(targets.owner()); available(targets.name()); if (slot<0 || slot>4) throw error(name+": target slot 0..4 required");
        return group(name,targets,slot,sourceOffset,null);
    }
    public Group arena(String name,Targets targets) { own(targets.owner()); available(targets.name()); return group(name,targets,-1,Vec3.ZERO,null); }
    public Group aimedMoving(String name,Targets targets,int slot,Vec3 offset,Path translation,Transforms.Transform transform) {
        own(targets.owner()); available(targets.name()); if (slot<0 || slot>4) throw error(name+": target slot 0..4 required");
        return group(name,targets,slot,offset,new GroupMotion(Frame.IDENTITY,translation,transform,0,List.of()));
    }
    private Group group(String name,Targets targets,int slot,Vec3 offset,GroupMotion motion) {
        unique(name); add(new ServerControlOperations.Group(name,targets.name(),slot,AuthorityDescriptions.TargetPoint.EYES,offset,Vec3.Y,Vec3.Z,true,motion));
        if (motion!=null) movingGroups.add(name);
        return new Group(this,name);
    }
    public Group offset(String name,Group source,Vec3 offset) {
        own(source.owner()); available(source.name()); unique(name); add(new ServerControlOperations.OffsetGroup(name,source.name(),offset)); return new Group(this,name);
    }
    public CardProgram fire(Group group,Actions.ChildProgram members,long warningTicks,long seed) { return fire(group,members,warningTicks,seed,false); }
    public CardProgram fireAttached(Group group,Actions.ChildProgram members,long warningTicks,long seed) { return fire(group,members,warningTicks,seed,true); }
    private CardProgram fire(Group group,Actions.ChildProgram members,long warning,long seed,boolean attached) {
        own(group.owner()); available(group.name());
        if (attached && !movingGroups.contains(group.name())) throw error(group.name()+": fireAttached requires aimedMoving group");
        add(new ServerControlOperations.Fire(group.name(),members,attached?new Actions.Binding(Actions.Attachment.FOLLOW_SOURCE,Actions.Velocity.ABSOLUTE,Actions.OnSourceLost.END):Actions.Binding.snapshot(),
                new Deterministic.Seed(seed,List.of()),admission,warning)); return this;
    }
    public CardProgram release(Group group,Selection center,Selection selected,double speed) {
        own(group.owner()); available(group.name()); add(new ServerControlOperations.RadialRelease(group.name(),center,selected,speed)); return this;
    }
    /**
     * Finite local corridor clearance check. When {@code cancelOnFailure} the whole group is
     * cancelled if any sampled voxel is solid or unloaded; used to withdraw a batch whose
     * declared physical gap is blocked by terrain. Coordinates are in the group's local frame.
     */
    public CardProgram corridor(Group group,Vec3 from,Vec3 until,double halfWidth,int maxQueries,boolean cancelOnFailure) {
        own(group.owner()); available(group.name());
        add(new ServerControlOperations.Corridor(group.name(),from,until,halfWidth,maxQueries,cancelOnFailure,true)); return this;
    }
    /**
     * Resolve a world surface for each of {@code anchors} (local to the group frame) and emit
     * {@code members} at every resolved point, in its own ordinary group. Used for
     * first-air-below rain and ground-anchored pillars.
     */
    public CardProgram surfaceFire(Group group,String name,List<Vec3> anchors,AuthorityDescriptions.SurfaceMode mode,
                                   int steps,int queries,Actions.ChildProgram members,long warningTicks,long seed,
                                   Vec3 forward,int maxPoints) {
        own(group.owner()); available(group.name());
        add(new ServerControlOperations.SurfaceFire(group.name(),name,anchors,false,AuthorityDescriptions.TargetPoint.FEET,
                mode,steps,queries,members,new Deterministic.Seed(seed,List.of()),Actions.Binding.snapshot(),
                admission,warningTicks,forward,maxPoints)); return this;
    }
    /** Surface emission with one anchor per live target slot (player-ground style). */
    public CardProgram surfaceFireAtTargets(Group group,String name,AuthorityDescriptions.TargetPoint point,
                                            AuthorityDescriptions.SurfaceMode mode,int steps,int queries,
                                            Actions.ChildProgram members,long warningTicks,long seed,
                                            Vec3 forward,int maxPoints) {
        own(group.owner()); available(group.name());
        add(new ServerControlOperations.SurfaceFire(group.name(),name,List.of(),true,point,
                mode,steps,queries,members,new Deterministic.Seed(seed,List.of()),Actions.Binding.snapshot(),
                admission,warningTicks,forward,maxPoints)); return this;
    }
    /** Fresh world-horizontal disc samples per shot and cycle, not build-time repeated points. */
    public CardProgram surfaceFireRandom(Group group,String name,int count,double radius,double height,
                                        AuthorityDescriptions.SurfaceMode mode,int steps,int queries,
                                        Actions.ChildProgram members,long warningTicks,long seed,Vec3 forward) {
        own(group.owner()); available(group.name());
        add(new ServerControlOperations.SurfaceFire(group.name(),name,List.of(Vec3.ZERO),false,AuthorityDescriptions.TargetPoint.FEET,
                mode,steps,queries,members,new Deterministic.Seed(seed,List.of()),Actions.Binding.snapshot(),
                admission,warningTicks,forward,count,radius,height)); return this;
    }
    public CardProgram reaim(Group group,int slot,Selection selected,double speed) {
        own(group.owner()); available(group.name()); add(new ServerControlOperations.RebaseTarget(group.name(),slot,AuthorityDescriptions.TargetPoint.EYES,selected,speed)); return this;
    }
    public CardProgram collapse(Group group,long fade) { return control(group,Actions.LifecycleKind.COLLAPSE,fade); }
    public CardProgram stopEmission(Group group) { return control(group,Actions.LifecycleKind.STOP_EMISSION,0); }
    public CardProgram lockMovement(Group group,long duration) { return control(group,Actions.LifecycleKind.LOCK_BOSS_MOVEMENT,duration); }
    private CardProgram control(Group group,Actions.LifecycleKind kind,long duration) {
        own(group.owner()); available(group.name()); add(new ServerControlOperations.Control(group.name(),kind,duration,"content:"+id)); return this;
    }
    /** Ends this card, never the encounter or its independent background. */
    public CardProgram endCard() { add(new ServerControlOperations.Control("",Actions.LifecycleKind.END_CARD,0,"content:"+id)); return this; }
    public Compiled once() { return compile(new PlaybackPolicy.Once(TailPolicy.RETAIN_UNTIL_LIFETIME)); }
    public Compiled loop(long ticks,TailPolicy tail) { return compile(new PlaybackPolicy.Loop(new TickSpan(ticks),tail)); }
    private Compiled compile(PlaybackPolicy policy) {
        try {
            // Author entries are absolutely anchored AtNode(CYCLE,t). Scheduling orders same-tick
            // events by node index and handle validation walks the AST in order, so sort by
            // (tick, insertion) to make list order == execution order even for out-of-order `at`.
            var ordered = timeline.stream()
                    .sorted(Comparator.comparingLong(node -> ((AtNode) node).offset().ticks()))
                    .toList();
            var root=new ParallelNode(nodes.nextId(),ordered);
            var result=ProgramCompiler.compile(id,1,policy,root,limits,StandardProgramOperations.registry());
            if (!result.success()) throw error(result.diagnostics().toString());
            var execution=StandardProgramOperations.execution(); var definition=result.definition().orElseThrow(); execution.validate(definition);
            return new Compiled(definition,execution);
        } catch (IllegalArgumentException ex) { throw error(ex.getMessage()); }
    }
    @SuppressWarnings("unchecked")
    private <P extends Record> void add(P value) {
        var operation=nodes.operation(StandardProgramOperations.type((Class<P>)value.getClass()),value,new TickSpan(0));
        timeline.add(new AtNode(nodes.nextId(),TimeDomain.CYCLE,new TickSpan(time),operation));
    }
    private void unique(String name) {
        if (name==null || name.isBlank() || !names.add(name)) throw error("duplicate/empty handle: "+name);
        createdAt.put(name,time);
    }
    private void available(String name) { if (!createdAt.containsKey(name) || createdAt.get(name)>time) throw error(name+": handle unavailable at tick "+time); }
    private void own(CardProgram owner) { if (owner!=this) throw error("handle belongs to another card"); }
    private IllegalArgumentException error(String message) { return new IllegalArgumentException(id+": "+message); }
}
