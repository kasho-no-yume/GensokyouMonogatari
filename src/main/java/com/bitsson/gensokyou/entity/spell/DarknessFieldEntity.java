package com.bitsson.gensokyou.entity.spell;

import com.bitsson.gensokyou.entity.AbstractTouhouBoss;
import com.bitsson.gensokyou.registry.ModEntityTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * 闇符『ディマーケイション』：跟随施放者的黑暗结界。界内敌对生物（含 BOSS）失去目标并
 * 在存续期禁重新锁定；施放者隐身。主动造成伤害即终止（由 {@code LivingHurtEvent} 处理）。
 */
public class DarknessFieldEntity extends AbstractSpellFieldEntity {

    /** 本结界标记为发光的实体（到期/离开时清理，避免误伤他人发光状态）。 */
    private final java.util.Set<java.util.UUID> glowing = new java.util.HashSet<>();

    public DarknessFieldEntity(EntityType<? extends DarknessFieldEntity> type, Level level) {
        super(type, level);
    }

    public DarknessFieldEntity(Level level, Player host, float radius, int durationTicks) {
        this(ModEntityTypes.DARKNESS_FIELD.get(), level);
        setHost(host);
        setPos(host.getX(), host.getY(), host.getZ());
        setRadius(radius);
        this.ticksLeft = durationTicks;
    }

    @Override
    protected void serverTick(ServerLevel level) {
        Player host = host();
        if (host == null || !host.isAlive()) {
            discard();
            return;
        }
        setPos(host.getX(), host.getY(), host.getZ());
        host.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 30, 0, false, false, false));

        float r = radius();
        AABB box = new AABB(getX() - r, getY() - r, getZ() - r, getX() + r, getY() + r, getZ() + r);
        List<Mob> mobs = level.getEntitiesOfClass(Mob.class, box,
                m -> m.isAlive() && m.distanceToSqr(this) <= (double) r * r);
        java.util.Set<java.util.UUID> inside = new java.util.HashSet<>();
        for (Mob mob : mobs) {
            if (!(mob instanceof Enemy)) {
                continue;
            }
            inside.add(mob.getUUID());
            if (glowing.add(mob.getUUID())) {
                mob.setGlowingTag(true);
            }
            if (mob instanceof AbstractTouhouBoss boss) {
                boss.applySpellControl(5);
                continue;
            }
            if (mob.getTarget() != null) {
                mob.setTarget(null);
            }
            mob.setLastHurtByMob(null);
            mob.getNavigation().stop();
        }
        glowing.removeIf(uuid -> {
            if (inside.contains(uuid)) {
                return false;
            }
            if (level.getEntity(uuid) instanceof Mob mob) {
                mob.setGlowingTag(false);
            }
            return true;
        });
    }

    @Override
    protected void onExpire(ServerLevel level) {
        for (java.util.UUID uuid : glowing) {
            if (level.getEntity(uuid) instanceof Mob mob) {
                mob.setGlowingTag(false);
            }
        }
        glowing.clear();
    }

    /** 玩家主动造成伤害 → 立即结束其黑暗结界（由 {@code LivingIncomingDamageEvent} 调用）。 */
    public static void terminateFor(Player player) {
        for (DarknessFieldEntity field : player.level().getEntitiesOfClass(DarknessFieldEntity.class,
                player.getBoundingBox().inflate(256D),
                f -> f.hostId().isPresent() && f.hostId().get().equals(player.getUUID()))) {
            field.discard();
        }
    }

    @Override
    protected void clientTick() {
        // 大范围黑色灵气：整片环形体积铺黑烟（客户端本地，零粒子包）
        float r = radius();
        for (int i = 0; i < 14; i++) {
            double angle = random.nextDouble() * Math.PI * 2D;
            double rr = r * (0.25D + random.nextDouble() * 0.85D);
            double y = random.nextDouble() * 3.5D;
            level().addParticle(ParticleTypes.LARGE_SMOKE,
                    getX() + Math.cos(angle) * rr, getY() + y - 0.5D,
                    getZ() + Math.sin(angle) * rr,
                    (random.nextDouble() - 0.5D) * 0.02D, 0.015D, (random.nextDouble() - 0.5D) * 0.02D);
        }
        for (int i = 0; i < 8; i++) {
            double angle = random.nextDouble() * Math.PI * 2D;
            double rr = r * random.nextDouble();
            level().addParticle(ParticleTypes.SQUID_INK,
                    getX() + Math.cos(angle) * rr, getY() + random.nextDouble() * 3D,
                    getZ() + Math.sin(angle) * rr, 0D, -0.01D, 0D);
        }
    }
}
