package com.bitsson.gensokyou.danmaku.program.network;

import com.bitsson.gensokyou.danmaku.program.*;
import com.bitsson.gensokyou.danmaku.program.math.*;
import com.bitsson.gensokyou.danmaku.program.ops.*;
import com.bitsson.gensokyou.danmaku.program.server.*;
import java.util.*;

/** Data projection of an authority boundary. No scheduler, world access, or side-effect callbacks. */
public final class ReplicaState {
    private ReplicaState() {}
    public record Group(long id, DanmakuWorldRuntime.Encounter encounter, long card, long cycle,
                        Long parent, int slot, boolean background, boolean emitting, long fadeEnd,
                        Frame frame, List<UUID> targets, Set<Integer> invalid,
                        DanmakuWorldRuntime.Clocks clocks, GroupMotion motion) {
        public Group { targets=List.copyOf(targets); invalid=Set.copyOf(invalid); Objects.requireNonNull(frame); Objects.requireNonNull(clocks); }
    }
    /** Mask bit positions address the ordered templates, never renumber stable member indices. */
    public record Batch(long group, long ordinal, long birth, Actions.Pose source, String description,
                        Actions.Binding binding, Deterministic.Seed seed, List<Pattern.Member> templates,
                         List<Long> alive, List<Long> harmful, boolean warning, GroupMotion motion) {
        public Batch(long group,long ordinal,long birth,Actions.Pose source,String description,Actions.Binding binding,
                     Deterministic.Seed seed,List<Pattern.Member> templates,List<Long> alive,List<Long> harmful,boolean warning) {
            this(group,ordinal,birth,source,description,binding,seed,templates,alive,harmful,warning,null);
        }
        public Batch { templates=List.copyOf(templates); alive=List.copyOf(alive); harmful=List.copyOf(harmful); }
        public boolean alive(int position) { return bit(alive,position); }
        public boolean harmful(int position) { return bit(harmful,position); }
    }
    public record Motion(DanmakuWorldRuntime.MemberId id, boolean attached, Path detached,
                         Long sourceGroup, DanmakuWorldRuntime.MemberId sourceMember,
                         Actions.Pose lastPose, DanmakuWorldRuntime.Nozzle nozzle) {}
    public record Mask(long group,long ordinal,List<Long> alive,List<Long> harmful,boolean warning) {
        public Mask { alive=List.copyOf(alive); harmful=List.copyOf(harmful); }
    }
    public record Instance(long id, DanmakuWorldRuntime.Encounter encounter,long card,boolean background,
                           String hash, ClockAnchors anchors,long nextTick) {}
    public record Cursor(long group, String key, long next) {}
    public record Emitter(long group,Actions.CreateEmitter description,Deterministic.Seed seed,
                           AuthorityDescriptions.Commit policy,long origin,int nextShot,Long sourceGroup) {}
    public record Pending(long group,long batch,AuthorityDescriptions.Commit policy,long ownershipEnd,boolean seal) {}
    public record SurfacePoint(Vec3 point) {}
    public record Sample(long group,String name,Vec3 point,List<SurfacePoint> surface,int queries) {
        public Sample { surface=List.copyOf(surface); }
    }
    public record Beam(DanmakuWorldRuntime.MemberId id,double length) {
        public Beam { Deterministic.nonnegative(length); }
    }
    public record Image(String dimension,long tick,long revision,Map<String,Record> entries) {
        public Image { entries=Map.copyOf(entries); }
    }
    public record Projection(Image image,Map<String,Actions.ChildProgram> descriptions,
                             Map<String,ProgramDefinition> definitions) {
        public Projection { descriptions=Map.copyOf(descriptions); definitions=Map.copyOf(definitions); }
    }
    public static String groupKey(long id) { return "g/"+id; }
    public static String batchKey(long group,long batch) { return "b/"+group+"/"+batch; }
    public static String motionKey(DanmakuWorldRuntime.MemberId id) { return "m/"+id.group()+"/"+id.batch()+"/"+id.index(); }
    private static boolean bit(List<Long> words,int index) { return (words.get(index/64)&(1L<<(index%64)))!=0; }
    private static List<Long> mask(List<Pattern.Member> templates,Set<Integer> indices) {
        Long[] words=new Long[(templates.size()+63)/64]; Arrays.fill(words,0L);
        for (int i=0;i<templates.size();i++) if (indices.contains(templates.get(i).index())) words[i/64]|=1L<<(i%64);
        return List.of(words);
    }
    public static Projection capture(ProgramSession.Snapshot snapshot,ProgramWire wire) {
        var c=snapshot.runtime(); Map<String,Record> out=new TreeMap<>();
        Map<String,Actions.ChildProgram> descriptions=new TreeMap<>(); Map<String,ProgramDefinition> definitions=new TreeMap<>();
        for (var i:snapshot.instances()) {
            var cursor=i.cursor(); definitions.put(cursor.definition().hash(),cursor.definition());
            out.put("i/"+i.id(),new Instance(i.id(),i.encounter(),i.card(),i.background(),cursor.definition().hash(),cursor.anchors(),cursor.nextTick()));
        }
        for (var g:c.groups()) {
            out.put(groupKey(g.id()),new Group(g.id(),g.encounter(),g.card(),g.cycle(),g.parent(),g.targetSlot(),g.background(),g.emitting(),g.fadeEnd(),
                    g.frame(),g.targets(),g.invalidTargets(),Objects.requireNonNull(c.clocks().get(g.id())),c.groupMotions().get(g.id())));
             Map<Long,EmissionPlan> plans=new TreeMap<>(); for (var p:g.plans()) plans.put(p.ordinal(),p);
             Map<Long,Set<Integer>> living=new HashMap<>(),damaging=new HashMap<>();
             for (var m:g.members()) {
                 plans.put(m.plan().ordinal(),m.plan());
                 living.computeIfAbsent(m.id().batch(),k->new HashSet<>()).add(m.id().index());
                 if (m.harmful()) damaging.computeIfAbsent(m.id().batch(),k->new HashSet<>()).add(m.id().index());
             }
             for (var p:plans.values()) {
                 String hash=wire.descriptionHash(p.program()); descriptions.put(hash,p.program());
                 Set<Integer> alive=living.getOrDefault(p.ordinal(),Set.of()), harmful=damaging.getOrDefault(p.ordinal(),Set.of());
                out.put(batchKey(g.id(),p.ordinal()),new Batch(g.id(),p.ordinal(),p.birthTick(),p.source(),hash,p.binding(),p.seed(),p.members(),
                         mask(p.members(),alive),mask(p.members(),harmful),g.plans().stream().anyMatch(plan->plan.ordinal()==p.ordinal()),p.motion()));
            }
            for (var m:g.members()) {
                var id=m.id(); Actions.Source source=c.following().get(id); Long sourceGroup=null; DanmakuWorldRuntime.MemberId sourceMember=c.parentSources().get(id);
                if (source!=null) {
                    ProgramRef.Group ref=source instanceof Actions.GroupPose pose?pose.group():((Actions.MemberPose)source).group();
                    sourceGroup=c.sourceGroups().get(ref);
                    if (source instanceof Actions.MemberPose member && sourceGroup!=null) {
                        long resolved=sourceGroup; Long batch=c.sourceBatches().get(ref);
                        var matching=c.groups().stream().filter(s->s.id()==resolved).flatMap(s->s.members().stream())
                                .filter(s->s.id().index()==member.stableIndex() && (batch==null || s.id().batch()==batch)).toList();
                        if (matching.size()!=1) throw new IllegalArgumentException("ambiguous replica source");
                        sourceMember=matching.getFirst().id(); sourceGroup=null;
                    }
                }
                boolean attached=c.attachedMembers().contains(id); Path detached=c.detached().get(id);
                if (attached || detached!=null || source!=null || sourceMember!=null)
                    out.put(motionKey(id),new Motion(id,attached,detached,sourceGroup,sourceMember,
                            sourceGroup==null && sourceMember==null?c.lastSourcePoses().get(id):null,c.nozzles().get(id)));
            }
        }
        for (var e:c.emitters()) out.put("e/"+e.group()+"/"+e.origin()+"/"+wire.hash(wire.encodeData(e.description())),new Emitter(e.group(),e.description(),e.seed(),e.policy(),e.origin(),e.nextShot(),
                e.description().source() instanceof Actions.GroupPose source?c.sourceGroups().get(source.group()):null));
        for (var p:c.commits()) out.put("p/"+p.plan().group()+"/"+p.plan().ordinal(),new Pending(p.plan().group(),p.plan().ordinal(),p.policy(),p.ownershipEnd(),p.seal()));
        for (String trigger:c.consumedTriggers()) {
            long group=c.groups().stream().filter(g->trigger.startsWith(g.encounter().owner()+":"+g.encounter().epoch()+":"+g.id()+":")).mapToLong(DanmakuWorldRuntime.GroupSnapshot::id).findFirst().orElseThrow();
            out.put("t/"+trigger,new Cursor(group,trigger,1));
        }
        for (var e:c.targetSamples().entrySet()) out.put("s/"+e.getKey().group()+"/"+e.getKey().name(),new Sample(e.getKey().group(),e.getKey().name(),e.getValue(),List.of(),0));
        for (var e:c.surfaces().entrySet()) out.put("u/"+e.getKey().group()+"/"+e.getKey().name(),new Sample(e.getKey().group(),e.getKey().name(),null,e.getValue().points().stream().map(p->new SurfacePoint(p.orElse(null))).toList(),e.getValue().queries()));
        return new Projection(new Image(c.dimension(),c.tick(),c.revision(),out),descriptions,definitions);
    }
    public static Set<String> required(Image image) {
        Set<String> result=new HashSet<>();
        for (Record r:image.entries().values()) { if (r instanceof Batch b) result.add(b.description()); if (r instanceof Instance i) result.add(i.hash()); }
        return result;
    }
    public static void validate(Image image,NetworkLimits limits,Map<String,Actions.ChildProgram> descriptions) {
        if (image.dimension()==null || image.dimension().isBlank() || image.tick()<0 || image.revision()<0 || image.entries().size()>limits.entries()) throw new IllegalArgumentException("invalid replica boundary");
        int members=0;
        for (var entry:image.entries().entrySet()) {
            Record r=entry.getValue();
            if (r instanceof Group g) {
                if (!entry.getKey().equals(groupKey(g.id())) || g.id()<1 || g.encounter().epoch()<1 || g.targets().size()>5 || g.slot()< -1 || g.slot()>=g.targets().size()
                        || new HashSet<>(g.targets()).size()!=g.targets().size() || g.invalid().stream().anyMatch(n->n<0 || n>=g.targets().size())) throw new IllegalArgumentException("invalid group");
                if (g.parent()!=null && (!(image.entries().get(groupKey(g.parent())) instanceof Group p) || g.parent()>=g.id() || !p.encounter().equals(g.encounter()))) throw new IllegalArgumentException("invalid parent closure");
            } else if (r instanceof Batch b) {
                if (!entry.getKey().equals(batchKey(b.group(),b.ordinal())) || b.ordinal()<0 || b.birth()<0 || !(image.entries().get(groupKey(b.group())) instanceof Group)
                        || b.templates().size()>512 || b.templates().stream().map(Pattern.Member::index).distinct().count()!=b.templates().size()) throw new IllegalArgumentException("invalid batch");
                int words=(b.templates().size()+63)/64;
                if (b.alive().size()!=words || b.harmful().size()!=words) throw new IllegalArgumentException("invalid mask");
                for (int n=0;n<words;n++) {
                    if ((b.harmful().get(n)&~b.alive().get(n))!=0) throw new IllegalArgumentException("harmful dead member");
                    if (n==words-1 && b.templates().size()%64!=0 && ((b.alive().get(n)|b.harmful().get(n))>>> (b.templates().size()%64))!=0) throw new IllegalArgumentException("mask padding");
                    members+=Long.bitCount(b.alive().get(n));
                }
                var program=descriptions.get(b.description()); if (program==null || b.templates().size()>program.pattern().countBound()) throw new IllegalArgumentException("missing batch description");
            } else if (r instanceof Motion m) {
                if (!entry.getKey().equals(motionKey(m.id())) || !hasMember(image,m.id())) throw new IllegalArgumentException("orphan motion");
                if (m.sourceMember()!=null && !hasMember(image,m.sourceMember()) || m.sourceGroup()!=null && !image.entries().containsKey(groupKey(m.sourceGroup()))) throw new IllegalArgumentException("missing source closure");
                if (m.attached() && ((Group)image.entries().get(groupKey(m.id().group()))).motion()==null) throw new IllegalArgumentException("missing group motion");
            } else if (r instanceof Beam b) { if (!entry.getKey().equals("l/"+motionKey(b.id())) || !hasMember(image,b.id())) throw new IllegalArgumentException("orphan beam"); }
            else if (r instanceof Emitter e) {
                if (!image.entries().containsKey(groupKey(e.group())) || e.origin()<0 || e.nextShot()<0 || e.nextShot()>=e.description().count()) throw new IllegalArgumentException("invalid emitter cursor");
                if (e.sourceGroup()!=null && !(image.entries().get(groupKey(e.sourceGroup())) instanceof Group)) throw new IllegalArgumentException("missing emitter source closure");
            } else if (r instanceof Pending p) {
                if (!(image.entries().get(batchKey(p.group(),p.batch())) instanceof Batch b) || !b.warning() || p.ownershipEnd()<0) throw new IllegalArgumentException("invalid future commit");
            } else if (r instanceof Instance i) {
                if (!entry.getKey().equals("i/"+i.id()) || i.id()<1 || i.encounter().epoch()<1 || i.nextTick()<0 || i.hash()==null || !i.hash().matches("[0-9a-f]{64}") || i.anchors()==null) throw new IllegalArgumentException("invalid instance");
            } else if (r instanceof Cursor c) {
                if (c.next()<0 || c.group()<0 || c.group()>0 && !image.entries().containsKey(groupKey(c.group()))) throw new IllegalArgumentException("invalid derived cursor");
            } else if (r instanceof Sample s) {
                if (!image.entries().containsKey(groupKey(s.group())) || s.queries()<0 || s.queries()>4096 || s.surface().size()>512) throw new IllegalArgumentException("invalid sample");
            } else throw new IllegalArgumentException("unknown replica entry");
        }
        if (members>limits.members()) throw new IllegalArgumentException("member budget");
        // Validate dependency cycles independently of evaluation time.
        for (Record r:image.entries().values()) if (r instanceof Motion m) {
            Set<DanmakuWorldRuntime.MemberId> seen=new HashSet<>(); Motion cursor=m;
            while (cursor!=null && cursor.sourceMember()!=null) {
                if (!seen.add(cursor.id()) || seen.size()>16) throw new IllegalArgumentException("cyclic source closure");
                cursor=(Motion)image.entries().get(motionKey(cursor.sourceMember()));
            }
        }
    }
    public static Image beam(Image image,DanmakuWorldRuntime.MemberId id,double length) {
        Map<String,Record> entries=new TreeMap<>(image.entries()); entries.put("l/"+motionKey(id),new Beam(id,length));
        return new Image(image.dimension(),image.tick(),image.revision(),entries);
    }
    public static boolean hasMember(Image image,DanmakuWorldRuntime.MemberId id) {
        if (!(image.entries().get(batchKey(id.group(),id.batch())) instanceof Batch b)) return false;
        for (int i=0;i<b.templates().size();i++) if (b.templates().get(i).index()==id.index()) return b.alive(i);
        return false;
    }
    public static Path.State state(Image image,Map<String,Actions.ChildProgram> descriptions,DanmakuWorldRuntime.MemberId id,double time) {
        return state(image,descriptions,id,time,new HashSet<>());
    }
    /** Authored beam axis; translational velocity never substitutes for a live laser source frame. */
    public static Vec3 direction(Image image,Map<String,Actions.ChildProgram> descriptions,DanmakuWorldRuntime.MemberId id,double time) {
        Batch batch=(Batch)image.entries().get(batchKey(id.group(),id.batch()));
        var template=batch.templates().stream().filter(m->m.index()==id.index()).findFirst().orElseThrow();
        var program=Objects.requireNonNull(descriptions.get(batch.description()));
        var motion=(Motion)image.entries().get(motionKey(id));
        double age=time-batch.birth();
        if (motion!=null && motion.detached() instanceof DanmakuWorldRuntime.WorldDetached path && path.direction()!=null) return path.direction();
        if (motion!=null && motion.detached()!=null && (!(motion.detached() instanceof DanmakuWorldRuntime.Rebased rebase) || time>=rebase.start())) {
            var velocity=state(image,descriptions,id,time).velocity();
            return velocity.length()>1e-9?velocity.unit():EmissionPlan.beamDirection(program,batch.source(),template,age);
        }
        if (motion!=null && motion.attached()) {
            var group=(Group)image.entries().get(groupKey(id.group()));
            return group.motion().direction(program.transform().direction(template.direction(),age),time);
        }
        if (motion!=null && (motion.sourceGroup()!=null || motion.sourceMember()!=null)) {
            Actions.Pose pose=motion.lastPose();
            if (motion.sourceMember()!=null) {
                var parent=state(image,descriptions,motion.sourceMember(),time);
                pose=new Actions.Pose(new Frame(parent.position(),Vec3.X,Vec3.Y,Vec3.Z),parent.velocity());
            } else {
                var group=(Group)image.entries().get(groupKey(motion.sourceGroup()));
                pose=(group.motion()==null?GroupMotion.stationary(group.frame(),0):group.motion()).pose(time);
            }
            if (motion.nozzle()!=null) pose=motion.nozzle().apply(pose,time);
            return EmissionPlan.beamDirection(program,pose,template,age);
        }
        return batch.motion()==null?EmissionPlan.beamDirection(program,batch.source(),template,age)
                :batch.motion().direction(program.transform().direction(template.direction(),age),time);
    }
    private static Path.State state(Image image,Map<String,Actions.ChildProgram> descriptions,DanmakuWorldRuntime.MemberId id,double time,Set<DanmakuWorldRuntime.MemberId> stack) {
        if (!hasMember(image,id) || stack.size()>=16 || !stack.add(id)) throw new IllegalArgumentException("missing/cyclic member");
        try {
            Batch b=(Batch)image.entries().get(batchKey(id.group(),id.batch())); Group g=(Group)image.entries().get(groupKey(id.group()));
            Pattern.Member template=b.templates().stream().filter(m->m.index()==id.index()).findFirst().orElseThrow();
            Actions.ChildProgram program=Objects.requireNonNull(descriptions.get(b.description()));
            Motion motion=(Motion)image.entries().get(motionKey(id));
            if (motion!=null && motion.detached()!=null
                    && (!(motion.detached() instanceof DanmakuWorldRuntime.Rebased rebase) || time>=rebase.start()))
                return motion.detached().at(time);
            if (motion!=null && motion.attached()) return g.motion().apply(program.memberState(template,b.seed(),time-b.birth()),time);
            if (motion!=null && (motion.sourceGroup()!=null || motion.sourceMember()!=null)) {
                Actions.Pose pose=motion.lastPose();
                if (motion.sourceMember()!=null) { Path.State s=state(image,descriptions,motion.sourceMember(),time,stack); pose=new Actions.Pose(new Frame(s.position(),Vec3.X,Vec3.Y,Vec3.Z),s.velocity()); }
                else { Group source=(Group)image.entries().get(groupKey(motion.sourceGroup())); pose=(source.motion()==null?GroupMotion.stationary(source.frame(),0):source.motion()).pose(time); }
                if (motion.nozzle()!=null) pose=motion.nozzle().apply(pose,time);
                Actions.Pose current=pose;
                var spawn=new Actions.SpawnChild(new Actions.GroupPose(new ProgramRef.Group(new NodeId(0))),program,b.binding());
                return Actions.emittedState(spawn,(s,t)->Optional.of(t==b.birth()?b.source():current),b.birth(),time,template,b.seed()).orElseThrow();
            }
            if (b.motion()!=null) return b.motion().apply(program.memberState(template,b.seed(),time-b.birth()),time);
            return EmissionPlan.state(program,b.binding(),b.source(),b.seed(),template,time-b.birth());
        } finally { stack.remove(id); }
    }
}
