package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.network.InfoLine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 共鸣候选三态循环 nextLinkState：入→出→无 完整循环、缺属性环节跳过、任何链接可取消。
 * 对应 spec resonance-relay-ritual 的"界面选链"三态循环要求。
 */
class ResonanceRelayBehaviorTest {

    private static final int NONE = InfoLine.LINK_NONE;
    private static final int IN = InfoLine.LINK_IN;
    private static final int OUT = InfoLine.LINK_OUT;

    @Test
    void dualAttributeFullCycle() {
        // 未选起步 → 首选"入"，完整循环 入→出→无→入
        assertEquals(IN, ResonanceRelayBehavior.nextLinkState(NONE, true, true));
        assertEquals(OUT, ResonanceRelayBehavior.nextLinkState(IN, true, true));
        assertEquals(NONE, ResonanceRelayBehavior.nextLinkState(OUT, true, true));
        assertEquals(IN, ResonanceRelayBehavior.nextLinkState(NONE, true, true));
    }

    @Test
    void outOnlyCyclesInAndNone() {
        assertEquals(IN, ResonanceRelayBehavior.nextLinkState(NONE, true, false));
        assertEquals(NONE, ResonanceRelayBehavior.nextLinkState(IN, true, false));
    }

    @Test
    void inOnlyCyclesOutAndNone() {
        assertEquals(OUT, ResonanceRelayBehavior.nextLinkState(NONE, false, true));
        assertEquals(NONE, ResonanceRelayBehavior.nextLinkState(OUT, false, true));
    }

    @Test
    void attributeLostResidualLinkUnlinksImmediately() {
        // 无属性残余（双 false）：任意当前态一步到"无"
        assertEquals(NONE, ResonanceRelayBehavior.nextLinkState(IN, false, false));
        assertEquals(NONE, ResonanceRelayBehavior.nextLinkState(OUT, false, false));
        assertEquals(NONE, ResonanceRelayBehavior.nextLinkState(NONE, false, false));
        // 单侧失效残余：当前"入"但 canIn=false → 先解除，不跳向其他方向
        assertEquals(NONE, ResonanceRelayBehavior.nextLinkState(IN, false, true));
        assertEquals(NONE, ResonanceRelayBehavior.nextLinkState(OUT, true, false));
    }

    @Test
    void cancelAlwaysReachable() {
        // 任意属性组合下，从任一已链接态有限步内可回到"无"
        assertEquals(NONE, ResonanceRelayBehavior.nextLinkState(OUT, true, true));
        assertEquals(NONE, ResonanceRelayBehavior.nextLinkState(IN, true, false));
        assertEquals(NONE, ResonanceRelayBehavior.nextLinkState(OUT, false, true));
    }

    @Test
    void monotonicRateSteadyNoOverread() {
        // 稳定 1280/s：任何 ≥1 周期窗口的差分读数恒为 1280，绝不过读
        assertEquals(1280L, ResonanceRelayBehavior.monotonicRate(1280L, 20L, 0L, 20));
        assertEquals(1280L, ResonanceRelayBehavior.monotonicRate(6400L, 100L, 0L, 20));
    }

    @Test
    void monotonicRateShortWindowKeepsLast() {
        // 窗不足周期：沿用旧读数，不闪 0
        assertEquals(1280L, ResonanceRelayBehavior.monotonicRate(320L, 5L, 1280L, 20));
        assertEquals(0L, ResonanceRelayBehavior.monotonicRate(0L, 1L, 0L, 20));
    }

    @Test
    void monotonicRateSilentAndRollback() {
        // 静默期：长窗零差分 → 0，无残影
        assertEquals(0L, ResonanceRelayBehavior.monotonicRate(0L, 200L, 1280L, 20));
        // 计数回退 / 时钟非正（BE 重载等）→ 0 重播种
        assertEquals(0L, ResonanceRelayBehavior.monotonicRate(-5L, 20L, 1280L, 20));
        assertEquals(0L, ResonanceRelayBehavior.monotonicRate(100L, 0L, 1280L, 20));
    }
}
