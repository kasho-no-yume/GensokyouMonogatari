package com.bitsson.gensokyou.danmaku.program.network;

import com.bitsson.gensokyou.danmaku.program.math.*;
import com.bitsson.gensokyou.danmaku.program.ops.*;
import com.bitsson.gensokyou.danmaku.program.server.*;
import java.util.*;

/** Attack-space subscription, independent of owner entity tracking. Bounds are conservative. */
public final class ProgramInterest {
    public record Box(Vec3 min,Vec3 max) {
        public Box { if (min.x()>max.x() || min.y()>max.y() || min.z()>max.z()) throw new IllegalArgumentException("inverted box"); }
        public boolean near(Vec3 point,double margin) { return point.x()>=min.x()-margin && point.x()<=max.x()+margin && point.y()>=min.y()-margin && point.y()<=max.y()+margin && point.z()>=min.z()-margin && point.z()<=max.z()+margin; }
        public double span() { return max.subtract(min).length(); }
    }
    public record Shard(long group,long batch,int ordinal,List<Integer> indices,Box bounds) {
        public Shard { indices=List.copyOf(indices); }
    }
    private ProgramInterest() {}
    /** Host supplies world-space conservative envelopes, including future plans and transform maxima. */
    public static Set<Long> select(ReplicaState.Image image,Map<Long,Box> envelopes,Vec3 viewer,double margin) {
        Deterministic.nonnegative(margin); Set<Long> selected=new HashSet<>();
        for (var e:envelopes.entrySet()) if (e.getValue().near(viewer,margin) && image.entries().get(ReplicaState.groupKey(e.getKey())) instanceof ReplicaState.Group) selected.add(e.getKey());
        boolean changed;
        do {
            changed=false;
            for (Record r:image.entries().values()) {
                if (r instanceof ReplicaState.Group g && selected.contains(g.id()) && g.parent()!=null) changed|=selected.add(g.parent());
                if (r instanceof ReplicaState.Motion m && selected.contains(m.id().group())) {
                    if (m.sourceGroup()!=null) changed|=selected.add(m.sourceGroup());
                    if (m.sourceMember()!=null) changed|=selected.add(m.sourceMember().group());
                }
                if (r instanceof ReplicaState.Emitter e && selected.contains(e.group()) && e.sourceGroup()!=null)
                    changed|=selected.add(e.sourceGroup());
            }
        } while (changed);
        return Set.copyOf(selected);
    }
    public static ReplicaState.Image filter(ReplicaState.Image image,UUID owner,Set<Long> selected) {
        Map<String,Record> out=new TreeMap<>();
        for (var e:image.entries().entrySet()) {
            Record r=e.getValue(); long group= r instanceof ReplicaState.Group g?g.id():r instanceof ReplicaState.Batch b?b.group():r instanceof ReplicaState.Motion m?m.id().group():r instanceof ReplicaState.Beam b?b.id().group():r instanceof ReplicaState.Sample s?s.group():r instanceof ReplicaState.Cursor c?c.group():r instanceof ReplicaState.Emitter emitter?emitter.group():r instanceof ReplicaState.Pending p?p.group():0;
            if (r instanceof ReplicaState.Instance i) { if (i.encounter().owner().equals(owner)) out.put(e.getKey(),r); }
            else if (selected.contains(group) && image.entries().get(ReplicaState.groupKey(group)) instanceof ReplicaState.Group g && g.encounter().owner().equals(owner)) out.put(e.getKey(),r);
        }
        return new ReplicaState.Image(image.dimension(),image.tick(),image.revision(),out);
    }
    /** Spatial partition keeps original group/batch/member identities. Used for large initial transfers. */
    public static List<Shard> shards(long group,long batch,Map<Integer,Box> members,double maxSpan,int maxMembers) {
        Deterministic.positive(maxSpan); if (maxMembers<1 || maxMembers>512) throw new IllegalArgumentException("shard member budget");
        List<Map.Entry<Integer,Box>> sorted=members.entrySet().stream().sorted(Comparator.comparingDouble((Map.Entry<Integer,Box> e)->e.getValue().min().x()).thenComparingInt(Map.Entry::getKey)).toList();
        List<Shard> result=new ArrayList<>(); List<Integer> indices=new ArrayList<>(); Box box=null;
        for (var e:sorted) {
            Box merged=box==null?e.getValue():union(box,e.getValue());
            if (!indices.isEmpty() && (indices.size()>=maxMembers || merged.span()>maxSpan)) { result.add(new Shard(group,batch,result.size(),indices,box)); indices=new ArrayList<>(); box=null; }
            indices.add(e.getKey()); box=box==null?e.getValue():union(box,e.getValue());
        }
        if (!indices.isEmpty()) result.add(new Shard(group,batch,result.size(),indices,box)); return List.copyOf(result);
    }
    private static Box union(Box a,Box b) { return new Box(new Vec3(StrictMath.min(a.min().x(),b.min().x()),StrictMath.min(a.min().y(),b.min().y()),StrictMath.min(a.min().z(),b.min().z())),new Vec3(StrictMath.max(a.max().x(),b.max().x()),StrictMath.max(a.max().y(),b.max().y()),StrictMath.max(a.max().z(),b.max().z()))); }
    public record Spatial(ReplicaState.Projection projection,Map<Long,Box> envelopes,String selection) {}
    /** Select bounded spatial shards before publication. Missing per-member bounds retain the whole batch. */
    public static Spatial spatial(ReplicaState.Projection projection,Map<Long,Box> envelopes,
                                  Map<DanmakuWorldRuntime.MemberId,Box> memberEnvelopes,Vec3 viewer,
                                  double margin,double maxSpan,int maxMembers) {
        var image=projection.image(); Map<String,Set<Integer>> selected=new TreeMap<>();
        Set<Long> visible=select(image,envelopes,viewer,margin); Set<Long> closures=new HashSet<>();
        for (Record r:image.entries().values()) if (r instanceof ReplicaState.Group g && visible.contains(g.id())) {
            if (g.parent()!=null) closures.add(g.parent());
        }
        for (Record r:image.entries().values()) if (r instanceof ReplicaState.Batch b && visible.contains(b.group())) {
            Map<Integer,Box> bounds=new TreeMap<>(); boolean complete=true;
            for (var member:b.templates()) {
                Box box=memberEnvelopes.get(new DanmakuWorldRuntime.MemberId(b.group(),b.ordinal(),member.index()));
                if (box==null) { complete=false; break; } bounds.put(member.index(),box);
            }
            Set<Integer> indices=new HashSet<>();
            if (!complete || closures.contains(b.group())) b.templates().forEach(m->indices.add(m.index()));
            else if (!bounds.isEmpty() && bounds.values().stream().allMatch(bounds.values().iterator().next()::equals)) {
                // The standard host supplies a conservative shared group envelope. Partitioning
                // identical boxes cannot change selection, regardless of shard size/span.
                if (bounds.values().iterator().next().near(viewer,margin)) indices.addAll(bounds.keySet());
            } else for (Shard shard:shards(b.group(),b.ordinal(),bounds,maxSpan,maxMembers)) if (shard.bounds().near(viewer,margin)) indices.addAll(shard.indices());
            selected.put(ReplicaState.batchKey(b.group(),b.ordinal()),indices);
        }
        // A visible child must retain its entire source evaluation closure, even outside the viewport.
        boolean changed;
        do {
            changed=false;
            for (Record r:image.entries().values()) if (r instanceof ReplicaState.Motion m && selected.getOrDefault(ReplicaState.batchKey(m.id().group(),m.id().batch()),Set.of()).contains(m.id().index())) {
                if (m.sourceMember()!=null) {
                    var parent=m.sourceMember(); Set<Integer> indices=selected.computeIfAbsent(ReplicaState.batchKey(parent.group(),parent.batch()),k->new HashSet<>());
                    changed|=indices.add(parent.index());
                }
                if (m.sourceGroup()!=null) for (Record candidate:image.entries().values()) if (candidate instanceof ReplicaState.Batch b && b.group()==m.sourceGroup()) {
                    Set<Integer> indices=selected.computeIfAbsent(ReplicaState.batchKey(b.group(),b.ordinal()),k->new HashSet<>());
                    for (var member:b.templates()) changed|=indices.add(member.index());
                }
            }
        } while (changed);
        Map<String,Record> entries=new TreeMap<>();
        for (var e:image.entries().entrySet()) {
            Record value=e.getValue();
            if (value instanceof ReplicaState.Batch b && selected.containsKey(e.getKey())) {
                Set<Integer> indices=selected.get(e.getKey()); List<Long> alive=new ArrayList<>(b.alive()),harmful=new ArrayList<>(b.harmful());
                for (int i=0;i<b.templates().size();i++) if (!indices.contains(b.templates().get(i).index())) {
                    alive.set(i/64,alive.get(i/64)&~(1L<<(i%64))); harmful.set(i/64,harmful.get(i/64)&~(1L<<(i%64)));
                }
                // Only selected templates travel; masks retain template-relative addressing.
                List<Pattern.Member> templates=new ArrayList<>(); Set<Integer> living=new HashSet<>(),damaging=new HashSet<>();
                for (int i=0;i<b.templates().size();i++) if (indices.contains(b.templates().get(i).index())) {
                    var member=b.templates().get(i); templates.add(member); if (b.alive(i)) living.add(member.index()); if (b.harmful(i)) damaging.add(member.index());
                }
                value=new ReplicaState.Batch(b.group(),b.ordinal(),b.birth(),b.source(),b.description(),b.binding(),b.seed(),templates,mask(templates,living),mask(templates,damaging),b.warning() && !templates.isEmpty(),b.motion(),b.aimLocal());
                if (templates.isEmpty()) continue;
            } else if (value instanceof ReplicaState.Batch) continue;
            if (value instanceof ReplicaState.Motion m && !selected.getOrDefault(ReplicaState.batchKey(m.id().group(),m.id().batch()),Set.of()).contains(m.id().index())) continue;
            if (value instanceof ReplicaState.Beam b && !selected.getOrDefault(ReplicaState.batchKey(b.id().group(),b.id().batch()),Set.of()).contains(b.id().index())) continue;
            if (value instanceof ReplicaState.Pending p && selected.getOrDefault(ReplicaState.batchKey(p.group(),p.batch()),Set.of()).isEmpty()) continue;
            entries.put(e.getKey(),value);
        }
        String signature=selected.entrySet().stream().map(e->e.getKey()+":"+new TreeSet<>(e.getValue())).collect(java.util.stream.Collectors.joining(";"));
        return new Spatial(new ReplicaState.Projection(new ReplicaState.Image(image.dimension(),image.tick(),image.revision(),entries),projection.descriptions(),projection.definitions()),envelopes,signature);
    }
    private static List<Long> mask(List<Pattern.Member> templates,Set<Integer> indices) {
        List<Long> words=new ArrayList<>(Collections.nCopies((templates.size()+63)/64,0L));
        for (int i=0;i<templates.size();i++) if (indices.contains(templates.get(i).index())) words.set(i/64,words.get(i/64)|(1L<<(i%64))); return List.copyOf(words);
    }
    /** Finite lifetime radiusBound; complex transforms must provide a host envelope rather than sampled guesses. */
    public static Box snapshotEnvelope(ReplicaState.Batch batch,Actions.ChildProgram program) {
        if (batch.binding().attachment()!=Actions.Attachment.WORLD_SNAPSHOT) throw new IllegalArgumentException("following source requires host envelope");
        double radius=program.path().radiusBound(program.lifetimeTicks())+program.pattern().radiusBound();
        radius=program.transform().radiusBound(radius,program.lifetimeTicks());
        radius+=batch.source().velocity().length()*program.lifetimeTicks()+batch.source().angularVelocity().length()*program.pattern().radiusBound()*program.lifetimeTicks();
        Vec3 center=batch.source().frame().origin(),extent=new Vec3(radius,radius,radius); return new Box(center.subtract(extent),center.add(extent));
    }
}
