package com.bitsson.gensokyou.item.weapon;

import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.ritual.RitualScaling;
import com.bitsson.gensokyou.spirit.SpiritPowerData;
import com.bitsson.gensokyou.spirit.attr.AttributeKey;
import com.bitsson.gensokyou.spirit.grace.GraceNumbers;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;

/**
 * 星移之仪洗练数值核（world-independent 纯逻辑，可单测）。
 *
 * <p>三条阶轴各管一件事：
 * <ul>
 *   <li><b>核阶</b>（1/2/3）= 收益阶梯：词条条数（1/3/5）与灵力花费（×100/阶）</li>
 *   <li><b>仪式阶</b>（1/3/5）= 吞吐阶梯：缓存（×12/阶）与受灵速率（×10/阶），
 *       且受灵速率恰为"该阶可洗的最高核阶花费 ÷ 1.5 秒"</li>
 *   <li><b>玩家阶</b> = 玩家属性词条的数值基准</li>
 * </ul>
 *
 * <p>玩家属性词条走 contribution 加区（绝不写台账），武器专有词条走 WeaponFiring 乘区。
 */
public final class SeiiNumbers {

    /** 核阶数（AMP_CORE_T1..T3）。 */
    public static final int CORE_TIERS = 3;

    /** 可进池的玩家属性键（排除永久池账 max_spirit / 永久属性 health_bonus / 已退役 danmaku_resist）。 */
    public static final List<AttributeKey> ATTR_KEYS = List.of(
            AttributeKey.SPIRIT_POWER,
            AttributeKey.SPIRIT_REGEN_RATE,
            AttributeKey.MOVE_SPEED_BONUS,
            AttributeKey.GRAZE_CHANCE,
            AttributeKey.DANMAKU_REDUCE,
            AttributeKey.TENACITY,
            AttributeKey.CRIT_CHANCE,
            AttributeKey.CRIT_DAMAGE,
            AttributeKey.SPELL_AMP,
            AttributeKey.SPELL_CDR,
            AttributeKey.BUFF_EXTEND,
            AttributeKey.SPIRIT_LEECH_RATE,
            AttributeKey.JUMP,
            AttributeKey.PHYS_RESIST,
            AttributeKey.MELEE_DAMAGE);

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
        return capacityOf(GensokyouConfig.SEII_BASE_CAPACITY.get(),
                GensokyouConfig.SEII_CAPACITY_MULT.get(), ritualLevel);
    }

    /** 洗练受灵速率（按仪式阶，每秒）。 */
    public static long inRate(int ritualLevel) {
        return inRateOf(GensokyouConfig.SEII_BASE_IN_RATE.get(),
                GensokyouConfig.SEII_IN_RATE_MULT.get(), ritualLevel);
    }

    /** 单次洗练灵力花费（按<strong>核阶</strong>，非仪式阶）。 */
    public static long spCost(int coreTier) {
        return spCostOf(GensokyouConfig.SEII_BASE_SP_COST.get(),
                GensokyouConfig.SEII_SP_COST_MULT.get(), coreTier);
    }

    /**
     * 词条条数（按核阶）。<b>逐位</b>合并：config 优先（尊重玩家显式调整），
     * 缺位/越界由内置默认补齐 —— 因为 NeoForge 不会把新的 list 默认值写进已有配置文件。
     */
    public static int affixCount(int coreTier) {
        return affixCountOf(coreTier, GensokyouConfig.RUNE_AFFIX_COUNT.get(),
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
        return 1;
    }

    /** 某仪式阶可洗的最高核阶（1 阶→1 / 3 阶→2 / 5 阶→3）。 */
    public static int maxCoreTier(int ritualLevel) {
        if (ritualLevel >= 5) {
            return 3;
        }
        return ritualLevel >= 3 ? 2 : 1;
    }

    /**
     * 核阶 → 玩家属性词条的参考玩家阶（T1 核→1 阶 / T2 核→3 阶 / T3 核→5 阶）。
     * 词条值 = 该玩家阶标准属性值的百分之几，故永远"占你已有属性的百分之几"，不随成长相对变弱。
     */
    public static int referencePlayerTier(int coreTier) {
        return switch (clampTier(coreTier)) {
            case 1 -> 1;
            case 2 -> 3;
            default -> 5;
        };
    }

    private static int clampTier(int coreTier) {
        return Math.min(Math.max(1, coreTier), CORE_TIERS);
    }

    // ---- 软保底 ----

    /** 软保底进度 t ∈ [0,1]（纯核，cap 注入）。 */
    public static double pityProgress(int rerolls, int cap) {
        int c = Math.max(1, cap);
        return Math.min(Math.max(0, rerolls), c) / (double) c;
    }

    /** 软保底进度 t ∈ [0,1]（读 config）。 */
    public static double pityProgress(int rerolls) {
        return pityProgress(rerolls, GensokyouConfig.RUNE_PITY_CAP.get());
    }

    /**
     * 软保底平移后的采样带。t=0 返回原带；t=1 时下界 = 原带中点、上界 = 原上界之上一个带宽
     * （即"最坏的一次也等于原来的平均"，同时带宽翻倍）。玩家属性键传"已乘过标准值"的绝对带，
     * 武器键传原始百分比带，走同一条公式。
     */
    public static double[] pityBand(double lo, double hi, double t) {
        if (t <= 0D || hi <= lo) {
            return new double[]{lo, hi};
        }
        double width = hi - lo;
        return new double[]{lo + width * t * 0.5D, lo + width * (1D + t * 0.5D)};
    }

    // ---- 弹幕护壁（无量纲指数键） ----

    /** 目标减伤 r → 指数增量 ΔP = -log2(1-r)。 */
    public static double wardExponentFor(double mitigation) {
        double r = Math.min(0.95D, Math.max(0D, mitigation));
        return -Math.log(1D - r) / Math.log(2D);
    }

    /** 弹幕护壁词条的目标减伤带（按核阶，index = 核阶-1 的成对 min/max）。 */
    public static double[] wardMitigationBand(int coreTier) {
        return wardMitigationBandOf(defaultWardBand(coreTier), coreTier,
                GensokyouConfig.RUNE_WARD_BAND.get());
    }

    /** 注入版：config 缺项时回落到 {@code fallback}。 */
    public static double[] wardMitigationBandOf(double[] fallback, int coreTier,
                                                List<? extends Double> table) {
        int index = (clampTier(coreTier) - 1) * 2;
        if (table != null && table.size() >= index + 2) {
            return new double[]{table.get(index), table.get(index + 1)};
        }
        return fallback;
    }

    /** 缺省减伤带：T1 1~3% / T2 2~5% / T3 5~8%。 */
    public static double[] defaultWardBand(int coreTier) {
        return switch (clampTier(coreTier)) {
            case 1 -> new double[]{0.01D, 0.03D};
            case 2 -> new double[]{0.02D, 0.05D};
            default -> new double[]{0.05D, 0.08D};
        };
    }

    // ---- 玩家属性基准与采样带 ----

    /**
     * 玩家属性的<b>标准值</b>（该玩家阶的累计值：Σ各阶 grace 增量）。
     * 词条数值的分母，MUST NOT 用玩家实际值 —— 否则洗练变成"给高玩开绿灯"的机制。
     */
    public static double standardValue(AttributeKey key, int playerTier) {
        int top = Math.min(Math.max(1, playerTier), SpiritPowerData.MAX_TIER);
        double sum = 0D;
        for (int t = 1; t <= top; t++) {
            GraceNumbers.Entry entry = GraceNumbers.entry(t, key);
            if (entry != null) {
                sum += entry.base();
            }
        }
        return sum;
    }

    /** 某玩家属性键在某核阶的采样带（百分比，非标准值）。 */
    public static double[] attrBand(AttributeKey key, int coreTier) {
        return attrBandOf(key, coreTier, GensokyouConfig.RUNE_ATTR_BAND.get());
    }

    /** 注入版行表（可单测）：缺项回落缺省带。 */
    public static double[] attrBandOf(AttributeKey key, int coreTier, List<? extends String> rows) {
        int tier = clampTier(coreTier);
        if (rows != null) {
            for (String raw : rows) {
                String[] parts = raw.split(",");
                if (parts.length != 5) {
                    continue;
                }
                try {
                    if (Integer.parseInt(parts[0].trim()) == tier
                            && parts[1].trim().equals(key.id())) {
                        return new double[]{Double.parseDouble(parts[2].trim()),
                                Double.parseDouble(parts[3].trim())};
                    }
                } catch (NumberFormatException ignored) {
                    // 坏行跳过（配置面容忍，加载不炸）
                }
            }
        }
        return defaultAttrBand(tier);
    }

    /** 缺省采样带：T1 1~3% / T2 2~5% / T3 5~8%。 */
    public static double[] defaultAttrBand(int coreTier) {
        return switch (clampTier(coreTier)) {
            case 1 -> new double[]{0.01D, 0.03D};
            case 2 -> new double[]{0.02D, 0.05D};
            default -> new double[]{0.05D, 0.08D};
        };
    }

    // ---- roll ----

    /**
     * 洗练 roll：武器专有键 + 15 个玩家属性键按权重不重复抽 {@code affixCount} 条。
     *
     * @param coreTier    核阶 1..3
     * @param rerolls     核上洗练度（软保底）
     * @param random      随机源
     * @param standardOf  玩家属性键 → 该核阶参考玩家阶的标准值（可注入以便单测）
     */
    /** roll 全部可调项的注入载体（纯核单测只构造这个 record，不碰 config）。 */
    public record Tunables(List<RuneGenerator.AffixDef> weaponPool,
                           List<? extends String> attrBandRows,
                           List<? extends Double> wardBand,
                           int attrWeight,
                           int pityCap,
                           int affixCount) {
    }

    /** 洗练 roll：武器专有键 + 15 个玩家属性键按权重不重复抽 {@code affixCount} 条（纯核）。 */
    public static List<RuneAffix> roll(int coreTier, int rerolls, RandomSource random,
                                      ToDoubleFunction<AttributeKey> standardOf,
                                      Tunables tune) {
        int tier = clampTier(coreTier);
        double t = pityProgress(rerolls, tune.pityCap());
        List<Weighted> candidates = new ArrayList<>();
        for (RuneGenerator.AffixDef def : tune.weaponPool()) {
            if (def.tier() == tier) {
                candidates.add(new Weighted(def.id(), def.weight(), null));
            }
        }
        int attrWeight = Math.max(1, tune.attrWeight());
        for (AttributeKey key : ATTR_KEYS) {
            candidates.add(new Weighted(key.id(), attrWeight, key));
        }
        int count = Math.min(Math.max(1, tune.affixCount()), candidates.size());
        List<RuneAffix> result = new ArrayList<>(count);
        Set<String> used = new HashSet<>();
        int guard = 0;
        while (result.size() < count && guard++ < 200) {
            Weighted pick = weightedPick(candidates, random);
            if (pick == null || !used.add(pick.id())) {
                continue;
            }
            result.add(new RuneAffix(pick.id(),
                    (float) valueOf(pick, tier, t, random, standardOf, tune)));
        }
        return List.copyOf(result);
    }

    /** 便捷重载：读 config + 真实标准值表。 */
    public static List<RuneAffix> roll(int coreTier, int rerolls, RandomSource random) {
        return roll(coreTier, rerolls, random,
                key -> standardValue(key, referencePlayerTier(coreTier)), tunables());
    }

    /** 当前 config 快照。 */
    public static Tunables tunables() {
        return new Tunables(RuneGenerator.pool(),
                GensokyouConfig.RUNE_ATTR_BAND.get(),
                GensokyouConfig.RUNE_WARD_BAND.get(),
                GensokyouConfig.RUNE_ATTR_WEIGHT.get(),
                GensokyouConfig.RUNE_PITY_CAP.get(),
                affixCount(1));
    }

    private static double valueOf(Weighted pick, int coreTier, double t, RandomSource random,
                                  ToDoubleFunction<AttributeKey> standardOf, Tunables tune) {
        AttributeKey key = pick.attr();
        if (key == null) {
            RuneGenerator.AffixDef def = null;
            for (RuneGenerator.AffixDef d : tune.weaponPool()) {
                if (d.tier() == coreTier && d.id().equals(pick.id())) {
                    def = d;
                    break;
                }
            }
            if (def == null) {
                return 0D;
            }
            double[] band = pityBand(def.min(), def.max(), t);
            return band[0] + random.nextDouble() * (band[1] - band[0]);
        }
        if (key == AttributeKey.DANMAKU_REDUCE) {
            double[] mit = wardMitigationBandOf(defaultWardBand(coreTier), coreTier, tune.wardBand());
            double hi = Math.min(0.95D, mit[1] + (mit[1] - mit[0]) * t * 0.5D);
            double r = mit[0] + random.nextDouble() * Math.max(0D, hi - mit[0]);
            return wardExponentFor(r);
        }
        double[] pct = attrBandOf(key, coreTier, tune.attrBandRows());
        double standard = standardOf.applyAsDouble(key);
        double[] band = pityBand(standard * pct[0], standard * pct[1], t);
        return band[0] + random.nextDouble() * (band[1] - band[0]);
    }

    private record Weighted(String id, int weight, AttributeKey attr) {
    }

    private static Weighted weightedPick(List<Weighted> candidates, RandomSource random) {
        int total = candidates.stream().mapToInt(Weighted::weight).sum();
        if (total <= 0) {
            return null;
        }
        int roll = random.nextInt(total);
        for (Weighted c : candidates) {
            roll -= c.weight();
            if (roll < 0) {
                return c;
            }
        }
        return null;
    }

    /** 词条值的统一展示（百分比语义）。 */
    public static String percent(double value) {
        return String.format(Locale.ROOT, "%+.1f%%", value * 100D);
    }

    private SeiiNumbers() {
    }
}
