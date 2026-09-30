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

    /**
     * 卡门判据 MUST 在「接近上限时对账」与「远离上限时不对账」之间取中。
     *
     * <p><b>这条守的是一个会自锁的设计。</b>旧实现把对账放在
     * {@code canEmit} 里、每 201 次调用一次，于是 BOSS 一停止攻击就没人调它，
     * 计数器永不自我纠正；一旦增量漂高，卡门就永久关死，而安全网恰好在需要它的
     * 时刻不跑。症状是「{@code live} 钉在上限、等多久都不降、画面上一发都没有」——
     * 而那个数字<b>本身就是不准的</b>，所以「多等一会儿」永远等不到它降。
     *
     * <p>两个方向都会写坏：永远对账 = 每 tick 全表扫描；永不对账 = 回到自锁。
     */
    @Test
    void gateReconcilesOnlyWhenNearTheCap() {
        long cap = 500L;
        assertFalse(DanmakuBudget.needsReconcileBeforeGate(0L, cap),
                "远离上限时 MUST NOT 对账（本来就会放行，精度无所谓）");
        assertFalse(DanmakuBudget.needsReconcileBeforeGate(100L, cap));
        assertFalse(DanmakuBudget.needsReconcileBeforeGate(468L, cap),
                "469 = 500 − max(16, 500/16) 是阈值，468 仍属安全区");
        assertTrue(DanmakuBudget.needsReconcileBeforeGate(469L, cap),
                "到达阈值 MUST 对账 —— 否则漂高的计数器会永久锁死卡门");
        assertTrue(DanmakuBudget.needsReconcileBeforeGate(cap, cap));
        assertTrue(DanmakuBudget.needsReconcileBeforeGate(cap + 137L, cap),
                "已超限（漂高）时 MUST 对账，那正是它能自愈的唯一机会");
    }

    /**
     * 极小上限时任何余量都算「接近」，否则 16 这个地板会让判据永远为假。
     *
     * <p>上限允许低到 16。若不取地板，{@code cap/16 = 1} 会让阈值变成 15，
     * 而 {@code live} 长期停在低位时永不触发对账 —— 恰好是最需要它的规模失去自愈。
     */
    @Test
    void tinyCapStillReconciles() {
        long cap = 16L;
        assertTrue(DanmakuBudget.needsReconcileBeforeGate(0L, cap),
                "上限 16 时 live=0 也算接近，地板 16 生效");
        assertTrue(DanmakuBudget.needsReconcileBeforeGate(16L, cap));
    }

    /**
     * 阈值 MUST 随上限缩放，不能是常数。
     *
     * <p>常数阈值在上限很大时会退化成「几乎从不对账」—— 而 500 这个实际配置下
     * 方向恰好相反：阈值太大 ⇒ 迟迟不对账 ⇒ 卡门先锁死后自愈。
     */
    @Test
    void thresholdScalesWithTheCap() {
        // cap 500  → 阈值 500 − max(16, 31)  = 469
        // cap 2000 → 阈值 2000 − max(16, 125) = 1875
        assertTrue(DanmakuBudget.needsReconcileBeforeGate(1875L, 2000L),
                "上限 2000 的阈值是 1875");
        assertFalse(DanmakuBudget.needsReconcileBeforeGate(1874L, 2000L),
                "1874 仍在安全区 —— 余量随上限成比例，不是常数");
        assertTrue(DanmakuBudget.needsReconcileBeforeGate(1000L, 500L),
                "同一个 live=1000 对上限 500 早就超限");
        assertFalse(DanmakuBudget.needsReconcileBeforeGate(1000L, 2000L),
                "而对上限 2000 它还有一半余量 —— 阈值必须随上限缩放");
    }
}
