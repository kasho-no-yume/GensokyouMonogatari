package com.bitsson.gensokyou.item.weapon;

import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.entity.AbstractDanmakuProjectile;
import com.bitsson.gensokyou.registry.TierPalette;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.Locale;

/** 槽1 弹幕核：注册时定死 firePattern × danmakuType 与基础乘区。 */
public class BulletCoreItem extends Item {

    private final FirePattern pattern;
    private final CoreStats stats;

    public BulletCoreItem(Properties properties, FirePattern pattern, CoreStats stats) {
        super(properties);
        this.pattern = pattern;
        this.stats = stats;
    }

    public FirePattern pattern() {
        return this.pattern;
    }

    public CoreStats stats() {
        return this.stats;
    }

    /** 弹幕核名字按 requiredTier 染品阶色（与贴图定色规则同源，0 级不染）。 */
    @Override
    public Component getName(ItemStack stack) {
        return TierPalette.tintName(super.getName(stack), this.stats.requiredTier().getAsInt());
    }

    /**
     * 按 firePattern 的三种构造形态分型渲染完整属性面板。
     *
     * <p>有效射程 = 弹速 × 存活时间 —— 这一行是 {@code range_pct} 词条的作用对象，
     * 让"看不见的乘区"有文本锚点。有效 DPS 因子复用 {@link CoreMath} 的纯函数
     * （与 {@code rune-affix-pool} 预算校验同一口径），MUST NOT 在此另写公式。
     *
     * <p>局限：tooltip 拿不到父武器的其它槽位，故显示的是<b>核自身基准值</b>，
     * 不反映装配后增幅核的加成。
     */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        int tier = this.stats.requiredTier().getAsInt();
        if (tier > 1) {
            tooltip.add(Component.translatable("tooltip.gensokyou.core_requires_level", tier)
                    .withStyle(ChatFormatting.GRAY));
        }
        appendCombatLines(this, tooltip, false);
    }

    /**
     * 弹核的战斗数值行（伤害倍率 / 射速 / 耗灵 / 形态专属参数 / 有效 DPS）。
     *
     * <p>核自身 tooltip 与<b>武器</b> tooltip MUST 共用本方法，否则两处数值会各自漂移
     * （打开武器 GUI 手续繁琐，武器 tooltip 是唯一能快速确认装配结果的地方）。
     *
     * @param compact {@code true} = 紧凑模式：省略 {@code --------} 分隔线。
     *                武器 tooltip 用（阶位已单独成行，不需要视觉分组）。
     */
    public static void appendCombatLines(BulletCoreItem core, List<Component> out, boolean compact) {
        CoreStats stats = core.stats;
        FirePattern p = core.pattern;
        if (!compact) {
            out.add(sep());
        }
        out.add(Component.translatable("tooltip.gensokyou.core_damage_mult",
                "x" + fmt(stats.coreBaseMult().get().doubleValue())).withStyle(ChatFormatting.BLUE));
        out.add(Component.translatable("tooltip.gensokyou.core_cooldown",
                stats.attackRateTicks().getAsInt(),
                fmt(rate(stats.attackRateTicks().getAsInt()))).withStyle(ChatFormatting.BLUE));
        out.add(Component.translatable("tooltip.gensokyou.core_spirit_cost",
                stats.spiritCost().getAsInt()).withStyle(ChatFormatting.BLUE));

        if (p.isLaser()) {
            double duration = p.laserDurationSeconds().getAsDouble();
            int pulses = Math.max(1, (int) (duration * 20D) / CoreMath.LASER_PULSE_TICKS);
            out.add(line("tooltip.gensokyou.core_laser_length", fmt(p.laserMaxLength().getAsDouble())));
            out.add(line("tooltip.gensokyou.core_laser_radius", fmt(p.laserRadius().getAsDouble())));
            out.add(line("tooltip.gensokyou.core_laser_delay", fmt(p.laserDelaySeconds().getAsDouble())));
            out.add(line("tooltip.gensokyou.core_laser_duration", fmt(duration), pulses));
            if (!compact) {
                out.add(sep());
            }
            out.add(Component.translatable("tooltip.gensokyou.core_dps",
                    fmt(CoreMath.laserDpsFactor(p.laserMaxLength().getAsDouble(),
                            stats.attackRateTicks().getAsInt(),
                            (int) (duration * 20D)))).withStyle(ChatFormatting.GOLD));
            return;
        }
        if (p.isTalisman()) {
            out.add(line("tooltip.gensokyou.core_pick_range",
                    fmt(GensokyouConfig.WEAPON_TALISMAN_PICK_RANGE.get())));
            out.add(line("tooltip.gensokyou.core_talisman_sensitivity",
                    fmt(p.talismanSensitivity().getAsDouble())));
            out.add(line("tooltip.gensokyou.core_projectile_speed",
                    fmt(p.projectileSpeed().getAsDouble())));
            if (!compact) {
                out.add(sep());
            }
            out.add(Component.translatable("tooltip.gensokyou.core_dps",
                    fmt(CoreMath.bulletDpsFactor(stats.coreBaseMult().get().doubleValue(), 1,
                            stats.attackRateTicks().getAsInt()))).withStyle(ChatFormatting.GOLD));
            return;
        }
        int pellets = Math.max(1, p.count().getAsInt());
        double speed = p.projectileSpeed().getAsDouble();
        // lifetimeSeconds <= 0 是"不覆盖"的编码：WeaponFiring 不调 setLifetimeTicks，
        // 弹丸沿用 AbstractDanmakuProjectile.MAX_LIFETIME_TICKS。此处 MUST 回落到该上限，
        // 否则会把这些核显示成"存活 0.00s / 有效距离 0.00"（真实是 speed × 60）。
        double lifetime = p.lifetimeSeconds().getAsDouble();
        if (lifetime <= 0D) {
            lifetime = AbstractDanmakuProjectile.MAX_LIFETIME_TICKS / 20D;
        }
        out.add(line("tooltip.gensokyou.core_pellets", String.valueOf(pellets)));
        if (pellets > 1) {
            out.add(line("tooltip.gensokyou.core_spread", fmt(p.spreadAngleDeg().getAsDouble())));
        }
        out.add(line("tooltip.gensokyou.core_projectile_speed", fmt(speed)));
        out.add(line("tooltip.gensokyou.core_lifetime", fmt(lifetime)));
        out.add(line("tooltip.gensokyou.core_effective_range", fmt(speed * lifetime)));
        if (!compact) {
            out.add(sep());
        }
        out.add(Component.translatable("tooltip.gensokyou.core_dps",
                fmt(CoreMath.bulletDpsFactor(stats.coreBaseMult().get().doubleValue(), pellets,
                        stats.attackRateTicks().getAsInt()))).withStyle(ChatFormatting.GOLD));
    }

    private static double rate(int ticks) {
        return 20D / Math.max(1, ticks);
    }

    private static MutableComponent line(String key, Object... args) {
        return Component.translatable(key, args).withStyle(ChatFormatting.GRAY);
    }

    private static MutableComponent sep() {
        return Component.literal("--------").withStyle(ChatFormatting.DARK_GRAY);
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }
}
