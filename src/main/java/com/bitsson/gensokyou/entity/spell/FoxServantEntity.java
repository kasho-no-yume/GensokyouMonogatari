package com.bitsson.gensokyou.entity.spell;

import com.bitsson.gensokyou.registry.ModDamageTypes;
import com.bitsson.gensokyou.registry.ModEntityTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

/**
 * 式神『狐の従者』：蓝紫色燃烧的狐火式神。行为（add-player-spellcards 5.2 反馈版）：
 * <pre>
 *   ORBIT    环绕玩家（平滑，非离散跳位）
 *   CHARGING 锁敌后快速飞向目标
 *   POSSESS  到达即"附身"：目标全身燃起蓝紫火焰（客户端表现），实体本体隐去
 * </pre>
 * 玩家脱离索敌半径或目标死亡 → 脱离附身：有下一个目标就继续扑，否则飞回玩家身边。
 */
public class FoxServantEntity extends AbstractSpellFieldEntity {

    private static final String TAG_RANGE = "Range";
    private static final String TAG_DAMAGE = "Damage";

    public static final int STATE_ORBIT = 0;
    public static final int STATE_CHARGING = 1;
    public static final int STATE_POSSESSING = 2;

    private static final EntityDataAccessor<Integer> DATA_STATE =
            SynchedEntityData.defineId(FoxServantEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_TARGET =
            SynchedEntityData.defineId(FoxServantEntity.class, EntityDataSerializers.INT);

    private static final double ORBIT_RADIUS = 1.3D;
    private static final double ORBIT_SPEED = 0.09D;
    private static final double HOVER = 1.4D;
    private static final double CHARGE_SPEED = 0.9D;

    private double searchRange = 8D;
    private double damage = 3D;
    private int intervalTicks = 30;
    private int damageCooldown;

    public FoxServantEntity(EntityType<? extends FoxServantEntity> type, Level level) {
        super(type, level);
    }

    public FoxServantEntity(Level level, Player host, double searchRange, int durationTicks,
                            double damage, int intervalTicks) {
        this(ModEntityTypes.FOX_SERVANT.get(), level);
        setHost(host);
        setRadius((float) searchRange);
        this.ticksLeft = durationTicks;
        this.searchRange = searchRange;
        this.damage = damage;
        this.intervalTicks = Math.max(1, intervalTicks);
        setPos(host.getX() + ORBIT_RADIUS, host.getY() + HOVER, host.getZ());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_STATE, STATE_ORBIT);
        builder.define(DATA_TARGET, 0);
    }

    public int state() {
        return entityData.get(DATA_STATE);
    }

    /** 当前附身/锁定目标的实体 id（0 = 无）。 */
    public int targetId() {
        return entityData.get(DATA_TARGET);
    }

    private void setState(int state, int targetId) {
        entityData.set(DATA_STATE, state);
        entityData.set(DATA_TARGET, targetId);
    }

    @Override
    protected void serverTick(ServerLevel level) {
        Player host = host();
        if (host == null || !host.isAlive()) {
            discard();
            return;
        }
        int state = state();
        switch (state) {
            case STATE_CHARGING -> tickCharging(level, host);
            case STATE_POSSESSING -> tickPossessing(level, host);
            default -> tickOrbit(level, host);
        }
    }

    private void tickOrbit(ServerLevel level, Player host) {
        double angle = tickCount * ORBIT_SPEED;
        setPos(host.getX() + Math.cos(angle) * ORBIT_RADIUS,
                host.getY() + HOVER + Math.sin(tickCount * 0.1D) * 0.12D,
                host.getZ() + Math.sin(angle) * ORBIT_RADIUS);
        LivingEntity target = findTarget(level, host);
        if (target != null) {
            setState(STATE_CHARGING, target.getId());
        }
    }

    private void tickCharging(ServerLevel level, Player host) {
        LivingEntity target = resolveTarget(level);
        if (target == null || !isValidTarget(host, target)) {
            setState(STATE_ORBIT, 0);
            return;
        }
        Vec3 to = target.position().add(0D, target.getBbHeight() * 0.5D, 0D);
        Vec3 delta = to.subtract(position());
        double dist = delta.length();
        if (dist <= 1.2D) {
            // 命中即附身：结算一次伤害，本体隐去到目标身上
            target.hurt(ModDamageTypes.danmaku(this, host), (float) damage);
            setPos(target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ());
            setState(STATE_POSSESSING, target.getId());
            damageCooldown = intervalTicks;
            return;
        }
        Vec3 step = delta.normalize().scale(Math.min(CHARGE_SPEED, dist));
        setPos(getX() + step.x, getY() + step.y, getZ() + step.z);
    }

