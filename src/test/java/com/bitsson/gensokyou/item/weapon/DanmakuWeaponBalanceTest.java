package com.bitsson.gensokyou.item.weapon;

import com.bitsson.gensokyou.spirit.attr.AttributeKey;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 弹幕主武器与增幅核数值规范回归（danmaku-weapon / rune-affix-pool）。
 *
 * <p>注意：config 无法在纯单测加载，故本类用与 config 同构的字面量锁定设计值
 * （核心倍率/攻速、五档倍率、T1 玩家数值、词条区间上界）。
 */
class DanmakuWeaponBalanceTest {

    /** 构造 ItemStack 需 Minecraft 注册表就绪（否则静态初始化失败并毒化整个测试 JVM）。 */
    @org.junit.jupiter.api.BeforeAll
    static void bootstrap() {
        com.bitsson.gensokyou.support.MinecraftTestBootstrap.ensureStarted();
    }

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
        // rebalance-tier1-spirit-and-danmaku-cost：spirit_power 整列 ÷6，比例结构不变
        double[] spiritPower = {1, 10, 90, 773, 6523};
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

    /**
     * spirit_power 阶梯的阶间倍率与 ÷6 之前逐项一致（design D1）。
     *
     * <p>断言的是"相对误差 &lt; 0.1%"而非严格相等：4/5 阶的 683、5750 是 4100/6、34500/6
     * 的舍入值，逐项相等在 4/5 阶字面为假（8.5926→8.5889、8.4353→8.4386）。
     */
    @Test
    void spiritPowerLadderRatiosSurviveUniformRescale() {
        double[] before = {6, 60, 540, 4640, 39140};
        double[] after = {1, 10, 90, 773, 6523};
        assertEquals(before.length, after.length);
        for (int i = 1; i < before.length; i++) {
            double rBefore = before[i] / before[i - 1];
            double rAfter = after[i] / after[i - 1];
            assertTrue(Math.abs(rAfter / rBefore - 1.0) < 0.001,
                    "第 " + (i + 1) + " 阶倍率相对偏差应 &lt; 0.1%: before x"
                            + rBefore + " after x" + rAfter);
        }
    }

    /**
     * 阶 1 伤害锚点：单发约 1，低于石剑（5）与铁剑（6）。
     *
     * <p>取代旧断言 {@code tierOneSphereDpsLandsBetweenDiamondAndSharpnessFive}——后者把
     * 阶 1 DPS 锁在 11.2~17.6（"钻石剑~锋利5下界剑"），意味着阶 1 单发 6.4 已超过铁剑，
     * 原版武器在该阶直接退伍。这正是 rebalance-tier1-spirit-and-danmaku-cost 否决的目标：
     * 阶 1 应当是"原版武器仍有竞争力"的起点，弹幕主武器不该一阶就碾压。
     */
    @Test
    void tierOneSphereDamageIsAboutOneAndBelowStoneSword() {
        // T1：spirit_power 1、coreBaseMult 1.0、暴击率 0.11、暴伤 +0.6
        double spiritPower = 1.0;
        double critAvg = 1.0 + 0.11 * 0.60;
        double perShot = spiritPower * 1.0 * 1.0 * critAvg;
        assertEquals(1.07, perShot, 0.01, "T1 球核单发伤害应约 1");
        assertTrue(perShot < 5.0, "T1 弹幕单发应低于石剑 5, got " + perShot);
        assertTrue(perShot < 6.0, "T1 弹幕单发应低于铁剑 6, got " + perShot);
        // 参考 DPS 同步下移：旧 16.0 → 新 2.67
        assertEquals(2.67, spiritPower * 1.0 * critAvg * 2.5, 0.01);
    }

