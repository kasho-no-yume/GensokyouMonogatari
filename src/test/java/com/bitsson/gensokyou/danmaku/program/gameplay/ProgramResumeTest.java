package com.bitsson.gensokyou.danmaku.program.gameplay;

import com.bitsson.gensokyou.danmaku.program.author.BossProgramController;
import com.bitsson.gensokyou.danmaku.program.author.StandardProgramOperations;
import com.bitsson.gensokyou.danmaku.program.content.BossProgramCards;
import com.bitsson.gensokyou.danmaku.program.math.*;
import com.bitsson.gensokyou.danmaku.program.ops.Actions;
import com.bitsson.gensokyou.danmaku.program.server.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Resumed-battle wiring: a restored encounter binds without re-installing background/cards, keeps
 * its HUD card index, and independent owners stay independent.
 */
class ProgramResumeTest {
    private static final UUID OWNER = new UUID(0, 1);
    private static final UUID OTHER = new UUID(0, 2);
    private static final DanmakuWorldRuntime.Limits LIMITS = new DanmakuWorldRuntime.Limits(8192, 512, 512, 512, 40000);

    @Test void resumeDoesNotReinstallAndKeepsCardIndex() {
        var deck = BossProgramCards.forOwner("big_fairy").orElseThrow();
        var original = session();
        var controller = new BossProgramController(original, OWNER);
        controller.background(deck.background(), 0);
        controller.card(deck.card(1).program(), 0); // ring => deck index 1
        for (int tick = 0; tick <= 65; tick++) { original.advance(tick, 8192); original.runtime().drainEvents(); }
        var encounter = original.runtime().encounter(OWNER).orElseThrow();
        int instancesBefore = original.checkpoint().instances().size();

        var restored = restore(original.checkpoint(), 5000);
        var resumedController = BossProgramController.resumed(restored, encounter);
        var battle = new BossProgramBattle(resumedController, deck);
        battle.adopt();
        assertEquals(1, battle.activeCardIndex(), "resumed deck index recovered from the definition hash");
        assertEquals(instancesBefore, restored.checkpoint().instances().size(), "no background/card re-install on resume");
        // Motion continues after resume.
        long from = restored.runtime().tick();
        restored.advance(from + 1, 8192); restored.runtime().drainEvents();
        assertTrue(restored.runtime().liveMembers() > 0);
        battle.close();
    }

    @Test void ownersRestoreIndependently() {
        var bigFairy = BossProgramCards.forOwner("big_fairy").orElseThrow();
        var yamame = BossProgramCards.forOwner("yamame").orElseThrow();
        var original = session();
        var a = new BossProgramController(original, OWNER);
        a.card(bigFairy.card(1).program(), 0);
        var b = new BossProgramController(original, OTHER);
        b.card(yamame.card(1).program(), 0);
        for (int tick = 0; tick <= 40; tick++) { original.advance(tick, 16384); original.runtime().drainEvents(); }
        assertTrue(original.runtime().active(original.runtime().encounter(OWNER).orElseThrow()));
        assertTrue(original.runtime().active(original.runtime().encounter(OTHER).orElseThrow()));

        var restored = restore(original.checkpoint(), 250);
        assertTrue(restored.runtime().active(restored.runtime().encounter(OWNER).orElseThrow()));
        assertTrue(restored.runtime().active(restored.runtime().encounter(OTHER).orElseThrow()));
        int ownerMembers = members(restored, OWNER);
        int otherMembers = members(restored, OTHER);
        assertTrue(ownerMembers > 0 && otherMembers > 0);
        // Ending one owner must not affect the other.
        new BossProgramController(restored, OWNER).close();
        assertTrue(restored.runtime().active(restored.runtime().encounter(OTHER).orElseThrow()), "other owner survives");
        assertEquals(otherMembers, members(restored, OTHER));
    }

