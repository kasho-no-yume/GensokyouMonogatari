package com.bitsson.gensokyou.danmaku;

import com.bitsson.gensokyou.danmaku.DanmakuBudget.RemovalCause;
import com.bitsson.gensokyou.danmaku.track.BossCards;
import com.bitsson.gensokyou.danmaku.track.Track;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回收诊断的死因 × 年龄分布。
 *
 * <p>这个统计存在的唯一理由，是回答「这批弹幕为什么消失了、什么时候消失的」。
 * 它的正确性直接决定调试结论是否可信，所以三件事必须被钉死：
 * 死因分桶不串、年龄分桶边界准确、以及「没记录时」报告里明说是 none
 * 而不是把 0 说成 0 次以外的任何东西。
 */
class DanmakuBudgetRemovalTest {

    /**
     * 记录一组样本后清空，保证不与其他测试共享静态计数。
     */
    private static void reset() {
        DanmakuBudget.resetStats();
    }

    @Test
    @DisplayName("死因分别计数，不会全被算成同一种")
    void countsEachCauseSeparately() {
        reset();
        try {
            for (int i = 0; i < 3; i++) {
                DanmakuBudget.recordRemoval(RemovalCause.LIFETIME, 200);
            }
            DanmakuBudget.recordRemoval(RemovalCause.BLOCK, 30);
            DanmakuBudget.recordRemoval(RemovalCause.ENTITY, 12);

            String stats = DanmakuBudget.removalStats();
            assertTrue(stats.contains("n=5"), stats);
            assertTrue(stats.contains("lifetime=3"), stats);
            assertTrue(stats.contains("block=1"), stats);
            assertTrue(stats.contains("entity=1"), stats);
            // 关键回归：撞方块 / 撞玩家 / 寿命到期 MUST NOT 混成一个数
            assertFalse(stats.contains("block=3"), stats);
            assertFalse(stats.contains("block=4"), stats);
        } finally {
            reset();
        }
    }

    @Test
    @DisplayName("年龄分桶：边界值和区间内部的取值都必须落在正确的桶")
    void bucketsAgeBoundaries() {
        // 每行是 {年龄, 期望桶标签}。区间<b>内部</b>的取值 MUST 一起覆盖——
        // 只测边界值的话，一个「整体偏移一格」的实现能全部蒙对。
        // 直接写期望<b>标签</b>而不是期望下标：下标和标签之间再做一次心算，
        // 就等于把同一种错误换个地方再犯一遍。
        Object[][] cases = {
                {0, "0"}, {1, "1"},
                {2, "2-3"}, {3, "2-3"},
                {4, "4-7"}, {5, "4-7"}, {7, "4-7"},
                {8, "8-15"}, {12, "8-15"}, {15, "8-15"},
                {16, "16-31"}, {24, "16-31"}, {31, "16-31"},
                {32, "32-63"}, {48, "32-63"}, {63, "32-63"},
                {64, "64-127"}, {96, "64-127"}, {127, "64-127"},
                {128, "128-255"}, {164, "128-255"}, {200, "128-255"}, {255, "128-255"},
                {256, "256-511"}, {384, "256-511"}, {511, "256-511"},
                {512, "512-1023"}, {768, "512-1023"}, {1023, "512-1023"},
                {1024, "1024-2047"}, {1536, "1024-2047"}, {2047, "1024-2047"},
                {2048, "2048-4095"}, {3072, "2048-4095"}, {4095, "2048-4095"},
                {4096, "4096+"}, {100000, "4096+"}
        };

        reset();
        try {
            // 逐个单独记，这样每条统计行只包含一个样本，可以直接断言它落在哪个桶
            for (Object[] c : cases) {
                int age = (Integer) c[0];
                String expected = c[1] + ":1";
                reset();
                DanmakuBudget.recordRemoval(RemovalCause.LIFETIME, age);
                String stats = DanmakuBudget.removalStats();
                assertTrue(stats.endsWith(expected + "]"),
                        "age " + age + " should be in bucket " + expected + " but got: " + stats);
            }
        } finally {
            reset();
        }
    }

    @Test
    @DisplayName("负年龄按 0 记，不会掉出桶数组")
    void clampsNegativeAge() {
        reset();
        try {
            DanmakuBudget.recordRemoval(RemovalCause.OTHER, -5);
            String stats = DanmakuBudget.removalStats();
            assertTrue(stats.contains("n=1"), stats);
            assertTrue(stats.contains("other=1"), stats);
        } finally {
            reset();
        }
    }

    @Test
    @DisplayName("一条都没记时明说 none")
    void reportsNoneWhenEmpty() {
        reset();
        assertTrue(DanmakuBudget.removalStats().contains("none"),
                DanmakuBudget.removalStats());
    }

    /**
     * 大妖精符卡 2 的形状回归：每朵 46 枚、寿命从真实符卡里读。
     *
     * <p>参数 MUST 从 {@code BossCards} 读，不能把 45/200/16 抄一遍——抄一遍的话，
     * 改寿命的那个提交不会让这条测试变红，诊断就会悄悄对着一个不存在的形状说话。
     */
    @Test
    @DisplayName("大妖精花海：每朵 46 枚同生同灭，且年龄分布只落在一个桶里")
    void bigFairyFlowersDieUniformlyAtLifetime() {
        Track flowers = BossCards.bigFairy().get(1).tracks().get(0);
        List<Track.Beat> beats = flowers.beats();
        int lifetime = beats.get(0).lifetimeTicks();
        // FLOWER 形状实际发射 count + 1（多出来的是花心）
        int petalsPerFlower = (int) beats.get(0).params().count() + 1;

        reset();
        try {
            int total = 0;
            for (Track.Beat beat : beats) {
                for (int p = 0; p < petalsPerFlower; p++) {
                    DanmakuBudget.recordRemoval(RemovalCause.LIFETIME, lifetime);
                    total++;
                }
            }
            String stats = DanmakuBudget.removalStats();
            assertTrue(stats.contains("n=" + total), stats);
            assertTrue(stats.contains("lifetime=" + total), stats);
            // 同一朵花的全部花瓣 MUST 落在同一个年龄桶里（所以只有 1 个非零桶）
            String agePart = stats.substring(stats.indexOf("age ") + 4).trim();
            long nonZeroBuckets = java.util.Arrays.stream(agePart.split("\\s+"))
                    .map(s -> s.replace("]", ""))
                    .filter(s -> Integer.parseInt(s.substring(s.indexOf(':') + 1)) > 0)
                    .count();
            assertTrue(nonZeroBuckets == 1L,
                    "同生同灭的花瓣 MUST 只占一个年龄桶，实际占了 " + nonZeroBuckets + " 个: " + stats);
        } finally {
            reset();
        }
    }
}