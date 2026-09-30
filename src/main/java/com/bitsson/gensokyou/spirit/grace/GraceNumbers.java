package com.bitsson.gensokyou.spirit.grace;

import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.spirit.attr.AttributeKey;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 八百万神恩数值表（world-independent 纯逻辑，可单测）：
 * config {@code grace.graceTierTableV2} 条目 "tier,key,base,roll" 的解析与 roll。
 *
 * <p>roll 语义：{@code value = base × uniform(1−roll, 1+roll)}；表值均为"该阶级的增量"，
 * 最终属性 = 配置基准 + Σ各阶增量（池两键走台账求和，其余走 grace_tier_N 贡献组）。
 */
public final class GraceNumbers {

    /** 阶级条目 key 上限（roll 区间钳制用）。 */
    private static final double MAX_ROLL = 0.95D;

    /** 某阶某键的增量表项。 */
    public record Entry(double base, double roll) {
    }

    /** 一阶全部键的 roll 结果（池两键不在其中，见 {@link GraceRoll}）。 */
    public record TierRolls(Map<AttributeKey, Float> values) {
    }

    /** 一次完整 roll：池份额 + 其余键增量（洗练预览与进阶 apply 共用载体）。 */
    public record GraceRoll(int tier, float maxGain, float powerGain, Map<AttributeKey, Float> contributions) {
    }

    private GraceNumbers() {
    }

    /** 查某阶某键表项（config 表；配置缺项回退内置默认）；未配置返回 null。 */
    public static Entry entry(int tier, AttributeKey key) {
        return entry(effectiveRows(), tier, key);
    }

    /**
     * 运行时有效表 = config 行 + 内置默认行（config 在前，故已配项优先；缺项由默认补齐）。
     * 目的：规避 NeoForge 不合并列表新默认值的坑（新键在旧配置文件下恒 0）。
     */
    private static List<String> effectiveRows() {
        List<String> merged = new ArrayList<>(GensokyouConfig.GRACE_TIER_TABLE.get());
        merged.addAll(GensokyouConfig.graceDefaultRows());
        return merged;
    }

    /** 查某阶某键表项（注入行表，可单测）；未配置返回 null。 */
    public static Entry entry(List<? extends String> rows, int tier, AttributeKey key) {
        for (String raw : rows) {
            String[] parts = raw.split(",");
            if (parts.length != 4) {
                continue;
            }
            try {
                if (Integer.parseInt(parts[0].trim()) == tier
                        && parts[1].trim().equals(key.id())) {
                    return new Entry(Double.parseDouble(parts[2].trim()),
                            Math.min(MAX_ROLL, Math.max(0D, Double.parseDouble(parts[3].trim()))));
                }
            } catch (NumberFormatException ignored) {
                // 坏行跳过（配置面容忍，加载不炸）
            }
        }
        return null;
    }

    /** 表值 × uniform(1−roll, 1+roll)。 */
    public static float rolled(Entry entry, RandomSource random) {
        double factor = 1D + (random.nextDouble() * 2D - 1D) * entry.roll();
        return (float) Math.max(0D, entry.base() * factor);
    }

    /** 按阶级 roll 全部表内键（config 表；配置缺项回退内置默认）。 */
    public static GraceRoll rollTier(int tier, RandomSource random) {
        return rollTier(effectiveRows(), tier, random);
    }

    /** 按阶级 roll 全部表内键（注入行表，可单测）：池两键记入 gain 字段，其余进 contributions map。 */
    public static GraceRoll rollTier(List<? extends String> rows, int tier, RandomSource random) {
        Map<AttributeKey, Float> contributions = new EnumMap<>(AttributeKey.class);
        float maxGain = 0F;
        float powerGain = 0F;
        for (AttributeKey key : AttributeKey.values()) {
            Entry entry = entry(rows, tier, key);
            if (entry == null) {
                continue;
            }
            float value = rolled(entry, random);
            switch (key) {
                case MAX_SPIRIT -> maxGain = value;
                case SPIRIT_POWER -> powerGain = value;
                default -> contributions.put(key, value);
            }
        }
        return new GraceRoll(tier, maxGain, powerGain, contributions);
    }

    /** 飞行耗灵费率（config 表；每秒扣最大灵力百分比）；非法阶级返回 0。 */
    public static double flightCostPctPerSecond(int tier) {
        return flightCostPctPerSecond(GensokyouConfig.GRACE_FLIGHT_COST_PCT.get(), tier);
    }

    /** 飞行耗灵费率（注入表，可单测）。 */
    public static double flightCostPctPerSecond(List<? extends Double> table, int tier) {
        if (tier < 1 || tier > table.size()) {
            return 0D;
        }
        return Math.max(0D, table.get(tier - 1));
    }

    /** 每 tick 应扣灵力（池上限 × 费率% / 20）。 */
    public static double flightCostPerTick(int tier, float maxSpirit) {
        return flightCostPerTick(GensokyouConfig.GRACE_FLIGHT_COST_PCT.get(), tier, maxSpirit);
    }

    /** 每 tick 应扣灵力（注入表，可单测）。 */
    public static double flightCostPerTick(List<? extends Double> table, int tier, float maxSpirit) {
        return maxSpirit * flightCostPctPerSecond(table, tier) / 100D / 20D;
    }
}
