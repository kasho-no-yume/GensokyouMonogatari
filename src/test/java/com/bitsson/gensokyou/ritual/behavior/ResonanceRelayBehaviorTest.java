package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.network.InfoLine;
import com.bitsson.gensokyou.ritual.RitualLink;
import com.bitsson.gensokyou.ritual.RitualRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 共鸣候选三态循环 nextLinkState：入→出→无 完整循环、缺属性环节跳过、任何链接可取消。
 * 对应 spec resonance-relay-ritual 的"界面选链"三态循环要求。
 * 另覆盖渲染侧纯函数：per-period 速率 memo 与通道位掩码序。
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

    // ---- 渲染侧纯函数：per-period memo / 通道掩码序 ----

    @Test
    void memoizedRateResolvesEachEndpointOncePerPeriod() {
        BlockPos a = new BlockPos(0, 64, 0);
        BlockPos b = new BlockPos(10, 64, 10);
        Map<BlockPos, Long> cache = new HashMap<>();
        Map<BlockPos, Long> truth = Map.of(a, 420L, b, 77L);
        AtomicInteger calls = new AtomicInteger();
        // 模拟 O(入×出) 内层重复取用：memo 后每端点每周期恰解析一次，值与逐次重扫一致
        for (int pair = 0; pair < 6; pair++) {
            for (BlockPos pos : List.of(a, b)) {
                long got = ResonanceRelayBehavior.memoizedRate(cache, pos,
                        p -> {
                            calls.incrementAndGet();
                            return truth.get(p);
                        });
                assertEquals(truth.get(pos), got);
            }
        }
        assertEquals(2, calls.get());
    }

    @Test
    void channelIndexOrderIsInLinksThenOutLinks() {
        BlockPos inA = new BlockPos(1, 2, 3);
        BlockPos inB = new BlockPos(4, 5, 6);
        BlockPos outA = new BlockPos(7, 8, 9);
        ResourceLocation pattern = ResourceLocation.parse("gensokyou:zaohua_circle");
        List<RitualLink> in = List.of(new RitualLink(inA, pattern), new RitualLink(inB, pattern));
        List<RitualLink> out = List.of(new RitualLink(outA, pattern));
        Map<BlockPos, Integer> channels = ResonanceRelayBehavior.channelIndexMap(in, out);
        assertEquals(0, channels.get(inA));
        assertEquals(1, channels.get(inB));
        assertEquals(2, channels.get(outA)); // 出向自 inCount 接续
    }

    @Test
    void setChannelBitMapsMaskToCanonicalOrder() {
        BlockPos inA = new BlockPos(1, 2, 3);
        BlockPos outA = new BlockPos(7, 8, 9);
        ResourceLocation pattern = ResourceLocation.parse("gensokyou:zaohua_circle");
        Map<BlockPos, Integer> channels = ResonanceRelayBehavior.channelIndexMap(
                List.of(new RitualLink(inA, pattern)), List.of(new RitualLink(outA, pattern)));
        long mask = ResonanceRelayBehavior.setChannelBit(0L, channels, outA);
        mask = ResonanceRelayBehavior.setChannelBit(mask, channels, inA);
        assertEquals(0b11L, mask);
        // 未知坐标不置位（防御：结算集与索引表短暂失同步时静默跳过）
        assertEquals(0b11L, ResonanceRelayBehavior.setChannelBit(mask, channels,
                new BlockPos(99, 99, 99)));
        // 与渲染态读取端逐位一致
        RitualRenderState state = new RitualRenderState(RitualRenderState.KIND_RELAY,
                true, 2, 0, 1, 20,
                new long[]{inA.asLong(), outA.asLong()}, 1, mask);
        assertEquals(true, state.channelMoving(0));
        assertEquals(true, state.channelMoving(1));
    }
}
