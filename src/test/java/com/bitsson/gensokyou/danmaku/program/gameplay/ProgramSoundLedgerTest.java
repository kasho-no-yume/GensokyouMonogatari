package com.bitsson.gensokyou.danmaku.program.gameplay;

import com.bitsson.gensokyou.client.ProgramSoundLedger;
import com.bitsson.gensokyou.danmaku.program.network.ProgramRenderView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgramSoundLedgerTest {
    @Test
    void deduplicatesWithinEpochAndResetsOnNewEpoch() {
        ProgramSoundLedger ledger=new ProgramSoundLedger();
        var cue=new ProgramRenderView.Sound(2,3,"test:sound","flash");
        assertTrue(ledger.accept(7,cue));
        assertFalse(ledger.accept(7,cue));
        assertTrue(ledger.accept(8,cue));
    }
}
