package com.bitsson.gensokyou.danmaku.visual;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 相位隐藏态的时序断言。
 *
 * <p>本状态的全部意义在于「整片弹幕墙按固定节奏明灭」。三条硬要求：
 * 零同步包、隐藏期不判伤且不销毁、方块碰撞仍生效。前者由「只读 {@code tickCount}」
 * 保证（可从下面的测试读出：无状态、无累加器），后两者由
 * {@code SphereDanmaku#canHitEntity} 覆写与方块分支的独立性保证。
 */
class DanmakuPhaseTest {

    /** 2 秒周期、50% 占空比——弹幕墙的典型参数。 */
    private static final int PERIOD = 40;
    private static final double DUTY = 0.5D;

    @Test
    void dutyOneIsAlwaysVisible() {
        for (int t = 0; t < 200; t++) {
            assertFalse(DanmakuPhase.isHidden(t, PERIOD, 1.0D, 0), "占空比 1 MUST 恒可见");
        }
    }

    @Test
    void zeroPeriodDisablesPhasing() {
        for (int t = 0; t < 50; t++) {
            assertFalse(DanmakuPhase.isHidden(t, 0, DUTY, 0), "周期 0 MUST 关闭相位隐藏");
            assertFalse(DanmakuPhase.isHidden(t, -5, DUTY, 0), "负周期 MUST 同样关闭");
        }
    }

    @Test
    void zeroDutyIsAlwaysHidden() {
        for (int t = 0; t < 50; t++) {
            assertTrue(DanmakuPhase.isHidden(t, PERIOD, 0.0D, 0), "占空比 0 MUST 恒隐藏");
        }
    }

    /** 50% 占空比下，隐藏态 MUST 恰好占一半 tick。 */
    @Test
    void halfDutyHidesHalfTheTicks() {
        int hidden = 0;
        for (int t = 0; t < PERIOD; t++) {
            if (DanmakuPhase.isHidden(t, PERIOD, DUTY, 0)) {
                hidden++;
            }
        }
        assertEquals(PERIOD / 2, hidden);
    }

    /**
     * 相位偏移 MUST 只平移波形，不改变隐藏比例。
     *
     * <p>这是「逐弹错峰」的依据：同批弹要能错开，但整体密度不变。
     */
    @Test
    void phaseOffsetShiftsWithoutChangingRatio() {
        int base = 0;
        int shifted = 0;
        for (int t = 0; t < PERIOD; t++) {
            if (DanmakuPhase.isHidden(t, PERIOD, DUTY, 0)) {
                base++;
            }
            if (DanmakuPhase.isHidden(t, PERIOD, DUTY, 7)) {
                shifted++;
            }
        }
        assertEquals(base, shifted, "相位偏移 MUST 不改变隐藏 tick 比例");
    }

    /** 相位偏移 MUST 真的生效（不是被忽略的占位参数）。 */
    @Test
    void phaseOffsetActuallyShiftsTheWave() {
        boolean differs = false;
        for (int t = 0; t < PERIOD; t++) {
            if (DanmakuPhase.isHidden(t, PERIOD, DUTY, 0)
                    != DanmakuPhase.isHidden(t, PERIOD, DUTY, PERIOD / 2)) {
                differs = true;
                break;
            }
        }
        assertTrue(differs, "偏移半个周期 MUST 产生与原波形不同的状态");
    }

    /**
     * 负偏移 MUST 产生正确波形，即与它的模等价正偏移（{@code P − |offset|}）完全一致。
     *
     * <p>用 {@code %} 时负偏移会出错：Java 的 {@code %} <b>保留符号</b>，
     * {@code 0 + (-5)} 得 −5，再与「可见段长度」比较即得出错误结论。
     * 负偏移是自然用法——「先发的那批弹」正是负相位。
     */
    @Test
    void negativePhaseOffsetMatchesModularEquivalent() {
        for (int t = 0; t < PERIOD; t++) {
            assertEquals(DanmakuPhase.isHidden(t, PERIOD, DUTY, -5),
                    DanmakuPhase.isHidden(t, PERIOD, DUTY, PERIOD - 5),
                    "tick=" + t + " 处负偏移 MUST 等于其模等价正偏移");
        }
    }

    /** 负偏移 MUST NOT 退化成「恒可见」——那正是 {@code %} 保留符号会导致的症状。 */
    @Test
    void negativePhaseOffsetStillProducesHiddenTicks() {
        int hidden = 0;
        for (int t = 0; t < PERIOD; t++) {
            if (DanmakuPhase.isHidden(t, PERIOD, DUTY, -5)) {
                hidden++;
            }
        }
        assertEquals(PERIOD / 2, hidden,
                "负偏移下隐藏 tick 数 MUST 与无偏移相同，实测 " + hidden);
    }

    /** 可见态 SHALL 留出足够反应时间——否则弹幕墙退化成「长时间无敌」而非「读节奏」。 */
    @Test
    void visibleWindowIsLongEnoughToReact() {
        int period = 40;
        double duty = 0.5D;
        int visibleTicks = (int) Math.round(duty * period);
        assertTrue(visibleTicks >= 20,
                "可见段应 ≥ 20 tick（1 秒）才够玩家反应，实测 " + visibleTicks);
    }

    @Test
    void visibleProgressResetsEachPeriod() {
        assertEquals(0.0D, DanmakuPhase.visibleProgress(0, PERIOD, 0), 1.0E-9D);
        assertEquals(0.25D, DanmakuPhase.visibleProgress(10, PERIOD, 0), 1.0E-9D);
        assertEquals(0.0D, DanmakuPhase.visibleProgress(PERIOD, PERIOD, 0), 1.0E-9D,
                "跨周期 MUST 归零");
        assertEquals(1.0D, DanmakuPhase.visibleProgress(0, 0, 0), 1.0E-9D,
                "周期 0（关闭）时进度 MUST 为 1，即恒可见");
    }
}
