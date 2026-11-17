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
    private static final String TAG_ORBIT_PHASE = "OrbitPhase";

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
    /** 返航/环绕的移动速度：所有位移都是逐步趋近，绝不把坐标直接设到目标点。 */
    private static final double RETURN_SPEED = 0.75D;

    private double searchRange = 8D;
    private double damage = 3D;
    private int intervalTicks = 30;
    private int damageCooldown;
    /** 同阶多只狐火的环绕相位偏移（弧度），避免重叠。 */
    private double orbitPhase = 0D;

    public FoxServantEntity(EntityType<? extends FoxServantEntity> type, Level level) {
        super(type, level);
    }

    public FoxServantEntity(Level level, Player host, double searchRange, int durationTicks,
                            double damage, int intervalTicks) {
        this(level, host, searchRange, durationTicks, damage, intervalTicks, 0D);
    }

    public FoxServantEntity(Level level, Player host, double searchRange, int durationTicks,
                            double damage, int intervalTicks, double orbitPhase) {
        this(ModEntityTypes.FOX_SERVANT.get(), level);
        setHost(host);
        setRadius((float) searchRange);
        this.ticksLeft = durationTicks;
        this.searchRange = searchRange;
        this.damage = damage;
        this.intervalTicks = Math.max(1, intervalTicks);
        this.orbitPhase = orbitPhase;
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
        // 让服务端每 tick 都发位置包（否则隔几 tick 才发一次，客户端插值会一顿一顿）。
        this.hasImpulse = true;
        int state = state();
        switch (state) {
            case STATE_CHARGING -> tickCharging(level, host);
            case STATE_POSSESSING -> tickPossessing(level, host);
            default -> tickOrbit(level, host);
        }
    }

    /**
     * 朝宿主的环绕轨道点移动，每 tick 最多走 {@link #RETURN_SPEED} 格——
     * 从附身/冲锋状态脱离时是"飞回来"而不是瞬移回来。
     */
    private void orbitAround(Player host) {
        double angle = tickCount * ORBIT_SPEED + orbitPhase;
        double tx = host.getX() + Math.cos(angle) * ORBIT_RADIUS;
        double ty = host.getY() + HOVER + Math.sin(tickCount * 0.1D) * 0.12D;
        double tz = host.getZ() + Math.sin(angle) * ORBIT_RADIUS;
        Vec3 delta = new Vec3(tx, ty, tz).subtract(position());
        double dist = delta.length();
        if (dist <= 1.0E-4D) {
            return;
        }
        Vec3 step = delta.scale(Math.min(RETURN_SPEED, dist) / dist);
        setPos(getX() + step.x, getY() + step.y, getZ() + step.z);
    }

    private void tickOrbit(ServerLevel level, Player host) {
        orbitAround(host);
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
            // 命中即附身：结算一次伤害；蓝火由目标生物自己显示，本体不挪到目标坐标
            target.hurt(ModDamageTypes.danmaku(this, host), (float) damage);
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
            // 脱离附身：找下一个目标或飞回玩家（客户端由狐火实体状态判定，无需清理目标）
            LivingEntity next = findTargetOtherThan(level, host, target);
            if (next != null) {
                setState(STATE_CHARGING, next.getId());
            } else {
                setState(STATE_ORBIT, 0);
            }
            return;
        }
        // 附身态本体保持静止（停在命中点，既不设成目标坐标，也不偷偷飞回宿主）。
        // 蓝火由目标自己显示；脱离后本体从命中点"可见地"逐步飞回宿主，
        // 而不是在附身期间隐形飞回、脱离瞬间直接出现在宿主身边（那看起来像瞬移）。
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
        // 同一宿主的多只狐火不得锁定同一目标：收集同伴已占用的目标 id。
        java.util.Set<Integer> claimed = new java.util.HashSet<>();
        if (hostId().isPresent()) {
            java.util.UUID hostUuid = hostId().get();
            for (FoxServantEntity other : level.getEntitiesOfClass(FoxServantEntity.class,
                    host.getBoundingBox().inflate(searchRange * 2D),
                    f -> f != this && f.hostId().isPresent() && f.hostId().get().equals(hostUuid))) {
                int id = other.targetId();
                if (id != 0) {
                    claimed.add(id);
                }
            }
        }
        // 以宿主为中心搜敌：附身态本体停在命中点，可能离宿主很远，用本体包围盒会漏怪。
        AABB box = host.getBoundingBox().inflate(searchRange);
        List<LivingEntity> found = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != host && e != exclude && e.isAlive() && e instanceof Enemy
                        && !claimed.contains(e.getId())
                        && host.distanceToSqr(e) <= searchRange * searchRange);
        found.sort(Comparator.comparingDouble(host::distanceToSqr));
        return found.isEmpty() ? null : found.get(0);
    }

    private boolean isValidTarget(Player host, LivingEntity target) {
        return target.isAlive() && host.distanceToSqr(target) <= searchRange * searchRange;
    }

    @Override
    protected void clientTick() {
        // 位置完全交给服务端同步：服务端每 tick 逐步移动，原版渲染插值负责平滑。
        // 客户端绝不能本地 setPos——增量式起点与服务端不一致，再叠加位置包来回覆盖，会"抽插"。
        if (state() != STATE_POSSESSING) {
            emitSelfFire();
        }
    }

    /** 复用原版灵魂火焰粒子，团成一簇向上飘的蓝色火苗（不再用零散火星）。 */
    private void emitSelfFire() {
        for (int i = 0; i < 5; i++) {
            double dx = (random.nextDouble() - 0.5D) * 0.3D;
            double dz = (random.nextDouble() - 0.5D) * 0.3D;
            level().addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                    getX() + dx, getY() + random.nextDouble() * 0.45D, getZ() + dz,
                    0D, 0.02D + random.nextDouble() * 0.02D, 0D);
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putDouble(TAG_RANGE, searchRange);
        tag.putDouble(TAG_DAMAGE, damage);
        tag.putDouble(TAG_ORBIT_PHASE, orbitPhase);
        tag.putInt("State", state());
        tag.putInt("TargetId", targetId());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.searchRange = tag.getDouble(TAG_RANGE);
        this.damage = tag.getDouble(TAG_DAMAGE);
        this.orbitPhase = tag.getDouble(TAG_ORBIT_PHASE);
        entityData.set(DATA_STATE, tag.getInt("State"));
        entityData.set(DATA_TARGET, tag.getInt("TargetId"));
    }
}
