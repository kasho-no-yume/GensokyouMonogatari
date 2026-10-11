package com.bitsson.gensokyou.danmaku.track;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BossProjectileLifetimeTest {
    @Test
    void flowerAndSilkKeepSixtySecondLifetimeIndependentOfCardCycle() {
        var flower = BossCards.bigFairy().get(1);
        for (var track : flower.tracks()) {
            for (var beat : track.beats()) {
                assertEquals(400, beat.lifetimeTicks());
                assertEquals(100, beat.behaviour().motion().burstAtTick());
            }
        }
        assertEquals(240, flower.cycleTicks());

        int silkBeats = 0;
        for (var card : BossCards.yamame()) {
            for (var track : card.tracks()) {
                for (var beat : track.beats()) {
                    if (beat.behaviour().trail().active()) {
                        silkBeats++;
                        assertEquals(100, beat.lifetimeTicks());
                        assertEquals(400, beat.behaviour().trail().childLifetimeTicks());
                        assertEquals(4, beat.behaviour().trail().everyTicks());
                    }
                }
            }
        }
        assertEquals(6, silkBeats);
    }
}
