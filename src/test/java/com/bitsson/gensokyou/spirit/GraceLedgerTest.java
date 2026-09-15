package com.bitsson.gensokyou.spirit;

import com.bitsson.gensokyou.spirit.grace.GraceNumbers;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 池/台账/roll 纯逻辑（superhuman-temper）：凡人空池起步、阶级台账求和重算、
 * roll 区间边界、飞行费率表与小数进位不为 0。对应 spec superhuman-temper
 * "指数属性表与随机 roll / 阶级洗练的整组重掷"与 player-spirit-attributes"空池起步"。
 */
class GraceLedgerTest {

    @Test
    void mortalStartsEmptyPool() {
        SpiritPowerData initial = SpiritPowerData.initial();
        assertEquals(0F, initial.max());
        assertEquals(0F, initial.current());
        assertEquals(0F, initial.spiritDamage());
        assertEquals(0, initial.temperLevel());
        assertTrue(initial.flightInertia(), "惯性默认开=原版手感");
    }

    @Test
    void ledgerTierWritesRecomputePoolAndClampCurrent() {
        SpiritPowerData data = SpiritPowerData.initial();
        data = data.withCurrent(0F).withLedgerTier(1, 200F, 8F);
        assertEquals(200F, data.max());
        assertEquals(8F, data.spiritDamage());
        assertFalse(data.hasLedgerTier(2));
        data = data.withCurrent(50F).withLedgerTier(2, 400F, 16F);
        assertEquals(600F, data.max(), "池上限=Σ台账");
        assertEquals(24F, data.spiritDamage());
        data = data.withLedgerTier(2, 300F, 10F);
        assertEquals(500F, data.max(), "同阶覆盖=整组替换（洗练语义）");
        assertTrue(data.current() <= data.max(), "current 钳到新上限");
    }

    /** 与 config 默认表同构的注入表（单测不触 config 加载态）。 */
    private static final List<String> TABLE = List.of(
            "1,max_spirit,200,0.15", "1,spirit_power,8,0.15", "1,danmaku_reduce,0.10,0.15",
            "2,max_spirit,400,0.15", "3,max_spirit,1000,0.15",
            "4,max_spirit,2400,0.15", "5,max_spirit,6000,0.15");
    private static final List<Double> FLIGHT = List.of(5D, 2D, 1D, 0.5D, 0D);

    @Test
    void rollStaysWithinFractionBand() {
        RandomSource random = RandomSource.create(20260915L);
        for (int tier = 1; tier <= SpiritPowerData.MAX_TIER; tier++) {
            GraceNumbers.Entry entry = GraceNumbers.entry(TABLE, tier, AttributeKeyProbe.MAX_SPIRIT);
            if (entry == null) {
                continue;
            }
            for (int i = 0; i < 200; i++) {
                float value = GraceNumbers.rolled(entry, random);
                double lo = entry.base() * (1 - entry.roll());
                double hi = entry.base() * (1 + entry.roll());
                assertTrue(value >= lo - 1e-3 && value <= hi + 1e-3,
                        "roll 必须落在基准±区间内: " + value + " of " + lo + ".." + hi);
            }
        }
    }

    @Test
    void rollTierSplitsPoolKeysIntoGainFields() {
        GraceNumbers.GraceRoll roll = GraceNumbers.rollTier(TABLE, 1, RandomSource.create(7L));
        assertTrue(roll.maxGain() > 0F, "表含 1 阶 max_spirit 行");
        assertTrue(roll.powerGain() > 0F);
        assertFalse(roll.contributions().containsKey(AttributeKeyProbe.MAX_SPIRIT),
                "池两键不得混入贡献 map（单写规约）");
        assertFalse(roll.contributions().containsKey(AttributeKeyProbe.SPIRIT_POWER));
        assertTrue(roll.contributions().containsKey(AttributeKeyProbe.DANMAKU_REDUCE));
        assertEquals(1, roll.tier());
    }

    @Test
    void flightRatesMatchSpecTable() {
        assertEquals(5D, GraceNumbers.flightCostPctPerSecond(FLIGHT, 1), 1e-9);
        assertEquals(2D, GraceNumbers.flightCostPctPerSecond(FLIGHT, 2), 1e-9);
        assertEquals(1D, GraceNumbers.flightCostPctPerSecond(FLIGHT, 3), 1e-9);
        assertEquals(0.5D, GraceNumbers.flightCostPctPerSecond(FLIGHT, 4), 1e-9);
        assertEquals(0D, GraceNumbers.flightCostPctPerSecond(FLIGHT, 5), 1e-9, "5 阶免费");
        assertEquals(0D, GraceNumbers.flightCostPctPerSecond(FLIGHT, 0), 1e-9);
    }

    @Test
    void flightPerTickAccumulatesWithoutRoundingToZero() {
        double perTick = GraceNumbers.flightCostPerTick(FLIGHT, 1, 200F);
        assertEquals(0.5D, perTick, 1e-9);
        double buffer = 0D;
        int wholePaid = 0;
        for (int tick = 0; tick < 40; tick++) {
            buffer += perTick;
            int whole = (int) Math.floor(buffer);
            wholePaid += whole;
            buffer -= whole;
        }
        assertEquals(20, wholePaid, "40 tick（2s）应累计付 20（=每秒 5%×200），小数进位不吞零");
    }

    @Test
    void initialIsNotPersistedAsOldFullPool() {
        // 旧语义回归锁：initial 绝不能再回 100 满池
        List<SpiritPowerData> samples = List.of(SpiritPowerData.initial());
        samples.forEach(d -> assertTrue(d.max() <= 0F));
    }

    /** 探针别名（避免 import 环与可读性）。 */
    private static final class AttributeKeyProbe {
        static final com.bitsson.gensokyou.spirit.attr.AttributeKey MAX_SPIRIT =
                com.bitsson.gensokyou.spirit.attr.AttributeKey.MAX_SPIRIT;
        static final com.bitsson.gensokyou.spirit.attr.AttributeKey SPIRIT_POWER =
                com.bitsson.gensokyou.spirit.attr.AttributeKey.SPIRIT_POWER;
        static final com.bitsson.gensokyou.spirit.attr.AttributeKey DANMAKU_REDUCE =
                com.bitsson.gensokyou.spirit.attr.AttributeKey.DANMAKU_REDUCE;
    }
}
