package com.bitsson.gensokyou.danmaku.program.network;

import com.bitsson.gensokyou.danmaku.program.*;
import com.bitsson.gensokyou.danmaku.program.math.*;
import com.bitsson.gensokyou.danmaku.program.ops.*;
import com.bitsson.gensokyou.danmaku.program.server.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

/** Per-connection publisher. Authority revisions may skip because interest filtering is sparse. */
public final class ProgramSyncServer {
    private static final class Subscription {
        long epoch, generation, sequence;
        ReplicaState.Image image;
        int lastRecovery=Integer.MIN_VALUE;
        boolean force;
        long boundaryTick=Long.MIN_VALUE;
    }
    public record Stats(Map<ProgramWire.Kind,Long> logicalBytes,Map<ProgramWire.Kind,Long> wireBytes,long recoveryRequests,long throttled) {
        public Stats { logicalBytes=Map.copyOf(logicalBytes); wireBytes=Map.copyOf(wireBytes); }
        public Stats(Map<ProgramWire.Kind,Long> logicalBytes,long recoveryRequests,long throttled) { this(logicalBytes,Map.of(),recoveryRequests,throttled); }
    }
    private final ProgramWire wire;
    private final Consumer<byte[]> sink;
    private final Map<ProgramReplica.Scope,Subscription> subscriptions=new HashMap<>();
    private final Set<String> sentDefinitions=new HashSet<>();
    private final Map<ProgramWire.Kind,Long> logicalBytes=new EnumMap<>(ProgramWire.Kind.class);
    private final Map<ProgramWire.Kind,Long> wireBytes=new EnumMap<>(ProgramWire.Kind.class);
    private long transfer,generation,requests,throttled;
    private final Map<String,Long> lastClock=new HashMap<>();
    private final Map<String,Integer> digestCursor=new HashMap<>();
    private int lastConnectionRecovery=Integer.MIN_VALUE;
    private ReplicaState.Image spatialAuthority;
    private ReplicaState.Image spatialImage;
    private final int clockPeriod;
    public ProgramSyncServer(ProgramWire wire,Consumer<byte[]> sink,int clockPeriod) {
        if (clockPeriod<1) throw new IllegalArgumentException("clock period"); this.wire=wire; this.sink=sink; this.clockPeriod=clockPeriod;
    }
    public void publish(ReplicaState.Projection projection,Map<Long,ProgramInterest.Box> envelopes,Vec3 viewer,double margin) {
        ReplicaState.Image image=projection.image(); Set<Long> selected=ProgramInterest.select(image,envelopes,viewer,margin);
        Map<UUID,Long> owners=new TreeMap<>();
        for (long id:selected) { var group=(ReplicaState.Group)image.entries().get(ReplicaState.groupKey(id)); owners.put(group.encounter().owner(),group.encounter().epoch()); }
        UUID digestOwner=null;
        if (!owners.isEmpty() && image.tick()%clockPeriod==0 && !Objects.equals(lastClock.get(image.dimension()),image.tick())) {
            int cursor=digestCursor.getOrDefault(image.dimension(),0);
            digestOwner=new ArrayList<>(owners.keySet()).get(Math.floorMod(cursor,owners.size()));
            digestCursor.put(image.dimension(),cursor==Integer.MAX_VALUE?0:cursor+1);
        }
        for (var e:subscriptions.entrySet()) if (e.getKey().dimension().equals(image.dimension()) && e.getValue().image!=null && !owners.containsKey(e.getKey().owner())) {
            Subscription sub=e.getValue(); send(e.getKey(),sub,ProgramWire.Kind.RELEASE,image.tick(),image.revision(),new byte[0]); sub.image=null;
        }
        for (var owner:owners.entrySet()) {
            ProgramReplica.Scope scope=new ProgramReplica.Scope(image.dimension(),owner.getKey());
            if (!subscriptions.containsKey(scope) && subscriptions.size()>=wire.limits().entries()) throw new IllegalArgumentException("subscription budget");
            Subscription sub=subscriptions.computeIfAbsent(scope,k->new Subscription());
            if (sub.epoch!=owner.getValue()) sub.image=null;
            if (sub.image==null) { sub.epoch=owner.getValue(); sub.generation=++generation; sub.sequence=0; sub.boundaryTick=Long.MIN_VALUE; }
            ReplicaState.Image next=ProgramInterest.filter(image,owner.getKey(),selected);
            if (sub.image!=null) {
                Set<String> oldGroups=new HashSet<>(),newGroups=new HashSet<>();
                for (var e:sub.image.entries().entrySet()) if (e.getValue() instanceof ReplicaState.Group) oldGroups.add(e.getKey());
                for (var e:next.entries().entrySet()) if (e.getValue() instanceof ReplicaState.Group) newGroups.add(e.getKey());
                // A subscription switch is not an authority End. Keep its removal out of tombstones.
                Set<Long> populatedGroups=new HashSet<>();
                for (Record record:image.entries().values()) if (record instanceof ReplicaState.Batch batch) populatedGroups.add(batch.group());
                boolean interestChange=oldGroups.stream().anyMatch(k->!newGroups.contains(k)
                        && image.entries().get(k) instanceof ReplicaState.Group g && populatedGroups.contains(g.id()))
                        || newGroups.stream().anyMatch(k->!oldGroups.contains(k) && sub.image.entries().containsKey(k));
                if (interestChange) { sub.generation=++generation; sub.sequence=0; sub.force=true; }
            }
            for (String hash:ReplicaState.required(next)) if (!sentDefinitions.contains(hash)) {
                byte[] payload;
                ProgramDefinition definition=projection.definitions().get(hash);
                ProgramWire.Kind kind;
                if (definition!=null) {
                    var base=ProgramLimits.defaults();
                    var limits=new ProgramLimits(base.maxNodes(),base.maxDepth(),base.maxBatchMembers(),base.maxDerivedMembers(),base.maxStringBytes(),wire.limits().snapshotBytes());
                    payload=ProgramCodec.encode(definition.id(),definition.playback(),definition.root(),limits); kind=ProgramWire.Kind.DEFINITION;
                }
                else { payload=wire.encodeData(Objects.requireNonNull(projection.descriptions().get(hash),"missing description")); kind=ProgramWire.Kind.DESCRIPTION; }
                if (sentDefinitions.size()>=wire.limits().cachedDefinitions()) throw new IllegalArgumentException("connection definition budget");
                send(scope,sub,kind,next.tick(),next.revision(),payload); sentDefinitions.add(hash);
            }
            if (sub.image==null || sub.force) {
                send(scope,sub,sub.image==null?ProgramWire.Kind.START:ProgramWire.Kind.CHECKPOINT,next.tick(),next.revision(),wire.image(next)); sub.force=false;
            } else {
                var patch=wire.diff(sub.image,next);
                if (!patch.changed().isEmpty() || !patch.removed().isEmpty()) {
                    send(scope,sub,ProgramWire.Kind.DECISIONS,next.tick(),next.revision(),wire.patch(patch));
                    next=wire.apply(sub.image,patch,next.tick(),next.revision());
                }
                else next=sub.image; // No fact change: no age-driven revision or per-member traffic.
            }
            sub.image=next;
            // Ordered after all fact transfers. Unlike Image.tick this advances even
            // when no member fact changed. No coordinates or full digest are sent.
            if (sub.boundaryTick!=image.tick()) {
                send(scope,sub,ProgramWire.Kind.BOUNDARY,image.tick(),next.revision(),new byte[0]);
                sub.boundaryTick=image.tick();
            }
            if (owner.getKey().equals(digestOwner)) {
                send(scope,sub,ProgramWire.Kind.CLOCK_DIGEST,image.tick(),next.revision(),wire.imageDigest(next).getBytes(StandardCharsets.US_ASCII));
                lastClock.put(image.dimension(),image.tick());
            }
        }
    }
    public void publishSpatial(ReplicaState.Projection projection,Map<Long,ProgramInterest.Box> envelopes,
                               Map<DanmakuWorldRuntime.MemberId,ProgramInterest.Box> members,Vec3 viewer,
                               double margin,double maxSpan,int maxMembers) {
        var spatial=ProgramInterest.spatial(projection,envelopes,members,viewer,margin,maxSpan,maxMembers);
        boolean interestChange=false;
        if (spatialImage!=null) for (Record entry:projection.image().entries().values()) {
            if (!(entry instanceof ReplicaState.Batch batch)) continue;
            String key=ReplicaState.batchKey(batch.group(),batch.ordinal());
            if (!(spatialAuthority.entries().get(key) instanceof ReplicaState.Batch before)) continue;
            var oldSelected=spatialImage.entries().get(key);
            var newSelected=spatial.projection().image().entries().get(key);
            for (int n=0;n<batch.templates().size();n++) {
                var id=new DanmakuWorldRuntime.MemberId(batch.group(),batch.ordinal(),batch.templates().get(n).index());
                // Births and deaths are authority facts, not changes in the viewer's subscription.
                if (batch.alive(n) && ReplicaState.hasMember(spatialAuthority,id)
                        && ReplicaState.hasMember(spatialImage,id)!=ReplicaState.hasMember(spatial.projection().image(),id)) interestChange=true;
            }
            if (before.warning() && batch.warning() && oldSelected instanceof ReplicaState.Batch a && newSelected instanceof ReplicaState.Batch b
                    && !a.templates().equals(b.templates())) interestChange=true;
        }
        if (interestChange) {
            for (var sub:subscriptions.values()) { sub.generation=++generation; sub.sequence=0; sub.force=true; }
        }
        spatialAuthority=projection.image(); spatialImage=spatial.projection().image();
        publish(spatial.projection(),spatial.envelopes(),viewer,margin);
    }
    private void send(ProgramReplica.Scope scope,Subscription sub,ProgramWire.Kind kind,long tick,long revision,byte[] bytes) {
        var header=new ProgramWire.Envelope(1,scope.dimension(),scope.owner(),sub.epoch,sub.generation,++sub.sequence,revision,tick,kind,++transfer,0,1,new byte[0]);
        logicalBytes.merge(kind,(long)bytes.length,Long::sum);
        for (var chunk:wire.split(header,bytes)) { byte[] encoded=wire.encode(chunk); wireBytes.merge(kind,(long)encoded.length,Long::sum); sink.accept(encoded); }
    }
    public void end(String dimension,DanmakuWorldRuntime.Encounter encounter,long tick,long revision) {
        var scope=new ProgramReplica.Scope(dimension,encounter.owner()); var sub=subscriptions.get(scope);
        if (sub==null || sub.epoch!=encounter.epoch()) return;
        send(scope,sub,ProgramWire.Kind.END,tick,revision,new byte[0]); sub.image=null;
    }
    /** Requests never authorize new subscriptions; only an already interested scope can recover. */
    public boolean recover(ProgramWire.Envelope request,int localTick) {
        if (request.kind()!=ProgramWire.Kind.RESYNC || request.count()!=1 || request.payload().length!=0) throw new IllegalArgumentException("invalid recovery request");
        var sub=subscriptions.get(new ProgramReplica.Scope(request.dimension(),request.owner()));
        if (sub==null || sub.image==null || request.epoch()!=sub.epoch || request.subscription()!=sub.generation) return false;
        requests++;
        if (lastConnectionRecovery!=Integer.MIN_VALUE && localTick-lastConnectionRecovery<wire.limits().requestInterval()
                || sub.lastRecovery!=Integer.MIN_VALUE && localTick-sub.lastRecovery<wire.limits().requestInterval()) { throttled++; return false; }
        lastConnectionRecovery=localTick; sub.lastRecovery=localTick; sub.force=true;
        // A missing definition must be recoverable even if the server previously sent it.
        sentDefinitions.removeAll(ReplicaState.required(sub.image)); return true;
    }
    public Stats stats() { return new Stats(logicalBytes,wireBytes,requests,throttled); }
}
