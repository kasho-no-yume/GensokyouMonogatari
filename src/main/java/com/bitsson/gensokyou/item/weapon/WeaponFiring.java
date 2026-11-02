package com.bitsson.gensokyou.item.weapon;

import com.bitsson.gensokyou.entity.AbstractDanmakuProjectile;
import com.bitsson.gensokyou.entity.LaserDanmaku;
import com.bitsson.gensokyou.entity.TalismanDanmaku;
import com.bitsson.gensokyou.registry.ModDataComponents;
import com.bitsson.gensokyou.spirit.ModAttachments;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Set;

/**
 * 主武器发射结算（服务端权威）：
 * 冷却门控 → 灵力校验（不足提示无消耗）→ 公式结算 → 按 firePattern 生成弹幕 → 物品冷却。
 */
public final class WeaponFiring {

    private static final java.util.Map<ServerPlayer, HeldState> HELD = new java.util.WeakHashMap<>();

    private record HeldState(ItemStack weapon, long tick, double credit) { }

    /** Held-use tick, once per player and game tick; idle time never banks a burst. */
    public static void tickHeld(ServerPlayer player, ItemStack weapon) {
        long now = player.level().getGameTime();
        HeldState previous = HELD.get(player);
        if (previous != null && previous.tick() == now) return;
        WeaponSlots slots = WeaponSlotsHelper.read(weapon);
        if (!(slots.slot1().getItem() instanceof BulletCoreItem core)) return;
        double carry = previous != null && previous.tick() == now - 1 && previous.weapon() == weapon
                ? previous.credit() : 0D;
        var runes = RuneSummary.of(slots.slot3().getOrDefault(ModDataComponents.RUNE_AFFIXES.get(), List.of()));
        int tier = com.bitsson.gensokyou.spirit.grace.GraceService.tierOf(player);
        if (tier <= 0) return;
        double speed = Math.min(com.bitsson.gensokyou.balance.BuildNumbers.cap("attack_rate_pct", tier),
                effectiveSpeed(slots.slot3(), runes.attackRatePct()));
        var step = com.bitsson.gensokyou.balance.WeaponFrequencyMath.tick(carry,
                core.stats().attackRateTicks().getAsInt(), speed, true);
        HELD.put(player, new HeldState(weapon, now, step.credit()));
        for (int i = 0; i < step.triggers(); i++) firePermitted(player, weapon, false);
    }

    public static void stopHeld(ServerPlayer player) {
        HELD.remove(player);
    }

    private static double effectiveSpeed(ItemStack amp, float value) {
        return amp.getOrDefault(ModDataComponents.RUNE_VALUE_VERSION.get(), 0) >= 1
                ? Math.max(0D, value)
                : com.bitsson.gensokyou.balance.SpellAttributeMath.legacyIntervalReductionToSpeed(value);
    }

    private WeaponFiring() {
    }

    public static void tryFire(ServerPlayer player, ItemStack weapon) {
        if (player.getCooldowns().isOnCooldown(weapon.getItem())) {
            return;
        }
        firePermitted(player, weapon, true);
    }

    private static void firePermitted(ServerPlayer player, ItemStack weapon, boolean initial) {
        // 凡人零灵力统一拦截（superhuman-temper）：0 阶不可使用弹幕主武器
        if (!com.bitsson.gensokyou.spirit.grace.GraceService.requireGrace(player)) {
            return;
        }
        WeaponSlots slots = WeaponSlotsHelper.read(weapon);
        if (!(slots.slot1().getItem() instanceof BulletCoreItem core)) {
            return;
        }
        CoreStats stats = core.stats();
        RuneSummary.Weapon runes = RuneSummary.of(
                        slots.slot3().getOrDefault(ModDataComponents.RUNE_AFFIXES.get(), List.of()))
                .weapon();

        int tier = com.bitsson.gensokyou.spirit.grace.GraceService.tierOf(player);
        double payload = stats.coreBaseMult().get() * (core.pattern().isLaser()
                ? Math.max(1D, Math.floor(core.pattern().laserDurationSeconds().getAsDouble() * 20D
                        / CoreMath.LASER_PULSE_TICKS))
                : Math.max(1, core.pattern().count().getAsInt()));
        double baseCost = com.bitsson.gensokyou.balance.BuildNumbers.sphereCost(tier) * payload / 2D;
        double reduction = Math.min(com.bitsson.gensokyou.balance.BuildNumbers.cap("spirit_cost_pct", tier),
                Math.max(0D, -runes.spiritCostPct()));
        double cost = baseCost * (1D - reduction);
        if (!com.bitsson.gensokyou.spirit.SpiritPayments.pay(player, cost)) {
            player.displayClientMessage(
                    Component.translatable("msg.gensokyou.weapon_no_sp", cost), true);
            return;
        }

        // 暴击：发射时服务端 roll 一次，系数烘入伤害（命中不重 roll），并随弹 NBT 持久化。
        // 暴击率/暴伤已迁到玩家属性域（核的 crit_chance / crit_damage 走 contribution 加区），
        // 故此处不再有增幅核侧的额外参数。
        float critMult = com.bitsson.gensokyou.spirit.attr.PlayerAttributes
                .rollCrit(player, player.getRandom());

        float finalDamage = com.bitsson.gensokyou.spirit.attr.PlayerAttributes.spiritPower(player)
                * stats.coreBaseMult().get()
                * WeaponSlotsHelper.weaponLevelMult(weapon)
                * (1F + (float) Math.min(com.bitsson.gensokyou.balance.BuildNumbers.cap("damage_pct", tier),
                        Math.max(0F, runes.damagePct())))
                * critMult;

        fire(player, core.pattern(), finalDamage, critMult, runes.rangeMultiplier());
        // Held-use scheduling owns frequency. A short re-click gate prevents click spam
        // from bypassing the fractional schedule without suppressing permitted held shots.
        if (initial) {
            double speed = Math.min(com.bitsson.gensokyou.balance.BuildNumbers.cap("attack_rate_pct", tier),
                    effectiveSpeed(slots.slot3(), runes.attackRatePct()));
            int rate = Math.max(1, (int) Math.ceil(stats.attackRateTicks().getAsInt() / (1D + speed)));
            player.getCooldowns().addCooldown(weapon.getItem(), rate);
            HELD.put(player, new HeldState(weapon, player.level().getGameTime(), 0D));
        }
    }

