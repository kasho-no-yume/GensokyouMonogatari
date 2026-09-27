package com.bitsson.gensokyou.ritual;

import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 星移演出渲染态（{@link RitualRenderState#KIND_SEII}）回归。
 *
 * <p>红线：仪式持续表现 MUST NOT 走服务端 {@code sendParticles}（逐追踪玩家广播
 * {@code ClientboundLevelParticlesPacket} = 包风暴），MUST 改为客户端 BER 本地生成 +
 * 只下发最小渲染态。本测试锁住"渲染态足以让客户端自推动画、且稳态不产生 diff"。
 */
class SeiiRenderStateTest {

    /** 客户端推进动画所需的全部信息：演出中 + 档位 + 起始 gameTime + 总时长。 */
    private static RitualRenderState performing(int tier, int startTick, int duration) {
        return new RitualRenderState(RitualRenderState.KIND_SEII, true, tier,
                startTick, duration, 0, new long[0], 0, 0L);
    }

    @Test
    void performingStateExposesClockAndDuration() {
        RitualRenderState s = performing(5, 12345, 60);
        assertTrue(s.enabled());
        assertEquals(5, s.tier());
        assertEquals(12345, s.seiiStartTick());
        assertEquals(60, s.seiiDurationTicks());
    }

    @Test
    void idleStateReportsNoStartTick() {
        RitualRenderState idle = new RitualRenderState(RitualRenderState.KIND_SEII, false, 1,
                999, 60, 0, new long[0], 0, 0L);
        assertFalse(idle.enabled());
        assertEquals(0, idle.seiiStartTick(),
                "非演出态 MUST 报告 startTick=0，客户端据此直接不画");
    }

    /**
     * 稳态零包的关键：演出进行中但起始 tick 不变时，渲染态 MUST 完全相等
     * —— BE 的 {@code lastSentRenderState} 相等比较因此不发 {@code sendBlockUpdated}。
     */
    @Test
    void steadyStateProducesNoDiff() {
        RitualRenderState a = performing(3, 1000, 60);
        RitualRenderState b = performing(3, 1000, 60);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void tierAndClockChangesDoProduceDiff() {
        RitualRenderState base = performing(3, 1000, 60);
        assertNotEquals(base, performing(5, 1000, 60), "档位变化须下发");
        assertNotEquals(base, performing(3, 1001, 60), "起始 tick 变化须下发");
        assertNotEquals(base, performing(3, 1000, 120), "时长变化须下发");
    }

    @Test
    void tagRoundTripKeepsClockAndDuration() {
        RitualRenderState s = performing(5, 4242, 90);
        CompoundTag tag = s.toTag();
        RitualRenderState back = RitualRenderState.fromTag(tag);
        assertEquals(RitualRenderState.KIND_SEII, back.kind());
        assertTrue(back.enabled());
        assertEquals(5, back.tier());
        assertEquals(4242, back.seiiStartTick(), "起始 gameTime MUST 完整往返（int 不截断）");
        assertEquals(90, back.seiiDurationTicks());
        assertEquals(s, back);
    }

    /**
     * 起始 gameTime 走 int（"Y0"）而非 period —— {@code period} 序列化时被截成 byte
     * （{@code putByte(min(period,255))}），装不下 gameTime。
     */
    @Test
    void startTickIsNotStoredInTheByteTruncatedPeriod() {
        long lateGameTime = 3_000_000L;
        RitualRenderState s = performing(1, (int) lateGameTime, 60);
        CompoundTag tag = s.toTag();
        assertEquals((int) lateGameTime, tag.getInt("Y0"));
        assertEquals(3000000, RitualRenderState.fromTag(tag).seiiStartTick());
    }
}