    private void tickPossessing(ServerLevel level, Player host) {
        LivingEntity target = resolveTarget(level);
        if (target == null || !target.isAlive() || host.distanceToSqr(target) > searchRange * searchRange) {
            // 脱离附身：找下一个目标，否则飞回玩家
            LivingEntity next = findTargetOtherThan(level, host, target);
            if (next != null) {
                setState(STATE_CHARGING, next.getId());
            } else {
                setState(STATE_ORBIT, 0);
            }
            return;
        }
        setPos(target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ());
        // 附身期间持续灼烧：按间隔反复结算伤害
        if (damageCooldown > 0) {
            damageCooldown--;
        }
        if (damageCooldown <= 0) {
            target.hurt(ModDamageTypes.danmaku(this, host), (float) damage);
            damageCooldown = intervalTicks;
        }
    }

    private LivingEntity resolveTarget(ServerLevel level) {
        int id = targetId();
        if (id == 0 || !(level.getEntity(id) instanceof LivingEntity living)) {
            return null;
        }
        return living;
    }

    private LivingEntity findTarget(ServerLevel level, Player host) {
        return findTargetOtherThan(level, host, null);
    }

    private LivingEntity findTargetOtherThan(ServerLevel level, Player host, LivingEntity exclude) {
        AABB box = getBoundingBox().inflate(searchRange);
        List<LivingEntity> found = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != host && e != exclude && e.isAlive() && e instanceof Enemy
                        && host.distanceToSqr(e) <= searchRange * searchRange);
        found.sort(Comparator.comparingDouble(host::distanceToSqr));
        return found.isEmpty() ? null : found.get(0);
    }

    private boolean isValidTarget(Player host, LivingEntity target) {
        return target.isAlive() && host.distanceToSqr(target) <= searchRange * searchRange;
    }

    @Override
    protected void clientTick() {
        // 位置完全由客户端本地推导（不消费服务端位置包）→ 平滑无跳位。
        // 状态/目标由服务端同步决定，客户端只负责把它渲染成连续运动。
        int state = state();
        if (state == STATE_POSSESSING) {
            Entity target = level().getEntity(targetId());
            if (target != null) {
                setPos(target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ());
                emitPossessFire(target);
            }
        } else if (state == STATE_CHARGING) {
            Entity target = level().getEntity(targetId());
            if (target != null) {
                Vec3 to = target.position().add(0D, target.getBbHeight() * 0.5D, 0D);
                Vec3 delta = to.subtract(position());
                double dist = delta.length();
                if (dist > 1.0E-3D) {
                    Vec3 step = delta.normalize().scale(Math.min(CHARGE_SPEED, dist));
                    setPos(getX() + step.x, getY() + step.y, getZ() + step.z);
                }
            }
            emitSelfFire();
        } else {
            Player host = host();
            if (host != null) {
                double angle = tickCount * ORBIT_SPEED;
                setPos(host.getX() + Math.cos(angle) * ORBIT_RADIUS,
                        host.getY() + HOVER + Math.sin(tickCount * 0.1D) * 0.12D,
                        host.getZ() + Math.sin(angle) * ORBIT_RADIUS);
            }
            emitSelfFire();
        }
    }

    private void emitSelfFire() {
        level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY(), getZ(), 0D, 0.01D, 0D);
        level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 0.4D, getZ(), 0D, 0.01D, 0D);
    }

    private void emitPossessFire(Entity target) {
        for (int i = 0; i < 6; i++) {
            double px = target.getX() + (random.nextDouble() - 0.5D) * target.getBbWidth() * 1.2D;
            double py = target.getY() + random.nextDouble() * target.getBbHeight();
            double pz = target.getZ() + (random.nextDouble() - 0.5D) * target.getBbWidth() * 1.2D;
            level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, px, py, pz, 0D, 0.01D, 0D);
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putDouble(TAG_RANGE, searchRange);
        tag.putDouble(TAG_DAMAGE, damage);
        tag.putInt("State", state());
        tag.putInt("TargetId", targetId());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.searchRange = tag.getDouble(TAG_RANGE);
        this.damage = tag.getDouble(TAG_DAMAGE);
        entityData.set(DATA_STATE, tag.getInt("State"));
        entityData.set(DATA_TARGET, tag.getInt("TargetId"));
    }
}
