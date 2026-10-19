package com.bitsson.gensokyou.danmaku.program.network;

import com.bitsson.gensokyou.danmaku.program.*;
import com.bitsson.gensokyou.danmaku.program.math.*;
import com.bitsson.gensokyou.danmaku.program.ops.*;
import com.bitsson.gensokyou.danmaku.program.server.DanmakuWorldRuntime;
import java.io.ByteArrayOutputStream;
import java.util.*;

/** Atomic bounded receiver. It can only install data; it cannot emit, damage, or play sounds. */
public final class ProgramReplica {
    public record Scope(String dimension,UUID owner) {}
    private record Transfer(Scope scope,long id) {}
    private static final class Assembly {
        final ProgramWire.Envelope header; final byte[][] pieces; final int created; int bytes,received;
        Assembly(ProgramWire.Envelope h,int localTick) { header=h; pieces=new byte[h.count()][]; created=localTick; }
    }
    private static final class Stream {
        long epoch,subscription,sequence,endEpoch;
        long confirmedTick=Long.MIN_VALUE;
        boolean replacingSubscription;
        ReplicaState.Image image;
        final Deque<ReplicaState.Image> history=new ArrayDeque<>();
        final Set<Long> endedGroups=new HashSet<>();
        final Set<DanmakuWorldRuntime.MemberId> endedMembers=new HashSet<>();
        final Map<Long,Long> retiredBatches=new HashMap<>();
        final Set<String> deadBatches=new HashSet<>();
    }
    private final ProgramWire wire;
    private final OperationRegistry registry;
    private final ProgramLimits programLimits;
    private final Map<String,Actions.ChildProgram> descriptions=new LinkedHashMap<>();
    private final Map<String,ProgramDefinition> definitions=new LinkedHashMap<>();
    private final Map<Scope,Stream> streams=new HashMap<>();
    private final Map<Transfer,Assembly> transfers=new HashMap<>();
    private final List<ProgramWire.Envelope> waiting=new ArrayList<>();
    private final Map<String,ProgramClock> clocks=new HashMap<>();
    private final Map<String,ProgramPresentationClock> presentationClocks=new HashMap<>();
    private final Set<Scope> recovery=new LinkedHashSet<>();
    private final Map<Scope,Integer> lastRequest=new HashMap<>();
    private long late,duplicates,failures;
    public ProgramReplica(ProgramWire wire,OperationRegistry registry,ProgramLimits programLimits) {
        this.wire=wire; this.registry=registry; this.programLimits=programLimits;
    }
    public void receive(byte[] bytes,int localTick) {
        ProgramWire.Envelope e=wire.decode(bytes); Scope scope=new Scope(e.dimension(),e.owner());
        if (streams.size()>=wire.limits().entries() && !streams.containsKey(scope)) throw new IllegalArgumentException("scope budget");
        Stream stream=streams.computeIfAbsent(scope,k->new Stream());
        if (e.epoch()<=stream.endEpoch || e.epoch()<stream.epoch || e.epoch()==stream.epoch && e.subscription()<stream.subscription) { duplicates++; return; }
        if (e.kind()==ProgramWire.Kind.RESYNC) throw new IllegalArgumentException("client received request");
        if (e.kind()==ProgramWire.Kind.END) {
            stream.endEpoch=e.epoch(); stream.epoch=e.epoch(); stream.image=null; stream.history.clear();
            stream.confirmedTick=Long.MIN_VALUE;
            waiting.removeIf(p->same(p,scope)); transfers.keySet().removeIf(t->t.scope().equals(scope)); recovery.remove(scope);
            releaseIdleClock(scope.dimension()); return;
        }
        if (e.epoch()>stream.epoch || e.subscription()>stream.subscription) {
            if (e.epoch()>stream.epoch) { stream.endedGroups.clear(); stream.endedMembers.clear(); stream.retiredBatches.clear(); stream.deadBatches.clear(); }
            boolean newEpoch=e.epoch()>stream.epoch;
            stream.replacingSubscription=true;
            stream.epoch=e.epoch(); stream.subscription=e.subscription(); stream.sequence=0;
            stream.confirmedTick=Long.MIN_VALUE;
            // Keep the last complete image while a same-epoch checkpoint is assembled.
            if (newEpoch) { stream.image=null; stream.history.clear(); }
            waiting.removeIf(p->same(p,scope)); transfers.keySet().removeIf(t->t.scope().equals(scope));
        }
        if (e.sequence()<=stream.sequence) { duplicates++; return; }
        ProgramWire.Envelope complete=assemble(scope,e,localTick); if (complete==null) return;
        install(scope,stream,complete,localTick);
        // Missing definitions may arrive after an initial checkpoint. Retry without replaying effects.
        for (var pending:List.copyOf(waiting)) {
            Scope s=new Scope(pending.dimension(),pending.owner()); Stream target=streams.get(s);
            if (target==null || pending.epoch()!=target.epoch || pending.subscription()!=target.subscription || pending.epoch()<=target.endEpoch) { waiting.remove(pending); continue; }
            if (pending.sequence()<=target.sequence) { waiting.remove(pending); continue; }
            if (ready(pending,target)) { waiting.remove(pending); install(s,target,pending,localTick); }
        }
    }
    private static boolean same(ProgramWire.Envelope e,Scope scope) { return e.dimension().equals(scope.dimension()) && e.owner().equals(scope.owner()); }
    private ProgramWire.Envelope assemble(Scope scope,ProgramWire.Envelope e,int localTick) {
        if (e.count()==1) return e;
        Transfer key=new Transfer(scope,e.transfer()); Assembly a=transfers.get(key);
        if (a==null) {
            if (transfers.size()>=wire.limits().pendingTransfers()) { recovery.add(scope); failures++; return null; }
            a=new Assembly(e,localTick); transfers.put(key,a);
        }
        var h=a.header;
        if (h.count()!=e.count() || h.kind()!=e.kind() || h.epoch()!=e.epoch() || h.subscription()!=e.subscription() || h.sequence()!=e.sequence() || h.revision()!=e.revision() || h.tick()!=e.tick()) { transfers.remove(key); recovery.add(scope); throw new IllegalArgumentException("mixed transfer"); }
        byte[] payload=e.payload();
        if (a.pieces[e.chunk()]!=null) { if (!Arrays.equals(a.pieces[e.chunk()],payload)) throw new IllegalArgumentException("conflicting chunk"); duplicates++; return null; }
        if (payload.length>wire.limits().snapshotBytes()-a.bytes) { transfers.remove(key); recovery.add(scope); throw new IllegalArgumentException("assembly budget"); }
        long retained=transfers.values().stream().mapToLong(v->v.bytes).sum();
        if (payload.length>wire.limits().snapshotBytes()-retained) { transfers.remove(key); recovery.add(scope); throw new IllegalArgumentException("total assembly budget"); }
        a.pieces[e.chunk()]=payload; a.bytes+=payload.length; a.received++;
        if (a.received!=a.pieces.length) return null;
        ByteArrayOutputStream out=new ByteArrayOutputStream(a.bytes); for (byte[] piece:a.pieces) out.writeBytes(piece); transfers.remove(key);
        return new ProgramWire.Envelope(e.version(),e.dimension(),e.owner(),e.epoch(),e.subscription(),e.sequence(),e.revision(),e.tick(),e.kind(),e.transfer(),0,1,out.toByteArray());
    }
    private boolean ready(ProgramWire.Envelope e,Stream stream) {
        if (e.kind()==ProgramWire.Kind.BOUNDARY) return stream.image!=null && !stream.replacingSubscription && stream.image.revision()==e.revision();
        ReplicaState.Image next=decodeImage(e,stream);
        return next!=null && available(next);
    }
    private boolean available(ReplicaState.Image image) {
        return ReplicaState.required(image).stream().allMatch(h->descriptions.containsKey(h) || definitions.containsKey(h));
    }
    private ReplicaState.Image decodeImage(ProgramWire.Envelope e,Stream stream) {
        if (e.kind()!=ProgramWire.Kind.CHECKPOINT && e.kind()!=ProgramWire.Kind.START && e.kind()!=ProgramWire.Kind.DECISIONS) return null;
        ReplicaState.Image next;
        if (e.kind()==ProgramWire.Kind.DECISIONS) {
            if (stream.image==null) return null;
            var patch=wire.patch(e.payload()); if (patch.baseRevision()!=stream.image.revision()) return null;
            next=wire.apply(stream.image,patch,e.tick(),e.revision());
        } else next=wire.image(e.payload());
        return next;
    }
    private void install(Scope scope,Stream stream,ProgramWire.Envelope e,int localTick) {
        if (e.kind()==ProgramWire.Kind.DESCRIPTION) {
            Actions.ChildProgram program=wire.decodeData(e.payload(),Actions.ChildProgram.class); put(descriptions,wire.descriptionHash(program),program); return;
        }
        if (e.kind()==ProgramWire.Kind.DEFINITION) {
            CompileResult result=ProgramCodec.decode(e.payload(),programLimits,registry);
            if (!result.success()) throw new IllegalArgumentException("incompatible definition: "+result.diagnostics());
            ProgramDefinition definition=result.definition().orElseThrow(); put(definitions,definition.hash(),definition); return;
        }
        if (e.sequence()<=stream.sequence) { duplicates++; return; }
        if (e.kind()==ProgramWire.Kind.RELEASE) {
            stream.image=null; stream.history.clear(); stream.sequence=e.sequence(); waiting.removeIf(p->same(p,scope));
            stream.confirmedTick=Long.MIN_VALUE;
            releaseIdleClock(scope.dimension()); return;
        }
        if (e.kind()==ProgramWire.Kind.BOUNDARY) {
            if (!ready(e,stream)) {
                if (waiting.size()<wire.limits().pendingTransfers() && waiting.stream().noneMatch(p->same(p,scope) && p.sequence()==e.sequence())) waiting.add(e);
                recovery.add(scope); return;
            }
            stream.confirmedTick=Math.max(stream.confirmedTick,e.tick());
            stream.sequence=e.sequence(); clock(e.dimension()).observe(e.tick(),localTick);
            return;
        }
        if (e.kind()==ProgramWire.Kind.CLOCK_DIGEST) {
            clock(e.dimension()).observe(e.tick(),localTick);
            if (stream.image!=null && e.revision()==stream.image.revision() && !Arrays.equals(e.payload(),wire.imageDigest(stream.image).getBytes(java.nio.charset.StandardCharsets.US_ASCII))) { failures++; recovery.add(scope); }
            return;
        }
        ReplicaState.Image next=decodeImage(e,stream);
        if (next==null || !available(next)) {
            if (waiting.stream().noneMatch(p->same(p,scope) && p.sequence()==e.sequence())) {
                long total=waiting.stream().mapToLong(p->p.payload().length).sum();
                if (waiting.size()>=wire.limits().pendingTransfers() || e.payload().length>wire.limits().snapshotBytes()-total) { waiting.removeIf(p->same(p,scope)); failures++; }
                else waiting.add(e);
            }
            recovery.add(scope); return;
        }
        if (!next.dimension().equals(e.dimension()) || next.tick()!=e.tick() || next.revision()!=e.revision()) throw new IllegalArgumentException("checkpoint envelope mismatch");
        ReplicaState.validate(next,wire.limits(),descriptions);
        Set<Long> endedGroups=new HashSet<>(stream.endedGroups);
        Set<DanmakuWorldRuntime.MemberId> endedMembers=new HashSet<>(stream.endedMembers);
        Map<Long,Long> retiredBatches=new HashMap<>(stream.retiredBatches);
        Set<String> deadBatches=new HashSet<>(stream.deadBatches);
        for (Record r:next.entries().values()) if (r instanceof ReplicaState.Batch b) {
            String key=ReplicaState.batchKey(b.group(),b.ordinal());
            if (deadBatches.contains(key) && b.alive().stream().anyMatch(bits->bits!=0)) throw new IllegalArgumentException("terminal batch resurrection");
            if (!b.warning() && b.alive().stream().allMatch(bits->bits==0)) deadBatches.add(key);
        }
        deadBatches.removeIf(key->!next.entries().containsKey(key));
        if (stream.image!=null && e.kind()==ProgramWire.Kind.DECISIONS) {
            for (Record r:next.entries().values()) if (r instanceof ReplicaState.Batch b
                    && !stream.image.entries().containsKey(ReplicaState.batchKey(b.group(),b.ordinal()))
                    && b.ordinal()<=retiredBatches.getOrDefault(b.group(),-1L))
                throw new IllegalArgumentException("terminal batch resurrection");
            for (Record r:stream.image.entries().values()) if (r instanceof ReplicaState.Batch b
                    && !next.entries().containsKey(ReplicaState.batchKey(b.group(),b.ordinal())))
                retiredBatches.merge(b.group(),b.ordinal(),Math::max);
        }
        if (e.kind()==ProgramWire.Kind.DECISIONS && stream.image!=null) {
            for (Record r:stream.image.entries().values()) if (r instanceof ReplicaState.Group g && !next.entries().containsKey(ReplicaState.groupKey(g.id()))) {
                // Interest changes use a new subscription checkpoint; a decision removal is terminal.
                if (endedGroups.size()>=wire.limits().entries()) throw new IllegalArgumentException("group tombstone budget");
                endedGroups.add(g.id());
            }
            for (Record r:stream.image.entries().values()) if (r instanceof ReplicaState.Batch b) for (int n=0;n<b.templates().size();n++) if (b.alive(n)) {
                var id=new DanmakuWorldRuntime.MemberId(b.group(),b.ordinal(),b.templates().get(n).index());
                if (next.entries().containsKey(ReplicaState.batchKey(b.group(),b.ordinal())) && !ReplicaState.hasMember(next,id)) endedMembers.add(id);
            }
        }
        // A retired batch is represented by its ordinal watermark, not thousands
        // of member tombstones retained for the entire encounter.
        endedMembers.removeIf(id->!next.entries().containsKey(ReplicaState.batchKey(id.group(),id.batch())) || deadBatches.contains(ReplicaState.batchKey(id.group(),id.batch())));
        if (endedMembers.size()>wire.limits().members()) throw new IllegalArgumentException("member tombstone budget");
        for (Record r:next.entries().values()) if (r instanceof ReplicaState.Group g && endedGroups.contains(g.id())) throw new IllegalArgumentException("terminal group resurrection");
        for (var id:endedMembers) if (ReplicaState.hasMember(next,id)) throw new IllegalArgumentException("terminal member resurrection");
        for (Record r:next.entries().values()) {
            DanmakuWorldRuntime.Encounter encounter=r instanceof ReplicaState.Group g?g.encounter():r instanceof ReplicaState.Instance i?i.encounter():null;
            if (encounter!=null && (!encounter.owner().equals(e.owner()) || encounter.epoch()!=e.epoch())) throw new IllegalArgumentException("cross encounter checkpoint");
        }
        if (stream.image!=null && (next.tick()<stream.image.tick() || next.revision()<stream.image.revision())) { duplicates++; return; }
        // An idle dimension has no displayed attacks to preserve. Re-anchor the first
        // new image instead of spending the whole encounter catching up a stale clock.
        releaseIdleClock(e.dimension());
        if (clock(e.dimension()).time(localTick)>e.tick()) late++;
        // Recovery of the same subscription must preserve pre-effective path/mask facts.
        // New epochs already clear history at receipt; START establishes a new visible set.
        if (e.kind()==ProgramWire.Kind.START || stream.replacingSubscription) stream.history.clear();
        stream.image=next; stream.replacingSubscription=false;
        stream.history.addLast(next); while (stream.history.size()>wire.limits().history()) stream.history.removeFirst();
        stream.endedGroups.clear(); stream.endedGroups.addAll(endedGroups); stream.endedMembers.clear(); stream.endedMembers.addAll(endedMembers);
        stream.retiredBatches.clear(); stream.retiredBatches.putAll(retiredBatches);
        stream.deadBatches.clear(); stream.deadBatches.addAll(deadBatches);
        stream.sequence=e.sequence(); clock(e.dimension()).observe(e.tick(),localTick); recovery.remove(scope);
    }
    private <T> void put(Map<String,T> cache,String hash,T value) {
        if (!cache.containsKey(hash) && cache.size()>=wire.limits().cachedDefinitions()) throw new IllegalArgumentException("definition cache budget"); cache.put(hash,value);
    }
    public ProgramClock clock(String dimension) { return clocks.computeIfAbsent(dimension,k->new ProgramClock(wire.limits().staleTicks())); }
    private void releaseIdleClock(String dimension) {
        if (streams.entrySet().stream().noneMatch(e->e.getKey().dimension().equals(dimension) && e.getValue().image!=null)) {
            clocks.remove(dimension);
            presentationClocks.remove(dimension);
        }
    }
    /** Minimum complete watermark across visible owners; no shared-server-thread shortcut. */
    public double presentationTime(String dimension,long now,boolean paused) {
        long confirmed=Long.MAX_VALUE;
        for (var entry:streams.entrySet()) if (entry.getKey().dimension().equals(dimension) && entry.getValue().image!=null) {
            if (entry.getValue().confirmedTick==Long.MIN_VALUE) return Double.NaN;
            confirmed=Math.min(confirmed,entry.getValue().confirmedTick);
        }
        if (confirmed==Long.MAX_VALUE) return Double.NaN;
        var clock=presentationClocks.computeIfAbsent(dimension,k->new ProgramPresentationClock());
        clock.observe(confirmed,now);
        return clock.time(now,paused);
    }
    public String presentationDiagnostic(String dimension) {
        var c=presentationClocks.get(dimension);
        return c==null?"uninitialized":"confirmed="+c.authority()+" freezes="+c.freezes()+" resets="+c.resets();
    }
    public Optional<ReplicaState.Image> image(Scope scope) { Stream s=streams.get(scope); return Optional.ofNullable(s==null?null:s.image); }
    public Optional<ProgramRenderView.Snapshot> renderView(Scope scope,double tick) {
        Stream stream=streams.get(scope);
        return stream==null || stream.image==null ? Optional.empty() : Optional.of(ProgramRenderView.capture(imageAt(stream,tick),descriptions,tick));
    }
    public List<ProgramRenderView.Snapshot> renderViews(String dimension,double tick) {
        List<ProgramRenderView.Snapshot> result=new ArrayList<>();
        for (var entry:streams.entrySet()) {
            if (!entry.getKey().dimension().equals(dimension) || entry.getValue().image==null) continue;
            result.add(ProgramRenderView.capture(imageAt(entry.getValue(),tick),descriptions,tick));
        }
        return List.copyOf(result);
    }
    public Path.State state(Scope scope,DanmakuWorldRuntime.MemberId id,double tick) {
        Stream stream=Objects.requireNonNull(streams.get(scope));
        return ReplicaState.state(imageAt(stream,tick),descriptions,id,tick);
    }
    private static ReplicaState.Image imageAt(Stream stream,double tick) {
        ReplicaState.Image image=stream.history.peekFirst();
        for (var candidate:stream.history) {
            if (candidate.tick()>tick) break;
            image=candidate;
        }
        return Objects.requireNonNull(image==null?stream.image:image);
    }
    public double renderTime(String dimension,double localTick) {
        // Clock uncertainty does not mean any facts are missing. Ordered decisions
        // and periodic clock digests already carry the samples needed to re-anchor.
        // Asking for a full checkpoint here amplifies a clock stall into more work
        // on the same stalled server/client. Fact gaps/digest failures still recover.
        return clock(dimension).time(localTick);
    }
    public void noteTick(int localTick) { for (String dimension:List.copyOf(clocks.keySet())) renderTime(dimension,localTick); }
    public Set<Scope> recoveryRequests(int localTick) {
        transfers.entrySet().removeIf(e->{
            if (localTick-e.getValue().created<=wire.limits().staleTicks()) return false;
            recovery.add(e.getKey().scope()); failures++; return true;
        });
        Set<Scope> result=new LinkedHashSet<>();
        for (Scope scope:recovery) if (!lastRequest.containsKey(scope) || localTick-lastRequest.get(scope)>=wire.limits().requestInterval()) { result.add(scope); lastRequest.put(scope,localTick); }
        return Set.copyOf(result);
    }
    public Optional<ProgramWire.Envelope> request(Scope scope,long requestId) {
        Stream stream=streams.get(scope); if (stream==null || stream.epoch<=stream.endEpoch) return Optional.empty();
        return Optional.of(new ProgramWire.Envelope(1,scope.dimension(),scope.owner(),stream.epoch,stream.subscription,requestId,stream.image==null?0:stream.image.revision(),stream.image==null?0:stream.image.tick(),ProgramWire.Kind.RESYNC,requestId,0,1,new byte[0]));
    }
    public long lateEvents() { return late; }
    public long duplicates() { return duplicates; }
    public long failures() { return failures; }
    public boolean active(String dimension) { return streams.entrySet().stream().anyMatch(e->e.getKey().dimension().equals(dimension) && e.getValue().image!=null); }
    public int pendingTransfers() { return transfers.size(); }
    public void clear() { streams.clear(); transfers.clear(); waiting.clear(); clocks.clear(); presentationClocks.clear(); recovery.clear(); lastRequest.clear(); descriptions.clear(); definitions.clear(); }
}
