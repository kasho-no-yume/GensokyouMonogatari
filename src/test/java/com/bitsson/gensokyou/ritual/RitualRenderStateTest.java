package com.bitsson.gensokyou.ritual;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 渲染态紧凑 tag 往返、掩码位与链接规范序（in 前 out 后）对应、截断防御。
 * 对应 spec resonance-relay-ritual"渲染态客户端同步"与 design R2/R4。
 */
class RitualRenderStateTest {

    private static final BlockPos IN_A = new BlockPos(10, 64, -20);
    private static final BlockPos IN_B = new BlockPos(-5, 70, 3);
    private static final BlockPos OUT_A = new BlockPos(200, 61, 200);

    private static RitualRenderState sample() {
        return new RitualRenderState(RitualRenderState.KIND_RELAY, true, 3, 60, 72, 20,
                new long[]{IN_A.asLong(), IN_B.asLong(), OUT_A.asLong()}, 2, 0b101L);
    }

    @Test
    void tagRoundTripPreservesAllFields() {
        RitualRenderState state = sample();
        CompoundTag tag = state.toTag();
        RitualRenderState back = RitualRenderState.fromTag(tag);
        assertEquals(state, back);
        assertEquals(state.channelCount(), back.channelCount());
        assertArrayEquals(state.linkPos(), back.linkPos());
        assertEquals(state.movingMask(), back.movingMask());
    }

    @Test
    void emptyRoundTripAndSteadyEquality() {
        assertEquals(RitualRenderState.EMPTY, RitualRenderState.fromTag(
                RitualRenderState.EMPTY.toTag()));
        // 值相等 → 稳态不发：equals 是变化比较基准（record 默认 equals 对数组失效应已覆写）
        assertEquals(sample(), sample());
        assertNotEquals(sample(), RitualRenderState.EMPTY);
    }

    @Test
    void allKindsRoundTrip() {
        for (RitualRenderState state : new RitualRenderState[]{
                sample(),
                new RitualRenderState(RitualRenderState.KIND_KAGUTSUICHI, true, 4, 60, 64, 0,
                        new long[]{IN_A.asLong(), IN_B.asLong()}, 0,
                        RitualRenderState.MASK_KAGUTSUCHI_BURNING),
                new RitualRenderState(RitualRenderState.KIND_BAFANG, true, 5, 0, 0, 0,
                        new long[0], 0, 0L),
                RitualRenderState.EMPTY}) {
            assertEquals(state, RitualRenderState.fromTag(state.toTag()), "kind=" + state.kind());
        }
    }

    @Test
    void missingKindByteDefaultsToRelay() {
        // 旧档/旧包无 "K" 键：按 relay 语义读
        CompoundTag tag = sample().toTag();
        tag.remove("K");
        assertEquals(RitualRenderState.KIND_RELAY, RitualRenderState.fromTag(tag).kind());
    }

    @Test
    void burningFlagOnlyMeaningfulForKagutsuchiKind() {
        RitualRenderState kag = new RitualRenderState(RitualRenderState.KIND_KAGUTSUICHI,
                true, 2, 0, 0, 0, new long[]{IN_A.asLong()}, 0,
                RitualRenderState.MASK_KAGUTSUCHI_BURNING);
        assertTrue(kag.burning());
        assertEquals(RitualRenderState.MASK_KAGUTSUCHI_BURNING,
                RitualRenderState.fromTag(kag.toTag()).movingMask());
        // 同 mask 的 relay kind 不解释为 burning
        RitualRenderState relay = new RitualRenderState(RitualRenderState.KIND_RELAY,
                true, 2, 0, 0, 20, new long[]{IN_A.asLong()}, 1,
                RitualRenderState.MASK_KAGUTSUCHI_BURNING);
        assertFalse(relay.burning());
        assertTrue(relay.channelMoving(0));
    }

    @Test
    void maskBitsAlignWithCanonicalLinkOrder() {
        RitualRenderState state = sample();
        // mask 0b101：通道 0（in A）与通道 2（out A）在动；通道 1（in B）不动
        assertTrue(state.channelMoving(0));
        assertFalse(state.channelMoving(1));
        assertTrue(state.channelMoving(2));
        assertFalse(state.channelMoving(3)); // 越出链接数
        assertFalse(state.channelMoving(-1));
        // 方向判据：i >= inCount 为出向
        assertEquals(2, state.inCount());
        assertTrue(state.channelMoving(2) && 2 >= state.inCount());
        assertEquals(OUT_A, state.linkAt(2));
    }

    @Test
    void clampMaskDropsBitsBeyondKeptChannels() {
        // 截断到 2 通道时高位清零：不会点亮被截掉/已删除的通道
        assertEquals(0b01L, RitualRenderState.clampMask(0b101L, 2));
        assertEquals(0L, RitualRenderState.clampMask(0b11L, 0));
        long all = -1L;
        assertEquals(all, RitualRenderState.clampMask(all, RitualRenderState.MAX_CHANNELS));
    }
}