    /**
     * {@code rangeMultiplier} = 弹道有效距离倍率（{@code (1+range_pct)^exp}，指数衰减）。
     * 施加于激光长度与弹幕存活时间（射程 = 弹速 × 存活时间）；符卡索敌半径刻意不参与。
     */
    private static void fire(ServerPlayer player, FirePattern pattern, float damage, float critMult,
                             float rangeMultiplier) {
        Level level = player.level();
        Vec3 look = player.getLookAngle();
        Set<EntityType<?>> whitelist = Set.of();

        if (pattern.isLaser()) {
            Vec3 origin = player.getEyePosition().add(look.scale(0.5D));
            LaserDanmaku laser = new LaserDanmaku(level, origin, look, damage, 0,
                    pattern.laserMaxLength().getAsDouble() * rangeMultiplier,
                    pattern.laserRadius().getAsDouble(),
                    pattern.laserDelaySeconds().getAsDouble(),
                    pattern.laserDurationSeconds().getAsDouble(),
                    player, whitelist);
            laser.setCritMult(critMult);
            laser.setFromWeapon(true);
            level.addFreshEntity(laser);
            return;
        }

        if (pattern.isTalisman()) {
            Entity target = DanmakuTargetPicker.pick(player);
            TalismanDanmaku talisman = new TalismanDanmaku(level, player, damage, 0, target,
                    pattern.talismanSensitivity().getAsDouble(), whitelist);
            talisman.setCritMult(critMult);
            talisman.setFromWeapon(true);
            aimFromEye(talisman, player);
            talisman.shoot(look.x, look.y, look.z,
                    (float) pattern.projectileSpeed().getAsDouble(), 0F);
            level.addFreshEntity(talisman);
            return;
        }

        int count = Math.max(1, pattern.count().getAsInt());
        double spread = pattern.spreadAngleDeg().getAsDouble();
        double speed = pattern.projectileSpeed().getAsDouble();
        // range_pct 的语义是"弹道有效距离"：对投射物即存活时间与运行距离（射程 = 弹速 × 存活 tick 数）。
        // 两个上限都乘同一个指数衰减倍率，保证"有效射程"这一可见数值的两个上限同步缩放。
        double lifetimeSeconds = pattern.lifetimeSeconds().getAsDouble() * rangeMultiplier;
        double travelDistance = pattern.travelDistance().getAsDouble() * rangeMultiplier;
        // 存活上限：未设寿命时沿用弹幕实体的全局兜底寿命（MAX_LIFETIME_TICKS = 1200 tick = 60 秒）。
        int lifetimeTicks = lifetimeSeconds > 0D
                ? (int) Math.round(lifetimeSeconds * 20.0D)
                : AbstractDanmakuProjectile.MAX_LIFETIME_TICKS;
        // 运行距离与存活时间取先到者。单位：弹速是「格 / tick」，故换算得 tick。
        int effectiveTicks = CoreMath.effectiveLifetimeTicks(lifetimeTicks, travelDistance, speed);
        for (int i = 0; i < count; i++) {
            double offset = count <= 1 ? 0D
                    : Math.toRadians(spread * (i - (count - 1) / 2.0D) / (count - 1));
            Vec3 direction = rotateAroundY(look, offset);
            AbstractDanmakuProjectile projectile = pattern.factory().create(level, player, damage);
            projectile.setCritMult(critMult);
            projectile.setFromWeapon(true);
            aimFromEye(projectile, player);
            projectile.setLifetimeTicks(effectiveTicks);
            projectile.shoot(direction.x, direction.y, direction.z, (float) speed, 0F);
            level.addFreshEntity(projectile);
        }
    }

    private static void aimFromEye(AbstractDanmakuProjectile projectile, Player player) {
        projectile.moveTo(player.getX(), player.getEyeY(), player.getZ(),
                player.getYRot(), player.getXRot());
    }

    /** 绕 Y 轴旋转（散弹扇形分布用）。 */
    public static Vec3 rotateAroundY(Vec3 vector, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return new Vec3(vector.x * cos + vector.z * sin, vector.y, -vector.x * sin + vector.z * cos);
    }
}
