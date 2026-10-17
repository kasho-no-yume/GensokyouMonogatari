package com.bitsson.gensokyou.danmaku.program.ops;

import com.bitsson.gensokyou.danmaku.program.*;
import com.bitsson.gensokyou.danmaku.program.math.*;
import java.util.*;

/** Typed handoff data for the future authority runtime. These records perform no world actions. */
public final class Actions {
    private Actions() {}
    public enum Attachment { WORLD_SNAPSHOT, FOLLOW_SOURCE }
    public enum Velocity { ABSOLUTE, ADD_SOURCE_VELOCITY }
    public enum OnSourceLost { END, DETACH_AT_LAST_AUTHORITATIVE_POSE }
    public enum OnReject { END_PARENT, KEEP_PARENT }
    public enum AtomicScope { PER_PARENT, ALL_SELECTED_PARENTS }
    public record Pose(Frame frame,Vec3 velocity,Vec3 angularVelocity) {
        public Pose { java.util.Objects.requireNonNull(frame); java.util.Objects.requireNonNull(velocity); java.util.Objects.requireNonNull(angularVelocity); }
        public Pose(Frame frame,Vec3 velocity) { this(frame,velocity,Vec3.ZERO); }
    }
    public sealed interface Source permits MemberPose,GroupPose,ExternalEntitySource {}
    public record MemberPose(ProgramRef.Group group,int stableIndex) implements Source {
        public MemberPose { java.util.Objects.requireNonNull(group); if (stableIndex<0) throw new IllegalArgumentException("stable index required"); }
    }
    public record GroupPose(ProgramRef.Group group) implements Source {
        public GroupPose { java.util.Objects.requireNonNull(group); }
    }
    /** Always sampled once at each emission by the server; source loss stops future shots. */
    public record ExternalEntitySource(UUID uuid) implements Source {
        public ExternalEntitySource { java.util.Objects.requireNonNull(uuid); }
    }
    public record Binding(Attachment attachment,Velocity velocity,OnSourceLost onSourceLost) {
        public Binding { java.util.Objects.requireNonNull(attachment); java.util.Objects.requireNonNull(velocity); java.util.Objects.requireNonNull(onSourceLost); }
        public static Binding snapshot() { return new Binding(Attachment.WORLD_SNAPSHOT,Velocity.ABSOLUTE,OnSourceLost.END); }
    }
    public record ChildProgram(Pattern pattern,Path path,long lifetimeTicks,int maxGenerations,long descendantBound,
                               List<ChildAction> actions,Presentation.Profile presentation,Transforms.Transform transform,
                               DirectionRule directionRule,long directionChannel,CollisionProfile collisionProfile) {
        public ChildProgram(Pattern pattern,Path path,long lifetimeTicks,int maxGenerations,long descendantBound,
                            List<ChildAction> actions,Presentation.Profile presentation,Transforms.Transform transform,
                            DirectionRule directionRule,long directionChannel) {
            this(pattern,path,lifetimeTicks,maxGenerations,descendantBound,actions,presentation,transform,
                    directionRule,directionChannel,CollisionProfile.projectile());
        }
        public ChildProgram(Pattern pattern,Path path,long lifetimeTicks,int maxGenerations,long descendantBound,
                            List<ChildAction> actions,Presentation.Profile presentation,Transforms.Transform transform) {
            this(pattern,path,lifetimeTicks,maxGenerations,descendantBound,actions,presentation,transform,DirectionRule.FIXED,0);
        }
        public ChildProgram(Pattern pattern,Path path,long lifetimeTicks,int maxGenerations,long descendantBound) {
            this(pattern,path,lifetimeTicks,maxGenerations,descendantBound,List.of(),
                    new Presentation.Profile(List.of(),null,null,List.of()),new Transforms.Compose(List.of()));
        }
        public ChildProgram {
            java.util.Objects.requireNonNull(pattern); java.util.Objects.requireNonNull(path);
            if (lifetimeTicks<1 || maxGenerations<0 || maxGenerations>8 || descendantBound<0 || descendantBound>4096) throw new IllegalArgumentException("bounded child program required");
            actions=List.copyOf(actions); java.util.Objects.requireNonNull(presentation); java.util.Objects.requireNonNull(transform);
            java.util.Objects.requireNonNull(directionRule);
            collisionProfile=CollisionProfile.require(collisionProfile);
            if (actions.size()>64) throw new IllegalArgumentException("child action limit");
            long derived=0; int generations=0;
            for (ChildAction action:actions) {
                double first=action.trigger().tick(path),last=first+(action.count()-1L)*action.period();
                if (first<0 || last>=lifetimeTicks) throw new IllegalArgumentException("child action must occur during parent lifetime");
                long selected=pattern.countBound();
                derived=Math.addExact(derived,Math.multiplyExact(Math.multiplyExact(selected,action.count()),action.child().totalMembersBound()));
                generations=StrictMath.max(generations,1+action.child().maxGenerations());
            }
            if (derived>descendantBound || generations>maxGenerations) throw new IllegalArgumentException("understated descendant/generation bound");
            if (actions.isEmpty() && (descendantBound!=0 || maxGenerations!=0)) throw new IllegalArgumentException("descendant bounds require an actual child graph");
        }
        public long totalMembersBound() { return Math.addExact(pattern.countBound(),descendantBound); }
        /** +Z path coordinates can explicitly follow each template or its stable random lineage. */
        public Path.State memberState(Pattern.Member member,Deterministic.Seed seed,double age) {
            Path.State state=path.at(age);
            Vec3 direction=switch(directionRule) {
                case FIXED -> Vec3.Z;
                case TEMPLATE -> member.direction();
                case SEEDED_SPHERE -> Deterministic.seedDirection(seed.child(member.index()),directionChannel,0,Deterministic.Distribution.UNIFORM_SPHERE,Vec3.Z,StrictMath.PI);
            };
            Frame frame=Frame.along(Vec3.ZERO,direction,Vec3.Y);
            return transform.apply(new Path.State(member.position().add(frame.direction(state.position())),frame.direction(state.velocity()),state.ended()||age>=lifetimeTicks),age);
        }
        public long treeLifetimeBound() {
            long end=lifetimeTicks;
            for (ChildAction action:actions) end=StrictMath.max(end,Math.addExact((long)StrictMath.ceil(action.trigger().tick(path)+(action.count()-1L)*action.period()),action.child().treeLifetimeBound()));
            return end;
        }
    }
    public enum DirectionRule { FIXED, TEMPLATE, SEEDED_SPHERE }
    public sealed interface Trigger permits AtAge,AtPathSegmentEnd { double tick(Path path); }
    public record AtAge(double age) implements Trigger {
        public AtAge { Deterministic.nonnegative(age); }
        public double tick(Path path) { return age; }
    }
    public record AtPathSegmentEnd(String label) implements Trigger {
        public AtPathSegmentEnd { if (label==null || label.isBlank()) throw new IllegalArgumentException("segment label required"); }
        public double tick(Path path) {
            if (!(path instanceof Paths.Segments segments)) throw new IllegalArgumentException("segment trigger requires segmented path");
            return segments.endOf(label);
        }
    }
    public enum ChildActionKind { EMIT, REPLACE }
    /** Per selected parent member. Pure description; authority consumes each occurrence once. */
    public record ChildAction(ChildActionKind kind,Selection selection,Trigger trigger,long period,int count,
                              ChildProgram child,Binding binding,AtomicScope scope,OnReject onReject,
                              ScalarCurve nozzleAngleRadians,Vec3 nozzleAxis) {
        public ChildAction {
            java.util.Objects.requireNonNull(kind); java.util.Objects.requireNonNull(selection); java.util.Objects.requireNonNull(trigger);
            java.util.Objects.requireNonNull(child); java.util.Objects.requireNonNull(binding); java.util.Objects.requireNonNull(scope); java.util.Objects.requireNonNull(onReject);
            java.util.Objects.requireNonNull(nozzleAngleRadians); nozzleAxis=nozzleAxis.unit();
            if (period<1 || count<1 || count>4096 || kind==ChildActionKind.REPLACE && count!=1) throw new IllegalArgumentException("bounded child schedule required");
        }
    }
    public record SpawnChild(Source source,ChildProgram child,Binding binding) {
        public SpawnChild { java.util.Objects.requireNonNull(source); java.util.Objects.requireNonNull(child); java.util.Objects.requireNonNull(binding); if (source instanceof ExternalEntitySource && binding.attachment()!=Attachment.WORLD_SNAPSHOT) throw new IllegalArgumentException("external sources only support world snapshots"); }
    }
    public record EmitAlongPath(Source source,long firstTick,long period,int count,ChildProgram child,Binding binding) {
        public EmitAlongPath { new SpawnChild(source,child,binding); if (firstTick<0 || period<1 || count<1 || count>4096) throw new IllegalArgumentException("finite integer-tick schedule required"); Math.addExact(firstTick,Math.multiplyExact(period,count-1L)); }
        public long totalMembersBound() { return Math.multiplyExact(count,Math.addExact(child.pattern().countBound(),child.descendantBound())); }
    }
    public record ReplaceMembers(Selection parents,ChildProgram child,AtomicScope scope,OnReject onReject) {
        public ReplaceMembers { java.util.Objects.requireNonNull(parents); java.util.Objects.requireNonNull(child); java.util.Objects.requireNonNull(scope); java.util.Objects.requireNonNull(onReject); }
        public long netMemberIncrease(int selectedParents) { if (selectedParents<0) throw new IllegalArgumentException("negative parent count"); return Math.multiplyExact(selectedParents,child.pattern().countBound()-1L); }
    }
    public record SplitAt(double effectiveTick,ReplaceMembers replacement) {
        public SplitAt { Deterministic.nonnegative(effectiveTick); java.util.Objects.requireNonNull(replacement); }
    }
    public record AtSegmentEnd(Paths.Segments path,String label) {
        public AtSegmentEnd { java.util.Objects.requireNonNull(path); path.endOf(label); }
        public double tick() { return path.endOf(label); }
        public boolean due(double fromInclusive,double untilExclusive) { return tick()>=fromInclusive && tick()<untilExclusive; }
    }
    /** Serializable segment trigger; resolve the compiled segment table once on installation. */
    public record SegmentEndTrigger(OperationLibrary.PathSpec path,String label) {
        public SegmentEndTrigger {
            java.util.Objects.requireNonNull(path);
            if (!(path.evaluator() instanceof Paths.Segments segments)) throw new IllegalArgumentException("finite segment table required");
            segments.endOf(label);
        }
        public double tick() { return ((Paths.Segments)path.evaluator()).endOf(label); }
    }
    public record Stagger(Selection selection,double delayStep) {
        public Stagger { java.util.Objects.requireNonNull(selection); Deterministic.nonnegative(delayStep); }
        public double offset(Pattern.Member member) { return selection.includes(member)?member.index()*delayStep:0; }
    }
    public record PhaseOffset(double baseRadians,double indexStepRadians) {
        public PhaseOffset { Deterministic.finite(baseRadians,indexStepRadians); }
        public double at(int stableIndex) { return Transforms.phaseOffset(baseRadians,stableIndex,indexStepRadians); }
    }
    public record SeedDirection(Deterministic.Seed seed,long channel,Deterministic.Distribution distribution,
                                Vec3 axis,double coneRadians) {
        public SeedDirection { java.util.Objects.requireNonNull(seed); java.util.Objects.requireNonNull(distribution); axis=axis.unit(); Deterministic.nonnegative(coneRadians); if (coneRadians>StrictMath.PI) throw new IllegalArgumentException("cone <= pi required"); }
        public Vec3 at(int stableIndex) { if (stableIndex<0) throw new IllegalArgumentException("stable index required"); return Deterministic.seedDirection(seed,channel,stableIndex,distribution,axis,coneRadians); }
    }
    public record CreateEmitter(Source source,ScalarCurve nozzleAngleRadians,Vec3 nozzleAxis,
                                long firstTick,long period,int count,ChildProgram child,Binding binding,
                                Presentation.Billboard billboard) {
        public CreateEmitter { new EmitAlongPath(source,firstTick,period,count,child,binding); java.util.Objects.requireNonNull(nozzleAngleRadians); nozzleAxis=nozzleAxis.unit(); }
        public long shotTick(int ordinal) { if (ordinal<0 || ordinal>=count) throw new IllegalArgumentException("shot ordinal out of range"); return Math.addExact(firstTick,Math.multiplyExact(period,ordinal)); }
        public Pose nozzle(Pose resolvedSource,double tick) {
            double angle=nozzleAngleRadians.at(tick); Frame f=resolvedSource.frame();
            return new Pose(new Frame(f.origin(),f.direction(Vec3.X.rotate(nozzleAxis,angle)),f.direction(Vec3.Y.rotate(nozzleAxis,angle)),f.direction(Vec3.Z.rotate(nozzleAxis,angle))),resolvedSource.velocity(),resolvedSource.angularVelocity().add(f.direction(nozzleAxis).multiply(Transforms.slope(nozzleAngleRadians,tick))));
        }
    }
    /** Explicit mock/sampled context; never asks the world or reads rendered interpolation. */
    public interface ResolvedSources { Optional<Pose> at(Source source,double tick); }
    public static Optional<Path.State> emittedState(SpawnChild spawn,ResolvedSources sources,double birthTick,
                                                   double tick,Vec3 localPosition) {
        return emittedState(spawn,sources,birthTick,tick,new Pattern.Member(0,localPosition,Vec3.Z,Pattern.Style.DEFAULT,Set.of(),-1),new Deterministic.Seed(0,List.of()));
    }
    public static Optional<Path.State> emittedState(SpawnChild spawn,ResolvedSources sources,double birthTick,
                                                   double tick,Pattern.Member member,Deterministic.Seed seed) {
        Deterministic.nonnegative(birthTick); Deterministic.nonnegative(tick);
        if (tick<birthTick) return Optional.empty();
        Optional<Pose> sampled=sources.at(spawn.source(),birthTick);
        if (sampled.isEmpty()) return Optional.empty();
        Pose birth=sampled.orElseThrow();
        double age=tick-birthTick;
        Path.State child=spawn.child().memberState(member,seed,age);
        Vec3 birthOffset=spawn.child().memberState(member,seed,0).position();
        Vec3 inherited=spawn.binding().velocity()==Velocity.ADD_SOURCE_VELOCITY
                ?birth.velocity().add(birth.angularVelocity().cross(birth.frame().direction(birthOffset))):Vec3.ZERO;
        boolean ended=child.ended() || age>=spawn.child().lifetimeTicks();
        if (spawn.binding().attachment()==Attachment.WORLD_SNAPSHOT) {
            return Optional.of(new Path.State(birth.frame().point(child.position()).add(inherited.multiply(age)),birth.frame().direction(child.velocity()).add(inherited),ended));
        }
        Optional<Pose> now=sources.at(spawn.source(),tick);
        if (now.isEmpty()) return Optional.empty(); // Authority must supply an explicit loss pose to detach.
        Vec3 localTravel=child.position();
        Pose pose=now.orElseThrow();
        Vec3 offset=pose.frame().direction(localTravel);
        return Optional.of(new Path.State(pose.frame().origin().add(offset).add(inherited.multiply(age)),
                pose.frame().direction(child.velocity()).add(pose.velocity()).add(pose.angularVelocity().cross(offset)).add(inherited),ended));
    }
    public enum LifecycleKind { STOP_EMISSION, END_MEMBERS, COLLAPSE, CANCEL_SCOPE, END_CARD, END_ENCOUNTER, LIFETIME, END_ON_RETURN, LOCK_BOSS_MOVEMENT }
    public record Lifecycle(LifecycleKind kind,String scope,double effectiveTick,double duration,String reason) {
        public Lifecycle { java.util.Objects.requireNonNull(kind); if (scope==null || scope.isBlank() || reason==null) throw new IllegalArgumentException("scope/reason required"); Deterministic.nonnegative(effectiveTick); Deterministic.nonnegative(duration); }
    }
    public enum DecisionKind { CAPTURE_TARGETS, CAPTURE_FRAME, SAMPLE_TARGET_POINT, RESOLVE_SURFACE, TRIGGER_PROXIMITY, COMMIT_BATCH }
    public record Decision(DecisionKind kind,int maxQueries,double radius,int maxSteps) {
        public Decision { java.util.Objects.requireNonNull(kind); Deterministic.nonnegative(radius); if (maxQueries<1 || maxQueries>512 || maxSteps<0 || maxSteps>4096) throw new IllegalArgumentException("bounded decision cost required"); }
    }
    public record CreateGroup(ProgramRef.Group parent,ProgramRef.Frame frame,double timeOrigin,
                              Set<String> tags,MemberMotion.Ownership ownership,TailPolicy tailPolicy) {
        public CreateGroup { java.util.Objects.requireNonNull(frame); Deterministic.finite(timeOrigin); tags=Set.copyOf(tags); java.util.Objects.requireNonNull(ownership); java.util.Objects.requireNonNull(tailPolicy); }
    }
    public record ForEachTarget(ProgramRef.TargetSet targets,List<Integer> resolvedSlots) {
        public ForEachTarget {
            java.util.Objects.requireNonNull(targets); resolvedSlots=List.copyOf(resolvedSlots);
            if (resolvedSlots.size()>5 || resolvedSlots.stream().distinct().count()!=resolvedSlots.size()
                    || resolvedSlots.stream().anyMatch(i->i<0 || i>4)) throw new IllegalArgumentException("fixed unique target slots required");
        }
    }
    public record Emit(ProgramRef.Plan approvedPlan,ChildProgram members,Binding binding) {
        public Emit { java.util.Objects.requireNonNull(approvedPlan); java.util.Objects.requireNonNull(members); java.util.Objects.requireNonNull(binding); }
    }
    public record PrepareEmission(Pattern pattern,Frame resolvedFrame,Vec3 sourceOffset,Vec3 sampledTarget,long birthTick) {
        public PrepareEmission { java.util.Objects.requireNonNull(pattern); java.util.Objects.requireNonNull(resolvedFrame); java.util.Objects.requireNonNull(sourceOffset); if (birthTick<0) throw new IllegalArgumentException("birth tick required"); }
        public Vec3 actualSource() { return resolvedFrame.point(sourceOffset); }
        public Vec3 aimDirection() { return MemberMotion.aimAtPoint(actualSource(),Objects.requireNonNull(sampledTarget)); }
    }
}
