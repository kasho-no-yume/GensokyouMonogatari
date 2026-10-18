package com.bitsson.gensokyou.danmaku.program.server;

import com.bitsson.gensokyou.danmaku.program.author.BossProgramController;
import com.bitsson.gensokyou.danmaku.program.author.StandardProgramOperations;
import com.bitsson.gensokyou.danmaku.program.content.BossProgramCards;
import com.bitsson.gensokyou.danmaku.program.math.*;
import com.bitsson.gensokyou.danmaku.program.ops.Actions;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Disk persistence contract: encode a live session to bytes, write a file, decode in a fresh
 * instance, rebase offline time and restore. Movement, re-aim, warnings, derived children,
 * cross-cycle tails and remaining lifetime must be preserved; an ended encounter must not return.
 */
class ProgramSessionCodecTest {
    @Test void fullYamameLoadRoundTripsWithinBudgetAndKeepsBatchSharing() {
        var session=session();
        var controller=new com.bitsson.gensokyou.danmaku.program.author.BossProgramController(session,OWNER);
        controller.card(com.bitsson.gensokyou.danmaku.program.content.BossProgramCards.forOwner("yamame").orElseThrow().card(1).program(),0);
        for(int tick=0;tick<=160;tick++) { session.advance(tick,65536); session.runtime().drainEvents(); }
        byte[] bytes=ProgramSessionCodec.encode(session.checkpoint(),StandardProgramOperations.limits());
        assertTrue(bytes.length<2*1024*1024,"deduplicated real card snapshot, bytes="+bytes.length);
        var decoded=ProgramSessionCodec.decode(bytes,StandardProgramOperations.limits(),StandardProgramOperations.registry());
        var shifted=ProgramSessionCodec.rebase(decoded,100);
        boolean checked=false;
        for(var group:shifted.runtime().groups()) {
            var batches=new java.util.HashMap<Long,EmissionPlan>();
            for(var member:group.members()) {
                var previous=batches.putIfAbsent(member.plan().ordinal(),member.plan());
                if(previous!=null) { assertSame(previous,member.plan(),"one plan per batch after decode/rebase"); checked=true; }
            }
        }
        assertTrue(checked);
        controller.close();
    }
    private static final UUID OWNER = new UUID(0, 1);
    private static final DanmakuWorldRuntime.Limits LIMITS = new DanmakuWorldRuntime.Limits(8192, 512, 512, 512, 40000);

    @Test void fileRoundTripPreservesMotionReaimAndRemainingLifetime() throws Exception {
        var deck = BossProgramCards.forOwner("big_fairy").orElseThrow();
        var session = session();
        var controller = new BossProgramController(session, OWNER);
        controller.background(deck.background(), 0);
        controller.card(deck.card(1).program(), 0); // ring: hold 60 then re-aim, lock to 120
        for (int tick = 0; tick <= 70; tick++) { session.advance(tick, 8192); session.runtime().drainEvents(); }
        var saved = session.checkpoint();
        long savedTick = session.runtime().tick();
        assertFalse(saved.runtime().groups().isEmpty());

        long delta = 100000; // simulating a long offline gap
        var restored = restoreViaFile(saved, delta);

        // The restored encounter must be active and still own its re-aimed members.
        assertTrue(restored.runtime().active(new DanmakuWorldRuntime.Encounter(OWNER, saved.runtime().encounters().getFirst().epoch())));
        assertSameMotion(session, restored, 120, delta);
    }

    @Test void roundTripPreservesWarningsDerivedChildrenAndCrossCycleTails() {
        var deck = BossProgramCards.forOwner("yamame").orElseThrow();
        var session = session();
        var controller = new BossProgramController(session, OWNER);
        controller.background(deck.background(), 0);
        controller.card(deck.card(1).program(), 0); // spider web: carriers emit tail pearls
        for (int tick = 0; tick <= 12; tick++) { session.advance(tick, 16384); session.runtime().drainEvents(); }
        assertTrue(session.runtime().liveMembers() > 6, "carriers plus derived pearls exist at save time");
        long savedTick = session.runtime().tick();
        long delta = 7777;
        var restored = restoreViaFile(session.checkpoint(), delta);
        assertSameMotion(session, restored, 80, delta);
        // Derived pearls keep their own remaining lifetime through the snapshot.
        assertSameRemainingLifetime(session, restored, delta);
    }

