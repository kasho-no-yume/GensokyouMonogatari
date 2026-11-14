package com.bitsson.gensokyou.danmaku.program.gameplay;

import com.bitsson.gensokyou.client.ProgramSoundLedger;
import com.bitsson.gensokyou.danmaku.program.math.Vec3;
import com.bitsson.gensokyou.danmaku.program.network.ProgramRenderView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgramSoundLedgerTest {
    @Test
    void deduplicatesWithinEpochAndResetsOnNewEpoch() {
        ProgramSoundLedger ledger = new ProgramSoundLedger();
        var cue = sound(1, 0, "b:1/0:MEMBER_AGE:0", "flash");
        assertTrue(ledger.accept(7, cue));
        assertFalse(ledger.accept(7, cue));
        assertTrue(ledger.accept(8, cue));
    }

    @Test
    void memberScopedCuesRemainPerBatch() {
        ProgramSoundLedger ledger = new ProgramSoundLedger();
        assertTrue(ledger.accept(7, sound(1, 0, "b:1/0:MEMBER_AGE:0", "throw")));
        assertTrue(ledger.accept(7, sound(2, 0, "b:2/0:MEMBER_AGE:0", "throw")));
    }

    @Test
    void cycleScopedCueCollapsesAcrossBatches() {
        ProgramSoundLedger ledger = new ProgramSoundLedger();
        assertTrue(ledger.accept(7, sound(1, 0, "c:100:CYCLE:100", "burst")));
        assertFalse(ledger.accept(7, sound(2, 1, "c:100:CYCLE:100", "burst")));
        // 下一轮 cycle 锚点不同，必须重新播放。
        assertTrue(ledger.accept(7, sound(3, 2, "c:340:CYCLE:100", "burst")));
    }

    @Test
    void cardScopedCueCollapsesWithinCard() {
        ProgramSoundLedger ledger = new ProgramSoundLedger();
        assertTrue(ledger.accept(7, sound(1, 0, "k:0:CARD:30", "declare")));
        assertFalse(ledger.accept(7, sound(2, 1, "k:0:CARD:30", "declare")));
        assertTrue(ledger.accept(7, sound(3, 2, "k:500:CARD:30", "declare")));
    }

    private static ProgramRenderView.Sound sound(long group, long batch, String scope, String key) {
        return new ProgramRenderView.Sound(group, batch, "test:sound", key, scope, Vec3.ZERO);
    }
}
