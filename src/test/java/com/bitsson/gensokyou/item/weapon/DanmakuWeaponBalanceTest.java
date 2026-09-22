package com.bitsson.gensokyou.item.weapon;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 弹幕主武器与增幅核数值规范回归（danmaku-weapon / rune-affix-pool）。
 *
 * <p>注意：config 无法在纯单测加载，故本类用与 config 同构的字面量锁定设计值
 * （核心倍率/攻速、五档倍率、T1 玩家数值、词条区间上界）。
 */
class DanmakuWeaponBalanceTest {

    @Test
    void coreEffectiveDpsFactorsWithinBand() {
        assertTrue(CoreMath.withinBand(CoreMath.bulletDpsFactor(1.0, 1, 8)));   // 球 2.50
        assertTrue(CoreMath.withinBand(CoreMath.bulletDpsFactor(1.4, 1, 12)));  // 飞刀 2.33
        assertTrue(CoreMath.withinBand(CoreMath.bulletDpsFactor(0.45, 5, 24))); // 散弹 1.88
        assertTrue(CoreMath.withinBand(CoreMath.bulletDpsFactor(1.2, 1, 16)));  // 灵符 1.50
        assertTrue(CoreMath.withinBand(CoreMath.laserDpsFactor(0.5, 10, 10)));  // 激光枪 2.00
        assertTrue(CoreMath.withinBand(CoreMath.laserDpsFactor(0.5, 60, 60)));  // 激光炮 2.00
    }

    @Test
    void laserPulseNormalization() {
        // 站桩激光按脉冲归一：脉冲数 × 单脉冲倍率 × 射速
        assertEquals(2.0, CoreMath.laserDpsFactor(0.5, 60, 60), 1e-6);
        // 不足一个判伤间隔记 1 脉冲
        assertEquals(0.5, CoreMath.laserDpsFactor(0.5, 20, 3), 1e-6);
    }

    @Test
    void weaponLevelLadderIsThreeBandsTwoTiersEach() {
        // Lv1=阶1-2 / Lv2=阶3-4 / Lv3=阶5；每级 ×2 = 跨带的一次跃升
        List<Double> table = List.of(1.0, 2.0, 4.0);
        assertEquals(3, table.size());
        assertEquals(2.0, table.get(1) / table.get(0), 1e-9);
        assertEquals(2.0, table.get(2) / table.get(1), 1e-9);
    }

    @Test
    void playerDpsLadderAtLeastTenPerTier() {
        // 与 config grace 表同构：spirit_power 累计 / 暴击期望 / 武器带倍率
        double[] spiritPower = {6, 60, 540, 4640, 39140};
        double[] critAvg = {1.066, 1.128, 1.253, 1.465, 1.80};
        double[] weaponBand = {1.0, 1.0, 2.0, 2.0, 4.0};
        double shotsPerSecond = 2.5;
        double prev = 0;
        for (int i = 0; i < spiritPower.length; i++) {
            double dps = spiritPower[i] * critAvg[i] * weaponBand[i] * shotsPerSecond;
            if (i > 0) {
                assertTrue(dps / prev >= 10.0 - 1e-6,
                        "第 " + (i + 1) + " 阶 DPS 应至少 ×10, got " + (dps / prev));
            }
            prev = dps;
        }
    }

    @Test
    void tierOneSphereDpsLandsBetweenDiamondAndSharpnessFive() {
        // T1：spirit_power 6、weaponLvMult 1.0、暴击率 0.11、暴伤 +0.6、球核 2.5 发/s
        double spiritPower = 6.0;
        double critAvg = 1.0 + 0.11 * 0.60;
        double dps = spiritPower * 1.0 * critAvg * 2.5;
        assertTrue(dps >= 11.2 && dps <= 17.6, "T1 平均 DPS 应在钻石剑~锋利5下界剑之间, got " + dps);
        assertEquals(16.0, dps, 0.6);
    }

    @Test
    void runeBudgetMaxRollUnderEightyPercent() {
        double maxMult = runeMultiplier(0.20, 0.15, 0.12, 0.45, 0.40, 2.0, 0.5);
        assertTrue(maxMult <= 1.80 + 1e-9, "max-roll 增幅核有效增益应 ≤ +80%, got " + maxMult);
        double typical = runeMultiplier(0.16, 0.125, 0.09, 0.35, 0.40, 2.0, 0.5);
        assertTrue(typical > 1.45 && typical < 1.75, "典型增益应在 +45%~+75% 附近, got " + typical);
    }

    @Test
    void runeRollKeepsIdsUniqueAndSummarySumsKnownAffixes() {
        List<RuneGenerator.AffixDef> pool = List.of(
                new RuneGenerator.AffixDef("damage_pct", 0.12f, 0.20f, 10, 3),
                new RuneGenerator.AffixDef("attack_rate_pct", 0.10f, 0.15f, 8, 3),
                new RuneGenerator.AffixDef("crit_chance_pct", 0.06f, 0.12f, 6, 3),
                new RuneGenerator.AffixDef("crit_damage_pct", 0.25f, 0.45f, 5, 3),
                new RuneGenerator.AffixDef("damage_pct", 0.12f, 0.20f, 10, 3));
        RandomSource random = RandomSource.create(20260921L);
        for (int i = 0; i < 100; i++) {
            List<RuneAffix> affixes = RuneGenerator.roll(pool, 3, 4, random);
            assertEquals(4, affixes.size());
            Set<String> ids = affixes.stream().map(RuneAffix::affixId).collect(Collectors.toSet());
            assertEquals(4, ids.size(), "同枚核词条 id 不应重复");
        }
        RuneSummary summary = RuneSummary.of(List.of(
                new RuneAffix("damage_pct", 0.15f),
                new RuneAffix("crit_chance_pct", 0.08f),
                new RuneAffix("crit_damage_pct", 0.30f),
                new RuneAffix("unknown_id", 9f)));
        assertEquals(0.15f, summary.damagePct(), 1e-6);
        assertEquals(0.08f, summary.critChancePct(), 1e-6);
        assertEquals(0.30f, summary.critDamagePct(), 1e-6);
    }

    /** 有效 DPS 增益：伤害 × 攻速折减 × 暴击期望变化（T 阶玩家基准）。 */
    private static double runeMultiplier(double damage, double rate, double critChance, double critDamage,
                                         double baseChance, double baseBonus, double chanceCap) {
        double baseCrit = 1.0 + baseChance * baseBonus;
        double chance = Math.min(chanceCap, baseChance + critChance);
        double newCrit = 1.0 + chance * (baseBonus + critDamage);
        double rateMult = 1.0 / (1.0 - rate);
        return (1.0 + damage) * rateMult * (newCrit / baseCrit);
    }
}
