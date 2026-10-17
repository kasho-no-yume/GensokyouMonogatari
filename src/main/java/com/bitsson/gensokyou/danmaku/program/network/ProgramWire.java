package com.bitsson.gensokyou.danmaku.program.network;

import com.bitsson.gensokyou.danmaku.program.*;
import com.bitsson.gensokyou.danmaku.program.ops.*;
import com.bitsson.gensokyou.danmaku.program.server.*;
import com.bitsson.gensokyou.danmaku.program.math.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Bounded canonical transport. Only local, explicitly registered data constructors are available. */
public final class ProgramWire {
    public enum Kind { DEFINITION, DESCRIPTION, START, CHECKPOINT, DECISIONS, CLOCK_DIGEST, RESYNC, END, RELEASE }
    public record Envelope(int version,String dimension,UUID owner,long epoch,long subscription,long sequence,
                           long revision,long tick,Kind kind,long transfer,int chunk,int count,byte[] payload) {
        public Envelope { payload=payload.clone(); Objects.requireNonNull(kind); Objects.requireNonNull(owner); }
        @Override public byte[] payload() { return payload.clone(); }
    }
    public record Patch(long baseRevision,List<String> removed,Map<String,Record> changed) {
        public Patch { removed=List.copyOf(removed); changed=Map.copyOf(changed); }
    }
    private final NetworkLimits limits;
    private final OperationDataCodec data;
    private final Map<Actions.ChildProgram,String> descriptionHashes=new IdentityHashMap<>();
    private final Map<String,Pattern> patterns=new HashMap<>();
    private final Map<Record,byte[]> recordDigests=new HashMap<>();
    public ProgramWire(NetworkLimits limits) {
        this.limits=limits;
        data=ServerControlOperations.dataRegistry()
                .register("wire:encounter",DanmakuWorldRuntime.Encounter.class).register("wire:member_id",DanmakuWorldRuntime.MemberId.class)
                .register("wire:clocks",DanmakuWorldRuntime.Clocks.class).register("wire:nozzle",DanmakuWorldRuntime.Nozzle.class)
                .register("wire:rebase",DanmakuWorldRuntime.Rebased.class).register("wire:detached",DanmakuWorldRuntime.WorldDetached.class)
                .register("wire:anchors",ClockAnchors.class).register("wire:group",ReplicaState.Group.class)
                .register("wire:batch",ReplicaState.Batch.class).register("wire:motion",ReplicaState.Motion.class)
                .register("wire:mask",ReplicaState.Mask.class)
                .register("wire:instance",ReplicaState.Instance.class).register("wire:cursor",ReplicaState.Cursor.class)
                .register("wire:emitter",ReplicaState.Emitter.class).register("wire:pending",ReplicaState.Pending.class)
                .register("wire:surface_point",ReplicaState.SurfacePoint.class)
                .register("wire:sample",ReplicaState.Sample.class).register("wire:beam",ReplicaState.Beam.class).freeze();
    }
    public NetworkLimits limits() { return limits; }
    public String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public String descriptionHash(Actions.ChildProgram program) {
        String cached=descriptionHashes.get(program); if (cached!=null) return cached;
        String result=hash(encodeData(program));
        if (patterns.size()>=limits.cachedDefinitions()) patterns.clear();
        patterns.put(result,program.pattern());
        if (descriptionHashes.size()>=limits.cachedDefinitions()) descriptionHashes.clear();
        descriptionHashes.put(program,result); return result;
    }
    /** Hash immutable facts once, then hash their ordered keys and fixed-size digests.
     * A clock heartbeat must neither allocate nor fit an entire checkpoint payload. */
    public String imageDigest(ReplicaState.Image image) {
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            DataOutputStream out=new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(),digest));
            text(out,image.dimension()); out.writeLong(image.tick()); out.writeLong(image.revision());
            out.writeInt(image.entries().size());
            if (recordDigests.size()>limits.entries()*2L) recordDigests.clear();
            for (var entry:new TreeMap<>(image.entries()).entrySet()) {
                text(out,entry.getKey());
                byte[] value=recordDigests.get(entry.getValue());
                if (value==null) {
                    MessageDigest item=MessageDigest.getInstance("SHA-256");
                    record(new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(),item)),entry.getValue(),false);
                    value=item.digest(); recordDigests.put(entry.getValue(),value);
                }
                out.write(value);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException e) { throw new IllegalStateException("fact digest",e); }
    }
    private interface Writer { void write(DataOutputStream out) throws IOException; }
    private byte[] bytes(Writer writer) {
        try {
            ByteArrayOutputStream buffer=new ByteArrayOutputStream();
            DataOutputStream out=new DataOutputStream(new FilterOutputStream(buffer) {
                int size;
                @Override public void write(int v) throws IOException { if (++size>limits.snapshotBytes()) throw new IOException("wire byte budget"); this.out.write(v); }
                @Override public void write(byte[] b,int offset,int length) throws IOException { if (length>limits.snapshotBytes()-size) throw new IOException("wire byte budget"); size+=length; this.out.write(b,offset,length); }
            });
            writer.write(out); out.flush(); return buffer.toByteArray();
        } catch (IOException e) { throw new IllegalArgumentException("invalid wire data",e); }
    }
    private DataInputStream input(byte[] bytes) { if (bytes.length>limits.snapshotBytes()) throw new IllegalArgumentException("wire byte budget"); return new DataInputStream(new ByteArrayInputStream(bytes)); }
    private static void end(DataInputStream in) throws IOException { if (in.available()!=0) throw new IOException("trailing bytes"); }
    private static int count(DataInputStream in,int maximum) throws IOException { int n=in.readInt(); if (n<0 || n>maximum) throw new IOException("wire count budget"); return n; }
    private static void text(DataOutputStream out,String value) throws IOException {
        byte[] bytes=value.getBytes(StandardCharsets.UTF_8); if (bytes.length>4096) throw new IOException("wire string budget"); out.writeInt(bytes.length); out.write(bytes);
    }
    private static String text(DataInputStream in) throws IOException {
        int n=count(in,4096); byte[] bytes=in.readNBytes(n); if (bytes.length!=n) throw new EOFException();
        String value=new String(bytes,StandardCharsets.UTF_8); if (!Arrays.equals(bytes,value.getBytes(StandardCharsets.UTF_8))) throw new IOException("invalid UTF-8"); return value;
    }
    private void record(DataOutputStream out,Record value) throws IOException {
        record(out,value,true);
    }
    private void record(DataOutputStream out,Record value,boolean compact) throws IOException {
        if (value instanceof Actions.Pose p) {
            out.writeInt(-2); vector(out,p.frame().origin()); vector(out,p.frame().right()); vector(out,p.frame().up()); vector(out,p.frame().forward());
            vector(out,p.velocity()); vector(out,p.angularVelocity()); return;
        }
        if (value instanceof Deterministic.Seed s) {
            out.writeInt(-3); out.writeLong(s.root()); out.writeInt(s.lineage().size());
            for (int index:s.lineage()) out.writeInt(index); return;
        }
        if (value instanceof Pattern.Member m) {
            if (m.tags().size()>64) throw new IOException("member tag budget");
            out.writeInt(-4); out.writeInt(m.index()); vector(out,m.position()); vector(out,m.direction());
            text(out,m.style().projectile()); text(out,m.style().visual());
            out.writeDouble(m.style().collisionRadius()); out.writeDouble(m.style().damageScale()); out.writeDouble(m.style().visualRadius());
            out.writeInt(m.tags().size()); for (String tag:new TreeSet<>(m.tags())) text(out,tag);
            out.writeInt(m.targetSlot()); return;
        }
        if (value instanceof ReplicaState.Batch b) {
            out.writeInt(-1); out.writeLong(b.group()); out.writeLong(b.ordinal()); out.writeLong(b.birth()); record(out,b.source()); text(out,b.description()); record(out,b.binding()); record(out,b.seed());
            if (b.templates().size()>512) throw new IOException("batch budget");
            Pattern pattern=patterns.get(b.description());
            // Only exact generator output is compacted. Spatial subsets and resolved exceptions
            // retain explicit templates; no client world query or authority operation is run.
            boolean generated=compact && pattern!=null && pattern.generate(b.seed()).equals(b.templates());
            out.writeBoolean(generated);
            if (generated) record(out,(Record)pattern);
            else { out.writeInt(b.templates().size()); for (var member:b.templates()) record(out,member); }
            out.writeInt(b.alive().size()); for (long word:b.alive()) out.writeLong(word);
            out.writeInt(b.harmful().size()); for (long word:b.harmful()) out.writeLong(word); out.writeBoolean(b.warning());
            out.writeBoolean(b.motion()!=null); if (b.motion()!=null) record(out,b.motion()); return;
        }
        var fields=new TreeMap<>(data.encode(value)); out.writeInt(fields.size());
        for (var e:fields.entrySet()) { text(out,e.getKey()); text(out,e.getValue()); }
    }
    private <T> T record(DataInputStream in,Class<T> type) throws IOException {
        int n=in.readInt();
        if (n==-2) return type.cast(new Actions.Pose(new Frame(vector(in),vector(in),vector(in),vector(in)),vector(in),vector(in)));
        if (n==-3) {
            long root=in.readLong(); int size=count(in,64); var lineage=new ArrayList<Integer>(size);
            for (int i=0;i<size;i++) lineage.add(in.readInt());
            return type.cast(new Deterministic.Seed(root,lineage));
        }
        if (n==-4) {
            int index=in.readInt(); Vec3 position=vector(in),direction=vector(in);
            var style=new Pattern.Style(text(in),text(in),in.readDouble(),in.readDouble(),in.readDouble());
            int size=count(in,64); var tags=new HashSet<String>(); for (int i=0;i<size;i++) tags.add(text(in));
            return type.cast(new Pattern.Member(index,position,direction,style,tags,in.readInt()));
        }
        if (n==-1) {
            long group=in.readLong(),ordinal=in.readLong(),birth=in.readLong(); var source=record(in,Actions.Pose.class); String description=text(in); var binding=record(in,Actions.Binding.class); var seed=record(in,com.bitsson.gensokyou.danmaku.program.math.Deterministic.Seed.class);
            List<Pattern.Member> templates;
            if (in.readBoolean()) {
                Pattern pattern=record(in,Pattern.class);
                if (pattern.countBound()>512) throw new IOException("generated batch budget");
                templates=pattern.generate(seed);
                if (patterns.size()>=limits.cachedDefinitions()) patterns.clear();
                patterns.put(description,pattern);
            } else {
                int size=count(in,512); templates=new ArrayList<>(); for (int i=0;i<size;i++) templates.add(record(in,Pattern.Member.class));
            }
            int a=count(in,8); List<Long> alive=new ArrayList<>(); for (int i=0;i<a;i++) alive.add(in.readLong());
            int h=count(in,8); List<Long> harmful=new ArrayList<>(); for (int i=0;i<h;i++) harmful.add(in.readLong());
            boolean warning=in.readBoolean(); var motion=in.readBoolean()?record(in,GroupMotion.class):null;
            return type.cast(new ReplicaState.Batch(group,ordinal,birth,source,description,binding,seed,templates,alive,harmful,warning,motion));
        }
        if (n<0 || n>4096) throw new IOException("record field budget"); Map<String,String> fields=new TreeMap<>();
        for (int i=0;i<n;i++) if (fields.put(text(in),text(in))!=null) throw new IOException("duplicate data field");
        return data.decode(fields,type);
    }
    public byte[] encodeData(Record value) { return bytes(out->record(out,value)); }
    private static void vector(DataOutputStream out,Vec3 value) throws IOException {
        out.writeDouble(value.x()); out.writeDouble(value.y()); out.writeDouble(value.z());
    }
    private static Vec3 vector(DataInputStream in) throws IOException { return new Vec3(in.readDouble(),in.readDouble(),in.readDouble()); }
    public <T> T decodeData(byte[] bytes,Class<T> type) {
        try { var in=input(bytes); T result=record(in,type); end(in); return result; }
        catch (IOException e) { throw new IllegalArgumentException("invalid record",e); }
    }
    private void entries(DataOutputStream out,Map<String,Record> entries) throws IOException {
        if (entries.size()>limits.entries()) throw new IOException("entry budget"); out.writeInt(entries.size());
        for (var e:new TreeMap<>(entries).entrySet()) { text(out,e.getKey()); record(out,e.getValue()); }
    }
    private Map<String,Record> entries(DataInputStream in) throws IOException {
        int n=count(in,limits.entries()); Map<String,Record> entries=new TreeMap<>();
        for (int i=0;i<n;i++) if (entries.put(text(in),record(in,Record.class))!=null) throw new IOException("duplicate entry"); return entries;
    }
    public byte[] image(ReplicaState.Image image) { return bytes(out->{ text(out,image.dimension()); out.writeLong(image.tick()); out.writeLong(image.revision()); entries(out,image.entries()); }); }
    public ReplicaState.Image image(byte[] bytes) {
        try { var in=input(bytes); var image=new ReplicaState.Image(text(in),in.readLong(),in.readLong(),entries(in)); end(in); return image; }
        catch (IOException e) { throw new IllegalArgumentException("invalid checkpoint",e); }
    }
    public Patch diff(ReplicaState.Image before,ReplicaState.Image after) {
        Map<String,Record> changed=new TreeMap<>();
        for (var e:after.entries().entrySet()) {
            Record previous=before.entries().get(e.getKey()),next=e.getValue();
            // A schedule cursor advances every tick but does not create a client fact.
            if (previous instanceof ReplicaState.Instance a && next instanceof ReplicaState.Instance b && a.id()==b.id() && a.encounter().equals(b.encounter()) && a.card()==b.card() && a.background()==b.background() && a.hash().equals(b.hash()) && a.anchors().equals(b.anchors())) continue;
            if (Objects.equals(next,previous)) continue;
            if (previous instanceof ReplicaState.Batch a && next instanceof ReplicaState.Batch b
                    && a.group()==b.group() && a.ordinal()==b.ordinal() && a.birth()==b.birth() && a.source().equals(b.source()) && a.description().equals(b.description()) && a.binding().equals(b.binding()) && a.seed().equals(b.seed()) && a.templates().equals(b.templates()) && Objects.equals(a.motion(),b.motion()))
                next=new ReplicaState.Mask(b.group(),b.ordinal(),b.alive(),b.harmful(),b.warning());
            changed.put(e.getKey(),next);
        }
        return new Patch(before.revision(),before.entries().keySet().stream().filter(k->!after.entries().containsKey(k)).sorted().toList(),changed);
    }
    public byte[] patch(Patch patch) { return bytes(out->{ out.writeLong(patch.baseRevision()); if (patch.removed().size()>limits.entries()) throw new IOException("removal budget"); out.writeInt(patch.removed().size()); for (String s:patch.removed()) text(out,s); entries(out,patch.changed()); }); }
    public Patch patch(byte[] bytes) {
        try { var in=input(bytes); long base=in.readLong(); int n=count(in,limits.entries()); List<String> removed=new ArrayList<>(); for (int i=0;i<n;i++) removed.add(text(in)); var p=new Patch(base,removed,entries(in)); end(in); if (new HashSet<>(removed).size()!=n) throw new IOException("duplicate removal"); return p; }
        catch (IOException e) { throw new IllegalArgumentException("invalid decisions",e); }
    }
    public ReplicaState.Image apply(ReplicaState.Image before,Patch patch,long tick,long revision) {
        if (before.revision()!=patch.baseRevision() || tick<before.tick() || revision<before.revision()) throw new IllegalArgumentException("decision boundary mismatch");
        Map<String,Record> entries=new TreeMap<>(before.entries()); patch.removed().forEach(entries::remove);
        for (var e:patch.changed().entrySet()) {
            Record value=e.getValue();
            if (value instanceof ReplicaState.Mask m) {
                if (!e.getKey().equals(ReplicaState.batchKey(m.group(),m.ordinal())) || !(entries.get(e.getKey()) instanceof ReplicaState.Batch b)) throw new IllegalArgumentException("mask has no batch");
                value=new ReplicaState.Batch(b.group(),b.ordinal(),b.birth(),b.source(),b.description(),b.binding(),b.seed(),b.templates(),m.alive(),m.harmful(),m.warning(),b.motion());
            }
            entries.put(e.getKey(),value);
        }
        return new ReplicaState.Image(before.dimension(),tick,revision,entries);
    }
    public List<Envelope> split(Envelope header,byte[] payload) {
        if (payload.length>limits.snapshotBytes()) throw new IllegalArgumentException("transfer budget");
        int size=limits.messageBytes()-512, count=StrictMath.max(1,(payload.length+size-1)/size);
        if (count>limits.chunks()) throw new IllegalArgumentException("chunk budget"); List<Envelope> result=new ArrayList<>();
        for (int i=0;i<count;i++) result.add(new Envelope(1,header.dimension(),header.owner(),header.epoch(),header.subscription(),header.sequence(),header.revision(),header.tick(),header.kind(),header.transfer(),i,count,Arrays.copyOfRange(payload,i*size,StrictMath.min(payload.length,(i+1)*size))));
        return result;
    }
    public byte[] encode(Envelope e) {
        validate(e); byte[] bytes=bytes(out->{ out.writeInt(e.version()); text(out,e.dimension()); out.writeLong(e.owner().getMostSignificantBits()); out.writeLong(e.owner().getLeastSignificantBits());
            out.writeLong(e.epoch()); out.writeLong(e.subscription()); out.writeLong(e.sequence()); out.writeLong(e.revision()); out.writeLong(e.tick()); out.writeByte(e.kind().ordinal()); out.writeLong(e.transfer()); out.writeInt(e.chunk()); out.writeInt(e.count()); out.writeInt(e.payload.length); out.write(e.payload); });
        if (bytes.length>limits.messageBytes()) throw new IllegalArgumentException("message budget"); return bytes;
    }
    public Envelope decode(byte[] bytes) {
        if (bytes.length>limits.messageBytes()) throw new IllegalArgumentException("message budget");
        try { var in=input(bytes); int version=in.readInt(); String dimension=text(in); UUID owner=new UUID(in.readLong(),in.readLong());
            long epoch=in.readLong(), subscription=in.readLong(), sequence=in.readLong(), revision=in.readLong(), tick=in.readLong(); int kind=in.readUnsignedByte();
            if (kind>=Kind.values().length) throw new IOException("unknown message kind"); long transfer=in.readLong(); int chunk=in.readInt(),count=in.readInt(),n=count(in,limits.messageBytes()); byte[] payload=in.readNBytes(n); if (payload.length!=n) throw new EOFException(); end(in);
            var result=new Envelope(version,dimension,owner,epoch,subscription,sequence,revision,tick,Kind.values()[kind],transfer,chunk,count,payload); validate(result); return result;
        } catch (IOException e) { throw new IllegalArgumentException("invalid envelope",e); }
    }
    private void validate(Envelope e) {
        if (e.version()!=1 || e.dimension().isBlank() || e.dimension().length()>256 || e.epoch()<1 || e.subscription()<1 || e.sequence()<1 || e.revision()<0 || e.tick()<0 || e.transfer()<1
                || e.count()<1 || e.count()>limits.chunks() || e.chunk()<0 || e.chunk()>=e.count() || e.payload.length>limits.messageBytes()-512) throw new IllegalArgumentException("invalid envelope fields");
    }
}