    /**
     * 击杀所需发数是尺度不变量：玩家伤害与 BOSS 血量同源于 spirit_power，均匀缩放两边同缩。
     *
     * <p>击杀所需发数 = bossSeconds × refShotsPerSecond / (coreBaseMult × 弹数)。
     * 分母是<b>每触发伤害倍率</b>，MUST NOT 用 docs §3 的"有效 DPS 因子"
     * （= coreBaseMult × 弹数 × 20/attackRate）——那含射速，球核会算出 160 而非 400。
     */
    @Test
    void shotsToKillBossIsScaleInvariant() {
        double bossSeconds = 160.0;
        double refShotsPerSecond = 2.5;
        // (名称, coreBaseMult, 弹数, 期望击杀发数)
        Object[][] cores = {
                {"球", 1.0, 1.0, 400.0},
                {"飞刀", 1.4, 1.0, 400.0 / 1.4},
                {"散弹", 0.45, 5.0, 400.0 / 2.25},
        };
        for (Object[] c : cores) {
            double perTrigger = (double) c[1] * (double) c[2];
            double shots = bossSeconds * refShotsPerSecond / perTrigger;
            assertEquals((double) c[3], shots, 1e-9, c[0] + "核击杀所需发数");
        }
        // 尺度不变性：spirit_power 任意缩放，发数不变
        for (double spiritPower : new double[]{1.0, 6.0, 60.0}) {
            double perShot = spiritPower * 1.0 * (1.0 + 0.11 * 0.60);
            double bossHp = spiritPower * 1.0 * (1.0 + 0.11 * 0.60) * refShotsPerSecond * bossSeconds;
            assertEquals(400.0, bossHp / perShot, 1e-9,
                    "spirit_power=" + spiritPower + " 时击杀发数应恒为 400");
        }
    }

    /**
     * 满池可负担发数 MUST 覆盖击杀所需发数（danmaku-weapon 灵力成本判据）。
     *
     * <p>阶 1：池 1000、bossSeconds 160。上界 = 1000 × coreBaseMult × 弹数 / 400。
     * 三核全部满足——包括散弹，因其弹数（5）抬高每触发倍率、需求发数正比下降，
     * <b>不需要射程豁免</b>。
     */
    @Test
    void fullPoolAffordsTheShotsNeededToKillSameTierBoss() {
        double pool = 1000.0;
        double bossSeconds = 160.0;
        double refShotsPerSecond = 2.5;
        // (名称, coreBaseMult, 弹数, spiritCost)
        Object[][] cores = {
                {"球", 1.0, 1.0, 2.0},
                {"飞刀", 1.4, 1.0, 3.0},
                {"散弹", 0.45, 5.0, 4.0},
        };
        for (Object[] c : cores) {
            double perTrigger = (double) c[1] * (double) c[2];
            double needed = bossSeconds * refShotsPerSecond / perTrigger;
            double affordable = pool / (double) c[3];
            assertTrue(affordable >= needed,
                    c[0] + "核满池可负担 " + affordable + " 发 < 击杀所需 " + needed + " 发");
        }
        // 球核具体锚点
        assertEquals(500.0, pool / 2.0, 1e-9);
        assertTrue(pool / 2.0 >= 400.0);
    }

    /**
     * 同档核"伤害/灵力"效率仍在同一量级（相对球核偏移 ≤ ±10%）。
     *
     * <p>不是精确保持：spiritCost 是 IntValue，2/3/4 是凑整而非 10/14/22 的 ÷5
     * （实际 ÷5 / ÷4.667 / ÷5.5），故飞刀 −6.7%、散弹 +10%。danmaku-weapon 要求的是
     * "量级一致"而非精确相等。
     */
    @Test
    void tierOneCoreSpiritEfficiencyStaysWithinOneMagnitude() {
        // (名称, coreBaseMult, 弹数, 变更前成本, 变更后成本)
        Object[][] cores = {
                {"球", 1.0, 1.0, 10.0, 2.0},
                {"飞刀", 1.4, 1.0, 14.0, 3.0},
                {"散弹", 0.45, 5.0, 22.0, 4.0},
        };
        double sphereBefore = 1.0 * 1.0 / 10.0;
        double sphereAfter = 1.0 * 1.0 / 2.0;
        for (Object[] c : cores) {
            double perTrigger = (double) c[1] * (double) c[2];
            double relBefore = perTrigger / (double) c[3] / sphereBefore;
            double relAfter = perTrigger / (double) c[4] / sphereAfter;
            assertTrue(Math.abs(relAfter - relBefore) / relBefore <= 0.10 + 1e-9,
                    c[0] + "核效率偏移应 ≤ ±10%: before x" + relBefore + " after x" + relAfter);
        }
    }

