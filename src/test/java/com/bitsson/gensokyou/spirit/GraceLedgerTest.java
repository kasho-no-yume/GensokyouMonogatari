package com.bitsson.gensokyou.spirit;

import com.bitsson.gensokyou.spirit.grace.GraceNumbers;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import com.bitsson.gensokyou.balance.GraceLedgerMigrationMath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
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

    /**
     * 化石对照（历史记录，NOT 当前行为）：旧版曾按阶 1 池阈值（500）判别旧表并按新表重 roll；
     * 该自动重掷已由 rework-player-stat-build-budgets 移除（{@code migrateIfNeeded} 不再含随机
     * 重掷，见 {@code BuildSourceContractTest} 静态守卫）。此处仅锁两张表字面量与阈值两侧归属，
     * MUST NOT 被解读为现行迁移规则；现行规则见 {@code GraceLedgerMigrationMathTest}。
     */
    @Test
    void legacyLedgerIsDetectedByTierOnePoolThreshold() {
        final float threshold = 500F;
        GraceNumbers.Entry oldT1 = GraceNumbers.entry(TABLE, 1, AttributeKeyProbe.MAX_SPIRIT);
        GraceNumbers.Entry newT1 = GraceNumbers.entry(NEW_TABLE, 1, AttributeKeyProbe.MAX_SPIRIT);
        assertEquals(200D, oldT1.base(), 1e-9, "TABLE 即化石旧表");
        assertEquals(1000D, newT1.base(), 1e-9);

        // 旧表 1 阶 roll 区间整体低于阈值 → 判为旧表档，触发重 roll
        double oldLo = oldT1.base() * (1 - oldT1.roll());
        double oldHi = oldT1.base() * (1 + oldT1.roll());
        assertTrue(oldHi < threshold,
                "旧表 1 阶上界 " + oldHi + " 应 < 500，否则旧档不会被识别");

        // 新表 1 阶 roll 区间整体高于阈值 → 不触发，避免"迁移后立刻再迁移"的循环
        double newLo = newT1.base() * (1 - newT1.roll());
        double newHi = newT1.base() * (1 + newT1.roll());
        assertTrue(newLo >= threshold,
                "新表 1 阶下界 " + newLo + " 应 >= 500，否则 migrateIfNeeded 会反复重 roll");
        assertTrue(newLo <= newHi);
    }

    /** 重 roll 后 spiritDamage 会落到新表量级（约 1），而非旧表的 8。 */
    @Test
    void rerollAgainstNewTableYieldsTierOneSpiritPowerAboutOne() {
        GraceNumbers.GraceRoll roll = GraceNumbers.rollTier(NEW_TABLE, 1, RandomSource.create(7L));
        double lo = 1.0 * (1 - 0.2);
        double hi = 1.0 * (1 + 0.2);
        assertTrue(roll.powerGain() >= lo - 1e-6 && roll.powerGain() <= hi + 1e-6,
                "重 roll 的 1 阶 spirit_power 应落在 [0.8, 1.2], got " + roll.powerGain());
        assertTrue(roll.powerGain() < 2F, "旧表量级（8）不应再出现");
    }

    /**
     * 死亡规则（rework-player-stat-build-budgets tasks 2.4）：仅当前灵力与进位 buffer 清零，
     * 上限/阶级/台账/强度/惯性全部保留；MUST NOT 重掷或补台账。
     */
    @Test
    void deathClearKeepsLedgerAndResetsOnlyCurrentAndBuffers() {
        SpiritPowerData data = new SpiritPowerData(75F, 173.25F, 3, .75F, 24.5F,
                List.of(1F, 9F, 80F, 683F, 5750F, 0F), .25F, false);
        SpiritPowerData cleared = data.withClearedCurrentForDeath();
        assertEquals(0F, cleared.current());
        assertEquals(0F, cleared.regenBuffer());
        assertEquals(0F, cleared.flightBuffer());
        assertEquals(data.max(), cleared.max());
        assertEquals(data.temperLevel(), cleared.temperLevel());
        assertEquals(data.spiritDamage(), cleared.spiritDamage());
        assertEquals(data.graceLedger(), cleared.graceLedger());
        assertEquals(data.flightInertia(), cleared.flightInertia());
    }

    /**
     * 重复登录 + 死亡组合幂等（tasks 2.4）：缺台账重建 → 死亡清池 → 再登录重建
     * 不得二次分配、不得补条、不得漂移；已有台账的二次重建返回同一实例。
     */
    @Test
    void deathAndReloginReconstructionIsIdempotent() {
        var weightsMax = List.of(1000D, 9000D, 90000D);
        var weightsPower = List.of(1D, 9D, 80D);
        SpiritPowerData saved = new SpiritPowerData(75F, 173.25F, 3, .75F, 24.5F,
                List.of(), .25F, true);
        SpiritPowerData reconstructed = GraceLedgerMigrationMath.reconstruct(saved, weightsMax, weightsPower);
        assertEquals(6, reconstructed.graceLedger().size(), "3 阶应有 6 条台账份额");
        SpiritPowerData afterDeath = reconstructed.withClearedCurrentForDeath();
        SpiritPowerData secondLogin = GraceLedgerMigrationMath.reconstruct(afterDeath, weightsMax, weightsPower);
        assertSame(afterDeath, secondLogin, "已有台账不得再次分配");
        assertEquals(reconstructed.graceLedger(), afterDeath.graceLedger());
        assertEquals(reconstructed.max(), afterDeath.max());
        assertEquals(0F, afterDeath.current());
    }

    /**
     * 逐阶洗练整组替换的幂等与隔离（tasks 2.4）：重复应用同一阶同一组数值无漂移；
     * 只重洗一阶不得动其他阶份额；池字段恒等于台账求和。
     */
    @Test
    void steppedRerollIsIdempotentAndGroupIsolated() {
        SpiritPowerData data = SpiritPowerData.initial()
                .withLedgerTier(1, 800F, 0.9F).withTemper(1)
                .withLedgerTier(2, 9000F, 9F).withTemper(2)
                .withLedgerTier(3, 90000F, 80F).withTemper(3);
        data = data.withCurrent(data.max());
        SpiritPowerData snapshot = data;
        // 同一组数值重复应用（重复登录/重复提交）→ 无漂移
        assertEquals(snapshot, snapshot.withLedgerTier(3, 90000F, 80F));
        // 只重洗 2 阶 → 1/3 阶份额不动
        SpiritPowerData rerolled = snapshot.withLedgerTier(2, 9500F, 9.5F);
        assertEquals(800F, rerolled.ledgerMaxGain(1));
        assertEquals(9500F, rerolled.ledgerMaxGain(2));
        assertEquals(90000F, rerolled.ledgerMaxGain(3));
        assertEquals(0.9F, rerolled.ledgerPowerGain(1));
        assertEquals(9.5F, rerolled.ledgerPowerGain(2));
        assertEquals(80F, rerolled.ledgerPowerGain(3));
        assertEquals(100_300F, rerolled.max(), "池上限=Σ台账");
        assertEquals(90.4F, rerolled.spiritDamage(), 1e-4F);
    }

    /** 变更后的 config 默认表 1 阶行（单测不触 config 加载态）。 */
    private static final List<String> NEW_TABLE = List.of(
            "1,max_spirit,1000,0.2", "1,spirit_power,1,0.2", "1,danmaku_reduce,1.0,0.2");

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
