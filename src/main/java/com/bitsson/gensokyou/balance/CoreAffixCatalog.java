package com.bitsson.gensokyou.balance;

import com.bitsson.gensokyou.spirit.attr.AttributeKey;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 增幅核统一受控词条定义表（rune-affix-pool / rework-amp-core-rolls-and-skill-slots）。
 *
 * <p>首次生成与星移洗练共享本表；config 只允许<b>改带宽/权重</b>（RUNE_AFFIX_POOL /
 * RUNE_ATTR_BAND / RUNE_WARD_BAND），不允许增删 id —— 新增词条必须改代码并满足
 * 「每条都有可测试的真实消费路径」。未知 id 的配置行被忽略并告警。
 *
 * <p><b>候选表：数值待用户审核</b>（rework-player-stat-build-budgets 的 1.6 关卡），
 * 结构与机制先行定案。
 *
 * <p>三种带语义（{@link Kind}）：
 * <ul>
 *   <li>{@link Kind#WEAPON_ABS}：武器专有域，绝对值带（如伤害百分比、频率加成、减耗幅度）；</li>
 *   <li>{@link Kind#ATTR_ABS}：玩家属性域，<b>绝对贡献</b>带，按各自单位设定
 *       （概率属性为绝对百分点；灵力强度为绝对点数、参考阶派生并受实际玩家阶总额度约束；
 *       杂项有限区间）。spec 明文不允许“统一按标准值 1~8% 小额比例”；</li>
 *   <li>{@link Kind#WARD_MIT}：弹幕护壁，带 = 目标减伤比例 pct，roll 时换算 ΔP = −log2(1−r)（绝对指数贡献）。</li>
 * </ul>
 */
public final class CoreAffixCatalog {

    public enum Kind {
        WEAPON_ABS,
        ATTR_ABS,
        WARD_MIT
    }

    /** 单条受控定义。lo/hi/weight 按核阶 T1..T3 三元组存放。 */
    public record Definition(String id, Kind kind, double[] lo, double[] hi, int[] weight) {
        public double loOf(int tier) {
            return lo[tier - 1];
        }

        public double hiOf(int tier) {
            return hi[tier - 1];
        }

        public int weightOf(int tier) {
            return weight[tier - 1];
        }
    }

    /**
     * 玩家属性域的绝对贡献带（候选，待审）：id → {{T1lo,T1hi},{T2lo,T2hi},{T3lo,T3hi}}。
     * 概率属性按绝对百分点、强度按绝对点数、护壁走 {@link Kind#WARD_MIT} 单独列、
     * 杂项有限区间。受控白名单内每个 attr id 必须在表内。
     */
    private static final Map<String, double[][]> ATTR_ABS_BAND = attrTable(
            "crit_chance", attr(.01D, .02D, .02D, .04D, .03D, .06D),
            "crit_damage", attr(.03D, .08D, .06D, .15D, .10D, .25D),
            "spell_cdr", attr(.01D, .03D, .02D, .05D, .03D, .08D),
            "spell_effect_amp", attr(.05D, .12D, .10D, .25D, .20D, .45D),
            "spell_duration", attr(.02D, .05D, .04D, .10D, .08D, .18D),
            "spell_cost_reduction", attr(.02D, .05D, .03D, .08D, .05D, .12D),
            "spirit_power", attr(.8D, 2D, 12D, 30D, 90D, 240D),
            "spirit_regen_rate", attr(.4D, .8D, 6D, 14D, 60D, 130D),
            "spirit_leech_rate", attr(.02D, .04D, .03D, .06D, .05D, .09D),
            "graze_chance", attr(.01D, .03D, .02D, .04D, .04D, .07D),
            "tenacity", attr(.01D, .03D, .02D, .05D, .03D, .08D),
            "phys_resist", attr(.01D, .03D, .02D, .04D, .03D, .07D),
            "buff_extend", attr(.02D, .04D, .03D, .07D, .05D, .12D),
            "jump", attr(.05D, .10D, .08D, .15D, .12D, .25D),
            "melee_damage", attr(1D, 3D, 2D, 5D, 4D, 8D),
            "move_speed_bonus", attr(.02D, .04D, .03D, .06D, .05D, .10D));

    /** 弹幕护壁的默认目标减伤带（T1 1~3% / T2 2~5% / T3 5~8%）。候选，待审。 */
    private static final double[][] WARD_MIT_BAND = {
            {0.01D, 0.03D},
            {0.02D, 0.05D},
            {0.05D, 0.08D},
    };

    private static final int DEFAULT_ATTR_WEIGHT = 6;

    /** 武器专有域内置默认带（候选，沿用既有投放表数值；待审）。id → {lo[3], hi[3], weight[3]}。 */
    private static final Map<String, Definition> WEAPON_DEFAULTS = orderedMap(
            weapon("damage_pct", new double[]{0.03D, 0.07D, 0.12D}, new double[]{0.06D, 0.12D, 0.20D},
                    new int[]{10, 10, 10}),
            weapon("attack_rate_pct", new double[]{0.03D, 0.06D, 0.10D}, new double[]{0.06D, 0.10D, 0.15D},
                    new int[]{8, 8, 8}),
            weapon("spirit_cost_pct", new double[]{-0.08D, -0.14D, -0.22D},
                    new double[]{-0.03D, -0.06D, -0.10D}, new int[]{8, 8, 8}),
            weapon("range_pct", new double[]{0.03D, 0.06D, 0.08D}, new double[]{0.06D, 0.10D, 0.12D},
                    new int[]{6, 6, 6}));

    /** 交错的 id → 值字符串表。 */
    private static Map<String, String> kv(String... entries) {
        Map<String, String> out = new LinkedHashMap<>();
        for (int i = 0; i + 1 < entries.length; i += 2) {
            out.put(entries[i], entries[i + 1]);
        }
        return out;
    }

    /**
     * 每条词条的真实消费路径（spec：无虚假词条）。受控 id 全集必须有条目；
     * validate 缺失即失败。新增词条必须先代码审计并登记此处。
     */
    private static final Map<String, String> CONSUMERS = kv(
            "damage_pct", "WeaponFiring (damage multiplier)",
            "attack_rate_pct", "WeaponFiring (effectiveSpeed / WeaponFrequencyMath)",
            "spirit_cost_pct", "WeaponFiring + SpiritPayments (weapon spirit cost)",
            "range_pct", "RuneSummary.weapon (range decay exponent)",
            "spirit_power", "EquippedAffixBridge -> PlayerAttributes (all damage chains)",
            "spirit_regen_rate", "ModAttachments (regen buffer) / PlayerAttributes.regenPerSecond",
            "move_speed_bonus", "PlayerAttributes (movement speed modifier)",
            "graze_chance", "DamageEventHandler (graze roll)",
            "danmaku_reduce", "DamageEventHandler (ward 2^-P mitigation)",
            "tenacity", "EffectDurationHandler (debuff duration)",
            "crit_chance", "WeaponFiring (crit roll)",
            "crit_damage", "WeaponFiring (crit multiplier)",
            "spell_cdr", "SpellCardEffects.effectiveCooldown / PlayerAttributes.effectiveSkillCooldown",
            "buff_extend", "EffectDurationHandler (beneficial duration)",
            "spirit_leech_rate", "SpiritLeechHandler (weapon leech allowance)",
            "jump", "PlayerAttributes (jump/flight allowance)",
            "phys_resist", "CultivationGiftEvents (non-danmaku physical mitigation)",
            "melee_damage", "CultivationGiftEvents (melee bonus damage)",
            "spell_effect_amp", "SpellCardEffects + SpellParameterPolicies (magnitude amp; heal coef 0.5)",
            "spell_duration", "SpellCardEffects (duration extension)",
            "spell_cost_reduction", "SpellCardEffects.effectiveCost / ModNetworking (card spirit cost)");

    /** 某词条 id 的真实消费路径（审计/报表用；未登记返回 null）。 */
    public static String consumerOf(String id) {
        return CONSUMERS.get(id);
    }

    /** 玩家属性域 id（新版池；旧 spell_amp 仅 legacy，不进池；新 spell_* 分类 ID 入池）。 */
    private static final List<String> ATTR_IDS = List.of(
            AttributeKey.SPIRIT_POWER.id(),
            AttributeKey.SPIRIT_REGEN_RATE.id(),
            AttributeKey.MOVE_SPEED_BONUS.id(),
            AttributeKey.GRAZE_CHANCE.id(),
            AttributeKey.DANMAKU_REDUCE.id(),
            AttributeKey.TENACITY.id(),
            AttributeKey.CRIT_CHANCE.id(),
            AttributeKey.CRIT_DAMAGE.id(),
            AttributeKey.SPELL_CDR.id(),
            AttributeKey.BUFF_EXTEND.id(),
            AttributeKey.SPIRIT_LEECH_RATE.id(),
            AttributeKey.JUMP.id(),
            AttributeKey.PHYS_RESIST.id(),
            AttributeKey.MELEE_DAMAGE.id(),
            AttributeKey.SPELL_EFFECT_AMP.id(),
            AttributeKey.SPELL_DURATION.id(),
            AttributeKey.SPELL_COST_REDUCTION.id());

    /** 武器域 id（受控白名单；config 不得引入其他 id）。 */
    public static List<String> weaponIds() {
        return List.copyOf(WEAPON_DEFAULTS.keySet());
    }

    /** 玩家属性域 id（受控白名单）。 */
    public static List<String> attributeIds() {
        return ATTR_IDS;
    }

    /**
     * 运行时有效定义表 = 内置默认 + config 覆盖。config 只改带宽/权重；未知 id 忽略并告警。
     *
     * @param weaponRows  RUNE_AFFIX_POOL 风格行：{@code id,min,max,weight,tier}
     * @param attrBandRows RUNE_ATTR_BAND 风格行：{@code tier,id,lo,hi}（<b>绝对贡献</b>带，单位随词条；不含权重，权重走 attrWeight）
     * @param wardBand    RUNE_WARD_BAND：6 个数 {@code t1lo,t1hi,t2lo,t2hi,t3lo,t3hi}（目标减伤 pct）
     * @param attrWeight  玩家属性域默认权重（RUNE_ATTR_WEIGHT，新版默认全 6）
     */
    public static List<Definition> merged(List<? extends String> weaponRows,
                                          List<? extends String> attrBandRows,
                                          List<? extends Double> wardBand,
                                          int attrWeight) {
        Map<String, Definition> table = new LinkedHashMap<>();
        WEAPON_DEFAULTS.forEach((id, def) -> table.put(id, copy(def)));
        int weight = Math.max(1, attrWeight);
        for (String id : ATTR_IDS) {
            if (AttributeKey.DANMAKU_REDUCE.id().equals(id)) {
                table.put(id, new Definition(id, Kind.WARD_MIT,
                        new double[]{WARD_MIT_BAND[0][0], WARD_MIT_BAND[1][0], WARD_MIT_BAND[2][0]},
                        new double[]{WARD_MIT_BAND[0][1], WARD_MIT_BAND[1][1], WARD_MIT_BAND[2][1]},
                        new int[]{weight, weight, weight}));
                continue;
            }
            double[][] band = ATTR_ABS_BAND.get(id);
            if (band == null) {
                throw new IllegalStateException("missing absolute band for attr affix: " + id);
            }
            table.put(id, new Definition(id, Kind.ATTR_ABS,
                    new double[]{band[0][0], band[1][0], band[2][0]},
                    new double[]{band[0][1], band[1][1], band[2][1]},
                    new int[]{weight, weight, weight}));
        }

        // 武器域 config 覆盖（逐 tier 覆盖带宽与权重；未知 id 忽略）
        if (weaponRows != null) {
            for (String raw : weaponRows) {
                String[] parts = raw.split(",");
                if (parts.length != 5) {
                    continue;
                }
                try {
                    String id = parts[0].trim();
                    Definition base = table.get(id);
                    if (base == null || base.kind() != Kind.WEAPON_ABS) {
                        continue; // 受控白名单：未知/非武器域 id 不进池
                    }
                    int tier = Integer.parseInt(parts[4].trim());
                    if (tier < 1 || tier > 3) {
                        continue;
                    }
                    double min = Double.parseDouble(parts[1].trim());
                    double max = Double.parseDouble(parts[2].trim());
                    int w = Integer.parseInt(parts[3].trim());
                    if (!(min < max) || !Double.isFinite(min) || !Double.isFinite(max) || w <= 0) {
                        continue;
                    }
                    base.lo()[tier - 1] = min;
                    base.hi()[tier - 1] = max;
                    base.weight()[tier - 1] = w;
                } catch (NumberFormatException ignored) {
                    // 坏行跳过（配置面容忍，加载不炸）
                }
            }
        }

        // 玩家属性域 config 覆盖（逐 tier 覆盖绝对贡献带；未知 id 忽略）
        if (attrBandRows != null) {
            for (String raw : attrBandRows) {
                String[] parts = raw.split(",");
                if (parts.length != 5) {
                    continue;
                }
                try {
                    int tier = Integer.parseInt(parts[0].trim());
                    String id = parts[1].trim();
                    Definition base = table.get(id);
                    if (tier < 1 || tier > 3 || base == null || base.kind() != Kind.ATTR_ABS
                            || AttributeKey.DANMAKU_REDUCE.id().equals(id)) {
                        continue;
                    }
                    double lo = Double.parseDouble(parts[2].trim());
                    double hi = Double.parseDouble(parts[3].trim());
                    if (!(lo < hi) || lo < 0D || !Double.isFinite(lo) || !Double.isFinite(hi)) {
                        continue;
                    }
                    base.lo()[tier - 1] = lo;
                    base.hi()[tier - 1] = hi;
                } catch (NumberFormatException ignored) {
                    // 坏行跳过
                }
            }
        }

        // 弹幕护壁 config 覆盖（目标减伤 pct，6 个数）
        if (wardBand != null && wardBand.size() >= 6) {
            Definition ward = table.get(AttributeKey.DANMAKU_REDUCE.id());
            try {
                for (int t = 0; t < 3; t++) {
                    double lo = wardBand.get(t * 2);
                    double hi = wardBand.get(t * 2 + 1);
                    if (!(lo >= 0D && lo < hi && hi <= 0.95D)) {
                        continue;
                    }
                    ward.lo()[t] = lo;
                    ward.hi()[t] = hi;
                }
            } catch (RuntimeException ignored) {
                // 坏表跳过（保持默认带）
            }
        }

        List<Definition> out = List.copyOf(table.values());
        validate(out);
        return out;
    }

    private static final List<Definition> DEFAULT_DEFS =
            merged(List.of(), List.of(), List.of(), DEFAULT_ATTR_WEIGHT);

    /** 仅内置默认（不读 config；用于纯逻辑单测与 {@link BuildNumbers#coreMaximum}）。 */
    public static List<Definition> defaults() {
        return DEFAULT_DEFS;
    }

    /** 定义表完整性校验；任何条目不合格即抛错（MUST NOT 静默少给或复制 id）。 */
    public static void validate(List<Definition> defs) {
        Map<String, Definition> seen = new LinkedHashMap<>();
        for (Definition def : defs) {
            if (def == null || def.id() == null || def.id().isBlank()) {
                throw new IllegalStateException("affix definition with blank id");
            }
            if (seen.put(def.id(), def) != null) {
                throw new IllegalStateException("duplicate affix id: " + def.id());
            }
            for (int t = 0; t < 3; t++) {
                double lo = def.lo()[t];
                double hi = def.hi()[t];
                if (!Double.isFinite(lo) || !Double.isFinite(hi) || !(lo < hi)) {
                    throw new IllegalStateException(String.format(Locale.ROOT,
                            "affix %s tier %d band invalid: [%f, %f]", def.id(), t + 1, lo, hi));
                }
                if (def.weight()[t] <= 0) {
                    throw new IllegalStateException("affix " + def.id() + " tier " + (t + 1)
                            + " weight must be positive");
                }
                if (def.kind() == Kind.WARD_MIT && (lo < 0D || hi > 0.95D)) {
                    throw new IllegalStateException("affix " + def.id() + " mitigation band out of range");
                }
                if (def.kind() == Kind.ATTR_ABS && lo < 0D) {
                    throw new IllegalStateException("affix " + def.id() + " absolute contribution must not be negative");
                }
            }
            switch (def.kind()) {
                case WEAPON_ABS -> {
                    if (!WEAPON_DEFAULTS.containsKey(def.id())) {
                        throw new IllegalStateException("weapon affix id not in controlled set: " + def.id());
                    }
                }
                case ATTR_ABS, WARD_MIT -> {
                    if (AttributeKey.byId(def.id()) == null || !ATTR_IDS.contains(def.id())) {
                        throw new IllegalStateException("attribute affix id not in controlled set: " + def.id());
                    }
                }
            }
        }
        // 完整性：受控 id 全部在场，且每条都登记了真实消费路径
        for (String id : ATTR_IDS) {
            if (!seen.containsKey(id)) {
                throw new IllegalStateException("missing controlled affix definition: " + id);
            }
            if (!CONSUMERS.containsKey(id)) {
                throw new IllegalStateException("affix " + id + " has no registered consumption path");
            }
        }
        for (String id : WEAPON_DEFAULTS.keySet()) {
            if (!seen.containsKey(id)) {
                throw new IllegalStateException("missing controlled affix definition: " + id);
            }
            if (!CONSUMERS.containsKey(id)) {
                throw new IllegalStateException("affix " + id + " has no registered consumption path");
            }
        }
    }

    /** 内置默认带的上沿（绝对值；数量级校验/模型用，不含 config 覆盖）。 */
    public static double defaultHi(String key, int coreTier) {
        Definition def = defaults().stream().filter(d -> d.id().equals(key)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown core affix key: " + key));
        int tier = clampTier(coreTier);
        return switch (def.kind()) {
            case WEAPON_ABS -> Math.max(Math.abs(def.hiOf(tier)), Math.abs(def.loOf(tier)));
            case WARD_MIT -> BuildBudgetMath.wardExponent(def.hiOf(tier));
            case ATTR_ABS -> def.hiOf(tier);
        };
    }

    /** 核阶 → 参考玩家阶（T1→1 / T2→3 / T3→5；当前仅供说明/注解使用，带均为绝对值）。 */
    public static int referencePlayerTier(int coreTier) {
        return switch (clampTier(coreTier)) {
            case 1 -> 1;
            case 2 -> 3;
            default -> 5;
        };
    }

    public static int clampTier(int coreTier) {
        return Math.min(Math.max(1, coreTier), 3);
    }

    private static Definition weapon(String id, double[] lo, double[] hi, int[] weight) {
        return new Definition(id, Kind.WEAPON_ABS, lo, hi, weight);
    }

    /** 构造 {{t1lo,t1hi},{t2lo,t2hi},{t3lo,t3hi}}。 */
    private static double[][] attr(double t1lo, double t1hi,
                                   double t2lo, double t2hi, double t3lo, double t3hi) {
        return new double[][]{{t1lo, t1hi}, {t2lo, t2hi}, {t3lo, t3hi}};
    }

    /** 交错的 id → 双层数组表。 */
    private static Map<String, double[][]> attrTable(Object... entries) {
        Map<String, double[][]> out = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            out.put((String) entries[i], (double[][]) entries[i + 1]);
        }
        return out;
    }

    private static Definition copy(Definition def) {
        return new Definition(def.id(), def.kind(),
                def.lo().clone(), def.hi().clone(), def.weight().clone());
    }

    private static Map<String, Definition> orderedMap(Definition... defs) {
        Map<String, Definition> out = new LinkedHashMap<>();
        for (Definition def : defs) {
            out.put(def.id(), def);
        }
        return out;
    }

    private CoreAffixCatalog() {
    }
}
