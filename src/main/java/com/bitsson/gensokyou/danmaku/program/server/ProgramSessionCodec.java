package com.bitsson.gensokyou.danmaku.program.server;

import com.bitsson.gensokyou.danmaku.program.*;
import com.bitsson.gensokyou.danmaku.program.math.*;
import com.bitsson.gensokyou.danmaku.program.ops.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Bounded, versioned disk codec for a {@link ProgramSession.Snapshot}.
 *
 * <p>Design constraints (migration persistence requirement):
 * <ul>
 *   <li>No Java object serialization: a fixed field order, explicit primitives, and the existing
 *       {@link OperationDataCodec} for typed leaf records. Every length is bounded before allocation.</li>
 *   <li>Versioned header; any malformed/oversized/unknown-version input throws
 *       {@link IllegalArgumentException} so the caller can log and degrade safely.</li>
 *   <li>Definitions are stored once (by hash) via {@link ProgramCodec} and re-resolved on decode.</li>
 *   <li>{@link #rebase} shifts all absolute server-time fields so offline time does not consume
 *       member lifetime, warning windows or schedule cursors.</li>
 * </ul>
 */
public final class ProgramSessionCodec {
    private static final int MAGIC = 0x47535031;
    private static final int VERSION = 2;
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final int MAX_ENTRIES = 8192;
    private static final int MAX_MEMBERS = 4096;
    private static final int MAX_DEFINITIONS = 256;
    private static final int MAX_STRING = 4096;

    private static final OperationDataCodec DATA = ServerControlOperations.dataRegistry()
            .register("disk:nozzle", DanmakuWorldRuntime.Nozzle.class)
            .register("disk:rebased", DanmakuWorldRuntime.Rebased.class)
            .register("disk:world_detached", DanmakuWorldRuntime.WorldDetached.class)
            .freeze();

    private ProgramSessionCodec() {}
    private static final class DiskOutput extends DataOutputStream {
        final Map<Map<String,String>,Integer> records=new HashMap<>();
        DiskOutput(OutputStream stream) { super(stream); }
    }
    private static final class DiskInput extends DataInputStream {
        final List<Object> records=new ArrayList<>();
        final Map<Map<String,String>,Object> legacyRecords=new HashMap<>();
        final int version;
        DiskInput(InputStream stream,int version) { super(stream); this.version=version; }
    }

    // ---------------------------------------------------------------- public

    public static byte[] encode(ProgramSession.Snapshot snapshot, ProgramLimits limits) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            DataOutputStream out = new DiskOutput(buffer);
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            writeCheckpoint(out, snapshot.runtime(), limits);
            Map<String, ProgramDefinition> definitions = new TreeMap<>();
            for (ProgramSession.Instance instance : snapshot.instances()) definitions.put(instance.cursor().definition().hash(), instance.cursor().definition());
            out.writeInt(definitions.size());
            for (var entry : definitions.entrySet()) {
                out.writeUTF(entry.getKey());
                byte[] bytes = ProgramCodec.encode(entry.getValue().id(), entry.getValue().playback(), entry.getValue().root(), limits);
                out.writeInt(bytes.length); out.write(bytes);
            }
            out.writeLong(snapshot.nextInstance());
            out.writeInt(snapshot.instances().size());
            for (ProgramSession.Instance instance : snapshot.instances()) {
                out.writeLong(instance.id());
                writeUuid(out, instance.encounter().owner());
                out.writeLong(instance.encounter().epoch());
                out.writeLong(instance.card());
                out.writeBoolean(instance.background());
                out.writeUTF(instance.cursor().definition().hash());
                writeAnchors(out, instance.cursor().anchors());
                out.writeLong(instance.cursor().nextTick());
            }
            out.flush();
            byte[] result = buffer.toByteArray();
            if (result.length > MAX_BYTES) throw new IllegalArgumentException("program snapshot exceeds disk budget");
            return result;
        } catch (IOException e) {
            throw new IllegalArgumentException("program snapshot encode failed", e);
        }
    }

    public static ProgramSession.Snapshot decode(byte[] bytes, ProgramLimits limits, OperationRegistry registry) {
        if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("empty program snapshot");
        if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("program snapshot exceeds disk budget");
        try {
            var source=new ByteArrayInputStream(bytes);
            DataInputStream header=new DataInputStream(source);
            if (header.readInt() != MAGIC) throw new IOException("bad program snapshot magic");
            int version=header.readInt();
            if (version<1 || version>VERSION) throw new IOException("unsupported program snapshot version");
            DataInputStream in=new DiskInput(source,version);
            DanmakuWorldRuntime.Checkpoint runtime = readCheckpoint(in, limits);
            int definitions = count(in, MAX_DEFINITIONS);
            Map<String, ProgramDefinition> byHash = new HashMap<>();
            for (int i = 0; i < definitions; i++) {
                String hash = in.readUTF();
                int length = count(in, MAX_BYTES);
                byte[] definition = in.readNBytes(length);
                if (definition.length != length) throw new EOFException();
                ProgramDefinition decoded = ProgramCodec.decode(definition, limits, registry).definition()
                        .orElseThrow(() -> new IOException("definition failed to compile"));
                byHash.put(hash, decoded);
            }
            long nextInstance = in.readLong();
            int instances = count(in, MAX_ENTRIES);
            List<ProgramSession.Instance> list = new ArrayList<>(instances);
            for (int i = 0; i < instances; i++) {
                long id = in.readLong();
                UUID owner = readUuid(in);
                long epoch = in.readLong();
                long card = in.readLong();
                boolean background = in.readBoolean();
                ProgramDefinition definition = byHash.get(in.readUTF());
                if (definition == null) throw new IOException("missing definition for instance");
                ClockAnchors anchors = readAnchors(in);
                long nextTick = in.readLong();
                list.add(new ProgramSession.Instance(id, new DanmakuWorldRuntime.Encounter(owner, epoch), card, background,
                        new ProgramCursor(definition, anchors, nextTick).snapshot()));
            }
            if (in.available() != 0) throw new IOException("trailing program snapshot bytes");
            return new ProgramSession.Snapshot(runtime, nextInstance, list);
        } catch (IOException e) {
            throw new IllegalArgumentException("program snapshot decode failed: " + e.getMessage(), e);
        }
    }

    /** Shift every absolute server-time field forward by {@code delta} ticks (offline does not elapse). */
    public static ProgramSession.Snapshot rebase(ProgramSession.Snapshot snapshot, long delta) {
        if (delta == 0) return snapshot;
        if (delta < 0) throw new IllegalArgumentException("rebase delta must be nonnegative");
        DanmakuWorldRuntime.Checkpoint c = snapshot.runtime();
        List<DanmakuWorldRuntime.GroupSnapshot> groups = new ArrayList<>();
        for (var g : c.groups()) {
            Map<Long,EmissionPlan> shiftedPlans=new HashMap<>();
            List<DanmakuWorldRuntime.MemberSnapshot> members = new ArrayList<>();
            for (var m : g.members()) members.add(new DanmakuWorldRuntime.MemberSnapshot(m.id(), shiftedPlans.computeIfAbsent(m.plan().ordinal(),key->shiftPlan(m.plan(),delta)), m.template(), m.harmful()));
            List<EmissionPlan> plans = new ArrayList<>();
            for (var p : g.plans()) plans.add(shiftPlan(p, delta));
            groups.add(new DanmakuWorldRuntime.GroupSnapshot(g.id(), g.encounter(), g.card(), g.cycle(), g.parent(), g.targetSlot(),
                    g.background(), g.retain(), g.emitting(), shift(g.fadeEnd(), delta), g.frame(), g.targets(), g.invalidTargets(),
                    g.nextBatch(), members, plans));
        }
        List<DanmakuWorldRuntime.EmitterSnapshot> emitters = new ArrayList<>();
        for (var e : c.emitters()) emitters.add(new DanmakuWorldRuntime.EmitterSnapshot(e.group(), e.description(), e.seed(), e.policy(), e.origin() + delta, e.nextShot()));
        List<DanmakuWorldRuntime.PendingCommand> commands = new ArrayList<>();
        for (var cmd : c.commands()) commands.add(new DanmakuWorldRuntime.PendingCommand(cmd.tick() + delta, cmd.phase(), cmd.sequence(), cmd.command()));
        List<DanmakuWorldRuntime.PendingCommit> commits = new ArrayList<>();
        for (var p : c.commits()) commits.add(new DanmakuWorldRuntime.PendingCommit(shiftPlan(p.plan(), delta), p.policy(), shift(p.ownershipEnd(), delta), p.seal()));
        Map<Long, Long> ownershipEnds = new HashMap<>();
        c.ownershipEnds().forEach((k, v) -> ownershipEnds.put(k, shift(v, delta)));
        Map<Long, DanmakuWorldRuntime.Clocks> clocks = new HashMap<>();
        c.clocks().forEach((k, v) -> clocks.put(k, new DanmakuWorldRuntime.Clocks(v.card() + delta, v.cycle() + delta, v.group() + delta)));
        Map<DanmakuWorldRuntime.MemberId, Path> detached = new HashMap<>();
        c.detached().forEach((k, v) -> detached.put(k, shiftPath(v, delta)));
        Map<Long, GroupMotion> motions = new HashMap<>();
        c.groupMotions().forEach((k, v) -> motions.put(k, shiftMotion(v, delta)));
        Map<DanmakuWorldRuntime.MemberId, DanmakuWorldRuntime.Nozzle> nozzles = new HashMap<>();
        c.nozzles().forEach((k, v) -> nozzles.put(k, new DanmakuWorldRuntime.Nozzle(v.angle(), v.axis(), v.origin() + delta)));
        List<ProgramSession.Instance> instances = new ArrayList<>();
        for (var i : snapshot.instances()) {
            ClockAnchors a = i.cursor().anchors();
            instances.add(new ProgramSession.Instance(i.id(), i.encounter(), i.card(), i.background(),
                    new ProgramCursor(i.cursor().definition(), new ClockAnchors(a.card() + delta, a.cycle() + delta, a.group(), a.member()), i.cursor().nextTick() + delta).snapshot()));
        }
        DanmakuWorldRuntime.Checkpoint shifted = new DanmakuWorldRuntime.Checkpoint(c.dimension(), c.tick() + delta, c.revision(), c.nextGroup(),
                groups, c.consumedTriggers(), c.movementLocks(), c.encounters(), c.epochs(), emitters, c.sourceGroups(), c.following(),
                c.lastSourcePoses(), detached, c.targetSamples(), c.surfaces(), c.parentSources(), motions, c.attachedMembers(),
                c.stoppedChildren(), c.admissionLimits(), commands, c.nextSequence(), commits, ownershipEnds, c.sourceBatches(), clocks,
                c.targetRadii(), c.capturedFrames(), c.groupHandles(), c.targetHandles(), nozzles, c.birthsThisTick(), c.evaluationsThisTick());
        return new ProgramSession.Snapshot(shifted, snapshot.nextInstance(), instances);
    }

    private static long shift(long value, long delta) { return value == Long.MAX_VALUE ? value : value + delta; }

    /**
     * Keep only the given encounters (and everything owned by their groups/members). Preview
     * encounters share the runtime but are ephemeral, so they must never reach disk.
     */
    public static ProgramSession.Snapshot persistentOnly(ProgramSession.Snapshot snapshot, Set<DanmakuWorldRuntime.Encounter> keep) {
        DanmakuWorldRuntime.Checkpoint c = snapshot.runtime();
        Set<DanmakuWorldRuntime.Encounter> kept = new HashSet<>(keep);
        List<DanmakuWorldRuntime.GroupSnapshot> groups = new ArrayList<>();
        Set<Long> groupIds = new HashSet<>();
        for (var g : c.groups()) if (kept.contains(g.encounter())) { groups.add(g); groupIds.add(g.id()); }
        Map<DanmakuWorldRuntime.Encounter, String> prefixes = new HashMap<>();
        for (var e : kept) prefixes.put(e, e.owner() + ":" + e.epoch() + ":");
        Set<String> triggers = new LinkedHashSet<>();
        for (String key : c.consumedTriggers()) if (prefixes.values().stream().anyMatch(key::startsWith)) triggers.add(key);
        List<ProgramSession.Instance> instances = new ArrayList<>();
        for (var i : snapshot.instances()) if (kept.contains(i.encounter())) instances.add(i);
        Map<UUID, Long> epochs = new HashMap<>();
        c.epochs().forEach((k, v) -> { if (kept.stream().anyMatch(e -> e.owner().equals(k))) epochs.put(k, v); });
        List<DanmakuWorldRuntime.Encounter> encounters = new ArrayList<>();
        for (var e : c.encounters()) if (kept.contains(e)) encounters.add(e);
        DanmakuWorldRuntime.Checkpoint filtered = new DanmakuWorldRuntime.Checkpoint(c.dimension(), c.tick(), c.revision(), c.nextGroup(),
                groups, triggers, retainLongSet(c.movementLocks(), groupIds), encounters, epochs,
                retainByGroup(c.emitters(), e -> e.group(), groupIds),
                retainGroupRefValues(c.sourceGroups(), groupIds), retainByMemberGroup(c.following(), groupIds),
                retainByMemberGroup(c.lastSourcePoses(), groupIds), retainByMemberGroup(c.detached(), groupIds),
                retainBySampleGroup(c.targetSamples(), groupIds), retainBySampleGroup(c.surfaces(), groupIds),
                retainByMemberGroup(c.parentSources(), groupIds), retainKeys(c.groupMotions(), groupIds), retainMemberSet(c.attachedMembers(), groupIds),
                retainLongSet(c.stoppedChildren(), groupIds), c.admissionLimits(),
                c.commands().stream().filter(cmd -> commandKept(cmd.command(), groupIds)).toList(), c.nextSequence(),
                c.commits().stream().filter(p -> groupIds.contains(p.plan().group())).toList(),
                retainKeys(c.ownershipEnds(), groupIds), retainGroupRefValues(c.sourceBatches(), groupIds), retainKeys(c.clocks(), groupIds),
                retainKeys(c.targetRadii(), groupIds), retainKeys(c.capturedFrames(), groupIds),
                retainStringKeyedByValue(c.groupHandles(), groupIds), c.targetHandles(),
                retainByMemberGroup(c.nozzles(), groupIds), c.birthsThisTick(), c.evaluationsThisTick());
        return new ProgramSession.Snapshot(filtered, snapshot.nextInstance(), instances);
    }

    private static boolean commandKept(AuthorityCommand command, Set<Long> groupIds) {
        return switch (command) {
            case AuthorityCommand.Remove remove -> groupIds.contains(remove.member().group());
            case AuthorityCommand.Cancel cancel -> groupIds.contains(cancel.group());
            case AuthorityCommand.Movement movement -> groupIds.contains(movement.group());
            case AuthorityCommand.Replace replace -> groupIds.contains(replace.group());
        };
    }
    private static <V> Map<Long, V> retainKeys(Map<Long, V> map, Set<Long> groupIds) {
        Map<Long, V> out = new HashMap<>();
        map.forEach((k, v) -> { if (groupIds.contains(k)) out.put(k, v); });
        return out;
    }
    private static Map<ProgramRef.Group, Long> retainGroupRefValues(Map<ProgramRef.Group, Long> map, Set<Long> groupIds) {
        Map<ProgramRef.Group, Long> out = new HashMap<>();
        map.forEach((k, v) -> { if (groupIds.contains(v)) out.put(k, v); });
        return out;
    }
    private static Map<String, Long> retainStringKeyedByValue(Map<String, Long> map, Set<Long> groupIds) {
        Map<String, Long> out = new HashMap<>();
        map.forEach((k, v) -> { if (groupIds.contains(v)) out.put(k, v); });
        return out;
    }
    private static <V> Map<DanmakuWorldRuntime.SampleKey, V> retainBySampleGroup(Map<DanmakuWorldRuntime.SampleKey, V> map, Set<Long> groupIds) {
        Map<DanmakuWorldRuntime.SampleKey, V> out = new HashMap<>();
        map.forEach((k, v) -> { if (groupIds.contains(k.group())) out.put(k, v); });
        return out;
    }
    private static <V> Map<DanmakuWorldRuntime.MemberId, V> retainByMemberGroup(Map<DanmakuWorldRuntime.MemberId, V> map, Set<Long> groupIds) {
        Map<DanmakuWorldRuntime.MemberId, V> out = new HashMap<>();
        map.forEach((k, v) -> { if (groupIds.contains(k.group())) out.put(k, v); });
        return out;
    }
    private static Set<DanmakuWorldRuntime.MemberId> retainMemberSet(Set<DanmakuWorldRuntime.MemberId> set, Set<Long> groupIds) {
        Set<DanmakuWorldRuntime.MemberId> out = new LinkedHashSet<>();
        for (var id : set) if (groupIds.contains(id.group())) out.add(id);
        return out;
    }
    private static Set<Long> retainLongSet(Set<Long> set, Set<Long> groupIds) {
        Set<Long> out = new LinkedHashSet<>();
        for (Long id : set) if (groupIds.contains(id)) out.add(id);
        return out;
    }
    private static <V> List<V> retainByGroup(List<V> list, java.util.function.ToLongFunction<V> group, Set<Long> groupIds) {
        List<V> out = new ArrayList<>();
        for (V v : list) if (groupIds.contains(group.applyAsLong(v))) out.add(v);
        return out;
    }

    private static Path shiftPath(Path path, long delta) {
        if (path instanceof DanmakuWorldRuntime.Rebased rebased)
            return new DanmakuWorldRuntime.Rebased(rebased.origin(), rebased.path(), rebased.start() + delta, rebased.end() + delta);
        if (path instanceof DanmakuWorldRuntime.WorldDetached detached)
            return new DanmakuWorldRuntime.WorldDetached(detached.position(), detached.velocity(), detached.origin() + delta, detached.end() + delta, detached.direction());
        return path;
    }

    private static GroupMotion shiftMotion(GroupMotion motion, long delta) {
        if (motion == null) return null;
        List<MemberMotion.Layer> parents = new ArrayList<>();
        for (var layer : motion.parents()) parents.add(new MemberMotion.Layer(layer.transform(), layer.timeOrigin() + delta));
        return new GroupMotion(motion.frame(), motion.translation(), motion.transform(), motion.origin() + delta, parents);
    }

    private static EmissionPlan shiftPlan(EmissionPlan plan, long delta) {
        return new EmissionPlan(plan.group(), plan.ordinal(), plan.birthTick() + delta, plan.source(), plan.program(), plan.binding(),
                plan.seed(), plan.members(), shiftMotion(plan.motion(), delta), plan.aimLocal());
    }

    // ------------------------------------------------------------ checkpoint

    private static void writeCheckpoint(DataOutputStream out, DanmakuWorldRuntime.Checkpoint c, ProgramLimits limits) throws IOException {
        out.writeUTF(c.dimension());
        out.writeLong(c.tick()); out.writeLong(c.revision()); out.writeLong(c.nextGroup());
        out.writeInt(c.groups().size());
        for (var g : c.groups()) writeGroup(out, g, limits);
        writeStrings(out, c.consumedTriggers());
        writeLongs(out, c.movementLocks());
        out.writeInt(c.encounters().size());
        for (var e : c.encounters()) { writeUuid(out, e.owner()); out.writeLong(e.epoch()); }
        writeUuidLongMap(out, c.epochs());
        out.writeInt(c.emitters().size());
        for (var e : c.emitters()) {
            out.writeLong(e.group()); writeData(out, e.description()); writeData(out, e.seed()); writeData(out, e.policy());
            out.writeLong(e.origin()); out.writeInt(e.nextShot());
        }
        writeGroupRefLongMap(out, c.sourceGroups());
        writeMemberSourceMap(out, c.following());
        writeMemberPoseMap(out, c.lastSourcePoses());
        writeMemberPathMap(out, c.detached());
        writeSampleVecMap(out, c.targetSamples());
        writeSampleSurfaceMap(out, c.surfaces());
        writeMemberMemberMap(out, c.parentSources());
        writeLongMotionMap(out, c.groupMotions());
        writeMembers(out, c.attachedMembers());
        writeLongs(out, c.stoppedChildren());
        out.writeDouble(c.admissionLimits().nearRadius()); out.writeInt(c.admissionLimits().nearMembers()); out.writeLong(c.admissionLimits().evaluationsPerTick());
        out.writeInt(c.commands().size());
        for (var cmd : c.commands()) { out.writeLong(cmd.tick()); out.writeByte(cmd.phase().ordinal()); out.writeLong(cmd.sequence()); writeCommand(out, cmd.command(), limits); }
        out.writeLong(c.nextSequence());
        out.writeInt(c.commits().size());
        for (var p : c.commits()) { writePlan(out, p.plan(), limits); writeData(out, p.policy()); out.writeLong(p.ownershipEnd()); out.writeBoolean(p.seal()); }
        writeLongLongMap(out, c.ownershipEnds());
        writeGroupRefLongMap(out, c.sourceBatches());
        writeLongClocksMap(out, c.clocks());
        writeLongDoubleMap(out, c.targetRadii());
        writeLongFrameMap(out, c.capturedFrames());
        writeStringLongMap(out, c.groupHandles());
        writeStringTargetsMap(out, c.targetHandles());
        writeMemberNozzleMap(out, c.nozzles());
        out.writeInt(c.birthsThisTick()); out.writeLong(c.evaluationsThisTick());
    }

    private static DanmakuWorldRuntime.Checkpoint readCheckpoint(DataInputStream in, ProgramLimits limits) throws IOException {
        String dimension = in.readUTF();
        long tick = in.readLong(), revision = in.readLong(), nextGroup = in.readLong();
        int groups = count(in, MAX_ENTRIES);
        List<DanmakuWorldRuntime.GroupSnapshot> groupList = new ArrayList<>(groups);
        for (int i = 0; i < groups; i++) groupList.add(readGroup(in, limits));
        Set<String> consumed = readStrings(in);
        Set<Long> locks = readLongs(in);
        int encounters = count(in, MAX_ENTRIES);
        List<DanmakuWorldRuntime.Encounter> encounterList = new ArrayList<>(encounters);
        for (int i = 0; i < encounters; i++) encounterList.add(new DanmakuWorldRuntime.Encounter(readUuid(in), in.readLong()));
        Map<UUID, Long> epochs = readUuidLongMap(in);
        int emitters = count(in, MAX_ENTRIES);
        List<DanmakuWorldRuntime.EmitterSnapshot> emitterList = new ArrayList<>(emitters);
        for (int i = 0; i < emitters; i++) {
            long group = in.readLong();
            emitterList.add(new DanmakuWorldRuntime.EmitterSnapshot(group, data(in, Actions.CreateEmitter.class), data(in, Deterministic.Seed.class),
                    data(in, AuthorityDescriptions.Commit.class), in.readLong(), in.readInt()));
        }
        Map<ProgramRef.Group, Long> sourceGroups = readGroupRefLongMap(in);
        Map<DanmakuWorldRuntime.MemberId, Actions.Source> following = readMemberSourceMap(in);
        Map<DanmakuWorldRuntime.MemberId, Actions.Pose> poses = readMemberPoseMap(in);
        Map<DanmakuWorldRuntime.MemberId, Path> detached = readMemberPathMap(in);
        Map<DanmakuWorldRuntime.SampleKey, Vec3> samples = readSampleVecMap(in);
        Map<DanmakuWorldRuntime.SampleKey, DecisionProvider.Surface> surfaces = readSampleSurfaceMap(in);
        Map<DanmakuWorldRuntime.MemberId, DanmakuWorldRuntime.MemberId> parents = readMemberMemberMap(in);
        Map<Long, GroupMotion> motions = readLongMotionMap(in);
        Set<DanmakuWorldRuntime.MemberId> attached = readMembers(in);
        Set<Long> stopped = readLongs(in);
        DanmakuWorldRuntime.AdmissionLimits admission = new DanmakuWorldRuntime.AdmissionLimits(in.readDouble(), in.readInt(), in.readLong());
        int commands = count(in, MAX_ENTRIES);
        List<DanmakuWorldRuntime.PendingCommand> commandList = new ArrayList<>(commands);
        for (int i = 0; i < commands; i++) {
            long t = in.readLong(); DanmakuWorldRuntime.Phase phase = phase(in.readUnsignedByte()); long sequence = in.readLong();
            commandList.add(new DanmakuWorldRuntime.PendingCommand(t, phase, sequence, readCommand(in, limits)));
        }
        long nextSequence = in.readLong();
        int commits = count(in, MAX_ENTRIES);
        List<DanmakuWorldRuntime.PendingCommit> commitList = new ArrayList<>(commits);
        for (int i = 0; i < commits; i++) commitList.add(new DanmakuWorldRuntime.PendingCommit(readPlan(in, limits), data(in, AuthorityDescriptions.Commit.class), in.readLong(), in.readBoolean()));
        Map<Long, Long> ownershipEnds = readLongLongMap(in);
        Map<ProgramRef.Group, Long> sourceBatches = readGroupRefLongMap(in);
        Map<Long, DanmakuWorldRuntime.Clocks> clocks = readLongClocksMap(in);
        Map<Long, Double> radii = readLongDoubleMap(in);
        Map<Long, Frame> frames = readLongFrameMap(in);
        Map<String, Long> groupHandles = readStringLongMap(in);
        Map<String, DanmakuWorldRuntime.Targets> targetHandles = readStringTargetsMap(in);
        Map<DanmakuWorldRuntime.MemberId, DanmakuWorldRuntime.Nozzle> nozzles = readMemberNozzleMap(in);
        int births = in.readInt(); long evaluations = in.readLong();
        return new DanmakuWorldRuntime.Checkpoint(dimension, tick, revision, nextGroup, groupList, consumed, locks, encounterList, epochs,
                emitterList, sourceGroups, following, poses, detached, samples, surfaces, parents, motions, attached, stopped, admission,
                commandList, nextSequence, commitList, ownershipEnds, sourceBatches, clocks, radii, frames, groupHandles, targetHandles,
                nozzles, births, evaluations);
    }

    private static void writeGroup(DataOutputStream out, DanmakuWorldRuntime.GroupSnapshot g, ProgramLimits limits) throws IOException {
        out.writeLong(g.id()); writeUuid(out, g.encounter().owner()); out.writeLong(g.encounter().epoch());
        out.writeLong(g.card()); out.writeLong(g.cycle());
        writeNullableLong(out, g.parent()); out.writeInt(g.targetSlot());
        out.writeBoolean(g.background()); out.writeBoolean(g.retain()); out.writeBoolean(g.emitting()); out.writeLong(g.fadeEnd());
        writeData(out, g.frame());
        out.writeInt(g.targets().size()); for (UUID t : g.targets()) writeUuid(out, t);
        out.writeInt(g.invalidTargets().size()); for (int v : g.invalidTargets()) out.writeInt(v);
        out.writeLong(g.nextBatch());
        out.writeInt(g.members().size());
        for (var m : g.members()) { writeMemberId(out, m.id()); writePlan(out, m.plan(), limits); writeData(out, m.template()); out.writeBoolean(m.harmful()); }
        out.writeInt(g.plans().size());
        for (var p : g.plans()) writePlan(out, p, limits);
    }

    private static DanmakuWorldRuntime.GroupSnapshot readGroup(DataInputStream in, ProgramLimits limits) throws IOException {
        long id = in.readLong();
        DanmakuWorldRuntime.Encounter encounter = new DanmakuWorldRuntime.Encounter(readUuid(in), in.readLong());
        long card = in.readLong(), cycle = in.readLong();
        Long parent = readNullableLong(in);
        int slot = in.readInt();
        boolean background = in.readBoolean(), retain = in.readBoolean(), emitting = in.readBoolean();
        long fadeEnd = in.readLong();
        Frame frame = data(in, Frame.class);
        int targetCount = count(in, 8);
        List<UUID> targets = new ArrayList<>(targetCount);
        for (int i = 0; i < targetCount; i++) targets.add(readUuid(in));
        int invalidCount = count(in, 64);
        Set<Integer> invalid = new LinkedHashSet<>();
        for (int i = 0; i < invalidCount; i++) invalid.add(in.readInt());
        long nextBatch = in.readLong();
        int members = count(in, MAX_MEMBERS);
        List<DanmakuWorldRuntime.MemberSnapshot> memberList = new ArrayList<>(members);
        Map<Long,EmissionPlan> sharedPlans=new HashMap<>();
        for (int i = 0; i < members; i++) {
            var memberId=readMemberId(in);
            var decoded=readPlan(in,limits);
            var shared=sharedPlans.putIfAbsent(decoded.ordinal(),decoded);
            memberList.add(new DanmakuWorldRuntime.MemberSnapshot(memberId,shared==null?decoded:shared,data(in,Pattern.Member.class),in.readBoolean()));
        }
        int plans = count(in, MAX_ENTRIES);
        List<EmissionPlan> planList = new ArrayList<>(plans);
        for (int i = 0; i < plans; i++) planList.add(readPlan(in, limits));
        return new DanmakuWorldRuntime.GroupSnapshot(id, encounter, card, cycle, parent, slot, background, retain, emitting, fadeEnd,
                frame, targets, invalid, nextBatch, memberList, planList);
    }

    private static void writePlan(DataOutputStream out, EmissionPlan plan, ProgramLimits limits) throws IOException {
        out.writeLong(plan.group()); out.writeLong(plan.ordinal()); out.writeLong(plan.birthTick());
        writeData(out, plan.source()); writeData(out, plan.program()); writeData(out, plan.binding()); writeData(out, plan.seed());
        out.writeInt(plan.members().size()); for (var m : plan.members()) writeData(out, m);
        out.writeBoolean(plan.motion() != null); if (plan.motion() != null) writeData(out, plan.motion());
        out.writeBoolean(plan.aimLocal() != null); if (plan.aimLocal() != null) writeData(out, plan.aimLocal());
    }

    private static EmissionPlan readPlan(DataInputStream in, ProgramLimits limits) throws IOException {
        long group = in.readLong(), ordinal = in.readLong(), birth = in.readLong();
        Actions.Pose source = data(in, Actions.Pose.class);
        Actions.ChildProgram program = data(in, Actions.ChildProgram.class);
        Actions.Binding binding = data(in, Actions.Binding.class);
        Deterministic.Seed seed = data(in, Deterministic.Seed.class);
        int members = count(in, 512);
        List<Pattern.Member> list = new ArrayList<>(members);
        for (int i = 0; i < members; i++) list.add(data(in, Pattern.Member.class));
        GroupMotion motion = in.readBoolean() ? data(in, GroupMotion.class) : null;
        Vec3 aimLocal = in.readBoolean() ? data(in, Vec3.class) : null;
        return new EmissionPlan(group, ordinal, birth, source, program, binding, seed, list, motion, aimLocal);
    }

    private static void writeCommand(DataOutputStream out, AuthorityCommand command, ProgramLimits limits) throws IOException {
        switch (command) {
            case AuthorityCommand.Remove remove -> { out.writeByte(0); writeMemberId(out, remove.member()); out.writeUTF(remove.reason()); }
            case AuthorityCommand.Cancel cancel -> { out.writeByte(1); out.writeLong(cancel.group()); out.writeLong(cancel.fadeTicks()); out.writeUTF(cancel.reason()); }
            case AuthorityCommand.Movement movement -> { out.writeByte(2); out.writeLong(movement.group()); out.writeBoolean(movement.locked()); }
            case AuthorityCommand.Replace replace -> {
                out.writeByte(3); out.writeLong(replace.group());
                out.writeInt(replace.parents().size()); for (var p : replace.parents()) writeMemberId(out, p);
                writeData(out, replace.replacement()); writeData(out, replace.policy()); out.writeUTF(replace.trigger());
            }
        }
    }

    private static AuthorityCommand readCommand(DataInputStream in, ProgramLimits limits) throws IOException {
        int tag = in.readUnsignedByte();
        return switch (tag) {
            case 0 -> new AuthorityCommand.Remove(readMemberId(in), in.readUTF());
            case 1 -> new AuthorityCommand.Cancel(in.readLong(), in.readLong(), in.readUTF());
            case 2 -> new AuthorityCommand.Movement(in.readLong(), in.readBoolean());
            case 3 -> {
                long group = in.readLong();
                int parents = count(in, 512);
                List<DanmakuWorldRuntime.MemberId> list = new ArrayList<>(parents);
                for (int i = 0; i < parents; i++) list.add(readMemberId(in));
                yield new AuthorityCommand.Replace(group, list, data(in, Actions.ReplaceMembers.class), data(in, AuthorityDescriptions.Commit.class), in.readUTF());
            }
            default -> throw new IOException("unknown authority command tag");
        };
    }

    // --------------------------------------------------------- typed leaves

    private static void writeData(DataOutputStream out, Object value) throws IOException {
        Map<String, String> fields = DATA.encode((Record) value);
        var disk=(DiskOutput)out;
        Integer previous=disk.records.get(fields);
        if(previous!=null) { out.writeInt(-previous-1); return; }
        if(disk.records.size()>=65536) throw new IOException("disk record dictionary limit");
        disk.records.put(fields,disk.records.size());
        out.writeInt(fields.size());
        for (var e : fields.entrySet()) { out.writeUTF(e.getKey()); out.writeUTF(e.getValue()); }
    }

    private static <T> T data(DataInputStream in, Class<T> type) throws IOException {
        var disk=(DiskInput)in;
        int size=in.readInt();
        if(size<0 && disk.version>=2) {
            long index=-(long)size-1;
            if(index>=disk.records.size() || !type.isInstance(disk.records.get((int)index))) throw new IOException("invalid disk record reference");
            return type.cast(disk.records.get((int)index));
        }
        if(size<0 || size>MAX_ENTRIES) throw new IOException("disk field limit");
        Map<String, String> fields = new TreeMap<>();
        for (int i = 0; i < size; i++) if (fields.put(in.readUTF(), in.readUTF()) != null) throw new IOException("duplicate data field");
        if(disk.version==1) {
            Object cached=disk.legacyRecords.get(fields);
            if(cached!=null && type.isInstance(cached)) return type.cast(cached);
        }
        T value=DATA.decode(fields, type);
        if(disk.version==1 && disk.legacyRecords.size()<65536) disk.legacyRecords.put(Map.copyOf(fields),value);
        if(disk.version>=2) {
            if(disk.records.size()>=65536) throw new IOException("disk record dictionary limit");
            disk.records.add(value);
        }
        return value;
    }

    // ------------------------------------------------------------ primitives

    private static void writeMemberId(DataOutputStream out, DanmakuWorldRuntime.MemberId id) throws IOException { out.writeLong(id.group()); out.writeLong(id.batch()); out.writeInt(id.index()); }
    private static DanmakuWorldRuntime.MemberId readMemberId(DataInputStream in) throws IOException { return new DanmakuWorldRuntime.MemberId(in.readLong(), in.readLong(), in.readInt()); }
    private static void writeUuid(DataOutputStream out, UUID id) throws IOException { out.writeLong(id.getMostSignificantBits()); out.writeLong(id.getLeastSignificantBits()); }
    private static UUID readUuid(DataInputStream in) throws IOException { return new UUID(in.readLong(), in.readLong()); }
    private static void writeAnchors(DataOutputStream out, ClockAnchors a) throws IOException { out.writeLong(a.card()); out.writeLong(a.cycle()); writeNullableLong(out, a.group()); writeNullableLong(out, a.member()); }
    private static ClockAnchors readAnchors(DataInputStream in) throws IOException { return new ClockAnchors(in.readLong(), in.readLong(), readNullableLong(in), readNullableLong(in)); }
    private static void writeNullableLong(DataOutputStream out, Long value) throws IOException { out.writeBoolean(value != null); if (value != null) out.writeLong(value); }
    private static Long readNullableLong(DataInputStream in) throws IOException { return in.readBoolean() ? in.readLong() : null; }
    private static int count(DataInputStream in, int max) throws IOException { int n = in.readInt(); if (n < 0 || n > max) throw new IOException("length limit " + n); return n; }

    private static void writeStrings(DataOutputStream out, Collection<String> values) throws IOException { out.writeInt(values.size()); for (String s : values) out.writeUTF(s); }
    private static Set<String> readStrings(DataInputStream in) throws IOException { int n = count(in, MAX_ENTRIES); Set<String> set = new LinkedHashSet<>(); for (int i = 0; i < n; i++) set.add(in.readUTF()); return set; }
    private static void writeLongs(DataOutputStream out, Collection<Long> values) throws IOException { out.writeInt(values.size()); for (long v : values) out.writeLong(v); }
    private static Set<Long> readLongs(DataInputStream in) throws IOException { int n = count(in, MAX_ENTRIES); Set<Long> set = new LinkedHashSet<>(); for (int i = 0; i < n; i++) set.add(in.readLong()); return set; }
    private static void writeMembers(DataOutputStream out, Collection<DanmakuWorldRuntime.MemberId> values) throws IOException { out.writeInt(values.size()); for (var v : values) writeMemberId(out, v); }
    private static Set<DanmakuWorldRuntime.MemberId> readMembers(DataInputStream in) throws IOException { int n = count(in, MAX_ENTRIES); Set<DanmakuWorldRuntime.MemberId> set = new LinkedHashSet<>(); for (int i = 0; i < n; i++) set.add(readMemberId(in)); return set; }

    private static void writeUuidLongMap(DataOutputStream out, Map<UUID, Long> map) throws IOException { out.writeInt(map.size()); for (var e : map.entrySet()) { writeUuid(out, e.getKey()); out.writeLong(e.getValue()); } }
    private static Map<UUID, Long> readUuidLongMap(DataInputStream in) throws IOException { int n = count(in, MAX_ENTRIES); Map<UUID, Long> map = new HashMap<>(); for (int i = 0; i < n; i++) map.put(readUuid(in), in.readLong()); return map; }
    private static void writeLongLongMap(DataOutputStream out, Map<Long, Long> map) throws IOException { out.writeInt(map.size()); for (var e : map.entrySet()) { out.writeLong(e.getKey()); out.writeLong(e.getValue()); } }
    private static Map<Long, Long> readLongLongMap(DataInputStream in) throws IOException { int n = count(in, MAX_ENTRIES); Map<Long, Long> map = new HashMap<>(); for (int i = 0; i < n; i++) map.put(in.readLong(), in.readLong()); return map; }
    private static void writeLongDoubleMap(DataOutputStream out, Map<Long, Double> map) throws IOException { out.writeInt(map.size()); for (var e : map.entrySet()) { out.writeLong(e.getKey()); out.writeDouble(e.getValue()); } }
    private static Map<Long, Double> readLongDoubleMap(DataInputStream in) throws IOException { int n = count(in, MAX_ENTRIES); Map<Long, Double> map = new HashMap<>(); for (int i = 0; i < n; i++) map.put(in.readLong(), in.readDouble()); return map; }
    private static void writeStringLongMap(DataOutputStream out, Map<String, Long> map) throws IOException { out.writeInt(map.size()); for (var e : map.entrySet()) { out.writeUTF(e.getKey()); out.writeLong(e.getValue()); } }
    private static Map<String, Long> readStringLongMap(DataInputStream in) throws IOException { int n = count(in, MAX_ENTRIES); Map<String, Long> map = new HashMap<>(); for (int i = 0; i < n; i++) map.put(in.readUTF(), in.readLong()); return map; }
    private static void writeGroupRefLongMap(DataOutputStream out, Map<ProgramRef.Group, Long> map) throws IOException { out.writeInt(map.size()); for (var e : map.entrySet()) { writeData(out, e.getKey()); out.writeLong(e.getValue()); } }
    private static Map<ProgramRef.Group, Long> readGroupRefLongMap(DataInputStream in) throws IOException { int n = count(in, MAX_ENTRIES); Map<ProgramRef.Group, Long> map = new HashMap<>(); for (int i = 0; i < n; i++) map.put(data(in, ProgramRef.Group.class), in.readLong()); return map; }
    private static void writeMemberSourceMap(DataOutputStream out, Map<DanmakuWorldRuntime.MemberId, Actions.Source> map) throws IOException { out.writeInt(map.size()); for (var e : map.entrySet()) { writeMemberId(out, e.getKey()); writeData(out, e.getValue()); } }
    private static Map<DanmakuWorldRuntime.MemberId, Actions.Source> readMemberSourceMap(DataInputStream in) throws IOException { int n = count(in, MAX_ENTRIES); Map<DanmakuWorldRuntime.MemberId, Actions.Source> map = new HashMap<>(); for (int i = 0; i < n; i++) map.put(readMemberId(in), data(in, Actions.Source.class)); return map; }
    private static void writeMemberPoseMap(DataOutputStream out, Map<DanmakuWorldRuntime.MemberId, Actions.Pose> map) throws IOException { out.writeInt(map.size()); for (var e : map.entrySet()) { writeMemberId(out, e.getKey()); writeData(out, e.getValue()); } }
    private static Map<DanmakuWorldRuntime.MemberId, Actions.Pose> readMemberPoseMap(DataInputStream in) throws IOException { int n = count(in, MAX_ENTRIES); Map<DanmakuWorldRuntime.MemberId, Actions.Pose> map = new HashMap<>(); for (int i = 0; i < n; i++) map.put(readMemberId(in), data(in, Actions.Pose.class)); return map; }
    private static void writeMemberPathMap(DataOutputStream out, Map<DanmakuWorldRuntime.MemberId, Path> map) throws IOException { out.writeInt(map.size()); for (var e : map.entrySet()) { writeMemberId(out, e.getKey()); writeData(out, e.getValue()); } }
    private static Map<DanmakuWorldRuntime.MemberId, Path> readMemberPathMap(DataInputStream in) throws IOException { int n = count(in, MAX_ENTRIES); Map<DanmakuWorldRuntime.MemberId, Path> map = new HashMap<>(); for (int i = 0; i < n; i++) map.put(readMemberId(in), data(in, Path.class)); return map; }
    private static void writeMemberMemberMap(DataOutputStream out, Map<DanmakuWorldRuntime.MemberId, DanmakuWorldRuntime.MemberId> map) throws IOException { out.writeInt(map.size()); for (var e : map.entrySet()) { writeMemberId(out, e.getKey()); writeMemberId(out, e.getValue()); } }
    private static Map<DanmakuWorldRuntime.MemberId, DanmakuWorldRuntime.MemberId> readMemberMemberMap(DataInputStream in) throws IOException { int n = count(in, MAX_ENTRIES); Map<DanmakuWorldRuntime.MemberId, DanmakuWorldRuntime.MemberId> map = new HashMap<>(); for (int i = 0; i < n; i++) map.put(readMemberId(in), readMemberId(in)); return map; }
    private static void writeSampleVecMap(DataOutputStream out, Map<DanmakuWorldRuntime.SampleKey, Vec3> map) throws IOException { out.writeInt(map.size()); for (var e : map.entrySet()) { out.writeLong(e.getKey().group()); out.writeUTF(e.getKey().name()); writeData(out, e.getValue()); } }
    private static Map<DanmakuWorldRuntime.SampleKey, Vec3> readSampleVecMap(DataInputStream in) throws IOException { int n = count(in, MAX_ENTRIES); Map<DanmakuWorldRuntime.SampleKey, Vec3> map = new HashMap<>(); for (int i = 0; i < n; i++) map.put(new DanmakuWorldRuntime.SampleKey(in.readLong(), in.readUTF()), data(in, Vec3.class)); return map; }
    private static void writeSampleSurfaceMap(DataOutputStream out, Map<DanmakuWorldRuntime.SampleKey, DecisionProvider.Surface> map) throws IOException {
        out.writeInt(map.size());
        for (var e : map.entrySet()) {
            out.writeLong(e.getKey().group()); out.writeUTF(e.getKey().name());
            out.writeInt(e.getValue().points().size());
            for (var p : e.getValue().points()) { out.writeBoolean(p.isPresent()); if (p.isPresent()) writeData(out, p.orElseThrow()); }
            out.writeInt(e.getValue().queries());
        }
    }
    private static Map<DanmakuWorldRuntime.SampleKey, DecisionProvider.Surface> readSampleSurfaceMap(DataInputStream in) throws IOException {
        int n = count(in, MAX_ENTRIES);
        Map<DanmakuWorldRuntime.SampleKey, DecisionProvider.Surface> map = new HashMap<>();
        for (int i = 0; i < n; i++) {
            var key = new DanmakuWorldRuntime.SampleKey(in.readLong(), in.readUTF());
            int points = count(in, 512);
            List<Optional<Vec3>> list = new ArrayList<>(points);
            for (int j = 0; j < points; j++) list.add(in.readBoolean() ? Optional.of(data(in, Vec3.class)) : Optional.empty());
            map.put(key, new DecisionProvider.Surface(list, in.readInt()));
        }
        return map;
    }
    private static void writeLongMotionMap(DataOutputStream out, Map<Long, GroupMotion> map) throws IOException { out.writeInt(map.size()); for (var e : map.entrySet()) { out.writeLong(e.getKey()); writeData(out, e.getValue()); } }
    private static Map<Long, GroupMotion> readLongMotionMap(DataInputStream in) throws IOException { int n = count(in, MAX_ENTRIES); Map<Long, GroupMotion> map = new HashMap<>(); for (int i = 0; i < n; i++) map.put(in.readLong(), data(in, GroupMotion.class)); return map; }
    private static void writeLongClocksMap(DataOutputStream out, Map<Long, DanmakuWorldRuntime.Clocks> map) throws IOException { out.writeInt(map.size()); for (var e : map.entrySet()) { out.writeLong(e.getKey()); out.writeLong(e.getValue().card()); out.writeLong(e.getValue().cycle()); out.writeLong(e.getValue().group()); } }
    private static Map<Long, DanmakuWorldRuntime.Clocks> readLongClocksMap(DataInputStream in) throws IOException { int n = count(in, MAX_ENTRIES); Map<Long, DanmakuWorldRuntime.Clocks> map = new HashMap<>(); for (int i = 0; i < n; i++) map.put(in.readLong(), new DanmakuWorldRuntime.Clocks(in.readLong(), in.readLong(), in.readLong())); return map; }
    private static void writeLongFrameMap(DataOutputStream out, Map<Long, Frame> map) throws IOException { out.writeInt(map.size()); for (var e : map.entrySet()) { out.writeLong(e.getKey()); writeData(out, e.getValue()); } }
    private static Map<Long, Frame> readLongFrameMap(DataInputStream in) throws IOException { int n = count(in, MAX_ENTRIES); Map<Long, Frame> map = new HashMap<>(); for (int i = 0; i < n; i++) map.put(in.readLong(), data(in, Frame.class)); return map; }
    private static void writeStringTargetsMap(DataOutputStream out, Map<String, DanmakuWorldRuntime.Targets> map) throws IOException {
        out.writeInt(map.size());
        for (var e : map.entrySet()) {
            out.writeUTF(e.getKey());
            out.writeInt(e.getValue().uuids().size()); for (UUID u : e.getValue().uuids()) writeUuid(out, u);
            out.writeDouble(e.getValue().radius());
        }
    }
    private static Map<String, DanmakuWorldRuntime.Targets> readStringTargetsMap(DataInputStream in) throws IOException {
        int n = count(in, MAX_ENTRIES);
        Map<String, DanmakuWorldRuntime.Targets> map = new HashMap<>();
        for (int i = 0; i < n; i++) {
            String key = in.readUTF();
            int uuids = count(in, 8);
            List<UUID> list = new ArrayList<>(uuids);
            for (int j = 0; j < uuids; j++) list.add(readUuid(in));
            map.put(key, new DanmakuWorldRuntime.Targets(list, in.readDouble()));
        }
        return map;
    }
    private static void writeMemberNozzleMap(DataOutputStream out, Map<DanmakuWorldRuntime.MemberId, DanmakuWorldRuntime.Nozzle> map) throws IOException { out.writeInt(map.size()); for (var e : map.entrySet()) { writeMemberId(out, e.getKey()); writeData(out, e.getValue()); } }
    private static Map<DanmakuWorldRuntime.MemberId, DanmakuWorldRuntime.Nozzle> readMemberNozzleMap(DataInputStream in) throws IOException { int n = count(in, MAX_ENTRIES); Map<DanmakuWorldRuntime.MemberId, DanmakuWorldRuntime.Nozzle> map = new HashMap<>(); for (int i = 0; i < n; i++) map.put(readMemberId(in), data(in, DanmakuWorldRuntime.Nozzle.class)); return map; }
    private static DanmakuWorldRuntime.Phase phase(int ordinal) throws IOException {
        if (ordinal < 0 || ordinal >= DanmakuWorldRuntime.Phase.values().length) throw new IOException("unknown phase");
        return DanmakuWorldRuntime.Phase.values()[ordinal];
    }
}
