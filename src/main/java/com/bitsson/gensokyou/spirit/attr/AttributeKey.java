package com.bitsson.gensokyou.spirit.attr;

import com.bitsson.gensokyou.config.GensokyouConfig;
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
            p -> GensokyouConfig.BASE_REGEN_PER_SECOND.get(), () -> -1D),
    SPIRIT_POWER("spirit_power", false, true,
            p -> ModAttachments.get(p).spiritDamage(), () -> -1D),
    HEALTH_BONUS("health_bonus", true, true,
            p -> GensokyouConfig.ATTR_BASE_HEALTH_BONUS.get(),
            () -> GensokyouConfig.ATTR_HEALTH_BONUS_CAP.get()),
    MOVE_SPEED_BONUS("move_speed_bonus", false, true,
            p -> GensokyouConfig.ATTR_BASE_MOVE_SPEED.get(),
            () -> GensokyouConfig.ATTR_MOVE_SPEED_CAP.get()),

    // ---- 防御 ----
    GRAZE_CHANCE("graze_chance", false, false,
            p -> GensokyouConfig.ATTR_BASE_GRAZE_CHANCE.get(),
            () -> GensokyouConfig.ATTR_GRAZE_CHANCE_CAP.get()),
    DANMAKU_REDUCE("danmaku_reduce", false, false,
            p -> GensokyouConfig.ATTR_BASE_DANMAKU_REDUCE.get(),
            () -> GensokyouConfig.ATTR_DANMAKU_REDUCE_CAP.get()),
    DANMAKU_RESIST("danmaku_resist", true, false,
            p -> GensokyouConfig.ATTR_BASE_DANMAKU_RESIST.get(),
            () -> GensokyouConfig.ATTR_DANMAKU_RESIST_CAP.get()),
    TENACITY("tenacity", false, false,
            p -> GensokyouConfig.ATTR_BASE_TENACITY.get(),
            () -> GensokyouConfig.ATTR_TENACITY_CAP.get()),

    // ---- 输出 ----
    CRIT_CHANCE("crit_chance", false, false,
            p -> GensokyouConfig.ATTR_BASE_CRIT_CHANCE.get(),
            () -> GensokyouConfig.ATTR_CRIT_CHANCE_CAP.get()),
    CRIT_DAMAGE("crit_damage", false, false,
            p -> GensokyouConfig.ATTR_BASE_CRIT_DAMAGE.get(),
            () -> GensokyouConfig.ATTR_CRIT_DAMAGE_CAP.get()),
    SPELL_AMP("spell_amp", false, true,
            p -> GensokyouConfig.ATTR_BASE_SPELL_AMP.get(),
            () -> GensokyouConfig.ATTR_SPELL_AMP_CAP.get()),

    // ---- 技能 / 续航 ----
    SPELL_CDR("spell_cdr", false, false,
            p -> GensokyouConfig.ATTR_BASE_SPELL_CDR.get(),
            () -> GensokyouConfig.ATTR_SPELL_CDR_CAP.get()),
    BUFF_EXTEND("buff_extend", false, false,
            p -> GensokyouConfig.ATTR_BASE_BUFF_EXTEND.get(),
            () -> GensokyouConfig.ATTR_BUFF_EXTEND_CAP.get()),
    SPIRIT_LEECH_RATE("spirit_leech_rate", false, false,
            p -> GensokyouConfig.SPIRIT_LEECH_RATE.get(),
            () -> GensokyouConfig.SPIRIT_LEECH_RATE_CAP.get());

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
