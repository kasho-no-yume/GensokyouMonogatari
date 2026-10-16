package com.bitsson.gensokyou.danmaku.program.server;

import com.bitsson.gensokyou.danmaku.program.*;
import com.bitsson.gensokyou.danmaku.program.math.*;
import com.bitsson.gensokyou.danmaku.program.ops.*;
import java.util.*;

/** Dimension-local authority state. No networking, entity spawning, rendering or real damage. */
public final class DanmakuWorldRuntime {
    public enum Phase { TERMINATE, CAPTURE, PREPARE, COMMIT, TRAJECTORY, COLLISION, PUBLISH }
    public enum Kind { START, TARGETS, TARGET_SAMPLE, FRAME, SURFACE, CORRIDOR, INVALIDATE_TARGET, PREPARE, COMMIT, REJECT, REMOVE, CANCEL, END, SOURCE_SAMPLE, STOP }
    public record Limits(int liveMembers, int birthsPerTick, int groups, int plans, int events) {
        public Limits { if (liveMembers<1 || birthsPerTick<1 || groups<1 || plans<1 || events<1) throw new IllegalArgumentException("positive limits required"); }
    }
    public record AdmissionLimits(double nearRadius,int nearMembers,long evaluationsPerTick) {
        public AdmissionLimits { Deterministic.positive(nearRadius); if (nearMembers<1 || evaluationsPerTick<1) throw new IllegalArgumentException("positive admission bounds required"); }
    }
    public record Encounter(UUID owner, long epoch) {}
    public record MemberId(long group, long batch, int index) {}
    public record Event(long revision, long tick, Encounter encounter, Kind kind, long group,
                        List<MemberId> members, String reason, EmissionPlan plan, List<UUID> targets, Vec3 point,
                        List<EmissionPlan> children, Frame frame, DecisionProvider.Surface surface, Boolean accepted,
                        long fadeEnd, boolean revokeWarnings, Path motion) {
        public Event { members=List.copyOf(members); targets=List.copyOf(targets); children=List.copyOf(children); }
    }
    public record MemberSnapshot(MemberId id, EmissionPlan plan, Pattern.Member template, boolean harmful) {}
    public record GroupSnapshot(long id, Encounter encounter, long card, long cycle, Long parent, int targetSlot,
                                boolean background, boolean retain, boolean emitting, long fadeEnd, Frame frame,
                                List<UUID> targets, Set<Integer> invalidTargets, long nextBatch,
                                List<MemberSnapshot> members, List<EmissionPlan> plans) {
        public GroupSnapshot { targets=List.copyOf(targets); invalidTargets=Set.copyOf(invalidTargets); members=List.copyOf(members); plans=List.copyOf(plans); }
    }
    public record SampleKey(long group,String name) {}
    public record Nozzle(ScalarCurve angle,Vec3 axis,long origin) {
        public Actions.Pose apply(Actions.Pose source,double tick) {
            double age=tick-origin, radians=angle.at(age);
            Frame f=source.frame();
            return new Actions.Pose(new Frame(f.origin(),f.direction(Vec3.X.rotate(axis,radians)),f.direction(Vec3.Y.rotate(axis,radians)),f.direction(Vec3.Z.rotate(axis,radians))),
                    source.velocity(),source.angularVelocity().add(f.direction(axis).multiply(Transforms.slope(angle,age))));
        }
    }
    public record RuntimeStats(int groups,int members,int plans,int emitters,int frames,int locks,int commands) {}
    public RuntimeStats stats() { return new RuntimeStats(groups.size(),liveMembers(),groups.values().stream().mapToInt(g->g.plans.size()).sum(),emitters.size(),groups.size(),movementLocks.size(),commands.size()); }
    public record Clocks(long card,long cycle,long group) {}
    public record Targets(List<UUID> uuids,double radius) { public Targets { uuids=List.copyOf(uuids); } }
    public record PendingCommand(long tick,Phase phase,long sequence,AuthorityCommand command) {}
    public record PendingCommit(EmissionPlan plan,AuthorityDescriptions.Commit policy,long ownershipEnd,boolean seal) {}
    public record EmitterSnapshot(long group, Actions.CreateEmitter description, Deterministic.Seed seed,
                                  AuthorityDescriptions.Commit policy, long origin, int nextShot) {}
    public record Checkpoint(String dimension, long tick, long revision, long nextGroup,
                             List<GroupSnapshot> groups, Set<String> consumedTriggers, Set<Long> movementLocks,
                             List<Encounter> encounters, Map<UUID,Long> epochs, List<EmitterSnapshot> emitters,
                             Map<ProgramRef.Group,Long> sourceGroups, Map<MemberId,Actions.Source> following,
                             Map<MemberId,Actions.Pose> lastSourcePoses, Map<MemberId,Path> detached,
                             Map<SampleKey,Vec3> targetSamples, Map<SampleKey,DecisionProvider.Surface> surfaces,
                             Map<MemberId,MemberId> parentSources, Map<Long,GroupMotion> groupMotions,
                             Set<MemberId> attachedMembers, Set<Long> stoppedChildren, AdmissionLimits admissionLimits,
                             List<PendingCommand> commands,long nextSequence,List<PendingCommit> commits,Map<Long,Long> ownershipEnds,
                             Map<ProgramRef.Group,Long> sourceBatches,Map<Long,Clocks> clocks,
                             Map<Long,Double> targetRadii,Map<Long,Frame> capturedFrames,
                             Map<String,Long> groupHandles,Map<String,Targets> targetHandles,Map<MemberId,Nozzle> nozzles,
                             int birthsThisTick,long evaluationsThisTick) {
        public Checkpoint {
            groups=List.copyOf(groups); consumedTriggers=Set.copyOf(consumedTriggers); movementLocks=Set.copyOf(movementLocks);
            encounters=List.copyOf(encounters); epochs=Map.copyOf(epochs); emitters=List.copyOf(emitters); sourceGroups=Map.copyOf(sourceGroups);
            following=Map.copyOf(following); lastSourcePoses=Map.copyOf(lastSourcePoses); detached=Map.copyOf(detached);
            targetSamples=Map.copyOf(targetSamples); surfaces=Map.copyOf(surfaces);
            parentSources=Map.copyOf(parentSources);
            groupMotions=Map.copyOf(groupMotions); attachedMembers=Set.copyOf(attachedMembers); stoppedChildren=Set.copyOf(stoppedChildren);
            commands=List.copyOf(commands);
            commits=List.copyOf(commits); ownershipEnds=Map.copyOf(ownershipEnds);
            sourceBatches=Map.copyOf(sourceBatches);
            clocks=Map.copyOf(clocks); targetRadii=Map.copyOf(targetRadii); capturedFrames=Map.copyOf(capturedFrames);
            groupHandles=Map.copyOf(groupHandles); targetHandles=Map.copyOf(targetHandles);
            nozzles=Map.copyOf(nozzles);
        }
    }
    @FunctionalInterface public interface CollisionSink { void collide(DanmakuWorldRuntime runtime, MemberId id, Path.State from, Path.State until); }
    @FunctionalInterface public interface OperationExecutor { void execute(DanmakuWorldRuntime runtime, ProgramSchedule.Event event, OperationNode operation); }
    private static final class Group {
        final long id, card, cycle; final Encounter encounter; final Long parent; final int slot;
        final boolean background, retain; final Frame frame; final List<UUID> targets;
        final Set<Integer> invalid=new HashSet<>(); final Map<MemberId,MemberSnapshot> members=new LinkedHashMap<>();
        final Map<Long,EmissionPlan> plans=new LinkedHashMap<>();
        long nextBatch, fadeEnd=Long.MAX_VALUE; boolean emitting=true;
        Group(long id, Encounter encounter, long card, long cycle, Long parent, int slot, boolean background,
              boolean retain, Frame frame, List<UUID> targets) {
            this.id=id; this.encounter=encounter; this.card=card; this.cycle=cycle; this.parent=parent; this.slot=slot;
            this.background=background; this.retain=retain; this.frame=frame; this.targets=List.copyOf(targets);
        }
    }
    private record Scheduled(long tick, Phase phase, long sequence, Runnable action) {}
    private record Emitter(long group, Actions.CreateEmitter description, Deterministic.Seed seed,
                           AuthorityDescriptions.Commit policy, long origin, int nextShot) {}
    private final String dimension;
    private final Limits limits;
    private final DecisionProvider world;
    private final Map<Long,Group> groups=new LinkedHashMap<>();
    private final Map<UUID,Encounter> encounters=new LinkedHashMap<>();
    private final Map<UUID,Long> epochs=new HashMap<>();
    private final Set<String> consumed=new HashSet<>();
    private final Set<Long> movementLocks=new HashSet<>();
    private final List<Event> output=new ArrayList<>();
    private final List<Emitter> emitters=new ArrayList<>();
    private final Map<ProgramRef.Group,Long> sourceGroups=new HashMap<>();
    private final Map<ProgramRef.Group,Long> sourceBatches=new HashMap<>();
    private final Map<Long,Clocks> clocks=new HashMap<>();
    private final Map<Long,Double> targetRadii=new HashMap<>();
    private final Map<Long,Frame> capturedFrames=new HashMap<>();
    private final Map<String,Long> groupHandles=new HashMap<>();
    private final Map<String,Targets> targetHandles=new HashMap<>();
    private final Map<MemberId,Nozzle> nozzles=new HashMap<>();
    private final Map<MemberId,Actions.Source> following=new HashMap<>();
    private final Map<MemberId,MemberId> parentSources=new HashMap<>();
    private final Map<Long,GroupMotion> groupMotions=new HashMap<>();
    private final Set<MemberId> attachedMembers=new HashSet<>();
    private final Set<Long> stoppedChildren=new HashSet<>();
    private final List<PendingCommand> commands=new ArrayList<>();
    private final List<PendingCommit> pendingCommits=new ArrayList<>();
    private final Map<Long,Long> ownershipEnds=new HashMap<>();
    private final Map<MemberId,Actions.Pose> lastSourcePoses=new HashMap<>();
    private final Map<MemberId,Path> detached=new HashMap<>();
    private final Map<SampleKey,Vec3> targetSamples=new HashMap<>();
    private final Map<SampleKey,DecisionProvider.Surface> surfaces=new HashMap<>();
    private final PriorityQueue<Scheduled> scheduled=new PriorityQueue<>(Comparator.comparingLong(Scheduled::tick)
            .thenComparing(Scheduled::phase).thenComparingLong(Scheduled::sequence));
    private long tick=-1, revision, nextGroup=1, sequence;
    private int births;
    private long evaluations;
    private AdmissionLimits admissionLimits;
    private Phase phase;
    private CollisionSink collisions=(r,id,a,b)->{};
    private final ThreadLocal<Set<MemberId>> evaluating=ThreadLocal.withInitial(HashSet::new);

