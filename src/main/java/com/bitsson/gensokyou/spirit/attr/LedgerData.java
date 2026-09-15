package com.bitsson.gensokyou.spirit.attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * 灵力汲取限速账本（transient 附件，不入存档）：按结算周期锁存每周期剩余额度。
 *
 * <p>周期序号 = gameTime / settlePeriod；周期推进时把 remaining 重置为每周期额度。
 * 复用仪式侧 TickRateLedger 的"周期序号幂等 + 剩余额度"思路，但独立实例、语义仅回灵。
 */
public record LedgerData(long period, float remaining) {

    public static final Codec<LedgerData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("period").forGetter(LedgerData::period),
            Codec.FLOAT.fieldOf("remaining").forGetter(LedgerData::remaining)
    ).apply(instance, LedgerData::new));

    public static LedgerData initial() {
        return new LedgerData(-1L, 0F);
    }

    /**
     * 推进到当前周期并返回本周期可用于汲取的额度账本快照。
     *
     * @param now            当前 gameTime
     * @param periodTicks    结算周期 tick 数
     * @param quotaPerPeriod 每周期额度
     */
    public LedgerData advance(long now, int periodTicks, float quotaPerPeriod) {
        long currentPeriod = Math.floorDiv(now, Math.max(1, periodTicks));
        if (currentPeriod != period) {
            return new LedgerData(currentPeriod, quotaPerPeriod);
        }
        return this;
    }

    /** 消费 n（不超过 remaining），返回消费后的账本。 */
    public LedgerData consume(float n) {
        return new LedgerData(period, Math.max(0F, remaining - n));
    }
}
