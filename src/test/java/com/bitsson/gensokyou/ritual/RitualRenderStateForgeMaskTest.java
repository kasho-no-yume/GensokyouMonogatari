package com.bitsson.gensokyou.ritual;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 煅炉燃烧位掩码的位序契约：bit0=任一任务在烧，bit(i+1)=第 i 个台位在烧。
 *
 * <p>客户端按 {@code linkPos} 索引取锚点并读同一位，故位序一旦错位就会
 * "A 台冒火、B 台不冒"，必须由纯函数测试钉死。
 */
class RitualRenderStateForgeMaskTest {

    @Test
    void noBurningLeavesWholeMaskEmpty() {
        assertEquals(0L, RitualRenderState.forgeBurnMask(false, 0L));
    }

    @Test
    void anyBurningOnlySetsBitZeroWhenNoPedestal() {
        long mask = RitualRenderState.forgeBurnMask(true, 0L);
        assertEquals(RitualRenderState.MASK_KANAYAMAHIKO_BURNING, mask);
    }

    @Test
    void pedestalBitsAreShiftedByOne() {
        // 原始位图 bit0/bit2 表示第 0、2 个台位在烧
        long mask = RitualRenderState.forgeBurnMask(true, 0b101L);
        assertTrue((mask & RitualRenderState.MASK_KANAYAMAHIKO_BURNING) != 0L, "bit0 = any");
        assertTrue((mask & (1L << 1)) != 0L, "pedestal 0 -> bit1");
        assertTrue((mask & (1L << 2)) == 0L, "pedestal 1 idle -> bit2 clear");
        assertTrue((mask & (1L << 3)) != 0L, "pedestal 2 -> bit3");
    }

    @Test
    void roundTripsThroughNbt() {
        long mask = RitualRenderState.forgeBurnMask(true, 0b1011L);
        RitualRenderState state = new RitualRenderState(
                RitualRenderState.KIND_KANAYAMAHIKO, true, 1, 0, 12, 0,
                new long[]{1L, 2L, 3L}, 0, mask);
        RitualRenderState decoded = RitualRenderState.fromTag(state.toTag());
        assertEquals(state, decoded);
        assertTrue(decoded.forgeBurning(), "any");
        assertTrue(decoded.forgePedestalBurning(0), "pedestal 0");
        assertTrue(decoded.forgePedestalBurning(1), "pedestal 1");
        assertFalse(decoded.forgePedestalBurning(2), "pedestal 2");
        assertFalse(decoded.forgePedestalBurning(3), "out of anchor range");
        assertFalse(decoded.forgePedestalBurning(-1), "negative index");
    }

    @Test
    void otherKindsNeverReadForgeBits() {
        RitualRenderState relay = new RitualRenderState(
                RitualRenderState.KIND_RELAY, true, 0, 0, 0, 20,
                new long[]{1L, 2L}, 0, RitualRenderState.MASK_KANAYAMAHIKO_BURNING);
        assertFalse(relay.forgeBurning());
        assertFalse(relay.forgePedestalBurning(0));
    }

    @Test
    void pedestalMaskIsClampedToChannelBudget() {
        // 63 个台位 = bit1..bit63，正好用满（bit0 被"任一燃烧"占用）
        long all = (1L << 63) - 1L;
        long mask = RitualRenderState.forgeBurnMask(true, all);
        for (int i = 0; i < 63; i++) {
            long bit = 1L << (i + 1);
            assertTrue((mask & bit) != 0L, "pedestal " + i + " must be addressable");
        }
    }

    @Test
    void unmaskedChannelBudgetIsExplicit() {
        assertEquals(64, RitualRenderState.MAX_CHANNELS);
        // 结构上限 32 个台位，位预算必须有富余
        assertTrue(RitualRenderState.MAX_CHANNELS - 1 >= 32);
    }

    @Test
    void linkAtRoundTripsBlockPos() {
        RitualRenderState state = new RitualRenderState(
                RitualRenderState.KIND_KANAYAMAHIKO, true, 0, 0, 8, 0,
                new long[]{new net.minecraft.core.BlockPos(5, 64, -7).asLong()}, 0, 0L);
        assertEquals(1, state.channelCount());
        assertEquals(5, state.linkAt(0).getX());
        assertEquals(64, state.linkAt(0).getY());
        assertEquals(-7, state.linkAt(0).getZ());
    }
}