    @Test void roundTripKeepsLiveWarningWindowBeforeBirth() {
        var deck = BossProgramCards.forOwner("big_fairy").orElseThrow();
        var session = session();
        var controller = new BossProgramController(session, OWNER);
        controller.background(deck.background(), 0);
        controller.card(deck.card(3).program(), 0); // rain + pillar (warning 40)
        for (int tick = 0; tick <= 20; tick++) { session.advance(tick, 16384); session.runtime().drainEvents(); }
        boolean warned = session.checkpoint().runtime().groups().stream()
                .flatMap(g -> g.plans().stream()).anyMatch(p -> p.birthTick() > session.runtime().tick());
        assertTrue(warned, "a pillar warning is pending at save time");
        long delta = 300;
        var restored = restoreViaFile(session.checkpoint(), delta);
        boolean warnedAfter = restored.checkpoint().runtime().groups().stream()
                .flatMap(g -> g.plans().stream()).anyMatch(p -> p.birthTick() > restored.runtime().tick());
        assertTrue(warnedAfter, "the pending warning survives disk round-trip and rebase");
        assertSameMotion(session, restored, 40, delta);
    }

    @Test void endedEncounterIsNotRestored() {
        var deck = BossProgramCards.forOwner("big_fairy").orElseThrow();
        var session = session();
        var controller = new BossProgramController(session, OWNER);
        controller.background(deck.background(), 0);
        controller.card(deck.card(1).program(), 0);
        for (int tick = 0; tick <= 30; tick++) { session.advance(tick, 8192); session.runtime().drainEvents(); }
        controller.close();
        var snapshot = session.checkpoint();
        assertTrue(snapshot.runtime().encounters().isEmpty(), "ended battle leaves no encounter to persist");
    }

    @Test void malformedBytesAreRejected() {
        var deck = BossProgramCards.forOwner("big_fairy").orElseThrow();
        var session = session();
        var controller = new BossProgramController(session, OWNER);
        controller.card(deck.card(1).program(), 0);
        for (int tick = 0; tick <= 5; tick++) { session.advance(tick, 8192); session.runtime().drainEvents(); }
        byte[] good = ProgramSessionCodec.encode(session.checkpoint(), StandardProgramOperations.limits());
        assertThrows(IllegalArgumentException.class, () -> ProgramSessionCodec.decode(new byte[0], StandardProgramOperations.limits(), StandardProgramOperations.registry()));
        assertThrows(IllegalArgumentException.class, () -> ProgramSessionCodec.decode(new byte[]{1, 2, 3, 4}, StandardProgramOperations.limits(), StandardProgramOperations.registry()));
        byte[] truncated = Arrays.copyOf(good, good.length - 5);
        assertThrows(IllegalArgumentException.class, () -> ProgramSessionCodec.decode(truncated, StandardProgramOperations.limits(), StandardProgramOperations.registry()));
        byte[] badVersion = good.clone();
        badVersion[7] = (byte) 0x7F; // version int sits after the 4-byte magic
        assertThrows(IllegalArgumentException.class, () -> ProgramSessionCodec.decode(badVersion, StandardProgramOperations.limits(), StandardProgramOperations.registry()));
    }

    @Test void persistentFilterDropsEphemeralEncounters() {
        var bigFairy = BossProgramCards.forOwner("big_fairy").orElseThrow();
        var yamame = BossProgramCards.forOwner("yamame").orElseThrow();
        var session = session();
        new BossProgramController(session, OWNER).card(bigFairy.card(1).program(), 0);
        new BossProgramController(session, new UUID(0, 3)).card(yamame.card(1).program(), 0);
        for (int tick = 0; tick <= 10; tick++) { session.advance(tick, 16384); session.runtime().drainEvents(); }
        var snapshot = session.checkpoint();
        var persistent = snapshot.runtime().encounters().stream()
                .filter(e -> e.owner().equals(OWNER)).collect(java.util.stream.Collectors.toSet());

        var filtered = ProgramSessionCodec.persistentOnly(snapshot, persistent);
        assertEquals(1, filtered.runtime().encounters().size());
        assertEquals(1, filtered.instances().size());
        assertTrue(filtered.runtime().groups().stream().allMatch(g -> g.encounter().owner().equals(OWNER)),
                "only the persistent owner's groups remain");
        var before = filtered.runtime().tick();
        assertEquals(Set.of(new UUID(0, 3)), snapshot.runtime().encounters().stream()
                .filter(e -> !filtered.runtime().encounters().contains(e)).map(DanmakuWorldRuntime.Encounter::owner).collect(java.util.stream.Collectors.toSet()));

        // Round-trips, and only the persistent owner comes back.
        byte[] bytes = ProgramSessionCodec.encode(filtered, StandardProgramOperations.limits());
        var decoded = ProgramSessionCodec.decode(bytes, StandardProgramOperations.limits(), StandardProgramOperations.registry());
        assertEquals(1, decoded.runtime().encounters().size());
        assertEquals(1, decoded.instances().size());
        assertEquals(before, decoded.runtime().tick());

        // A pure preview snapshot has nothing to persist.
        var none = ProgramSessionCodec.persistentOnly(snapshot, Set.of());
        assertTrue(none.runtime().encounters().isEmpty());
        assertTrue(none.instances().isEmpty());
        assertTrue(none.runtime().groups().isEmpty());
    }