    public DanmakuWorldRuntime(String dimension, Limits limits, DecisionProvider world) {
        if (dimension==null || dimension.isBlank()) throw new IllegalArgumentException("dimension required");
        this.dimension=dimension; this.limits=Objects.requireNonNull(limits); this.world=Objects.requireNonNull(world);
        admissionLimits=new AdmissionLimits(16,limits.liveMembers(),Long.MAX_VALUE);
    }
    public void admissionLimits(AdmissionLimits value) { admissionLimits=Objects.requireNonNull(value); }
    public long tick() { return tick; }
    public boolean active(Encounter encounter) { return encounter.equals(encounters.get(encounter.owner())); }
    public boolean hasGroup(long group) { return groups.containsKey(group); }
    public Actions.Pose ownerPose(Encounter encounter) { requireEncounter(encounter); return world.entity(encounter.owner()).orElseThrow(); }
    public void targetHandle(String key,List<UUID> targets,double radius) {
        Deterministic.positive(radius);
        if (targets.size()>5 || new HashSet<>(targets).size()!=targets.size()) throw new IllegalArgumentException("fixed target slots required");
        if (targetHandles.size()>=limits.plans() && !targetHandles.containsKey(key)) throw new IllegalStateException("target handle limit");
        targetHandles.put(key,new Targets(targets,radius));
    }
    public List<UUID> targetHandle(String key) { return Objects.requireNonNull(targetHandles.get(key),"missing target handle "+key).uuids(); }
    public double targetHandleRadius(String key) { return Objects.requireNonNull(targetHandles.get(key),"missing target handle").radius(); }
    public Optional<Vec3> targetPoint(List<UUID> targets,int slot,AuthorityDescriptions.TargetPoint point) {
        return slot<0 || slot>=targets.size()?Optional.empty():world.target(targets.get(slot)).map(p->p.point(point));
    }
    public void groupHandle(String key,long id) {
        group(id);
        if (groupHandles.size()>=limits.groups()) throw new IllegalStateException("group handle limit");
        if (groupHandles.putIfAbsent(key,id)!=null) throw new IllegalArgumentException("duplicate occurrence group handle");
    }
    public OptionalLong groupHandle(String key) { Long id=groupHandles.get(key); return id==null || !hasGroup(id)?OptionalLong.empty():OptionalLong.of(id); }
    public String resolveHandle(String prefix,String name,boolean target) {
        String candidate=prefix+name;
        Map<String,?> handles=target?targetHandles:groupHandles;
        if (handles.containsKey(candidate)) return candidate;
        int open=prefix.indexOf('['), close=prefix.indexOf(']',open);
        if (open<0 || close<0) return candidate;
        String scope=prefix.substring(open+1,close);
        while (!scope.isEmpty()) {
            int comma=scope.lastIndexOf(','); scope=comma<0?"":scope.substring(0,comma).trim();
            candidate=prefix.substring(0,open+1)+scope+"]:"+name;
            if (handles.containsKey(candidate)) return candidate;
        }
        return prefix+name;
    }
    public Actions.Pose groupPose(long id,double time) { Group g=group(id); return groupMotions.getOrDefault(id,GroupMotion.stationary(g.frame,0)).pose(time); }
    public void endMembers(long id,String reason) { for (MemberId member:List.copyOf(group(id).members.keySet())) remove(member,reason); }
    public List<MemberId> members(long id,Selection selection) { return group(id).members.values().stream().filter(m->selection.includes(m.template())).map(MemberSnapshot::id).toList(); }
    public void releaseInstanceHandles(long instance) {
        String prefix=instance+":"; groupHandles.keySet().removeIf(key->key.startsWith(prefix)); targetHandles.keySet().removeIf(key->key.startsWith(prefix));
    }
    public void releaseOldOccurrences(long instance,long minimumCycle) {
        String prefix=instance+":";
        java.util.function.Predicate<String> old=key->{
            if (!key.startsWith(prefix)) return false;
            int start=prefix.length();
            if (start>=key.length() || key.charAt(start)!='[') return false;
            int end=key.indexOf(']',start); if (end<0) return false;
            String first=key.substring(start+1,end).split(",",2)[0].trim();
            return !first.isEmpty() && Long.parseLong(first)<minimumCycle;
        };
        targetHandles.keySet().removeIf(old); groupHandles.keySet().removeIf(old);
    }
    public void lockUntil(long group,long endTick) {
        if (endTick<=tick || commands.size()>=limits.events()) throw new IllegalArgumentException("lock deadline/command limit");
        movementLock(group,true);
        commands.add(new PendingCommand(endTick,Phase.TERMINATE,sequence++,new AuthorityCommand.Movement(group,false)));
    }
    public void close(String reason) {
        for (Encounter encounter:List.copyOf(encounters.values())) endEncounter(encounter,reason);
        scheduled.clear(); commands.clear(); pendingCommits.clear();
        targetHandles.clear(); groupHandles.clear();
    }
    public int liveMembers() { return groups.values().stream().mapToInt(g->g.members.size()).sum(); }
    public int frameReferences() { return groups.size(); }
    public void collisions(CollisionSink sink) { collisions=Objects.requireNonNull(sink); }
    public Encounter startEncounter(UUID owner) {
        Encounter old=encounters.get(owner); if (old!=null) endEncounter(old,"restart");
        Encounter encounter=new Encounter(owner,epochs.merge(owner,1L,Math::addExact));
        encounters.put(owner,encounter); emit(Kind.START,encounter,0,List.of(),"",null,List.of(),null); return encounter;
    }
    private void requireEncounter(Encounter encounter) {
        if (!encounter.equals(encounters.get(encounter.owner()))) throw new IllegalArgumentException("stale encounter");
    }
    public long createGroup(Encounter encounter, long card, long cycle, Long parent, int targetSlot,
                            boolean background, boolean retain, Frame frame, List<UUID> targets) {
        requireEncounter(encounter);
        if (groups.size()>=limits.groups() || targets.size()>5 || new HashSet<>(targets).size()!=targets.size()
                || targetSlot< -1 || targetSlot>=targets.size()) throw new IllegalArgumentException("group limits/slots");
        if (parent!=null) {
            Group p=group(parent);
            if (!p.encounter.equals(encounter) || p.card!=card || p.background!=background) throw new IllegalArgumentException("parent scope mismatch");
        }
        long id=nextGroup++; groups.put(id,new Group(id,encounter,card,cycle,parent,targetSlot,background,retain,Objects.requireNonNull(frame),targets));
        long origin=StrictMath.max(0,tick); clocks.put(id,new Clocks(origin,origin,origin));
        return id;
    }
    public void clocks(long id,Clocks anchors) { group(id); clocks.put(id,Objects.requireNonNull(anchors)); }
    public void targetRadius(long id,double radius) { group(id); Deterministic.positive(radius); targetRadii.put(id,radius); }
    public void groupMotion(long id,GroupMotion motion) { group(id); groupMotions.put(id,Objects.requireNonNull(motion)); }
    /** Group-clock geometry is explicit, rather than accidentally rebased at every batch birth. */
    public void attachToGroup(MemberId id) {
        member(id).orElseThrow();
        if (!groupMotions.containsKey(id.group())) throw new IllegalStateException("install group motion first");
        attachedMembers.add(id);
    }
    public List<UUID> captureTargets(Encounter encounter, Vec3 origin, AuthorityDescriptions.CaptureTargets policy) {
        requireEncounter(encounter); List<UUID> targets=world.captureTargets(origin,policy);
        emit(Kind.TARGETS,encounter,0,List.of(),"",null,targets,null); return targets;
    }
    public Optional<Vec3> sampleTarget(long id,int slot,AuthorityDescriptions.TargetPoint point) {
        Group g=group(id);
        if (slot<0 || slot>=g.targets.size() || g.invalid.contains(slot)) return Optional.empty();
        Optional<Vec3> result=world.target(g.targets.get(slot)).map(p->p.point(point));
        result.ifPresent(p->{
            targetSamples.put(new SampleKey(id,slot+":"+point),p);
            emit(Kind.TARGET_SAMPLE,g.encounter,id,List.of(),Integer.toString(slot),null,List.of(),p);
        });
        return result;
    }
    public void rebase(MemberId id,Path next,boolean discontinuousVelocity) {
        if (output.size()>=limits.events()) throw new IllegalStateException("drain events before rebase");
        MemberSnapshot member=member(id).orElseThrow(); Path.State boundary=state(member,tick);
        Path.State initial=next.at(0);
        if (initial.position().length()>1e-9) throw new IllegalArgumentException("rebased path must begin at zero");
        if (!discontinuousVelocity && initial.velocity().subtract(boundary.velocity()).length()>1e-8)
            throw new IllegalArgumentException("declare rebase velocity discontinuity");
        detached.put(id,new Rebased(boundary.position(),next,tick,member.plan().birthTick()+member.plan().program().lifetimeTicks()));
        following.remove(id); parentSources.remove(id); nozzles.remove(id); attachedMembers.remove(id); lastSourcePoses.remove(id);
        motionEvent(id,"rebase",detached.get(id));
    }
    public record Rebased(Vec3 origin,Path path,long start,long end) implements Path {
        public State at(double time) { State s=path.at(time-start); return new State(origin.add(s.position()),s.velocity(),s.ended()||time>=end); }
        public double radiusBound(double time) { return origin.length()+path.radiusBound(StrictMath.max(0,time-start)); }
    }
    public Optional<Frame> captureFrame(long id,AuthorityDescriptions.CaptureFrame policy) {
        Group g=group(id);
        Optional<Vec3> target=sampleTarget(id,policy.slot(),policy.point());
        Optional<Actions.Pose> owner=world.entity(g.encounter.owner());
        if (target.isEmpty() || owner.isEmpty()) return Optional.empty();
        Vec3 origin=owner.orElseThrow().frame().point(policy.sourceOffset());
        Frame frame=DecisionProvider.captureFrame(origin,target.orElseThrow(),policy.preferredUp(),policy.previousRight(),policy.fallbackRight());
        capturedFrames.put(id,frame);
        resultEvent(Kind.FRAME,g,"capture",frame,null,null); return Optional.of(frame);
    }
    public DecisionProvider.Surface resolveSurface(long id,String name,List<Vec3> points,AuthorityDescriptions.ResolveSurface policy) {
        Group g=group(id);
        var result=world.resolveSurface(points,policy.mode(),policy.maxSteps(),policy.maxQueries());
        surfaces.put(new SampleKey(id,name),result); resultEvent(Kind.SURFACE,g,name,null,result,null); return result;
    }
    public boolean corridor(long id,Vec3 from,Vec3 until,double halfWidth,int maxQueries) {
        Group g=group(id); boolean clear=world.corridor(from,until,halfWidth,maxQueries);
        resultEvent(Kind.CORRIDOR,g,"corridor",null,null,clear); return clear;
    }
    public void schedule(long effectiveTick, Phase at, Runnable action) {
        requireSchedulable(effectiveTick,at);
        reserveScheduled(1);
        scheduled.add(new Scheduled(effectiveTick,at,sequence++,Objects.requireNonNull(action)));
    }
    public void requireSchedulable(long effectiveTick,Phase at) {
        if (effectiveTick<0 || effectiveTick<tick || effectiveTick==tick && (phase==null || at.compareTo(phase)<=0))
            throw new IllegalArgumentException("cannot schedule into an executed phase");
    }
    public void reserveScheduled(int count) { if (count<0 || count>limits.events()-scheduled.size()) throw new IllegalStateException("scheduled work limit"); }
    /** Collision-originated work always starts at the next integer boundary. */
    public void afterCollision(Runnable action) { schedule(Math.addExact(tick,1),Phase.PREPARE,action); }
    public void afterCollision(AuthorityCommand command) {
        if (commands.size()>=limits.events()) throw new IllegalStateException("command queue limit");
        commands.add(new PendingCommand(Math.addExact(tick,1),Phase.PREPARE,sequence++,Objects.requireNonNull(command)));
    }
    public void advance(long serverTick) {
        if (serverTick<0 || serverTick<=tick) throw new IllegalArgumentException("strictly increasing authority ticks required");
        // Do not replay missed world facts against the present world.
        if (tick>=0 && serverTick!=tick+1) throw new IllegalArgumentException("advance every dimension tick");
        tick=serverTick; births=0; evaluations=0;
        for (Phase current:Phase.values()) {
            phase=current;
            if (current==Phase.TERMINATE) terminateInvalid();
            for (PendingCommand command:commands.stream().filter(c->c.tick()==tick && c.phase()==current)
                    .sorted(Comparator.comparingLong(PendingCommand::sequence)).toList()) {
                commands.remove(command); command.command().execute(this);
            }
            while (!scheduled.isEmpty() && scheduled.peek().tick()==tick && scheduled.peek().phase()==current) scheduled.remove().action().run();
            if (current==Phase.PREPARE) { runEmitters(); runChildren(); }
            if (current==Phase.COMMIT) for (PendingCommit pending:List.copyOf(pendingCommits)) if (pending.plan().birthTick()==tick) {
                pendingCommits.remove(pending);
                if (hasGroup(pending.plan().group())) {
                    commit(pending.plan(),pending.policy());
                    if (pending.seal() && hasGroup(pending.plan().group())) stopPlans(pending.plan().group());
                }
            }
            if (current==Phase.COMMIT) runChildren();
            if (current==Phase.COLLISION) collide();
            if (current==Phase.PUBLISH) collect();
        }
        phase=null;
    }
    private void terminateInvalid() {
        for (var end:Map.copyOf(ownershipEnds).entrySet()) if (tick>=end.getValue() && hasGroup(end.getKey())) cancel(end.getKey(),0,"ownership-end");
        for (Encounter e:List.copyOf(encounters.values())) if (world.entity(e.owner()).isEmpty()) endEncounter(e,"owner-unavailable");
        for (Group g:List.copyOf(groups.values())) updateFollowing(g);
        for (Group g:List.copyOf(groups.values())) {
            for (int slot=0;slot<g.targets.size();slot++) if (!g.invalid.contains(slot) && !qualified(g,g.targets.get(slot))) {
                g.invalid.add(slot); emit(Kind.INVALIDATE_TARGET,g.encounter,g.id,List.of(),Integer.toString(slot),null,List.of(),null);
                for (Group personal:groups.values()) if (personal.encounter.equals(g.encounter) && personal.card==g.card
                        && personal.slot==slot && personal.targets.equals(g.targets)) stopEmission(personal.id);
            }
            for (MemberSnapshot m:List.copyOf(g.members.values())) {
                Path.State state=state(m,tick);
                if (state.ended() || !world.loaded(state.position())) remove(m.id(),"lifetime-or-unloaded");
            }
            if (tick>=g.fadeEnd) { for (MemberId id:List.copyOf(g.members.keySet())) remove(id,"fade-end"); }
        }
    }
    private boolean qualified(Group g,UUID target) {
        Optional<DecisionProvider.Player> player=world.target(target);
        if (player.isEmpty()) return false;
        Double radius=targetRadii.get(g.id);
        if (radius==null) return true;
        return world.entity(g.encounter.owner()).map(p->player.orElseThrow().feet().subtract(p.frame().origin()).length()<=radius).orElse(false);
    }
    public EmissionPlan prepare(long id, long birthTick, Actions.Pose source, Actions.ChildProgram program,
                                Actions.Binding binding, Deterministic.Seed seed) {
        Group g=group(id);
        if (program.pattern().countBound()>512) throw new IllegalArgumentException("batch exceeds compiled member limit");
        if (!g.emitting || birthTick<tick || groups.values().stream().mapToInt(scope->scope.plans.size()).sum()>=limits.plans()) throw new IllegalStateException("inactive or full plan scope");
        EmissionPlan plan=new EmissionPlan(id,g.nextBatch++,birthTick,source,program,binding,seed,program.pattern().generate(seed));
        g.plans.put(plan.ordinal(),plan); emit(Kind.PREPARE,g.encounter,id,List.of(),"",plan,List.of(),null); return plan;
    }
    public EmissionPlan prepare(long id,Actions.PrepareEmission description,Actions.ChildProgram program,
                                Actions.Binding binding,Deterministic.Seed seed) {
        Frame frame=description.sampledTarget()==null
                ?new Frame(description.actualSource(),description.resolvedFrame().right(),description.resolvedFrame().up(),description.resolvedFrame().forward())
                :Frame.along(description.actualSource(),description.aimDirection(),description.resolvedFrame().up());
        if (!description.pattern().equals(program.pattern())) throw new IllegalArgumentException("plan and member pattern differ");
        return prepare(id,description.birthTick(),new Actions.Pose(frame,Vec3.ZERO),program,binding,seed);
    }
    public boolean commit(EmissionPlan plan, AuthorityDescriptions.Commit policy) {
        Group g=group(plan.group());
        if (!plan.equals(g.plans.get(plan.ordinal()))) return false;
        if (plan.birthTick()!=tick) throw new IllegalArgumentException("commit only at planned birth boundary");
        int reserve=1;
        if (policy.onFailure()==AuthorityDescriptions.Failure.CANCEL_GROUP) {
            for (Group scope:groups.values()) if (descendant(scope,g.id)) reserve=Math.addExact(reserve,1+scope.members.size());
        }
        if (reserve>limits.events()-output.size()) throw new IllegalStateException("drain event queue before commit");
        g.plans.remove(plan.ordinal());
        String reason=!g.emitting?"stopped":admission(List.of(plan),0,policy);
        if (!reason.isEmpty()) {
            emit(Kind.REJECT,g.encounter,g.id,List.of(),reason,plan,List.of(),null);
            if (policy.onFailure()==AuthorityDescriptions.Failure.CANCEL_GROUP) cancel(g.id,0,"admission-"+reason);
            return false;
        }
        install(g,plan); emit(Kind.COMMIT,g.encounter,g.id,ids(plan),"",plan,List.of(),null); return true;
    }
    public void commitAtBirth(EmissionPlan plan,AuthorityDescriptions.Commit policy,long ownershipEnd) {
        commitAtBirth(plan,policy,ownershipEnd,false);
    }
    public void commitAtBirth(EmissionPlan plan,AuthorityDescriptions.Commit policy,long ownershipEnd,boolean seal) {
        if (pendingCommits.size()>=limits.plans() || ownershipEnd<=plan.birthTick()) throw new IllegalArgumentException("pending commit bound/ownership");
        pendingCommits.add(new PendingCommit(plan,policy,ownershipEnd,seal));
        ownershipEnds.put(plan.group(),ownershipEnd);
    }
    private String admission(List<EmissionPlan> plans,int released,AuthorityDescriptions.Commit policy) {
        return admission(plans,Set.of(),released,policy);
    }
    private String admission(List<EmissionPlan> plans,Set<MemberId> releasedIds,int released,AuthorityDescriptions.Commit policy) {
        long count=plans.stream().mapToLong(p->p.members().size()).sum();
        if (count>policy.memberQuota() || liveMembers()-released+count>limits.liveMembers()
                || births+count>limits.birthsPerTick()) return "budget";
        List<DecisionProvider.Player> combatPlayers=world.players().stream().filter(p->p.alive() && !p.creative() && !p.spectator()).toList();
        long work;
        try { work=Math.multiplyExact(Math.addExact(count,liveMembers()),StrictMath.max(1,combatPlayers.size())); }
        catch (ArithmeticException overflow) { return "evaluation-budget"; }
        if (work>admissionLimits.evaluationsPerTick()-evaluations) return "evaluation-budget";
        evaluations+=work;
        for (DecisionProvider.Player player:combatPlayers) {
            long near=0;
            for (Group scope:groups.values()) for (MemberSnapshot m:scope.members.values())
                if (!releasedIds.contains(m.id()) && player.distance(state(m,tick).position())<=admissionLimits.nearRadius()) near++;
            for (EmissionPlan plan:plans) for (Pattern.Member m:plan.members())
                if (player.distance(plan.state(m,tick).position())<=admissionLimits.nearRadius()) near++;
            if (near>admissionLimits.nearMembers()) return "near-density";
        }
        for (EmissionPlan p:plans) for (Pattern.Member m:p.members()) {
            Vec3 position=p.state(m,tick).position();
            if (!world.loaded(position)) return "unloaded";
            Group g=group(p.group());
            for (DecisionProvider.Player player:combatPlayers) if ((policy.allCombatPlayers() || g.targets.contains(player.uuid()))
                    && player.distance(position)-m.style().collisionRadius()<policy.minimumClearance()) return "clearance";
        }
        return "";
    }
    private List<MemberId> ids(EmissionPlan p) { return p.members().stream().map(m->new MemberId(p.group(),p.ordinal(),m.index())).toList(); }
    private void install(Group g,EmissionPlan p) {
        for (Pattern.Member m:p.members()) {
            MemberId id=new MemberId(g.id,p.ordinal(),m.index()); g.members.put(id,new MemberSnapshot(id,p,m,true));
        }
        births+=p.members().size();
    }
    public Optional<MemberSnapshot> member(MemberId id) { Group g=groups.get(id.group()); return g==null?Optional.empty():Optional.ofNullable(g.members.get(id)); }
    public Path.State state(MemberId id,double time) { return state(member(id).orElseThrow(),time); }
    private Path.State state(MemberSnapshot m,double time) {
        Set<MemberId> stack=evaluating.get();
        if (stack.size()>=16 || !stack.add(m.id())) throw new IllegalStateException("cyclic or excessive source dependency");
        try { return evaluateState(m,time); } finally { stack.remove(m.id()); }
    }
    private Path.State evaluateState(MemberSnapshot m,double time) {
        Path detachedPath=detached.get(m.id());
        if (detachedPath!=null) return detachedPath.at(time);
        Actions.Source attached=following.get(m.id());
        MemberId parent=parentSources.get(m.id());
        if (attached==null && parent==null) {
            if (attachedMembers.contains(m.id())) return groupMotions.get(m.id().group()).apply(
                    m.plan().program().memberState(m.template(),m.plan().seed(),time-m.plan().birthTick()),time);
            return m.plan().state(m.template(),time);
        }
        Actions.Pose pose=(parent!=null?parentPose(parent,time):source(group(m.id().group()),attached,time)).orElseGet(()->lastSourcePoses.get(m.id()));
        if (pose==null) throw new IllegalStateException("follow source has no authoritative pose");
        Nozzle nozzle=nozzles.get(m.id()); if (nozzle!=null) pose=nozzle.apply(pose,time);
        Actions.Pose currentPose=pose;
        var spawn=new Actions.SpawnChild(attached==null?new Actions.GroupPose(new ProgramRef.Group(new NodeId(0))):attached,m.plan().program(),m.plan().binding());
        return Actions.emittedState(spawn,(source,t)->Optional.of(t==m.plan().birthTick()?m.plan().source():currentPose),
                m.plan().birthTick(),time,m.template(),m.plan().seed()).orElseThrow();
    }
    public void remove(MemberId id,String reason) {
        Group g=groups.get(id.group());
        if (g!=null && g.members.remove(id)!=null) {
            following.remove(id); lastSourcePoses.remove(id); detached.remove(id);
            parentSources.remove(id);
            nozzles.remove(id); attachedMembers.remove(id);
            emit(Kind.REMOVE,g.encounter,g.id,List.of(id),reason,null,List.of(),null);
        }
    }
    public void stopEmission(long id) {
        Group g=group(id);
        if (output.size()>=limits.events()) throw new IllegalStateException("drain events before stop");
        stopPlans(id); stoppedChildren.add(id); emit(Kind.STOP,g.encounter,id,List.of(),"stop-emission",null,List.of(),null);
    }
    public void sealPlans(long id) { stopPlans(id); }
    private void stopPlans(long id) { Group g=group(id); g.emitting=false; g.plans.clear(); }
    public void cancel(long id,long fadeTicks,String reason) {
        if (fadeTicks<0) throw new IllegalArgumentException("negative fade");
        Group root=group(id);
        int required=0;
        for (Group g:groups.values()) if (descendant(g,root.id)) required=Math.addExact(required,1+(fadeTicks==0?g.members.size():0));
        if (required>limits.events()-output.size()) throw new IllegalStateException("drain event queue before cancellation");
        for (Group g:List.copyOf(groups.values())) if (descendant(g,root.id)) {
            stopPlans(g.id); movementLocks.remove(g.id); g.fadeEnd=Math.addExact(StrictMath.max(0,tick),fadeTicks);
            stoppedChildren.add(g.id);
            if (fadeTicks==0) for (MemberId m:List.copyOf(g.members.keySet())) remove(m,reason);
            else g.members.replaceAll((key,m)->new MemberSnapshot(key,m.plan(),m.template(),false));
            emit(Kind.CANCEL,g.encounter,g.id,List.of(),reason,null,List.of(),null);
        }
    }
    private boolean descendant(Group g,long root) {
        for (Group cursor=g;cursor!=null;cursor=cursor.parent==null?null:groups.get(cursor.parent)) if (cursor.id==root) return true;
        return false;
    }
    public void endCard(Encounter encounter,long card) {
        requireEncounter(encounter);
        for (Group g:List.copyOf(groups.values())) if (g.encounter.equals(encounter) && g.card==card && !g.background) cancel(g.id,0,"card-end");
        collect();
    }
    public void endCycle(Encounter encounter,long card,long cycle) {
        requireEncounter(encounter);
        for (Group g:List.copyOf(groups.values())) if (g.encounter.equals(encounter) && g.card==card && g.cycle==cycle && !g.background) {
            stopPlans(g.id); if (!g.retain) cancel(g.id,0,"cycle-end");
        }
        collect();
    }
    public void endEncounter(Encounter encounter,String reason) {
        requireEncounter(encounter);
        for (Group g:List.copyOf(groups.values())) if (g.encounter.equals(encounter)) cancel(g.id,0,reason);
        groups.values().removeIf(g->g.encounter.equals(encounter));
        emitters.removeIf(e->!groups.containsKey(e.group()));
        encounters.remove(encounter.owner());
        emit(Kind.END,encounter,0,List.of(),reason,null,List.of(),null);
        consumed.removeIf(key->key.startsWith(encounter.owner()+":"+encounter.epoch()+":"));
        collect();
    }
    public void movementLock(long group,boolean locked) { group(group); if (locked) movementLocks.add(group); else movementLocks.remove(group); }
    public boolean movementLocked(Encounter encounter) { return movementLocks.stream().anyMatch(id->group(id).encounter.equals(encounter)); }
    private void collect() {
        boolean removed;
        do { removed=groups.values().removeIf(g->!g.emitting && g.members.isEmpty() && g.plans.isEmpty()
                && groups.values().stream().noneMatch(child->Objects.equals(child.parent,g.id))); } while (removed);
        movementLocks.retainAll(groups.keySet());
        emitters.removeIf(e->!groups.containsKey(e.group()) || !groups.get(e.group()).emitting);
        sourceGroups.values().removeIf(id->!groups.containsKey(id));
        sourceBatches.keySet().retainAll(sourceGroups.keySet());
        groupHandles.values().removeIf(id->!hasGroup(id));
        consumed.removeIf(key->groups.values().stream().noneMatch(g->key.startsWith(triggerKey(g,""))));
        following.keySet().removeIf(id->member(id).isEmpty());
        parentSources.keySet().removeIf(id->member(id).isEmpty());
        lastSourcePoses.keySet().removeIf(id->!following.containsKey(id) && !parentSources.containsKey(id));
        detached.keySet().removeIf(id->member(id).isEmpty());
        nozzles.keySet().removeIf(id->member(id).isEmpty());
        targetSamples.keySet().removeIf(key->!groups.containsKey(key.group()));
        surfaces.keySet().removeIf(key->!groups.containsKey(key.group()));
        clocks.keySet().retainAll(groups.keySet()); targetRadii.keySet().retainAll(groups.keySet()); capturedFrames.keySet().retainAll(groups.keySet());
        groupMotions.keySet().retainAll(groups.keySet()); stoppedChildren.retainAll(groups.keySet());
        ownershipEnds.keySet().retainAll(groups.keySet());
        pendingCommits.removeIf(p->!hasGroup(p.plan().group()) || !group(p.plan().group()).plans.containsKey(p.plan().ordinal()));
        attachedMembers.removeIf(id->member(id).isEmpty());
    }
    private void collide() {
        for (Group g:List.copyOf(groups.values())) for (MemberSnapshot m:List.copyOf(g.members.values()))
            if (member(m.id()).isPresent() && damaging(m,tick)) collisions.collide(this,m.id(),state(m,tick),state(m,tick+1));
    }
    public boolean damaging(MemberId id,double time) { return damaging(member(id).orElseThrow(),time); }
    private boolean damaging(MemberSnapshot member,double time) {
        if (!member.harmful() || state(member,time).ended()) return false;
        Clocks anchors=clocks.get(member.id().group());
        return member.plan().program().presentation().at(new Presentation.Clocks(time-anchors.card(),time-anchors.cycle(),
                time-anchors.group(),time-member.plan().birthTick())).damaging();
    }
    private Group group(long id) { Group g=groups.get(id); if (g==null) throw new IllegalArgumentException("unknown group "+id); return g; }
    private void emit(Kind kind,Encounter encounter,long group,List<MemberId> members,String reason,EmissionPlan plan,List<UUID> targets,Vec3 point) {
        if (output.size()>=limits.events()) throw new IllegalStateException("event queue full; drain before advancing");
        Group state=groups.get(group);
        output.add(new Event(++revision,StrictMath.max(0,tick),encounter,kind,group,members,reason,plan,targets,point,List.of(),null,null,null,
                state==null?Long.MAX_VALUE:state.fadeEnd,kind==Kind.CANCEL || kind==Kind.STOP,null));
    }
    private void resultEvent(Kind kind,Group g,String reason,Frame frame,DecisionProvider.Surface surface,Boolean accepted) {
        if (output.size()>=limits.events()) throw new IllegalStateException("event queue full");
        output.add(new Event(++revision,StrictMath.max(0,tick),g.encounter,kind,g.id,List.of(),reason,null,List.of(),null,List.of(),frame,surface,accepted,g.fadeEnd,false,null));
    }
    private void motionEvent(MemberId id,String reason,Path motion) {
        Group g=group(id.group());
        if (output.size()>=limits.events()) throw new IllegalStateException("event queue full");
        output.add(new Event(++revision,tick,g.encounter,Kind.SOURCE_SAMPLE,g.id,List.of(id),reason,null,List.of(),null,List.of(),null,null,null,g.fadeEnd,false,motion));
    }
    public List<Event> drainEvents() { List<Event> result=List.copyOf(output); output.clear(); return result; }
    public Checkpoint checkpoint() {
        if (!scheduled.isEmpty()) throw new IllegalStateException("opaque scheduled callbacks cannot be checkpointed; drain them first");
        return new Checkpoint(dimension,tick,revision,nextGroup,groups.values().stream().map(g->new GroupSnapshot(g.id,g.encounter,g.card,g.cycle,g.parent,g.slot,
                g.background,g.retain,g.emitting,g.fadeEnd,g.frame,g.targets,g.invalid,g.nextBatch,List.copyOf(g.members.values()),List.copyOf(g.plans.values()))).toList(),consumed,movementLocks,
                List.copyOf(encounters.values()),epochs,emitters.stream().map(e->new EmitterSnapshot(e.group(),e.description(),e.seed(),e.policy(),e.origin(),e.nextShot())).toList(),sourceGroups,following,lastSourcePoses,detached,targetSamples,surfaces,parentSources,groupMotions,attachedMembers,stoppedChildren,admissionLimits,commands,sequence,pendingCommits,ownershipEnds,sourceBatches,clocks,targetRadii,capturedFrames,groupHandles,targetHandles,nozzles,births,evaluations);
    }
    /** In-memory recovery only. Network owns bounded wire decoding; disk persistence is deliberately absent. */
    public static DanmakuWorldRuntime restore(Checkpoint checkpoint,Limits limits,DecisionProvider world) {
        DanmakuWorldRuntime r=new DanmakuWorldRuntime(checkpoint.dimension(),limits,world);
        r.tick=checkpoint.tick(); r.revision=checkpoint.revision(); r.nextGroup=checkpoint.nextGroup();
        r.epochs.putAll(checkpoint.epochs());
        for (Encounter e:checkpoint.encounters()) r.encounters.put(e.owner(),e);
        for (GroupSnapshot s:checkpoint.groups()) {
            Group g=new Group(s.id(),s.encounter(),s.card(),s.cycle(),s.parent(),s.targetSlot(),s.background(),s.retain(),s.frame(),s.targets());
            g.invalid.addAll(s.invalidTargets()); g.nextBatch=s.nextBatch(); g.emitting=s.emitting(); g.fadeEnd=s.fadeEnd();
            for (MemberSnapshot m:s.members()) g.members.put(m.id(),m);
            for (EmissionPlan p:s.plans()) g.plans.put(p.ordinal(),p);
            r.groups.put(g.id,g);
        }
        r.consumed.addAll(checkpoint.consumedTriggers()); r.movementLocks.addAll(checkpoint.movementLocks());
        r.sourceGroups.putAll(checkpoint.sourceGroups());
        r.sourceBatches.putAll(checkpoint.sourceBatches());
        r.clocks.putAll(checkpoint.clocks()); r.targetRadii.putAll(checkpoint.targetRadii()); r.capturedFrames.putAll(checkpoint.capturedFrames());
        r.groupHandles.putAll(checkpoint.groupHandles()); r.targetHandles.putAll(checkpoint.targetHandles());
        r.nozzles.putAll(checkpoint.nozzles());
        r.births=checkpoint.birthsThisTick(); r.evaluations=checkpoint.evaluationsThisTick();
        r.following.putAll(checkpoint.following()); r.lastSourcePoses.putAll(checkpoint.lastSourcePoses()); r.detached.putAll(checkpoint.detached());
        r.targetSamples.putAll(checkpoint.targetSamples()); r.surfaces.putAll(checkpoint.surfaces());
        r.parentSources.putAll(checkpoint.parentSources());
        r.groupMotions.putAll(checkpoint.groupMotions()); r.attachedMembers.addAll(checkpoint.attachedMembers()); r.stoppedChildren.addAll(checkpoint.stoppedChildren());
        r.admissionLimits=checkpoint.admissionLimits();
        r.commands.addAll(checkpoint.commands()); r.sequence=checkpoint.nextSequence();
        r.pendingCommits.addAll(checkpoint.commits()); r.ownershipEnds.putAll(checkpoint.ownershipEnds());
        for (EmitterSnapshot e:checkpoint.emitters()) r.emitters.add(new Emitter(e.group(),e.description(),e.seed(),e.policy(),e.origin(),e.nextShot()));
        if (r.groups.size()>limits.groups() || r.liveMembers()>limits.liveMembers() || r.commands.size()>limits.events()
                || r.emitters.size()>limits.plans() || r.pendingCommits.size()>limits.plans()
                || r.groups.values().stream().mapToInt(g->g.plans.size()).sum()>limits.plans()) throw new IllegalArgumentException("checkpoint exceeds runtime limits");
        long greatest=0;
        for (Group g:r.groups.values()) {
            r.requireEncounter(g.encounter); greatest=StrictMath.max(greatest,g.id);
            if (g.parent!=null && (!r.groups.containsKey(g.parent) || g.parent>=g.id)) throw new IllegalArgumentException("invalid parent closure");
            for (MemberSnapshot m:g.members.values()) if (m.id().group()!=g.id || m.id().batch()>=g.nextBatch || m.id().index()!=m.template().index())
                throw new IllegalArgumentException("invalid member identity");
        }
        if (r.nextGroup<=greatest) throw new IllegalArgumentException("group identity would be reused");
        return r;
    }

