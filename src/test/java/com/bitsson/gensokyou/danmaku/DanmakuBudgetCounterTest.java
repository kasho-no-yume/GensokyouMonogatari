package com.bitsson.gensokyou.danmaku;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 弹幕诊断计数器「可写且不漏桶」的验证。
 *
 * <p><b>本文件守的是一次已发生的运行时崩溃</b>：三分类扩容时把直方图从
 * {@code new AtomicLong[]{ new AtomicLong(), … }} 改成了按长度计算的
 * {@code new AtomicLong[n]}——而<b>引用类型数组的元素默认初始化是 {@code null}</b>。
 * 编译无警告、既有单测无一碰它，直到客户端收到第一个位置包才在渲染线程上 NPE，
 * 表现为「一放弹幕就网络错误 / 客户端崩溃」。诊断统计读起来毫无异常。
 *
 * <p>所以这里不测数值，只测<b>每个桶都真的存在且真的能被写入</b>。
 */
class DanmakuBudgetCounterTest {

    @BeforeEach
    void reset() {
        DanmakuBudget.resetTiming();
    }

    @Test
    void ageOffsetAcceptsEverySource() {
        // 三个来源各写一次。任何一个来源的桶数组为 null 都会在此抛 NPE。
        for (int source = 0; source < 3; source++) {
            DanmakuBudget.recordAgeOffset(1.5D, source);
        }
        String stats = DanmakuBudget.ageOffsetStats();
        assertTrue(stats.contains("fresh-behind:n=1"), "fresh 落后段应有 1 个样本：" + stats);
        assertTrue(stats.contains("rebuilt-behind:n=1"), "rebuilt 落后段应有 1 个样本：" + stats);
        assertTrue(stats.contains("unseeded-behind:n=1"), "unseeded 落后段应有 1 个样本：" + stats);
    }

    /**
     * 「本端超前」MUST 被计入，MUST NOT 与「落后」混为一谈。
     *
     * <p><b>本条守的是一次已发生的诊断失效</b>：早期实现对 {@code lagTicks < 0} 直接丢弃。
     * 而超前恰恰是更糟的那一族（自变量对不上 = 失步，不是延迟）。实测 10677 个样本里
     * 只有 91 个进了读数，被丢弃的 10586 个全是超前——于是「lag=1、很健康」的读数实际
     * 只描述了不到 1% 的样本，剩下 99% 的失步**在读数里完全不存在**。
     *
     * <p>后果：整个归因过程被误导了几轮，包括「硬纠正计数高但滞后分布小」这种自相矛盾的
     * 读数无人能解释。诊断在失败时指错方向，比没有诊断更糟。
     */
    @Test
    void negativeProjectionIsCountedSeparatelyNotDropped() {
        DanmakuBudget.recordAgeOffset(4.0D, 1);   // 落后
        DanmakuBudget.recordAgeOffset(-9.0D, 1);  // 超前
        String stats = DanmakuBudget.ageOffsetStats();
        assertTrue(stats.contains("rebuilt-behind:n=1"), "落后样本 MUST 计入落后段：" + stats);
        assertTrue(stats.contains("rebuilt-ahead:n=1"), "超前样本 MUST 计入超前段：" + stats);
        // 超前段同样 MUST 给出分位数——它的幅度才是判定「是否真失步」的依据。
        String ahead = stats.replace("age[", "").replace("]", "").split(" ")[3];
        assertTrue(ahead.contains(",p50=") && ahead.contains(",p95="),
                "超前段 MUST 给出分位数（实际段：" + ahead + "）");
    }

    @Test
    void negativeAndNonFiniteSamplesAreIgnored() {
        DanmakuBudget.recordAgeOffset(Double.NaN, 0);
        DanmakuBudget.recordAgeOffset(Double.POSITIVE_INFINITY, 0);
        DanmakuBudget.recordAgeOffset(1.0D, -1);
        DanmakuBudget.recordAgeOffset(1.0D, 3);
        DanmakuBudget.recordAgeValue(-5);
        String stats = DanmakuBudget.ageOffsetStats();
        assertTrue(stats.contains("fresh-behind:n=0"), "非有限 / 非法来源 MUST NOT 计数：" + stats);
        assertTrue(stats.contains("rebuilt-behind:n=0"), "非法来源 MUST NOT 计数：" + stats);
        assertTrue(DanmakuBudget.ageValueStats().contains("0:0"),
                "负年龄 MUST NOT 计数：" + DanmakuBudget.ageValueStats());
    }

    @Test
    void resetClearsEveryBucket() {
        DanmakuBudget.recordAgeOffset(1.0D, 0);
        DanmakuBudget.recordAgeOffset(-1.0D, 1);
        DanmakuBudget.recordAgeValue(50);
        DanmakuBudget.resetTiming();
        String stats = DanmakuBudget.ageOffsetStats();
        for (String name : stats.replace("age[", "").replace("]", "").split(" ")) {
            assertTrue(name.endsWith("n=0"), "重置后每一段 MUST 为 0：" + stats);
        }
        assertTrue(DanmakuBudget.ageValueStats().contains("11-100:0"),
                "重置后年龄量级 MUST 清零：" + DanmakuBudget.ageValueStats());
    }
}
