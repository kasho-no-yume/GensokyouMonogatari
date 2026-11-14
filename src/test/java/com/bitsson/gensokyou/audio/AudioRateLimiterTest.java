package com.bitsson.gensokyou.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudioRateLimiterTest {

    @Test
    void perTickCapAppliesToNewInstances() {
        AudioRateLimiter limiter = new AudioRateLimiter(4);
        for (int i = 0; i < 100; i++) {
            limiter.accept(AudioCue.SHOT_NORMAL);
        }
        assertEquals(4, limiter.usedThisTick(), "同一 tick 内最多放行 perTickCap 条");
    }

    @Test
    void nextTickRestoresTheBudget() {
        AudioRateLimiter limiter = new AudioRateLimiter(2);
        assertTrue(limiter.accept(AudioCue.SHOT_NORMAL));
        assertTrue(limiter.accept(AudioCue.KNIFE_THROW));
        assertFalse(limiter.accept(AudioCue.LASER_SHOOT));
        limiter.onTick();
        assertTrue(limiter.accept(AudioCue.LASER_SHOOT));
    }

    @Test
    void perCueMinimumIntervalIsMeasuredInTicks() {
        AudioRateLimiter limiter = new AudioRateLimiter(16);
        // SHOT_FAST: minIntervalTicks = 2
        assertTrue(limiter.accept(AudioCue.SHOT_FAST));
        assertFalse(limiter.accept(AudioCue.SHOT_FAST), "同一 tick 内重复必须被吞");
        limiter.onTick();
        assertFalse(limiter.accept(AudioCue.SHOT_FAST), "差 1 tick 仍不足最小间隔");
        limiter.onTick();
        assertTrue(limiter.accept(AudioCue.SHOT_FAST), "满 2 tick 后恢复");
    }

    @Test
    void informationalCueIsNotCountedAgainstTheGlobalCap() {
        AudioRateLimiter limiter = new AudioRateLimiter(1);
        assertTrue(limiter.accept(AudioCue.SHOT_NORMAL));
        assertFalse(limiter.accept(AudioCue.KNIFE_THROW), "全局额度已打满");
        assertTrue(limiter.accept(AudioCue.WARNING_SHIFT), "信息类 cue 不占全局额度");
        assertTrue(limiter.accept(AudioCue.SPELLCARD_SWITCH), "信息类 cue 不占全局额度");
        assertEquals(1, limiter.usedThisTick());
    }

    @Test
    void suppressedTriggersLeaveNoQueue() {
        AudioRateLimiter limiter = new AudioRateLimiter(1);
        assertTrue(limiter.accept(AudioCue.SHOT_NORMAL));
        for (int i = 0; i < 10; i++) {
            assertFalse(limiter.accept(AudioCue.KNIFE_THROW));
        }
        limiter.onTick();
        // 新 tick 只给新请求额度；被吞掉的 10 次不会补播。
        assertTrue(limiter.accept(AudioCue.KNIFE_THROW));
        assertEquals(1, limiter.usedThisTick());
    }

    @Test
    void resetClearsTicksAndIntervals() {
        AudioRateLimiter limiter = new AudioRateLimiter(1);
        limiter.accept(AudioCue.SHOT_FAST);
        limiter.onTick();
        limiter.reset();
        assertEquals(0, limiter.tick());
        assertEquals(0, limiter.usedThisTick());
        assertTrue(limiter.accept(AudioCue.SHOT_FAST));
    }
}