    @Test void resumedSwitchEndsTheRealPreviousCardAndKeepsOneBackground() {
        var deck = BossProgramCards.forOwner("big_fairy").orElseThrow();
        var original = session();
        var controller = new BossProgramController(original, OWNER);
        controller.background(deck.background(), 0);
        controller.card(deck.card(1).program(), 0);
        for (int tick = 0; tick <= 65; tick++) { original.advance(tick, 8192); original.runtime().drainEvents(); }
        var restored = restore(original.checkpoint(), 1000);
        var encounter = restored.runtime().encounter(OWNER).orElseThrow();
        var resumed = BossProgramController.resumed(restored, encounter);
        assertEquals(1, restored.issuedCard(encounter), "recovered lifecycle card id");
        assertTrue(restored.backgroundInstalled(encounter));
        long tick = restored.runtime().tick();
        assertThrows(IllegalStateException.class, () -> resumed.background(deck.background(), tick + 1),
                "background must not install a second layer after resume");

        resumed.card(deck.card(2).program(), tick + 1);
        var nonBackground = restored.checkpoint().instances().stream().filter(i -> !i.background()).toList();
        assertEquals(1, nonBackground.size(), "exactly one live card");
        assertEquals(2, nonBackground.getFirst().card(), "new card got the next id, not a collision with the old");
        assertEquals(1, restored.checkpoint().instances().stream().filter(i -> i.background()).count(), "one background");
        assertTrue(restored.checkpoint().runtime().groups().stream().filter(g -> !g.background() && g.card() == 1)
                        .allMatch(g -> !g.emitting() && g.members().stream().noneMatch(DanmakuWorldRuntime.MemberSnapshot::harmful)),
                "the real previous card's groups were ended (their danmaku fade out instead of vanishing)");
    }

    @Test void restoredOwnersSurviveUntilTheirEntityLoads() {
        var bigFairy = BossProgramCards.forOwner("big_fairy").orElseThrow();
        var yamame = BossProgramCards.forOwner("yamame").orElseThrow();
        var original = session();
        new BossProgramController(original, OWNER).card(bigFairy.card(1).program(), 0);
        new BossProgramController(original, OTHER).card(yamame.card(1).program(), 0);
        for (int tick = 0; tick <= 10; tick++) { original.advance(tick, 16384); original.runtime().drainEvents(); }

        byte[] bytes = ProgramSessionCodec.encode(original.checkpoint(), StandardProgramOperations.limits());
        var decoded = ProgramSessionCodec.decode(bytes, StandardProgramOperations.limits(), StandardProgramOperations.registry());
        // Restore into a world where neither owner entity is present yet (chunk/entity still loading).
        var restored = ProgramSession.restore(ProgramSessionCodec.rebase(decoded, 50), LIMITS, new AbsentOwnerWorld(), StandardProgramOperations.execution());
        assertEquals(2, restored.runtime().encounters().size());
        long from = restored.runtime().tick();
        for (int k = 1; k <= 100; k++) { restored.advance(from + k, 16384); restored.runtime().drainEvents(); }
        assertEquals(2, restored.runtime().encounters().size(), "unseen owners are not cleared while loading");
        for (int k = 101; k <= 720; k++) { restored.advance(from + k, 16384); restored.runtime().drainEvents(); }
        assertTrue(restored.runtime().encounters().isEmpty(), "grace expires for owners that never load");
    }

    private static int members(ProgramSession session, UUID owner) {
        var encounter = session.runtime().encounter(owner).orElseThrow();
        return session.checkpoint().runtime().groups().stream()
                .filter(g -> g.encounter().equals(encounter))
                .mapToInt(g -> g.members().size()).sum();
    }

    private static ProgramSession restore(ProgramSession.Snapshot snapshot, long delta) {
        byte[] bytes = ProgramSessionCodec.encode(snapshot, StandardProgramOperations.limits());
        var decoded = ProgramSessionCodec.decode(bytes, StandardProgramOperations.limits(), StandardProgramOperations.registry());
        return ProgramSession.restore(ProgramSessionCodec.rebase(decoded, delta), LIMITS, new World(), StandardProgramOperations.execution());
    }

    private static ProgramSession session() {
        var runtime = new DanmakuWorldRuntime("test:resume", LIMITS, new World());
        return new ProgramSession(runtime, StandardProgramOperations.execution());
    }

    private static class World implements DecisionProvider {
        public List<Player> players() {
            return List.of(new Player(new UUID(0, 9), new Vec3(0, 0, 20), new Vec3(0, 1, 20), 2, true, false, false, .3));
        }
        public Optional<Actions.Pose> entity(UUID uuid) { return Optional.of(new Actions.Pose(Frame.IDENTITY, Vec3.ZERO)); }
        public boolean loaded(Vec3 point) { return true; }
        public Block block(int x, int y, int z) { return y <= 0 ? new Block(true, false, true, 1.0D) : new Block(true, true, false, 0); }
    }

    /** No owner entity is present (chunk/entity still loading). */
    private static final class AbsentOwnerWorld extends World {
        @Override public Optional<Actions.Pose> entity(UUID uuid) { return Optional.empty(); }
    }
}
