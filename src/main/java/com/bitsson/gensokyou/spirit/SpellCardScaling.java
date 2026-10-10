package com.bitsson.gensokyou.spirit;

import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.spirit.attr.AttributeKey;
import com.bitsson.gensokyou.spirit.attr.PlayerAttributes;
import com.bitsson.gensokyou.spirit.grace.GraceNumbers;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * 玩家符卡缩放（player-spellcard-quality）：品质 × 灵力强度的幂律模型。
 *
 * <pre>
 *   已学形态：值 = Base × S^α_card      （S = 玩家当前灵力强度）
 *   道具形态：值 = Base × S_std(品)^α_card（固定，不读玩家属性）
 *   α_card 逐卡独立（config），落在类别带内：伤害 [0.85,1.0]、防御 [0.2,0.3]、
 *   恢复 [0.15,0.2]、控制/反制 [0.1,0.15]。
 * </pre>
 *
 * <p>纯逻辑、world-independent、可单测：所有 config 读取都有显式参数的静态重载。
 * 最终倍率经 config 钳制上下限（防配置误写）。
 */
public final class SpellCardScaling {

    /** 品阶范围。 */
    public static final int MIN_QUALITY = 1;
    public static final int MAX_QUALITY = 5;

    private SpellCardScaling() {
    }

    /**
     * 第 N 阶神恩的标准灵力强度 `S_std(N)` = Σ_{t=1..N} grace(t, spirit_power).base。
     * 派生自 grace 表（{@link GraceNumbers}），与 {@code MonsterStatBudget} 同源，MUST NOT 硬编码。
     * N≤0 返回 0；N&gt;5 不额外扩展（grace 表只有 5 阶）。
     */
    public static double standardSpiritPower(int quality) {
        return standardSpiritPower(GensokyouConfig.GRACE_TIER_TABLE.get(), quality);
    }

    /** 注入行表的 {@link #standardSpiritPower(int)}（可单测，不触 config 加载态）。 */
    public static double standardSpiritPower(List<? extends String> rows, int quality) {
        int tier = Math.max(0, quality);
        double sum = 0D;
        for (int t = 1; t <= tier; t++) {
            GraceNumbers.Entry entry = GraceNumbers.entry(rows, t, AttributeKey.SPIRIT_POWER);
            if (entry != null) {
                sum += entry.base();
            }
        }
        return sum;
    }

    /** 已学形态求值（读玩家灵力强度）。 */
    public static double learnedValue(double base, double alpha, ServerPlayer player) {
        return scaled(base, alpha, PlayerAttributes.spiritPower(player));
    }

    /** 道具形态求值（固定，品决定）。 */
    public static double itemValue(double base, double alpha, int quality) {
        return scaled(base, alpha, standardSpiritPower(quality));
    }

    /** 道具形态求值（注入表与钳制，可单测）。 */
    public static double itemValue(double base, double alpha, int quality,
                                   List<? extends String> rows, double minFactor, double maxFactor) {
        return scaled(base, alpha, standardSpiritPower(rows, quality), minFactor, maxFactor);
    }

    /** 幂律缩放 + 钳制（config 读取）。 */
    public static double scaled(double base, double alpha, double spiritPower) {
        return scaled(base, alpha, spiritPower,
                GensokyouConfig.SPELLCARD_SCALE_MIN_FACTOR.get(),
                GensokyouConfig.SPELLCARD_SCALE_MAX_FACTOR.get());
    }

    /**
     * 幂律缩放 + 显式钳制（可单测）。倍率 `S^α` 被钳入 `[minFactor, maxFactor]`；
     * 灵力强度先取下界 0（负值/缺失视为 0）。
     */
    public static double scaled(double base, double alpha, double spiritPower,
                                double minFactor, double maxFactor) {
        double s = Math.max(0D, spiritPower);
        double multiplier = Math.pow(s, Math.max(0D, alpha));
        multiplier = Math.max(minFactor, Math.min(maxFactor, multiplier));
        return base * multiplier;
    }

    /**
     * 取逐品固定表项（半径/边长/时长等固定参数）：品 1..5 → index 0..4。
     * 越界钳制到端点；空表返回 0。
     */
    public static double tableEntry(List<? extends Double> table, int quality) {
        if (table == null || table.isEmpty()) {
            return 0D;
        }
        int index = clampQuality(quality) - 1;
        index = Math.max(0, Math.min(table.size() - 1, index));
        return table.get(index);
    }

    /** 取固定表项并取整（时长 tick 等用）。 */
    public static int tableEntryInt(List<? extends Double> table, int quality) {
        return (int) Math.round(tableEntry(table, quality));
    }

    public static int clampQuality(int quality) {
        return Math.max(MIN_QUALITY, Math.min(MAX_QUALITY, quality));
    }
}
