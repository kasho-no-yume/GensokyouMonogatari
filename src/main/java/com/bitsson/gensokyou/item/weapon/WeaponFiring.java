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

    private WeaponFiring() {
    }

    public static void tryFire(ServerPlayer player, ItemStack weapon) {
        if (player.getCooldowns().isOnCooldown(weapon.getItem())) {
            return;
        }
        // 凡人零灵力统一拦截（superhuman-temper）：0 阶不可使用弹幕主武器
        if (!com.bitsson.gensokyou.spirit.grace.GraceService.requireGrace(player)) {
            return;
        }
        WeaponSlots slots = WeaponSlotsHelper.read(weapon);
        if (!(slots.slot1().getItem() instanceof BulletCoreItem core)) {
            return;
        }
        CoreStats stats = core.stats();
        RuneSummary runes = RuneSummary.of(
                slots.slot3().getOrDefault(ModDataComponents.RUNE_AFFIXES.get(), List.of()));

        int cost = Math.max(0, Math.round(stats.spiritCost().getAsInt() * (1F + runes.spiritCostPct())));
        var power = ModAttachments.get(player);
        if (power.current() < cost) {
            player.displayClientMessage(
                    Component.translatable("msg.gensokyou.weapon_no_sp", cost), true);
            return;
        }
        ModAttachments.set(player, power.withCurrent(power.current() - cost));

        // 暴击：发射时服务端 roll 一次，系数烘入伤害（命中不重 roll），并随弹 NBT 持久化
        // 增幅核的暴击率/暴伤词条在此折入
        float critMult = com.bitsson.gensokyou.spirit.attr.PlayerAttributes
                .rollCrit(player, player.getRandom(), runes.critChancePct(), runes.critDamagePct());

        float finalDamage = com.bitsson.gensokyou.spirit.attr.PlayerAttributes.spiritPower(player)
                * stats.coreBaseMult().get()
                * WeaponSlotsHelper.weaponLevelMult(weapon)
                * (1F + runes.damagePct())
                * critMult;

        int rate = Math.max(1, Math.round(stats.attackRateTicks().getAsInt()
                * (1F - Math.min(0.8F, Math.max(0F, runes.attackRatePct())))));
        fire(player, core.pattern(), finalDamage, critMult);
        player.getCooldowns().addCooldown(weapon.getItem(), rate);
    }

    private static void fire(ServerPlayer player, FirePattern pattern, float damage, float critMult) {
        Level level = player.level();
        Vec3 look = player.getLookAngle();
        Set<EntityType<?>> whitelist = Set.of();

        if (pattern.isLaser()) {
            Vec3 origin = player.getEyePosition().add(look.scale(0.5D));
            LaserDanmaku laser = new LaserDanmaku(level, origin, look, damage, 0,
                    pattern.laserMaxLength().getAsDouble(),
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
        int lifetime = (int) Math.round(pattern.lifetimeSeconds().getAsDouble() * 20.0D);
        for (int i = 0; i < count; i++) {
            double offset = count <= 1 ? 0D
                    : Math.toRadians(spread * (i - (count - 1) / 2.0D) / (count - 1));
            Vec3 direction = rotateAroundY(look, offset);
            AbstractDanmakuProjectile projectile = pattern.factory().create(level, player, damage);
            projectile.setCritMult(critMult);
            projectile.setFromWeapon(true);
            aimFromEye(projectile, player);
            if (lifetime > 0) {
                projectile.setLifetimeTicks(lifetime);
            }
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