    private String triggerKey(Group g,String trigger) { return g.encounter.owner()+":"+g.encounter.epoch()+":"+g.id+":"+trigger; }
    /** Consumes successes and failures once. All selected parents share a single result revision. */
    public boolean replace(long id,List<MemberId> selected,Actions.ReplaceMembers replacement,
                           AuthorityDescriptions.Commit policy,String trigger) {
        return replace(id,selected,replacement,policy,trigger,0);
    }
    private boolean replace(long id,List<MemberId> selected,Actions.ReplaceMembers replacement,
                            AuthorityDescriptions.Commit policy,String trigger,int channel) {
        Group g=group(id); String key=triggerKey(g,trigger);
        int eventBound=replacement.scope()==Actions.AtomicScope.PER_PARENT?selected.size():1;
        if (eventBound>limits.events()-output.size()) throw new IllegalStateException("drain event queue before replace");
        if (!consumed.add(key)) return false;
        List<MemberSnapshot> parents=selected.stream().distinct().map(g.members::get).filter(Objects::nonNull)
                .filter(m->replacement.parents().includes(m.template())).sorted(Comparator.comparingInt(m->m.id().index())).toList();
        if (parents.isEmpty()) return false;
        if (replacement.scope()==Actions.AtomicScope.PER_PARENT) {
            boolean all=true;
            for (MemberSnapshot parent:parents) all=replaceUnit(g,List.of(parent),replacement,policy,channel) && all;
            return all;
        }
        return replaceUnit(g,parents,replacement,policy,channel);
    }
    private boolean replaceUnit(Group g,List<MemberSnapshot> parents,Actions.ReplaceMembers replacement,AuthorityDescriptions.Commit policy,int channel) {
        if (output.size()>=limits.events()) throw new IllegalStateException("drain event queue before transaction");
        List<EmissionPlan> children=new ArrayList<>();
        for (MemberSnapshot parent:parents) {
            Path.State boundary=state(parent,tick);
            var seed=parent.plan().seed().child(parent.template().index()).child(channel);
            Actions.Pose pose=new Actions.Pose(new Frame(boundary.position(),Vec3.X,Vec3.Y,Vec3.Z),boundary.velocity());
            children.add(new EmissionPlan(g.id,g.nextBatch++,tick,pose,replacement.child(),Actions.Binding.snapshot(),seed,replacement.child().pattern().generate(seed)));
        }
        String reason=admission(children,new HashSet<>(parents.stream().map(MemberSnapshot::id).toList()),parents.size(),policy);
        if (!reason.isEmpty()) {
            if (replacement.onReject()==Actions.OnReject.END_PARENT) for (MemberSnapshot p:parents) releaseMember(g,p.id());
            emit(Kind.REJECT,g.encounter,g.id,parents.stream().map(MemberSnapshot::id).toList(),"replace-"+reason,null,List.of(),null);
            return false;
        }
        // No callbacks between release and installation. One revision describes the whole mutation.
        for (MemberSnapshot p:parents) releaseMember(g,p.id());
        for (EmissionPlan p:children) install(g,p);
        if (output.size()>=limits.events()) throw new IllegalStateException("event queue full");
        output.add(new Event(++revision,tick,g.encounter,Kind.COMMIT,g.id,parents.stream().map(MemberSnapshot::id).toList(),
                "replace",null,List.of(),null,children,null,null,true,g.fadeEnd,false,null));
        return true;
    }
    private void releaseMember(Group group,MemberId id) {
        group.members.remove(id); following.remove(id); parentSources.remove(id); lastSourcePoses.remove(id); detached.remove(id);
        nozzles.remove(id); attachedMembers.remove(id);
    }
    private void runChildren() {
        Set<MemberId> processed=new HashSet<>();
        boolean progress;
        do {
        progress=false;
        for (Group g:List.copyOf(groups.values())) if (!stoppedChildren.contains(g.id)) {
            List<MemberSnapshot> snapshot=List.copyOf(g.members.values());
            Set<String> handled=new HashSet<>();
            for (MemberSnapshot m:snapshot) if (g.members.containsKey(m.id()) && processed.add(m.id())) {
                progress=true;
                List<Actions.ChildAction> actions=m.plan().program().actions();
                for (int a=0;a<actions.size();a++) {
                    var action=actions.get(a);
                    double first=StrictMath.ceil(action.trigger().tick(m.plan().program().path()));
                    double age=tick-m.plan().birthTick();
                    double occurrence=(age-first)/action.period();
                    if (occurrence<0 || occurrence>=action.count() || occurrence!=StrictMath.floor(occurrence) || !action.selection().includes(m.template())) continue;
                    String key=m.id().batch()+":"+a+":"+(long)occurrence;
                    var policy=new AuthorityDescriptions.Commit(new ProgramRef.Plan(new NodeId(0)),0,4096,AuthorityDescriptions.Failure.REJECT_BATCH,true);
                    if (action.kind()==Actions.ChildActionKind.REPLACE) {
                        if (!handled.add(key)) continue;
                        var parents=snapshot.stream().filter(p->p.id().batch()==m.id().batch()).map(MemberSnapshot::id).toList();
                        replace(g.id,parents,new Actions.ReplaceMembers(action.selection(),action.child(),action.scope(),action.onReject()),policy,key,a);
                    } else {
                        String shotKey=triggerKey(g,key+":"+m.id().index());
                        if (!consumed.add(shotKey)) continue;
                        Path.State boundary=state(m,tick);
                        var seed=m.plan().seed().child(m.template().index()).child(a).child((int)occurrence);
                        Actions.Pose pose=new Actions.Pose(Frame.along(boundary.position(),Vec3.Z.rotate(action.nozzleAxis(),action.nozzleAngleRadians().at(age)),Vec3.Y),boundary.velocity());
                        boolean emitting=g.emitting;
                        g.emitting=true;
                        EmissionPlan p;
                        try { p=prepare(g.id,tick,pose,action.child(),action.binding(),seed); }
                        finally { g.emitting=emitting; }
                        // Existing independent carriers keep their finite child schedule across a normal cycle.
                        g.emitting=true;
                        boolean accepted;
                        try { accepted=commit(p,policy); } finally { g.emitting=emitting; }
                        if (accepted && action.binding().attachment()==Actions.Attachment.FOLLOW_SOURCE) {
                            for (MemberId child:ids(p)) {
                                parentSources.put(child,m.id()); lastSourcePoses.put(child,new Actions.Pose(new Frame(boundary.position(),Vec3.X,Vec3.Y,Vec3.Z),boundary.velocity()));
                                nozzles.put(child,new Nozzle(action.nozzleAngleRadians(),action.nozzleAxis(),m.plan().birthTick()));
                            }
                        }
                    }
                }
            }
        }
        } while (progress);
    }

