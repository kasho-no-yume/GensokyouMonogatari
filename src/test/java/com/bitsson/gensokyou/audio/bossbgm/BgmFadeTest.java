package com.bitsson.gensokyou.audio.bossbgm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 淡变状态机的纯逻辑断言。
 *
 * <p>关键不是"数值好看"，而是三条<b>会被实机听见</b>的硬约束：
 * 首帧必须非零（否则被 {@code SoundEngine} 丢弃）、淡变不短于 2 秒、曲线不是线性。
 */
class BgmFadeTest {

    @Test
    void firstFrameIsPlayableForTheEngine() {
        BgmFade fade = new BgmFade();
        // 起音的线性进度是 0，但交给引擎的振幅必须 > 0，否则实例在入队前就被丢弃。
        assertEquals(0.0D, fade.amplitude(), 1.0E-9D);
        assertTrue(fade.playableAmplitude() > 0.0D, "首帧必须给引擎非零音量");
        assertTrue(fade.playableAmplitude() < 0.01D, "首帧必须听不见");
    }

    @Test
    void fadeInTakesTheDeclaredDuration() {
        BgmFade fade = new BgmFade(40, 50);
        for (int i = 0; i < 39; i++) {
            fade.signal();
            fade.advance();
        }
        assertTrue(!fade.sustained(), "39 tick 时淡入不应结束");
        fade.signal();
        fade.advance();
        assertTrue(fade.sustained(), "40 tick 后必须进入常驻");
        assertEquals(1.0D, fade.amplitude(), 1.0E-9D);
    }

    @Test
    void fadesAreNotShorterThanTwoSeconds() {
        assertTrue(BgmFade.FADE_IN_TICKS >= 40, "淡入不得短于 2 秒（40 tick）");
        assertTrue(BgmFade.FADE_OUT_TICKS >= 40, "淡出不得短于 2 秒（40 tick）");
    }

    @Test
    void perceptualCurveIsConvexNotLinear() {
        // 振幅 = p^1.7：中点必须明显低于线性的 0.5，否则听感是"前半段没变、末尾骤降"。
        double half = BgmFade.perceptual(0.5D);
        assertTrue(half < 0.5D, "曲线必须比线性更凹");
        assertTrue(half > 0.2D, "曲线不能凹到听不见");
        assertEquals(1.0D, BgmFade.perceptual(1.0D), 1.0E-9D);
        assertEquals(0.0D, BgmFade.perceptual(0.0D), 1.0E-9D);
        assertEquals(1.0D, BgmFade.perceptual(2.0D), 1.0E-9D, "进度必须夹到 1");
    }

    @Test
    void fadeOutRunsToCompletionAndStops() {
        BgmFade fade = new BgmFade(40, 50);
        for (int i = 0; i < 40; i++) {
            fade.signal();
            fade.advance();
        }
        fade.requestStop();
        fade.requestStop(); // 幂等
        for (int i = 0; i < 49; i++) {
            fade.signal();
            fade.advance();
        }
        assertTrue(!fade.stopped(), "淡出未满 50 tick 不应停止");
        assertTrue(fade.amplitude() > 0.0D);
        fade.signal();
        fade.advance();
        assertTrue(fade.stopped());
        assertEquals(0.0D, fade.amplitude(), 1.0E-9D);
    }

    @Test
    void watchdogOutlastsFadeOutAndStopsTheInstance() {
        assertTrue(BgmFade.WATCHDOG_TICKS > BgmFade.FADE_OUT_TICKS,
                "看门狗必须长于淡出，否则会把淡出截断");
        BgmFade fade = new BgmFade();
        for (int i = 0; i < BgmFade.WATCHDOG_TICKS; i++) {
            fade.advance(); // 从不 signal
        }
        assertTrue(fade.stopped());
    }

    @Test
    void progressIsMonotonicInBothDirections() {
        BgmFade fade = new BgmFade(40, 50);
        double previous = -1.0D;
        for (int i = 0; i < 40; i++) {
            fade.signal();
            fade.advance();
            double now = fade.amplitude();
            assertTrue(now >= previous - 1.0E-9D, "淡入必须单调不减");
            previous = now;
        }
        fade.requestStop();
        previous = 2.0D;
        while (!fade.stopped()) {
            fade.signal();
            fade.advance();
            double now = fade.amplitude();
            assertTrue(now <= previous + 1.0E-9D, "淡出必须单调不增");
            previous = now;
        }
    }
}