    /**
     * 词条总增益预算（seii-reroll-ritual 口径）。
     *
     * <p>暴击已迁到玩家属性域：核的 {@code crit_chance} / {@code crit_damage} 走 contribution 加区，
     * 值为「参考玩家阶标准值的 5%~8%」，而非旧的武器侧 {@code crit_*_pct} 直接百分比。
     * 最坏 5 条组合 = spirit_power + damage_pct + attack_rate_pct + crit_chance + crit_damage。
     */
    @Test
    void runeBudgetMaxRollUnderEightyPercent() {
        double maxMult = playerSideRuneMultiplier(0.08, 0.20, 0.15, 0.08, 0.08);
        assertTrue(maxMult <= 1.80 + 1e-9,
                "max-roll 增幅核有效增益应 ≤ +80%, got " + maxMult);
        double typical = playerSideRuneMultiplier(0.065, 0.16, 0.125, 0.065, 0.065);
        assertTrue(typical > 1.40 && typical < 1.70,
                "典型增益应在 +40%~+70% 附近, got " + typical);
    }

    /** 5 阶满属性玩家的基准：暴击率 0.35、暴伤加成 1.50、硬上限 0.5。 */
    private static double playerSideRuneMultiplier(double spiritPowerPct, double damage, double rate,
                                                  double critChancePct, double critDamagePct) {
        final double baseChance = 0.35D;
        final double baseCritDamage = 1.50D;
        final double chanceCap = 0.50D;
        double baseCrit = 1.0D + baseChance * baseCritDamage;
        // 玩家属性域的词条值 = 该玩家阶标准值的百分比，故暴击率/暴伤按同比例放大
        double chance = Math.min(chanceCap, baseChance * (1.0D + critChancePct));
        double newCritDamage = baseCritDamage * (1.0D + critDamagePct);
        double newCrit = 1.0D + chance * newCritDamage;
        double rateMult = 1.0D / (1.0D - rate);
        return (1.0D + spiritPowerPct) * (1.0D + damage) * rateMult * (newCrit / baseCrit);
    }

    @Test
    void runeRollKeepsIdsUniqueAndSummarySumsKnownAffixes() {
        List<RuneGenerator.AffixDef> pool = List.of(
                new RuneGenerator.AffixDef("damage_pct", 0.12f, 0.20f, 10, 3),
                new RuneGenerator.AffixDef("attack_rate_pct", 0.10f, 0.15f, 8, 3),
                new RuneGenerator.AffixDef("range_pct", 0.08f, 0.12f, 6, 3),
                new RuneGenerator.AffixDef("spirit_cost_pct", -0.22f, -0.10f, 8, 3));
        RandomSource random = RandomSource.create(20260921L);
        for (int i = 0; i < 100; i++) {
            List<RuneAffix> affixes = RuneGenerator.roll(pool, 3, 4, random);
            assertEquals(4, affixes.size());
            Set<String> ids = affixes.stream().map(RuneAffix::affixId).collect(Collectors.toSet());
            assertEquals(4, ids.size(), "同枚核词条 id 不应重复");
        }
        RuneSummary summary = RuneSummary.of(List.of(
                new RuneAffix("damage_pct", 0.15f),
                new RuneAffix("range_pct", 0.10f),
                new RuneAffix("crit_damage", 0.30f),
                new RuneAffix("unknown_id", 9f)));
        assertEquals(0.15f, summary.damagePct(), 1e-6);
        assertEquals(0.10f, summary.rangePct(), 1e-6);
        assertEquals(0.30f, summary.attr(AttributeKey.CRIT_DAMAGE), 1e-6);
        assertNull(summary.attr(AttributeKey.CRIT_CHANCE), "未出现的键应返回 null");
        // range_pct 经指数衰减：+10% → (1.10)^0.75 ≈ 1.074
        assertEquals(Math.pow(1.10D, 0.75D), summary.weapon(0.75D).rangeMultiplier(), 1e-6);
    }

