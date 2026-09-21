package com.bitsson.gensokyou.spirit;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * 玩家灵力池与超人类阶级（superhuman-temper）单一存储。
 *
 * <p>阶级 0 = 凡人：池恒 0/0、灵力强度 0（"和普通史蒂夫无异"）。
 * 阶级提升唯一途径为八百万神恩进阶仪式；每阶的池字段份额记入
 * {@code graceLedger}（tier N 占下标 2(N-1)/2(N-1)+1 = max/强度增量），
 * 池 max 与 spirit_damage 恒等于台账求和——单写事实来源（player-spirit-attributes）。
 */
public record SpiritPowerData(float current, float max, int temperLevel, float regenBuffer,
                              float spiritDamage, List<Float> graceLedger, float flightBuffer,
                              boolean flightInertia) {

    /** 台账每阶两条目（max 增量、强度增量）；阶级上限。 */
    public static final int MAX_TIER = 5;

    /** 池上限硬顶（balance-player-monster-stats：5 阶约 10^7，留出余量）。 */
    public static final float MAX_SPIRIT_CEILING = 100_000_000F;

    public static final Codec<SpiritPowerData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.FLOAT.fieldOf("current").forGetter(SpiritPowerData::current),
                    Codec.FLOAT.fieldOf("max").forGetter(SpiritPowerData::max),
                    Codec.INT.fieldOf("temper_level").forGetter(SpiritPowerData::temperLevel),
                    Codec.FLOAT.optionalFieldOf("regen_buffer", 0F).forGetter(SpiritPowerData::regenBuffer),
                    Codec.FLOAT.optionalFieldOf("spirit_damage", 0F).forGetter(SpiritPowerData::spiritDamage),
                    Codec.FLOAT.listOf().optionalFieldOf("grace_ledger", List.of())
                            .forGetter(SpiritPowerData::graceLedger),
                    Codec.FLOAT.optionalFieldOf("flight_buffer", 0F).forGetter(SpiritPowerData::flightBuffer),
                    Codec.BOOL.optionalFieldOf("flight_inertia", true).forGetter(SpiritPowerData::flightInertia)
            ).apply(instance, SpiritPowerData::new));

    /** 凡人起步：空池、0 阶、惯性默认开（原版手感）。 */
    public static SpiritPowerData initial() {
        return new SpiritPowerData(0F, 0F, 0, 0F, 0F, List.of(), 0F, true);
    }

    public SpiritPowerData withCurrent(float newCurrent) {
        return new SpiritPowerData(Mth.clamp(newCurrent, 0F, max), max, temperLevel, regenBuffer,
                spiritDamage, graceLedger, flightBuffer, flightInertia);
    }

    public SpiritPowerData withAddedCurrent(float amount) {
        return withCurrent(current + amount);
    }

    public SpiritPowerData withRegenBuffer(float buffer) {
        return new SpiritPowerData(current, max, temperLevel, buffer, spiritDamage,
                graceLedger, flightBuffer, flightInertia);
    }

    public SpiritPowerData withFlightBuffer(float buffer) {
        return new SpiritPowerData(current, max, temperLevel, regenBuffer, spiritDamage,
                graceLedger, buffer, flightInertia);
    }

    public SpiritPowerData withFlightInertia(boolean inertia) {
        return new SpiritPowerData(current, max, temperLevel, regenBuffer, spiritDamage,
                graceLedger, flightBuffer, inertia);
    }

    /** 某阶级台账 max 份额（未 roll 过返回 0）。 */
    public float ledgerMaxGain(int tier) {
        int i = (tier - 1) * 2;
        return tier >= 1 && tier <= MAX_TIER && graceLedger.size() > i + 1 ? graceLedger.get(i) : 0F;
    }

    /** 某阶级台账强度份额（未 roll 过返回 0）。 */
    public float ledgerPowerGain(int tier) {
        int i = (tier - 1) * 2 + 1;
        return tier >= 1 && tier <= MAX_TIER && graceLedger.size() > i + 1 ? graceLedger.get(i) : 0F;
    }

    /** 该阶级池份额是否已由进阶写入。 */
    public boolean hasLedgerTier(int tier) {
        int i = (tier - 1) * 2;
        return tier >= 1 && tier <= MAX_TIER && graceLedger.size() > i + 1;
    }

    /**
     * 写入/覆盖某阶级台账份额并整池重算（池 max/灵力强度 = Σ台账；current 钳到新上限）。
     * 进阶 apply 与洗练采纳共用——台账即"按阶级可回洗"的持久事实来源。
     */
    public SpiritPowerData withLedgerTier(int tier, float maxGain, float powerGain) {
        List<Float> ledger = new ArrayList<>(graceLedger);
        while (ledger.size() < tier * 2) {
            ledger.add(0F);
        }
        ledger.set((tier - 1) * 2, maxGain);
        ledger.set((tier - 1) * 2 + 1, powerGain);
        return withRecomputedPool(ledger);
    }

    /** 池字段 = Σ台账 的整池重算入口（单写规约：除本方法外无人改 max/spirit_damage）。 */
    public SpiritPowerData withRecomputedPool(List<Float> ledger) {
        float sumMax = 0F;
        float sumPower = 0F;
        for (int i = 0; i + 1 < ledger.size(); i += 2) {
            sumMax += ledger.get(i);
            sumPower += ledger.get(i + 1);
        }
        float newMax = Math.min(Math.max(0F, sumMax), MAX_SPIRIT_CEILING);
        return new SpiritPowerData(Math.min(current, newMax), newMax, temperLevel, regenBuffer,
                Math.max(0F, sumPower), List.copyOf(ledger), flightBuffer, flightInertia);
    }

    /** 阶级跃迁（进阶 apply 事务内一步完成；台账由 withLedgerTier 先行写入）。 */
    public SpiritPowerData withTemper(int newTemperLevel) {
        return new SpiritPowerData(current, max, newTemperLevel, regenBuffer, spiritDamage,
                graceLedger, flightBuffer, flightInertia);
    }
}
