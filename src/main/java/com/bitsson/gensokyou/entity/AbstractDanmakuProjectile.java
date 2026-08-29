package com.bitsson.gensokyou.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * 所有弹幕的抽象基类。
 *
 * <p>直接继承 {@link Projectile} 而非 ThrowableProjectile，目的是绕开原版每 tick 的
 * 0.99 空气阻力，使弹幕保持匀速（弹幕游戏的基本要求）。
 *
 * <p>双端约定：渲染与运动学所需的数据全部通过 {@link SynchedEntityData} 下发，
 * 客户端与服务端各自按相同规则推进，因此不依赖高频位置同步。
 * 伤害与命中判定仅在服务端生效。
 */
public abstract class AbstractDanmakuProjectile extends Projectile {
    /** 最大存活时间：60 秒。 */
    protected static final int MAX_LIFETIME_TICKS = 1200;

    /**
     * 位置纠偏阈值（平方）。双端运动学一致时误差极小，
     * 只有真正偏离（如客户端漏收 spawn 后的参数）才需要硬纠正。
     */
    private static final double POSITION_CORRECTION_THRESHOLD_SQR = 1.0D;

    /** 弹幕颜色（0xRRGGBB），客户端渲染需要，必须同步。 */
    private static final EntityDataAccessor<Integer> DATA_COLOR =
            SynchedEntityData.defineId(AbstractDanmakuProjectile.class, EntityDataSerializers.INT);

    /** 伤害值，仅服务端使用。 */
    protected float damage = 4.0F;

    /** 白名单：其中的实体类型不会被伤害，也不会阻挡弹幕。仅服务端使用。 */
    protected Set<EntityType<?>> whitelist = new HashSet<>();

    public AbstractDanmakuProjectile(EntityType<? extends AbstractDanmakuProjectile> type, Level level) {
        super(type, level);
        this.noPhysics = false;
    }

    public AbstractDanmakuProjectile(EntityType<? extends AbstractDanmakuProjectile> type,
                                     LivingEntity owner, Level level) {
        this(type, level);
        this.setOwner(owner);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_COLOR, 0xFFFFFF);
    }

    @Override
    public void tick() {
        super.tick();

        if (this.tickCount > MAX_LIFETIME_TICKS) {
            this.discard();
            return;
        }

        Vec3 velocity = this.getDeltaMovement();

        // 命中判定（仅服务端结算伤害，客户端也做以保证 discard 时机一致）
        HitResult hitResult = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hitResult.getType() != HitResult.Type.MISS) {
            this.onHit(hitResult);
            if (this.isRemoved()) {
                return;
            }
        }

        // 匀速前进，不施加任何阻力
        this.setPos(this.getX() + velocity.x, this.getY() + velocity.y, this.getZ() + velocity.z);
        this.updateRotationFromVelocity();
    }

    /**
     * 让朝向跟随速度方向，飞刀渲染依赖这个。
     *
     * <p>注意：不要在这里覆写 xRotO/yRotO。原版每 tick 前会调用 setOldPosAndRot()
     * 维护上一 tick 的值，渲染时靠它做插值；手动覆写会让插值失效并产生阶梯感。
     */
    protected void updateRotationFromVelocity() {
        Vec3 velocity = this.getDeltaMovement();
        if (velocity.lengthSqr() < 1.0E-7D) {
            return;
        }
        double horizontal = velocity.horizontalDistance();
        this.setYRot((float) (Mth.atan2(velocity.x, velocity.z) * (180D / Math.PI)));
        this.setXRot((float) (Mth.atan2(velocity.y, horizontal) * (180D / Math.PI)));
    }

    /**
     * 客户端与服务端跑相同的运动学，因此位置包只作为纠偏手段。
     *
     * <p>若无条件接受服务端位置，每个位置包都会把弹幕拽一下，产生可见抖动。
     * 这里只在误差超过阈值时才硬纠正，其余情况信任本地模拟，从而获得完全平滑的轨迹。
     */
    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps) {
        double errorSqr = this.position().distanceToSqr(x, y, z);
        if (errorSqr > POSITION_CORRECTION_THRESHOLD_SQR) {
            this.setPos(x, y, z);
            this.setYRot(yaw);
            this.setXRot(pitch);
        }
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return super.canHitEntity(target) && !this.isWhitelisted(target);
    }

    @Override
    protected void onHit(HitResult result) {
        if (result.getType() == HitResult.Type.ENTITY) {
            this.onHitEntity((EntityHitResult) result);
        } else if (result.getType() == HitResult.Type.BLOCK) {
            this.onHitBlock((BlockHitResult) result);
        }
    }

    @Override
    protected abstract void onHitEntity(EntityHitResult result);

    @Override
    protected abstract void onHitBlock(BlockHitResult result);

    /** 该弹幕是否需要外发光层。 */
    public abstract boolean hasGlowEffect();

    // ---------------------------------------------------------------
    // 速度 / 方向变更接口（用于编排弹幕阵列）
    // ---------------------------------------------------------------

    /** 直接设置速度向量。 */
    public void setVelocity(Vec3 velocity) {
        this.setDeltaMovement(velocity);
        this.updateRotationFromVelocity();
    }

    /** 设置方向与速率，方向会被归一化。 */
    public void setDirection(Vec3 direction, double speed) {
        this.setVelocity(direction.normalize().scale(speed));
    }

    /** 叠加一个速度增量。 */
    public void addVelocity(Vec3 deltaVelocity) {
        this.setVelocity(this.getDeltaMovement().add(deltaVelocity));
    }

    /** 当前速率。 */
    public double getSpeed() {
        return this.getDeltaMovement().length();
    }

    // ---------------------------------------------------------------
    // 白名单
    // ---------------------------------------------------------------

    /** 发射者与白名单内的实体类型不受伤害，也不阻挡弹幕。 */
    protected boolean isWhitelisted(Entity entity) {
        if (entity == null) {
            return false;
        }
        if (entity == this.getOwner()) {
            return true;
        }
        return this.whitelist.contains(entity.getType());
    }

    public void setWhitelist(Set<EntityType<?>> whitelist) {
        this.whitelist = (whitelist == null) ? new HashSet<>() : new HashSet<>(whitelist);
    }

    // ---------------------------------------------------------------
    // 访问器
    // ---------------------------------------------------------------

    public float getDamage() {
        return this.damage;
    }

    public void setDamage(float damage) {
        this.damage = damage;
    }

    public int getColor() {
        return this.entityData.get(DATA_COLOR);
    }

    public void setColor(int color) {
        this.entityData.set(DATA_COLOR, color);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Damage", this.damage);
        tag.putInt("Color", this.getColor());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.damage = tag.getFloat("Damage");
        if (tag.contains("Color")) {
            this.setColor(tag.getInt("Color"));
        }
    }
}
