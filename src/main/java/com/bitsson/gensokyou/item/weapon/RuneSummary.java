package com.bitsson.gensokyou.item.weapon;

import com.bitsson.gensokyou.spirit.attr.AttributeKey;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 增幅核词条汇总：按 id 聚合的 Σ 值容器。
 *
 * <p>词条池从 5 条纯武器 id 扩到 19 键（15 玩家属性 + 4 武器专有）后，定长 record 不再可行，
 * 改为 id → Σ值 的注册表驱动聚合。玩家属性域的 id 直接等于 {@link AttributeKey#id()}，
 * 由 {@code PlayerAttributes} 侧消费（走 contribution 加区）；武器专有域由
 * {@link #weapon()} 暴露定长视图供 {@code WeaponFiring} 读取。
 *
 * <p>取不到的键按类型安全返回缺省值（百分比 0 / flat 0），未知 id 静默忽略。
 */
public final class RuneSummary {

    private final Map<String, Float> byId;

    private RuneSummary(Map<String, Float> byId) {
        this.byId = byId;
    }

    public static RuneSummary of(@Nullable List<RuneAffix> affixes) {
        if (affixes == null || affixes.isEmpty()) {
            return new RuneSummary(Map.of());
        }
        Map<String, Float> sums = new HashMap<>();
        for (RuneAffix affix : affixes) {
            sums.merge(affix.affixId(), affix.value(), Float::sum);
        }
        return new RuneSummary(Map.copyOf(sums));
    }

    public static RuneSummary empty() {
        return new RuneSummary(Map.of());
    }

    /** 武器专有四键的定长视图（读 config 的衰减指数）。 */
    public Weapon weapon() {
        return weapon(com.bitsson.gensokyou.config.GensokyouConfig.RUNE_RANGE_DECAY_EXP.get());
    }

    /** 注入衰减指数的视图（纯核单测用，避免单测加载 config）。 */
    public Weapon weapon(double rangeDecayExp) {
        return new Weapon(this, rangeDecayExp);
    }

    /** 原始 id → Σ 值（面板 / tooltip / 属性注入共用）。 */
    public Map<String, Float> raw() {
        return byId;
    }

    public boolean isEmpty() {
        return byId.isEmpty();
    }

    /** 词条 id 是否属于玩家属性域（可直接写 PlayerAttributes contribution）。 */
    public static boolean isPlayerAttribute(String affixId) {
        return AttributeKey.byId(affixId) != null;
    }

    // ---- 武器专有四键的便捷读取 ----

    /** 伤害乘区加成（damage_pct）。 */
    public float damagePct() {
        return get("damage_pct");
    }

    /** 攻击冷却折减（attack_rate_pct）。 */
    public float attackRatePct() {
        return get("attack_rate_pct");
    }

    /** 单次灵力消耗增减（spirit_cost_pct，负值=减耗）。 */
    public float spiritCostPct() {
        return get("spirit_cost_pct");
    }

    /** 弹道有效距离加成（range_pct）。 */
    public float rangePct() {
        return get("range_pct");
    }

    /** 任意 id 的 Σ 值；缺失返回 0。 */
    public float get(String affixId) {
        return byId.getOrDefault(affixId, 0F);
    }

    /** 玩家属性键的 Σ 值；非玩家属性键或缺失返回 null。 */
    @Nullable
    public Float attr(AttributeKey key) {
        return key == null ? null : byId.get(key.id());
    }

    /** 武器专有四键的定长快照。 */
    public record Weapon(RuneSummary source, double rangeDecayExp) {
        public float damagePct() {
            return source.damagePct();
        }

        public float attackRatePct() {
            return source.attackRatePct();
        }

        public float spiritCostPct() {
            return source.spiritCostPct();
        }

        /** 经指数衰减后的有效距离倍率 {@code (1+r)^exp}。 */
        public float rangeMultiplier() {
            float r = Math.max(0F, source.rangePct());
            return (float) Math.pow(1D + r, rangeDecayExp);
        }
    }
}
