package com.bitsson.gensokyou.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeamEnvelopeTest {

    @Test
    void attackRampsNonLinearlyAndReachesFullAmplitudeOnTheThirdTick() {
        BeamEnvelope envelope = new BeamEnvelope(false);
        envelope.signal();
        envelope.advance();
        double expected = Math.pow(1.0D / BeamEnvelope.FADE_TICKS,
                BeamEnvelope.PERCEPTUAL_EXPONENT);
        assertEquals(expected, envelope.amplitude(), 1.0E-9,
                "起音必须走 p^1.7 感知曲线，而不是线性振幅");
        envelope.advance();
        envelope.advance();
        assertEquals(1.0D, envelope.amplitude(), 1.0E-9, "3 tick 后应到达满幅");
    }

    @Test
    void fadeDurationIsAtLeastThreeTicks() {
        BeamEnvelope envelope = new BeamEnvelope(false);
        envelope.signal();
        for (int i = 0; i < BeamEnvelope.FADE_TICKS; i++) {
            envelope.advance();
        }
        envelope.requestStop();
        assertFalse(envelope.stopped(), "收尾刚开始时不得立刻停止");
        envelope.advance();
        assertFalse(envelope.stopped());
        envelope.advance();
        assertFalse(envelope.stopped());
        envelope.advance();
        assertTrue(envelope.stopped(), "收尾恰好 3 tick 后停止");
        assertTrue(BeamEnvelope.FADE_TICKS * 50 >= 150, "3 tick = 150 ms，满足下限");
    }

    @Test
    void releaseStartsFromCurrentAmplitudeWithoutAJump() {
        BeamEnvelope envelope = new BeamEnvelope(false);
        envelope.signal();
        envelope.advance(); // attack, 1/3
        double before = envelope.amplitude();
        envelope.requestStop();
        assertEquals(before, envelope.amplitude(), 1.0E-9,
                "收尾必须从当前振幅连续开始，MUST NOT 先跳回满幅");
    }

    @Test
    void watchdogStopsAfterTenTicksWithoutSignal() {
        BeamEnvelope envelope = new BeamEnvelope(false);
        for (int i = 0; i < BeamEnvelope.WATCHDOG_TICKS - 1; i++) {
            envelope.advance();
        }
        assertFalse(envelope.stopped(), "9 tick 时仍可存活");
        envelope.advance();
        assertTrue(envelope.stopped(), "第 10 tick 未收到存续信号必须自行停止");
    }

    @Test
    void signalResetsTheWatchdog() {
        BeamEnvelope envelope = new BeamEnvelope(false);
        for (int i = 0; i < BeamEnvelope.WATCHDOG_TICKS - 1; i++) {
            envelope.advance();
        }
        envelope.signal();
        for (int i = 0; i < BeamEnvelope.WATCHDOG_TICKS - 1; i++) {
            envelope.advance();
        }
        assertFalse(envelope.stopped());
    }

    @Test
    void pitchRampIsOptionalAndOnlyOnAttack() {
        BeamEnvelope ramped = new BeamEnvelope(true);
        ramped.signal();
        ramped.advance();
        assertEquals(BeamEnvelope.PITCH_FROM + (1.0D - BeamEnvelope.PITCH_FROM) / 3.0D,
                ramped.pitchFactor(), 1.0E-9);
        for (int i = 0; i < 5; i++) {
            ramped.advance();
        }
        assertEquals(1.0D, ramped.pitchFactor(), 1.0E-9, "循环段音高回到基准");

        BeamEnvelope flat = new BeamEnvelope(false);
        flat.signal();
        flat.advance();
        assertEquals(1.0D, flat.pitchFactor(), 1.0E-9, "可由 cue 参数关闭音高斜坡");
    }

    @Test
    void stopIsIdempotent() {
        BeamEnvelope envelope = new BeamEnvelope(false);
        envelope.signal();
        envelope.advance();
        envelope.requestStop();
        envelope.requestStop();
        for (int i = 0; i < BeamEnvelope.FADE_TICKS; i++) {
            envelope.advance();
        }
        assertTrue(envelope.stopped());
        assertEquals(0.0D, envelope.amplitude(), 1.0E-9);
    }

    @Test
    void playableAmplitudeNeverFeedsZeroToTheEngine() {
        // SoundEngine 在 play 时对 volume==0 的实例直接跳过；起音第 0 tick 必须被夹到非零下限。
        assertEquals(BeamEnvelope.MIN_PLAYABLE_AMPLITUDE,
                BeamEnvelope.playableAmplitude(0.0D), 1.0E-12);
        assertTrue(BeamEnvelope.playableAmplitude(0.0D) > 0.0D);
        assertEquals(0.5D, BeamEnvelope.playableAmplitude(0.5D), 1.0E-12);
    }
}
