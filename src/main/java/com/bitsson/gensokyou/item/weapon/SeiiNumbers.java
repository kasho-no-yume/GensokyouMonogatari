package com.bitsson.gensokyou.item.weapon;

import com.bitsson.gensokyou.balance.BalanceNumbers;
import com.bitsson.gensokyou.balance.BuildBudgetMath;
import com.bitsson.gensokyou.balance.BuildNumbers;
import com.bitsson.gensokyou.balance.CoreAffixCatalog;
import com.bitsson.gensokyou.config.GensokyouServerConfig;

import com.bitsson.gensokyou.ritual.RitualScaling;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 星移之仪洗练数值核（world-independent 纯逻辑，可单测）。
 *
 * <p>三条阶轴各管一件事：
 * <ul>
 *   <li><b>核阶</b>（1/2/3）= 收益阶梯：词条条数（3/4/5）与灵力花费（×100/阶）</li>
 *   <li><b>仪式阶</b>（1/3/5）= 吞吐阶梯：缓存（×12/阶）与受灵速率（×10/阶），
 *       且受灵速率恰为"该阶可洗的最高核阶花费 ÷ 1.5 秒"</li>
 *   <li><b>玩家阶</b> 不参与 roll 数值：属性词条带为绝对贡献（单位见词条），
 *       实际生效再受实际玩家阶边界钳制（先增幅后钳制）</li>
 * </ul>
 *
 * <p>玩家属性词条走 contribution 加区（绝不写台账），武器专有词条走 WeaponFiring 乘区。
 *
 * <p><b>2026-11 新版生成规则</b>（rework-amp-core-rolls-and-skill-slots）：首次生成与洗练
 * 共用 {@link CoreAffixCatalog} 单池；T1/T2/T3 = 3/4/5 条、id 唯一；数值低位密集抽样
 * （q = u^k(t)，软保底只把指数 k 压向 1，不平移上沿）；整核过
 * {@link BuildNumbers#legalCore} 组合预算（64 次有界拒绝 + 确定性低位合法回退）；
 * 预算是天花板，不归一成等强，不运行时打折。<b>候选数值待用户审核</b>。
 */
public final class SeiiNumbers {

    /** 核阶数（AMP_CORE_T1..T3）。 */
    public static final int CORE_TIERS = 3;

    /** 新版核生成规则版本（写入 {@code CORE_RULE_VERSION} 组件；旧核缺失即 legacy 0）。 */
    public static final int RULE_VERSION = 1;

    /** legacy 规则版本（无组件/写入 0 的核：旧带宽、旧条数、旧攻速语义）。 */
    public static final int LEGACY_RULE_VERSION = 0;

    /** 生成拒绝采样的尝试上限（候选；超限走确定性低位合法回退，不阻塞服务端）。 */
    public static final int MAX_ATTEMPTS = 64;

    /** 低位偏置指数基值（t=0 时 q = u^3）。候选，待审。 */
    public static final double BASE_EXPONENT = 3D;

    // ---- 阶梯换算（纯核，参数注入以便单测；config 包装器在其下） ----
    //
    // 语义：base 是「level = 1」那一档的值，倍率按<b>阶数差</b>累乘
    // （level 1/3/5 → mult^0 / mult^2 / mult^4），故指数用 level-1 而非 level。

    /** 缓存上限 = {@code base * mult^(level-1)}（饱和乘）。 */
    public static long capacityOf(long base, int mult, int ritualLevel) {
        return RitualScaling.scale(base, mult, Math.max(0, ritualLevel - 1));
    }

    /** 受灵速率 = {@code base * mult^(level-1)}（饱和乘）。 */
    public static long inRateOf(long base, int mult, int ritualLevel) {
        return RitualScaling.scale(base, mult, Math.max(0, ritualLevel - 1));
    }

    /** 单次洗练花费 = {@code base * mult^(coreTier-1)}（饱和乘）。 */
    public static long spCostOf(long base, int mult, int coreTier) {
        return RitualScaling.scale(base, mult, Math.max(0, coreTier - 1));
    }

    /** 洗练缓存上限（按仪式阶）。 */
    public static long capacity(int ritualLevel) {
        return capacityOf(GensokyouServerConfig.SEII_BASE_CAPACITY.get(),
                GensokyouServerConfig.SEII_CAPACITY_MULT.get(), ritualLevel);
    }

    /** 洗练受灵速率（按仪式阶，每秒）。 */
    public static long inRate(int ritualLevel) {
        return inRateOf(GensokyouServerConfig.SEII_BASE_IN_RATE.get(),
                GensokyouServerConfig.SEII_IN_RATE_MULT.get(), ritualLevel);
    }

    /** 单次洗练灵力花费（按<strong>核阶</strong>，非仪式阶）。 */
    public static long spCost(int coreTier) {
        return spCostOf(GensokyouServerConfig.SEII_BASE_SP_COST.get(),
                GensokyouServerConfig.SEII_SP_COST_MULT.get(), coreTier);
    }

    /**
     * 词条条数（按核阶）。<b>逐位</b>合并：config 优先（尊重玩家显式调整），
     * 缺位/越界由内置默认补齐 —— 因为 NeoForge 不会把新的 list 默认值写进已有配置文件。
     * 新版默认 3/4/5（rework-amp-core-rolls-and-skill-slots）。
     */
    public static int affixCount(int coreTier) {
        return affixCountOf(coreTier, BalanceNumbers.RUNE_AFFIX_COUNT,
                RuneGenerator.countDefaults());
    }

    /** 注入版（纯核单测）。 */
    public static int affixCountOf(int coreTier, List<? extends Integer> config,
                                   List<? extends Integer> defaults) {
        int index = clampTier(coreTier) - 1;
        if (config != null && index < config.size() && config.get(index) != null) {
            return Math.max(1, config.get(index));
        }
        if (defaults != null && index < defaults.size() && defaults.get(index) != null) {
            return Math.max(1, defaults.get(index));
        }
        return 3;
    }

    /** 某仪式阶可洗的最高核阶（1 阶→1 / 3 阶→2 / 5 阶→3）。 */
    public static int maxCoreTier(int ritualLevel) {
        if (ritualLevel >= 5) {
            return 3;
        }
        return ritualLevel >= 3 ? 2 : 1;
    }

    /** 跨版本预览采纳决策。 */
    public enum AcceptAction {
        /** 新版预览：写回并盖新版本章。 */
        APPLY_NEW,
        /** 旧档（legacy 表）预览：身份转换，写回旧语义词条、保持旧版本状态，不静默升级。 */
        APPLY_LEGACY,
        /** 未来/未知版本预览：明确作废，保留原核。 */
        REJECT_UNKNOWN
    }

    /** 按预览携带的规则版本决定采纳动作（纯函数）。 */
    public static AcceptAction acceptAction(int ruleVersion) {
        if (ruleVersion == RULE_VERSION) {
            return AcceptAction.APPLY_NEW;
        }
        if (ruleVersion == LEGACY_RULE_VERSION) {
            return AcceptAction.APPLY_LEGACY;
        }
        return AcceptAction.REJECT_UNKNOWN;
    }

    /** 核阶 → 参考玩家阶（T1→1 / T2→3 / T3→5；仅供说明与注解，带均为绝对值）。 */
    public static int referencePlayerTier(int coreTier) {
        return CoreAffixCatalog.referencePlayerTier(coreTier);
    }

    private static int clampTier(int coreTier) {
        return CoreAffixCatalog.clampTier(coreTier);
    }

    // ---- 软保底 ----

    /** 软保底进度 t ∈ [0,1]（纯核，cap 注入）。 */
    public static double pityProgress(int rerolls, int cap) {
        int c = Math.max(1, cap);
        return Math.min(Math.max(0, rerolls), c) / (double) c;
    }

    /** 软保底进度 t ∈ [0,1]（读 config）。 */
    public static double pityProgress(int rerolls) {
        return pityProgress(rerolls, BalanceNumbers.RUNE_PITY_CAP);
    }

    /**
     * 软保底只压平低位偏置：k(t) = baseK − (baseK − 1)·t ∈ [1, baseK]。
     * t=0 → u^3（低值常见）；t=1 → 均匀（不保极品、不平移带、不抬法定上沿、不改类型权重）。
     */
    public static double pityExponent(double baseK, double t) {
        double clamped = Math.min(1D, Math.max(0D, t));
        return baseK - (baseK - 1D) * clamped;
    }

    // ---- 弹幕护壁（无量纲指数键） ----

    /** 目标减伤 r → 指数增量 ΔP = -log2(1-r)。 */
    public static double wardExponentFor(double mitigation) {
        return BuildBudgetMath.wardExponent(mitigation);
    }

    // ---- roll ----

    /** Roll 全部可调项的注入载体（纯核单测只构造这个 record，不碰 config）。 */
    public record Tunables(List<? extends String> weaponRows,
                           List<? extends String> attrBandRows,
                           List<? extends Double> wardBand,
                           int attrWeight,
                           int pityCap,
                           int[] affixCounts) {
    }

    /** roll 结果与整核组合预算核验所用的数值映射（spirit_cost_pct 取绝对值）。 */
    static Map<String, Double> budgetMap(List<RuneAffix> rolled) {
        Map<String, Double> out = new HashMap<>();
        for (RuneAffix affix : rolled) {
            double value = affix.value();
            if ("spirit_cost_pct".equals(affix.affixId())) {
                value = Math.abs(value);
            }
            out.put(affix.affixId(), value);
        }
        return out;
    }

    /** roll 结果 + 诊断信息（仅供统计/测试，不改变游戏逻辑）：attempts = 通过组合预算所用的尝试次数（64=回退）。 */
    public record RollOutcome(List<RuneAffix> affixes, int attempts, boolean fallbackUsed) {
    }

    /** 诊断路径（测试/报表用）；与 {@link SeiiNumbers#roll(int, int, RandomSource, Tunables)} 同一套逻辑。 */
    public static RollOutcome rollDiagnostic(int coreTier, int rerolls, RandomSource random,
                                             Tunables tune) {
        return rollInternal(coreTier, rerolls, random, tune);
    }

    /** 洗练/首次 roll：共享单池、T1/T2/T3 = 3/4/5 条、低位偏置 + 软保底、整核组合预算。 */
    public static List<RuneAffix> roll(int coreTier, int rerolls, RandomSource random,
                                       Tunables tune) {
        return rollInternal(coreTier, rerolls, random, tune).affixes();
    }

    private static RollOutcome rollInternal(int coreTier, int rerolls, RandomSource random,
                                            Tunables tune) {
        int tier = clampTier(coreTier);
        List<CoreAffixCatalog.Definition> defs = CoreAffixCatalog.merged(
                tune.weaponRows(), tune.attrBandRows(), tune.wardBand(), tune.attrWeight());
        int count = Math.min(Math.max(1, tune.affixCounts()[tier - 1]), defs.size());
        double t = pityProgress(rerolls, tune.pityCap());
        double k = pityExponent(BASE_EXPONENT, t);

        // 有界拒绝采样：越过组合预算则重掷；64 次仍不行走确定性低位合法回退
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            List<CoreAffixCatalog.Definition> picks = weightedUniquePick(defs, tier, count, random);
            if (picks == null) {
                break;
            }
            List<RuneAffix> rolled = new ArrayList<>(count);
            for (CoreAffixCatalog.Definition def : picks) {
                rolled.add(new RuneAffix(def.id(), (float) valueOf(def, tier, k, random)));
            }
            if (BuildNumbers.legalCore(budgetMap(rolled), tier)) {
                return new RollOutcome(List.copyOf(rolled), attempt + 1, false);
            }
        }
        // 确定性低位回退：取权重最高（同权重按 id 字典序）的 count 种类型，全部取带下沿（不乘保底）；
        // 下沿组合仍可能撞预算（如攻速/减耗/增伤三件套），按所需比例整体缩放并向 0 收敛，
        // 条数不减、不补极品、值收缩进带内。
        List<CoreAffixCatalog.Definition> fallback = defs.stream()
                .sorted(java.util.Comparator
                        .comparingInt((CoreAffixCatalog.Definition d) -> d.weightOf(tier)).reversed()
                        .thenComparing(CoreAffixCatalog.Definition::id))
                .limit(count)
                .toList();
        List<RuneAffix> rolled = new ArrayList<>(fallback.size());
        for (CoreAffixCatalog.Definition def : fallback) {
            rolled.add(new RuneAffix(def.id(), (float) floorOf(def, tier)));
        }
        double score = BuildNumbers.coreScore(budgetMap(rolled), tier);
        if (score > BuildNumbers.coreBudget(tier)) {
            scaleAffixes(rolled, Math.sqrt(BuildNumbers.coreBudget(tier) / score) * 0.99D);
        }
        for (int i = 0; i < 8 && !BuildNumbers.legalCore(budgetMap(rolled), tier); i++) {
            scaleAffixes(rolled, 0.5D);
        }
        if (!BuildNumbers.legalCore(budgetMap(rolled), tier)) {
            // 带定义本身撞上组合预算（只可能由坏 config 造成）：显式失败，不静默放行
            throw new IllegalStateException(
                    "core fallback roll violates combination budget; check affix band config for tier "
                            + tier);
        }
        return new RollOutcome(List.copyOf(rolled), MAX_ATTEMPTS, true);
    }

    /** 便捷重载：读 config + 真实标准值表。 */
    public static List<RuneAffix> roll(int coreTier, int rerolls, RandomSource random) {
        return roll(coreTier, rerolls, random, tunables());
    }

    /** 当前 config 快照。 */
    public static Tunables tunables() {
        return new Tunables(BalanceNumbers.RUNE_AFFIX_POOL,
                BalanceNumbers.RUNE_ATTR_BAND,
                BalanceNumbers.RUNE_WARD_BAND,
                BalanceNumbers.RUNE_ATTR_WEIGHT,
                BalanceNumbers.RUNE_PITY_CAP,
                new int[]{affixCount(1), affixCount(2), affixCount(3)});
    }

    private static double valueOf(CoreAffixCatalog.Definition def, int tier, double k,
                                  RandomSource random) {
        double frac = Math.pow(random.nextDouble(), k);
        return switch (def.kind()) {
            case WEAPON_ABS, ATTR_ABS -> def.loOf(tier) + (def.hiOf(tier) - def.loOf(tier)) * frac;
            case WARD_MIT -> {
                double r = def.loOf(tier) + (def.hiOf(tier) - def.loOf(tier)) * frac;
                yield BuildBudgetMath.wardExponent(r);
            }
        };
    }

    private static double floorOf(CoreAffixCatalog.Definition def, int tier) {
        return switch (def.kind()) {
            case WEAPON_ABS, ATTR_ABS -> def.loOf(tier);
            case WARD_MIT -> BuildBudgetMath.wardExponent(def.loOf(tier));
        };
    }

    /** 低位回退用：整体缩放（向 0 收缩，MUST NOT 反向放大）。 */
    private static void scaleAffixes(List<RuneAffix> rolled, double factor) {
        double f = Math.min(1D, Math.max(0D, factor));
        for (int i = 0; i < rolled.size(); i++) {
            RuneAffix a = rolled.get(i);
            rolled.set(i, new RuneAffix(a.affixId(), (float) (a.value() * f)));
        }
    }

    private static List<CoreAffixCatalog.Definition> weightedUniquePick(
            List<CoreAffixCatalog.Definition> defs, int tier, int count, RandomSource random) {
        int total = 0;
        for (CoreAffixCatalog.Definition def : defs) {
            total += def.weightOf(tier);
        }
        if (total <= 0) {
            return null;
        }
        Set<String> used = new HashSet<>();
        List<CoreAffixCatalog.Definition> picks = new ArrayList<>(count);
        int guard = 0;
        while (picks.size() < count && guard++ < 200) {
            int roll = random.nextInt(total);
            CoreAffixCatalog.Definition hit = null;
            for (CoreAffixCatalog.Definition def : defs) {
                roll -= def.weightOf(tier);
                if (roll < 0) {
                    hit = def;
                    break;
                }
            }
            if (hit == null) {
                return null;
            }
            if (used.add(hit.id())) {
                picks.add(hit);
            }
        }
        return picks.size() == count ? picks : null;
    }

    private SeiiNumbers() {
    }
}
