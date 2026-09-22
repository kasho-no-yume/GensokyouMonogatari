package com.bitsson.gensokyou.spirit.grace;

import com.bitsson.gensokyou.spirit.SpiritPowerData;
import com.bitsson.gensokyou.spirit.attr.AttributeKey;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * balance-player-monster-stats 数值带回归（注入表与 config grace 表同构，
 * 单测不触 config 加载态）：回灵占池 0.03%~0.3%、池量级 ×10/阶、上限硬顶放开到 10^8。
 */
class GraceBalanceTest {

    private static final List<String> TABLE = List.of(
            "1,max_spirit,1000,0.2", "2,max_spirit,9000,0.2", "3,max_spirit,90000,0.2",
            "4,max_spirit,900000,0.2", "5,max_spirit,9000000,0.2",
            "1,spirit_regen_rate,1.2,0.7", "2,spirit_regen_rate,10.8,0.7", "3,spirit_regen_rate,108,0.7",
            "4,spirit_regen_rate,1080,0.7", "5,spirit_regen_rate,10800,0.7");

    @Test
    void regenCumulativeStaysInsidePoolBand() {
        // 逐阶随机 roll 200 轮：累计回灵 / 累计池上限 恒在 [0.03%, 0.3%]
        for (int seed = 0; seed < 200; seed++) {
            RandomSource random = RandomSource.create(1000L + seed);
            double cumMax = 0D;
            double cumRegen = 0D;
            for (int tier = 1; tier <= SpiritPowerData.MAX_TIER; tier++) {
                GraceNumbers.GraceRoll roll = GraceNumbers.rollTier(TABLE, tier, random);
                cumMax += roll.maxGain();
                cumRegen += roll.contributions().getOrDefault(AttributeKey.SPIRIT_REGEN_RATE, 0F);
                double ratio = cumRegen / cumMax;
                assertTrue(ratio >= 0.0003D - 2e-5 && ratio <= 0.003D + 2e-5,
                        "回灵占池越界: ratio=" + ratio + " seed=" + seed + " tier=" + tier);
            }
        }
    }

    @Test
    void poolCeilingAllowsTenMillionTierFive() {
        SpiritPowerData data = SpiritPowerData.initial().withLedgerTier(5, 9_000_000F, 550F);
        assertEquals(9_000_000F, data.max(), 1F, "5 阶池上限不应被旧 10^6 硬顶截断");
        assertEquals(550F, data.spiritDamage(), 1F);
    }

    @Test
    void allRolledKeysCarryNonZeroSpread() {
        // 注入表每阶都带非零 roll（洗练有意义的最小保证）
        for (int tier = 1; tier <= SpiritPowerData.MAX_TIER; tier++) {
            for (AttributeKey key : new AttributeKey[]{AttributeKey.MAX_SPIRIT,
                    AttributeKey.SPIRIT_REGEN_RATE}) {
                GraceNumbers.Entry entry = GraceNumbers.entry(TABLE, tier, key);
                assertTrue(entry != null && entry.roll() > 0D,
                        "键 " + key.id() + " 阶 " + tier + " 缺非零 roll");
            }
        }
    }
}