    /**
     * config 合并（照 {@code GraceNumbers.effectiveRows()} 范式）：NeoForge 不会把新的 list
     * 默认值写进已有配置文件，所以"改代码里的默认表"对老配置无效 —— T1 核仍会 roll 2 条。
     * 合并后：默认表补齐缺失的 id（含新增的 range_pct），退役 id 无条件剔除。
     */
    @Test
    void staleConfigIsMergedWithBuiltInDefaults() {
        // 模拟 9/22 的旧配置：15 条含退役 id、无 range_pct
        List<String> staleConfig = List.of(
                "damage_pct,0.03,0.06,10,1", "damage_pct,0.07,0.12,10,2", "damage_pct,0.12,0.20,10,3",
                "attack_rate_pct,0.03,0.06,8,1", "attack_rate_pct,0.06,0.10,8,2", "attack_rate_pct,0.10,0.15,8,3",
                "spirit_cost_pct,-0.08,-0.03,8,1", "spirit_cost_pct,-0.14,-0.06,8,2", "spirit_cost_pct,-0.22,-0.10,8,3",
                "crit_chance_pct,0.02,0.04,6,1", "crit_chance_pct,0.04,0.07,6,2", "crit_chance_pct,0.06,0.12,6,3",
                "crit_damage_pct,0.06,0.12,5,1", "crit_damage_pct,0.14,0.25,5,2", "crit_damage_pct,0.25,0.45,5,3");
        List<RuneGenerator.AffixDef> merged =
                RuneGenerator.pool(staleConfig, RuneGenerator.poolDefaults());
        List<String> ids = merged.stream().map(RuneGenerator.AffixDef::id).distinct().toList();
        assertFalse(ids.contains("crit_chance_pct"), "退役 id 必须无条件剔除");
        assertFalse(ids.contains("crit_damage_pct"), "退役 id 必须无条件剔除");
        assertTrue(ids.contains("range_pct"), "缺失的新 id 应由内置默认补齐");
        // 同一 id 的三个核阶条目都必须在（曾经被 putIfAbsent 覆盖成只剩一档）
        for (int tier = 1; tier <= 3; tier++) {
            final int t = tier;
            long damageRows = merged.stream()
                    .filter(d -> d.id().equals("damage_pct") && d.tier() == t).count();
            assertEquals(1L, damageRows, "damage_pct 的 tier " + t + " 条目应各存一条");
        }
        assertEquals(12, merged.size(), "9 条 config + 3 条默认补缺（退役 6 条不计）");
    }

    /** 条数逐位合并：config 优先、内置默认补缺。老配置的 [2,3,4] 仍会被尊重（需手删该行）。 */
    @Test
    void affixCountMergesPerIndexWithDefaults() {
        assertEquals(1, SeiiNumbers.affixCountOf(1, List.of(), RuneGenerator.countDefaults()));
        assertEquals(3, SeiiNumbers.affixCountOf(2, List.of(), RuneGenerator.countDefaults()));
        assertEquals(5, SeiiNumbers.affixCountOf(3, List.of(), RuneGenerator.countDefaults()));
        assertEquals(2, SeiiNumbers.affixCountOf(1, List.of(2, 3, 4), RuneGenerator.countDefaults()),
                "config 显式值应被尊重");
        assertEquals(3, SeiiNumbers.affixCountOf(2, List.of(7), RuneGenerator.countDefaults()),
                "config 只有 1 位时，越界位应回落默认 3");
        assertEquals(5, SeiiNumbers.affixCountOf(3, List.of(7), RuneGenerator.countDefaults()),
                "越界位应回落默认 5");
    }

    @Test
    void danmakuReduceUsesMitigationSemanticsNotPercentageOfExponent() {
        // 弹幕护壁是 2^-P 指数：目标减伤 5%~8% → ΔP 0.074~0.120（而非 8.6 的 5%~8% = 0.43~0.69）
        double lo = SeiiNumbers.wardExponentFor(0.05D);
        double hi = SeiiNumbers.wardExponentFor(0.08D);
        assertTrue(lo > 0.07D && lo < 0.08D, "5% 减伤应 ≈ +0.074 P, got " + lo);
        assertTrue(hi > 0.11D && hi < 0.13D, "8% 减伤应 ≈ +0.120 P, got " + hi);
        // 等效减伤回算一致
        assertEquals(0.05D, 1.0D - Math.pow(2.0D, -lo), 1e-9);
    }