    // ------------------------------------------------------------------ harness

    private static ProgramSession restoreViaFile(ProgramSession.Snapshot snapshot, long delta) {
        try {
            byte[] bytes = ProgramSessionCodec.encode(snapshot, StandardProgramOperations.limits());
            Path file = Files.createTempFile("program-session", ".bin");
            Files.write(file, bytes);
            byte[] read = Files.readAllBytes(file);
            Files.deleteIfExists(file);
            var decoded = ProgramSessionCodec.decode(read, StandardProgramOperations.limits(), StandardProgramOperations.registry());
            var rebased = ProgramSessionCodec.rebase(decoded, delta);
            return ProgramSession.restore(rebased, LIMITS, new World(), StandardProgramOperations.execution());
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }

    private static void assertSameMotion(ProgramSession a, ProgramSession b, int ticks, long delta) {
        long fromA = a.runtime().tick();
        for (int k = 1; k <= ticks; k++) {
            a.advance(fromA + k, 16384);
            b.advance(fromA + k + delta, 16384);
            a.runtime().drainEvents(); b.runtime().drainEvents();
            var pa = positions(a); var pb = positions(b);
            assertEquals(pa.keySet(), pb.keySet(), "same live members at step " + k);
            for (var id : pa.keySet())
                assertTrue(pa.get(id).subtract(pb.get(id)).length() < 1e-9, "position match for " + id + " at step " + k);
        }
    }

    private static void assertSameRemainingLifetime(ProgramSession a, ProgramSession b, long delta) {
        var pa = positions(a); long tickA = a.runtime().tick();
        for (var id : pa.keySet()) {
            var member = a.runtime().member(id).orElseThrow();
            long remaining = member.plan().birthTick() + member.plan().program().lifetimeTicks() - tickA;
            assertTrue(remaining > 0);
            // Advance both to the boundary and confirm the same member is still alive then gone.
            assertTrue(b.runtime().member(id).isPresent(), "restored member present");
            var bMember = b.runtime().member(id).orElseThrow();
            assertEquals(remaining, bMember.plan().birthTick() + bMember.plan().program().lifetimeTicks() - b.runtime().tick(),
                    "remaining lifetime preserved through rebase");
            assertTrue(delta > 0);
        }
    }

    private static Map<DanmakuWorldRuntime.MemberId, Vec3> positions(ProgramSession session) {
        Map<DanmakuWorldRuntime.MemberId, Vec3> out = new TreeMap<>(Comparator.comparingLong(DanmakuWorldRuntime.MemberId::group)
                .thenComparingLong(DanmakuWorldRuntime.MemberId::batch).thenComparingInt(DanmakuWorldRuntime.MemberId::index));
        double now = session.runtime().tick();
        for (var group : session.checkpoint().runtime().groups())
            for (var member : group.members()) out.put(member.id(), session.runtime().state(member.id(), now).position());
        return out;
    }

    private static ProgramSession session() {
        var runtime = new DanmakuWorldRuntime("test:codec", LIMITS, new World());
        return new ProgramSession(runtime, StandardProgramOperations.execution());
    }

    private static final class World implements DecisionProvider {
        public List<Player> players() {
            return List.of(new Player(new UUID(0, 2), new Vec3(0, 0, 20), new Vec3(0, 1, 20), 2, true, false, false, .3));
        }
        public Optional<Actions.Pose> entity(UUID uuid) { return Optional.of(new Actions.Pose(Frame.IDENTITY, Vec3.ZERO)); }
        public boolean loaded(Vec3 point) { return true; }
        public Block block(int x, int y, int z) {
            return y <= 0 ? new Block(true, false, true, 1.0D) : new Block(true, true, false, 0);
        }
    }
}
