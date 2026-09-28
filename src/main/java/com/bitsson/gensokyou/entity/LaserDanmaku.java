package com.bitsson.gensokyou.entity;

import com.bitsson.gensokyou.registry.ModDamageTypes;
import com.bitsson.gensokyou.registry.ModEntityTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

/**
 * 激光型弹幕。
 *
 * <p>两阶段：延迟期发出半透明红色指示线（同长度同方向），延迟结束后发出激光本体，
 * 持续期内每 5 tick 对射线范围内所有实体判伤一次，实体离开射线立即停止受伤。
 *
 * <p>激光自身不移动，方向在生成时固定。所有渲染参数均通过 SynchedEntityData 同步，
 * 客户端据此独立推算阶段，无需额外的状态包。
 */
public class LaserDanmaku extends AbstractDanmakuProjectile {
    private static final int DEFAULT_COLOR = 0xFF0000;

    /** 激活期判伤间隔。 */
    private static final int DAMAGE_INTERVAL_TICKS = 5;

    private static final EntityDataAccessor<Float> DATA_DIR_X =
            SynchedEntityData.defineId(LaserDanmaku.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_DIR_Y =
            SynchedEntityData.defineId(LaserDanmaku.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_DIR_Z =
            SynchedEntityData.defineId(LaserDanmaku.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_MAX_LENGTH =
            SynchedEntityData.defineId(LaserDanmaku.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_RADIUS =
            SynchedEntityData.defineId(LaserDanmaku.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_DELAY_TICKS =
            SynchedEntityData.defineId(LaserDanmaku.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_DURATION_TICKS =
            SynchedEntityData.defineId(LaserDanmaku.class, EntityDataSerializers.INT);

    /** 客户端渲染用的长度缓存，避免每帧做一次 clip。 */
    private double cachedLength = -1.0D;
    private int cachedLengthTick = -1;

    public enum Phase {
        /** 延迟期：只显示指示线，不判伤。 */
        DELAY,
        /** 激活期：显示激光本体并判伤。 */
        ACTIVE,
        /** 结束。 */
        DONE
    }

    public LaserDanmaku(EntityType<? extends LaserDanmaku> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /**
     * @param direction 激光方向，会被归一化
     * @param color     传 0 表示使用默认红色
     * @param maxLength 最大长度（格）
     * @param radius    粗细半径（格）
     * @param delayTime 延迟时间（秒）
     * @param duration  持续时间（秒）
     */
    public LaserDanmaku(Level level, Vec3 position, Vec3 direction, float damage, int color,
                        double maxLength, double radius, double delayTime, double duration,
                        @Nullable LivingEntity owner, Set<EntityType<?>> whitelist) {
        super(ModEntityTypes.LASER_DANMAKU.get(), level);
        this.setOwner(owner);
        this.noPhysics = true;

        this.setPos(position.x, position.y, position.z);
        this.damage = damage;
        this.setWhitelist(whitelist);
        this.setColor(color == 0 ? DEFAULT_COLOR : color);
        this.setLaserDirection(direction);

        this.entityData.set(DATA_MAX_LENGTH, (float) maxLength);
        this.entityData.set(DATA_RADIUS, (float) radius);
        this.entityData.set(DATA_DELAY_TICKS, (int) Math.round(delayTime * 20.0D));
        this.entityData.set(DATA_DURATION_TICKS, (int) Math.round(duration * 20.0D));

        this.setDeltaMovement(Vec3.ZERO);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_DIR_X, 0.0F);
        builder.define(DATA_DIR_Y, 0.0F);
        builder.define(DATA_DIR_Z, 1.0F);
        builder.define(DATA_MAX_LENGTH, 20.0F);
        builder.define(DATA_RADIUS, 0.3F);
        builder.define(DATA_DELAY_TICKS, 20);
        builder.define(DATA_DURATION_TICKS, 60);
    }

    @Override
    public void tick() {
        // 不调用 super.tick()：激光静止，不需要移动与命中判定
        this.baseTick();

        if (this.getPhase() == Phase.ACTIVE
                && this.level() instanceof ServerLevel
                && (this.tickCount - this.getDelayTicks()) % DAMAGE_INTERVAL_TICKS == 0) {
            this.damageEntitiesInBeam();
        }

        if (this.tickCount >= this.getDelayTicks() + this.getDurationTicks()) {
            this.discard();
        }
    }

    /** 对当前射线覆盖范围内的实体判伤，离开射线的实体不再受伤。 */
    private void damageEntitiesInBeam() {
        Vec3 start = this.position();
        Vec3 end = start.add(this.getLaserDirection().scale(this.getActualLength()));

        AABB scanBox = new AABB(start, end).inflate(this.getRadius() + 1.0D);
        // 带类型过滤的查询：激光所在的实体类型子表被整体跳过（见 DanmakuHitScan 的说明）。
        // canBeHitByProjectile 排除掉落物 / 经验球等，isWhitelisted 排除友方；getEntitiesOfClass
        // 自带 NO_SPECTATORS，故观测者不再单列。
        List<LivingEntity> candidates = this.level().getEntitiesOfClass(
                LivingEntity.class, scanBox, e -> !this.isWhitelisted(e)
                        && e.isAlive() && e.canBeHitByProjectile());

        double radius = this.getRadius();
        for (LivingEntity entity : candidates) {
            // 用实体碰撞箱做距离判定，比用中心点公平
            AABB box = entity.getBoundingBox().inflate(radius);
            if (box.clip(start, end).isPresent()) {
                entity.hurt(ModDamageTypes.danmaku(this, this.getOwner()), this.damage);
            }
        }
    }

    /** 当前阶段，由 tickCount 与同步过来的时长推算，双端一致。 */
    public Phase getPhase() {
        int delay = this.getDelayTicks();
        int duration = this.getDurationTicks();
        if (this.tickCount < delay) {
            return Phase.DELAY;
        }
        if (this.tickCount < delay + duration) {
            return Phase.ACTIVE;
        }
        return Phase.DONE;
    }

    /**
     * 实际长度 = min(最大长度, 到第一个方块的距离)。
     * 结果按 tick 缓存，渲染每帧调用也不会重复 clip。
     */
    public double getActualLength() {
        if (this.cachedLengthTick == this.tickCount && this.cachedLength >= 0.0D) {
            return this.cachedLength;
        }

        Vec3 start = this.position();
        double maxLength = this.getMaxLength();
        Vec3 end = start.add(this.getLaserDirection().scale(maxLength));

        BlockHitResult hit = this.level().clip(new ClipContext(
                start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));

        double length = (hit.getType() == HitResult.Type.BLOCK)
                ? start.distanceTo(hit.getLocation())
                : maxLength;

        this.cachedLength = length;
        this.cachedLengthTick = this.tickCount;
        return length;
    }

    /**
     * 渲染用的包围盒必须覆盖整条光束，否则实体在视锥外时整条激光会被剔除。
     */
    @Override
    protected AABB makeBoundingBox() {
        Vec3 pos = this.position();
        Vec3 dir = this.getLaserDirection();
        double length = this.getMaxLength();
        double radius = Math.max(this.getRadius(), 0.25D);
        return new AABB(pos, pos.add(dir.scale(length))).inflate(radius);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        // 长激光即使中心点很远也应该渲染
        double range = this.getMaxLength() + 64.0D;
        return distanceSqr < range * range;
    }

    // 激光静止，屏蔽速度变更接口
    @Override
    public void setVelocity(Vec3 velocity) {
        // no-op
    }

    @Override
    public void setDirection(Vec3 direction, double speed) {
        // no-op
    }

    @Override
    public void addVelocity(Vec3 deltaVelocity) {
        // no-op
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        // 激光不走投射物命中，改用射线判伤
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        // 激光不走投射物命中
    }

    @Override
    public boolean hasGlowEffect() {
        return true;
    }

    // ---------------------------------------------------------------
    // 访问器
    // ---------------------------------------------------------------

    public Vec3 getLaserDirection() {
        return new Vec3(
                this.entityData.get(DATA_DIR_X),
                this.entityData.get(DATA_DIR_Y),
                this.entityData.get(DATA_DIR_Z));
    }

    private void setLaserDirection(Vec3 direction) {
        Vec3 normalized = direction.normalize();
        this.entityData.set(DATA_DIR_X, (float) normalized.x);
        this.entityData.set(DATA_DIR_Y, (float) normalized.y);
        this.entityData.set(DATA_DIR_Z, (float) normalized.z);
    }

    public double getMaxLength() {
        return this.entityData.get(DATA_MAX_LENGTH);
    }

    public double getRadius() {
        return this.entityData.get(DATA_RADIUS);
    }

    public int getDelayTicks() {
        return this.entityData.get(DATA_DELAY_TICKS);
    }

    public int getDurationTicks() {
        return this.entityData.get(DATA_DURATION_TICKS);
    }

    /** 延迟期进度 0~1，供指示线做闪烁等表现。 */
    public float getDelayProgress() {
        int delay = this.getDelayTicks();
        if (delay <= 0) {
            return 1.0F;
        }
        return Math.min(1.0F, (float) this.tickCount / delay);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        Vec3 dir = this.getLaserDirection();
        tag.putDouble("DirX", dir.x);
        tag.putDouble("DirY", dir.y);
        tag.putDouble("DirZ", dir.z);
        tag.putFloat("MaxLength", (float) this.getMaxLength());
        tag.putFloat("Radius", (float) this.getRadius());
        tag.putInt("DelayTicks", this.getDelayTicks());
        tag.putInt("DurationTicks", this.getDurationTicks());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("DirX")) {
            this.setLaserDirection(new Vec3(
                    tag.getDouble("DirX"), tag.getDouble("DirY"), tag.getDouble("DirZ")));
        }
        if (tag.contains("MaxLength")) {
            this.entityData.set(DATA_MAX_LENGTH, tag.getFloat("MaxLength"));
        }
        if (tag.contains("Radius")) {
            this.entityData.set(DATA_RADIUS, tag.getFloat("Radius"));
        }
        if (tag.contains("DelayTicks")) {
            this.entityData.set(DATA_DELAY_TICKS, tag.getInt("DelayTicks"));
        }
        if (tag.contains("DurationTicks")) {
            this.entityData.set(DATA_DURATION_TICKS, tag.getInt("DurationTicks"));
        }
    }
}