    @Test
    void softPityShiftsBandUpwardAndWidensIt() {
        // lo' = lo + w*t*0.5；hi' = lo + w*(1+t*0.5)
        // t=1 时下界 = 原带中点（"最坏的一次也等于原来的平均"），带宽翻倍
        assertEquals(0.10D, SeiiNumbers.pityBand(0.10D, 0.20D, 0D)[0], 1e-9, "t=0 返回原带");
        assertEquals(0.125D, SeiiNumbers.pityBand(0.10D, 0.20D, 0.5D)[0], 1e-9,
                "t=0.5 下界上移 1/4 带宽");
        double[] full = SeiiNumbers.pityBand(0.10D, 0.20D, 1.0D);
        assertEquals(0.15D, full[0], 1e-9, "t=1 下界 = 原带中点");
        assertEquals(0.25D, full[1], 1e-9, "t=1 上界 = 原上界之上一个带宽");
        assertEquals(0.10D, full[1] - full[0], 1e-9, "t=1 带宽翻倍");
        assertEquals(0.0D, SeiiNumbers.pityProgress(0, 10), 1e-9);
        assertEquals(1.0D, SeiiNumbers.pityProgress(99, 10), 1e-9, "超过 cap 应饱和");
        assertEquals(0.5D, SeiiNumbers.pityProgress(5, 10), 1e-9);
    }

    @Test
    void seiiLaddersDeriveInRateFromHighestWashableCoreTier() {
        // inrate = 该阶可洗最高核阶花费 ÷ 1.5s；花费 = 30k × 100^(核阶-1)
        assertEquals(1, SeiiNumbers.maxCoreTier(1));
        assertEquals(2, SeiiNumbers.maxCoreTier(3));
        assertEquals(3, SeiiNumbers.maxCoreTier(5));
        assertEquals(30_000L, SeiiNumbers.spCostOf(30_000L, 100, 1));
        assertEquals(3_000_000L, SeiiNumbers.spCostOf(30_000L, 100, 2));
        assertEquals(300_000_000L, SeiiNumbers.spCostOf(30_000L, 100, 3));
        // 自然配对恒 1.5s
        for (int level : new int[]{1, 3, 5}) {
            long cost = SeiiNumbers.spCostOf(30_000L, 100, SeiiNumbers.maxCoreTier(level));
            long rate = SeiiNumbers.inRateOf(20_000L, 10, level);
            assertEquals(1.5D, (double) cost / rate, 0.001D,
                    "自然配对（" + level + " 阶洗 " + SeiiNumbers.maxCoreTier(level) + " 阶核）应为 1.5s");
        }
        // 缓存阶梯 ×12/阶
        assertEquals(50_000L, SeiiNumbers.capacityOf(50_000L, 12, 1));
        assertEquals(7_200_000L, SeiiNumbers.capacityOf(50_000L, 12, 3));
        assertEquals(1_036_800_000L, SeiiNumbers.capacityOf(50_000L, 12, 5));
    }

    @Test
    void coreTierReferencePlayerTiersAreOneThreeFive() {
        assertEquals(1, SeiiNumbers.referencePlayerTier(1));
        assertEquals(3, SeiiNumbers.referencePlayerTier(2));
        assertEquals(5, SeiiNumbers.referencePlayerTier(3));
    }

    /**
     * 核心 GUI 目标槽的<b>双索引空间</b>不变量。
     *
     * <p>{@code SlotItemHandler} 的构造参数是 handler <b>内部</b>索引，不是 menu 槽位索引。
     * 目标槽在 menu 里是第 1 个（紧邻灵力核槽），但 handler 只有 1 格，故 handler 索引必须是 0。
     * 传成 menu 索引会让客户端在收 {@code ContainerSetContent} 时抛
     * {@code "Slot 1 not in valid range - [0,1)"} 并被踢下线（实机踩过一次）。
     */
    @Test
    void slotItemHandlerIndexIsHandlerLocalNotMenuLocal() {
        var handler = new net.neoforged.neoforge.items.ItemStackHandler(1);
        var stack = new net.minecraft.world.item.ItemStack(
                net.minecraft.world.item.Items.DIAMOND);

        // handler 内部索引 0 = 合法：写入成功
        var ok = new net.neoforged.neoforge.items.SlotItemHandler(handler, 0, 0, 0);
        ok.set(stack);
        assertEquals(1, handler.getStackInSlot(0).getCount());

        // handler 内部索引 1 = 越界（menu 索引才是 1）：set 抛错 → 客户端被踢的根因
        var bad = new net.neoforged.neoforge.items.SlotItemHandler(handler, 1, 0, 0);
        RuntimeException thrown = org.junit.jupiter.api.Assertions.assertThrows(
                RuntimeException.class, () -> bad.set(stack));
        assertTrue(thrown.getMessage().contains("not in valid range"),
                "越界报错应指明 handler 有效范围，实际: " + thrown.getMessage());
    }
}