    public void createEmitter(long group,Actions.CreateEmitter emitter,Deterministic.Seed seed,AuthorityDescriptions.Commit policy) {
        Group g=group(group);
        if (!g.emitting || emitters.size()>=limits.plans()) throw new IllegalStateException("emitter scope/limit");
        if (!(emitter.source() instanceof Actions.ExternalEntitySource)) {
            ProgramRef.Group ref=emitter.source() instanceof Actions.GroupPose p?p.group():((Actions.MemberPose)emitter.source()).group();
            Long source=sourceGroups.get(ref);
            if (source==null) throw new IllegalArgumentException("bind emitter source before installation");
            if (emitter.binding().attachment()==Actions.Attachment.FOLLOW_SOURCE && source==group)
                throw new IllegalArgumentException("followSource requires a distinct source group");
        }
        emitters.add(new Emitter(group,emitter,seed,policy,StrictMath.max(0,tick),0));
    }
    public void bindSource(ProgramRef.Group ref,long id) {
        group(id);
        Long previous=sourceGroups.putIfAbsent(ref,id);
        if (previous!=null && previous!=id) throw new IllegalArgumentException("source reference already bound; use an occurrence-local reference");
    }
    public void bindSource(ProgramRef.Group ref,long id,long batch) {
        Group g=group(id);
        if (g.members.keySet().stream().noneMatch(m->m.batch()==batch)) throw new IllegalArgumentException("unknown source batch");
        bindSource(ref,id);
        Long previous=sourceBatches.putIfAbsent(ref,batch);
        if (previous!=null && previous!=batch) throw new IllegalArgumentException("source batch cannot be rebound");
    }
    private Optional<Actions.Pose> source(Group g,Actions.Source source,double time) {
        if (source instanceof Actions.ExternalEntitySource external) return world.entity(external.uuid());
        ProgramRef.Group ref=source instanceof Actions.GroupPose p?p.group():((Actions.MemberPose)source).group();
        Long bound=sourceGroups.get(ref);
        Group resolved=bound==null?null:groups.get(bound);
        if (resolved==null || !resolved.encounter.equals(g.encounter)) return Optional.empty();
        if (source instanceof Actions.GroupPose) return Optional.of(groupMotions.getOrDefault(resolved.id,GroupMotion.stationary(resolved.frame,0)).pose(time));
        Actions.MemberPose member=(Actions.MemberPose)source;
        Long batch=sourceBatches.get(ref);
        List<MemberSnapshot> matching=resolved.members.values().stream().filter(m->m.template().index()==member.stableIndex()
                && (batch==null || m.id().batch()==batch)).toList();
        if (matching.size()>1) throw new IllegalArgumentException("MemberPose requires an unambiguous batch-local source group");
        return matching.stream().findFirst().map(m->{
            Path.State s=state(m,time); return new Actions.Pose(new Frame(s.position(),Vec3.X,Vec3.Y,Vec3.Z),s.velocity());
        });
    }
    private void runEmitters() {
        for (Emitter e:List.copyOf(emitters)) {
            Group g=groups.get(e.group());
            if (g==null || !g.emitting) { emitters.remove(e); continue; }
            long due=Math.addExact(e.origin(),e.description().shotTick(e.nextShot()));
            if (due!=tick) continue;
            Optional<Actions.Pose> sampled=source(g,e.description().source(),tick);
            emitters.remove(e);
            if (sampled.isEmpty()) continue; // Source loss ends this emitter, not its independent children.
            Actions.Pose pose=e.description().nozzle(sampled.orElseThrow(),tick-e.origin());
            var seed=e.seed().child(e.nextShot());
            EmissionPlan p=prepare(g.id,tick,pose,e.description().child(),e.description().binding(),seed);
            if (e.description().source() instanceof Actions.ExternalEntitySource)
                emit(Kind.SOURCE_SAMPLE,g.encounter,g.id,List.of(),"external-shot",p,List.of(),pose.frame().origin());
            if (commit(p,e.policy()) && e.description().binding().attachment()==Actions.Attachment.FOLLOW_SOURCE) {
                for (MemberId id:ids(p)) {
                    following.put(id,e.description().source()); lastSourcePoses.put(id,sampled.orElseThrow());
                    nozzles.put(id,new Nozzle(e.description().nozzleAngleRadians(),e.description().nozzleAxis(),e.origin()));
                }
            }
            if (e.nextShot()+1<e.description().count()) emitters.add(new Emitter(e.group(),e.description(),e.seed(),e.policy(),e.origin(),e.nextShot()+1));
        }
    }
    private void updateFollowing(Group g) {
        for (MemberSnapshot m:List.copyOf(g.members.values())) {
            Actions.Source ref=following.get(m.id()); MemberId parent=parentSources.get(m.id());
            if (ref==null && parent==null) continue;
            Optional<Actions.Pose> pose=parent!=null?parentPose(parent,tick):source(g,ref,tick);
            if (pose.isPresent()) { lastSourcePoses.put(m.id(),pose.orElseThrow()); continue; }
            if (m.plan().binding().onSourceLost()==Actions.OnSourceLost.END) { remove(m.id(),"source-lost"); continue; }
            Path.State boundary=state(m,tick);
            // Immutable analytic world path; no further source reads after detachment.
            detached.put(m.id(),new WorldDetached(boundary.position(),boundary.velocity(),tick,m.plan().birthTick()+m.plan().program().lifetimeTicks()));
            following.remove(m.id()); lastSourcePoses.remove(m.id());
            parentSources.remove(m.id());
            nozzles.remove(m.id());
            motionEvent(m.id(),"detach-last-authoritative-pose",detached.get(m.id()));
        }
    }
    private Optional<Actions.Pose> parentPose(MemberId parent,double time) {
        return member(parent).map(m->{ Path.State s=state(m,time); return new Actions.Pose(new Frame(s.position(),Vec3.X,Vec3.Y,Vec3.Z),s.velocity()); });
    }
    public record WorldDetached(Vec3 position,Vec3 velocity,long origin,long end) implements Path {
        public State at(double time) { return new State(position.add(velocity.multiply(time-origin)),velocity,time>=end); }
        public double radiusBound(double time) { return position.length()+velocity.length()*StrictMath.abs(time-origin); }
    }

    public boolean proximity(MemberId id,double radius,boolean once,String trigger) {
        Deterministic.positive(radius); MemberSnapshot m=member(id).orElseThrow(); Group g=group(id.group());
        String key=triggerKey(g,"proximity:"+id+":"+trigger);
        if (once && consumed.contains(key)) return false;
        Vec3 position=state(m,tick).position();
        boolean triggered=world.players().stream().anyMatch(p->p.alive() && !p.creative() && !p.spectator() && p.distance(position)<=radius);
        if (triggered) { if (once) consumed.add(key); emit(Kind.TARGET_SAMPLE,g.encounter,g.id,List.of(id),"proximity:"+trigger,null,List.of(),position); }
        return triggered;
    }
    public void endOnReturn(MemberId id,AuthorityDescriptions.EndOnReturn policy) {
        MemberSnapshot m=member(id).orElseThrow();
        if (policy.ended(tick-m.plan().birthTick())) remove(id,"return-origin");
    }
}
