package com.bitsson.gensokyou.spirit.attr;

import com.bitsson.gensokyou.balance.BalanceNumbers;

import com.bitsson.gensokyou.spirit.ModAttachments;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.DoubleSupplier;
import java.util.function.ToDoubleFunction;

/**
 * 玩家属性注册表（player-attribute-suite）：全部玩家属性的唯一定义处。
 * 表外键 MUST NOT 被读写；新属性 = 增键 + 接消费点，不改容器持久化结构。
 *
 * <p>finalValue = base(player) + Σ贡献（持久层+临时层），cap()&gt;=0 的键按上限封顶（{@link AttributeMath}）。
 * 暴击乘区不在容器内（发射时独立 roll，见 WeaponFiring）。
 */
public enum AttributeKey {

    // ---- 成长核心（池两键基准=池字段/阶级台账，灵力恢复基准=配置 + grace_tier_N 增量；单一事实来源在 SpiritPowerData，不双写） ----
    MAX_SPIRIT("max_spirit", true, false,
            p -> ModAttachments.get(p).max(), () -> -1D),
    SPIRIT_REGEN_RATE("spirit_regen_rate", false, true,
            p -> BalanceNumbers.BASE_REGEN_PER_SECOND, () -> -1D),
    SPIRIT_POWER("spirit_power", false, true,
            p -> ModAttachments.get(p).spiritDamage(), () -> -1D),
    HEALTH_BONUS("health_bonus", true, true,
            p -> BalanceNumbers.ATTR_BASE_HEALTH_BONUS,
            () -> BalanceNumbers.ATTR_HEALTH_BONUS_CAP),
    MOVE_SPEED_BONUS("move_speed_bonus", false, true,
            p -> BalanceNumbers.ATTR_BASE_MOVE_SPEED,
            () -> BalanceNumbers.ATTR_MOVE_SPEED_CAP),

    // ---- 防御 ----
    GRAZE_CHANCE("graze_chance", false, false,
            p -> BalanceNumbers.ATTR_BASE_GRAZE_CHANCE,
            () -> BalanceNumbers.ATTR_GRAZE_CHANCE_CAP),
    DANMAKU_REDUCE("danmaku_reduce", false, false,
            p -> BalanceNumbers.ATTR_BASE_DANMAKU_REDUCE,
            () -> -1D),
    DANMAKU_RESIST("danmaku_resist", true, false,
            p -> 0D,
            () -> -1D),
    TENACITY("tenacity", false, false,
            p -> BalanceNumbers.ATTR_BASE_TENACITY,
            () -> BalanceNumbers.ATTR_TENACITY_CAP),

    // ---- 输出 ----
    CRIT_CHANCE("crit_chance", false, false,
            p -> BalanceNumbers.ATTR_BASE_CRIT_CHANCE,
            () -> BalanceNumbers.ATTR_CRIT_CHANCE_CAP),
    CRIT_DAMAGE("crit_damage", false, false,
            p -> BalanceNumbers.ATTR_BASE_CRIT_DAMAGE,
            () -> BalanceNumbers.ATTR_CRIT_DAMAGE_CAP),
    SPELL_AMP("spell_amp", false, true,
            p -> BalanceNumbers.ATTR_BASE_SPELL_AMP,
            () -> BalanceNumbers.ATTR_SPELL_AMP_CAP),

    // ---- 技能 / 续航 ----
    SPELL_CDR("spell_cdr", false, false,
            p -> BalanceNumbers.ATTR_BASE_SPELL_CDR,
            () -> BalanceNumbers.ATTR_SPELL_CDR_CAP),
    BUFF_EXTEND("buff_extend", false, false,
            p -> BalanceNumbers.ATTR_BASE_BUFF_EXTEND,
            () -> BalanceNumbers.ATTR_BUFF_EXTEND_CAP),
    SPIRIT_LEECH_RATE("spirit_leech_rate", false, false,
            p -> BalanceNumbers.SPIRIT_LEECH_RATE,
            () -> BalanceNumbers.SPIRIT_LEECH_RATE_CAP),

    // ---- 修灵馈赠（add-cultivation-gifts，与东方弹幕战斗解耦的身体强化） ----
    JUMP("jump", true, false,
            p -> BalanceNumbers.ATTR_BASE_JUMP,
            () -> BalanceNumbers.ATTR_JUMP_CAP),
    PHYS_RESIST("phys_resist", false, false,
            p -> BalanceNumbers.ATTR_BASE_PHYS_RESIST,
            () -> BalanceNumbers.ATTR_PHYS_RESIST_CAP),
    MELEE_DAMAGE("melee_damage", true, false,
            p -> BalanceNumbers.ATTR_BASE_MELEE_DAMAGE,
            () -> BalanceNumbers.ATTR_MELEE_DAMAGE_CAP);

    private final String id;
    /** true=固定值语义（生命/抵抗），false=百分比语义。仅影响展示；容器内同为加法贡献。 */
    private final boolean flat;
    /** 临时改写层（降神变身）白名单：改写域 = 生命/移速/灵力强度/灵力恢复 + 符卡增幅（玩法已裁决）。 */
    private final boolean transformRewritable;
    private final ToDoubleFunction<ServerPlayer> baseFn;
    private final DoubleSupplier capSupplier;

    AttributeKey(String id, boolean flat, boolean transformRewritable,
                 ToDoubleFunction<ServerPlayer> baseFn, DoubleSupplier capSupplier) {
        this.id = id;
        this.flat = flat;
        this.transformRewritable = transformRewritable;
        this.baseFn = baseFn;
        this.capSupplier = capSupplier;
    }

    public String id() {
        return id;
    }

    public boolean isFlat() {
        return flat;
    }

    public boolean isTransformRewritable() {
        return transformRewritable;
    }

    public String langKey() {
        return "attribute.gensokyou." + id;
    }

    public double baseValue(ServerPlayer player) {
        return baseFn.applyAsDouble(player);
    }

    /** 硬上限；返回负值表示不限（配置侧不允许负 cap）。 */
    public double cap() {
        return capSupplier.getAsDouble();
    }

    /** 按 id 查键；未知 id 返回 null（容器写入侧据此拒绝表外键）。 */
    public static AttributeKey byId(String id) {
        for (AttributeKey key : values()) {
            if (key.id.equals(id)) {
                return key;
            }
        }
        return null;
    }
}
