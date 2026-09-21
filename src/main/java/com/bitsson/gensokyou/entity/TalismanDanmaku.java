package com.bitsson.gensokyou.entity;

import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.registry.ModDamageTypes;
import com.bitsson.gensokyou.registry.ModEntityTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Set;

/**
 * 灵符型弹幕。
 *
 * <p>特性：扁平长方体、默认红色、可指定追踪目标。指定目标后每 tick 朝目标偏转，
 * 偏转上限 = 灵敏度(度/秒) ÷ 20。未指定目标时行为与普通弹幕一致。
 *
 * <p>目标以 entity network id 同步，客户端据此执行<b>相同</b>的偏转计算，
 * 因此不依赖位置包也能表现出平滑转弯。
 */
public class TalismanDanmaku extends AbstractDanmakuProjectile {
    private static final int DEFAULT_COLOR = 0xFF0000;

    /** 追踪目标的 network id，0 表示无目标。双端查找都可用。 */
    private static final EntityDataAccessor<Integer> DATA_TARGET_ID =
            SynchedEntityData.defineId(TalismanDanmaku.class, EntityDataSerializers.INT);

    /** 灵敏度：每秒可偏转的角度。 */
    private static final EntityDataAccessor<Float> DATA_SENSITIVITY =
            SynchedEntityData.defineId(TalismanDanmaku.class, EntityDataSerializers.FLOAT);

    /** 缓存的目标实体，避免每 tick 都做一次查表。 */
    @Nullable
    private Entity cachedTarget;
    private int cachedTargetId = 0;

    /**
     * 是否已永久丢失目标（不可逆）。瞬态字段，不持久化、不参与存档同步。
     * 双端各自按同一条件（夹角超阈值）置位，用于消除数据包延迟窗口内客户端多追的情况。
     */
    private boolean targetLost;

    public TalismanDanmaku(EntityType<? extends TalismanDanmaku> type, Level level) {
        super(type, level);
    }

    /**
     * @param color       传 0 表示使用默认红色
     * @param target      追踪目标，null 表示直线飞行
     * @param sensitivity 每秒可偏转角度
     */
    public TalismanDanmaku(Level level, LivingEntity owner, float damage, int color,
                           @Nullable Entity target, double sensitivity,
                           Set<EntityType<?>> whitelist) {
        super(ModEntityTypes.TALISMAN_DANMAKU.get(), owner, level);
        this.damage = damage;
        this.setWhitelist(whitelist);
        this.setColor(color == 0 ? DEFAULT_COLOR : color);
        this.setSensitivity(sensitivity);
        if (target != null) {
            this.setTarget(target);
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_TARGET_ID, 0);
        builder.define(DATA_SENSITIVITY, 180.0F);
    }

    @Override
    public void tick() {
        // 先偏转，再由父类推进位置，这样本 tick 的移动就用上了新方向
        this.tickHoming();
        super.tick();
    }

    /**
     * 朝目标偏转，每 tick 最多偏转 sensitivity/20 度。
     * 双端执行相同逻辑。
     *
     * <p>若速度方向与「灵符→目标」方向的夹角超过配置阈值（默认 150°，即目标被甩到身后），
     * 视为永久丢失目标：此后不再偏转、匀速直线飞行。判定双端同构；服务端在丢失时
     * 清空同步目标 id，客户端据本地判定即时停止。
     */
    private void tickHoming() {
        if (this.targetLost) {
            return;
        }
        Entity target = this.getTarget();
        if (target == null || !target.isAlive()) {
            return;
        }

        Vec3 velocity = this.getDeltaMovement();
        double speed = velocity.length();
        if (speed < 1.0E-6D) {
            return;
        }

        Vec3 currentDir = velocity.scale(1.0D / speed);
        Vec3 aimPoint = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
        Vec3 toTarget = aimPoint.subtract(this.position());
        if (toTarget.lengthSqr() < 1.0E-8D) {
            return;
        }
        Vec3 targetDir = toTarget.normalize();

        // 目标落到身后（夹角超过阈值）→ 永久丢失目标
        double lossAngle = Math.toRadians(GensokyouConfig.TALISMAN_TARGET_LOSS_ANGLE_DEG.get());
        double angle = Math.acos(Mth.clamp(currentDir.dot(targetDir), -1.0D, 1.0D));
        if (angle > lossAngle) {
            this.targetLost = true;
            if (!this.level().isClientSide) {
                // 同步目标 id 归零：新追踪客户端/重载后不再追踪
                this.setTarget(null);
            }
            return;
        }

        double maxTurn = Math.toRadians(this.getSensitivity() / 20.0D);
        Vec3 newDir = rotateTowards(currentDir, targetDir, maxTurn);

        this.setDeltaMovement(newDir.scale(speed));
        this.updateRotationFromVelocity();
    }

    /**
     * 将 from 朝 to 旋转，最多旋转 maxAngle 弧度。
     * 用球面线性插值保证角速度恒定。
     */
    private static Vec3 rotateTowards(Vec3 from, Vec3 to, double maxAngle) {
        double dot = Math.max(-1.0D, Math.min(1.0D, from.dot(to)));
        double angle = Math.acos(dot);

        // 已经足够接近，直接对齐
        if (angle <= maxAngle || angle < 1.0E-6D) {
            return to;
        }

        // 完全反向时叉积为零，需要挑一个任意垂直轴
        if (angle > Math.PI - 1.0E-6D) {
            Vec3 axis = Math.abs(from.y) < 0.9D
                    ? from.cross(new Vec3(0.0D, 1.0D, 0.0D)).normalize()
                    : from.cross(new Vec3(1.0D, 0.0D, 0.0D)).normalize();
            return rotateAroundAxis(from, axis, maxAngle);
        }

        double t = maxAngle / angle;
        double sinAngle = Math.sin(angle);
        double w1 = Math.sin((1.0D - t) * angle) / sinAngle;
        double w2 = Math.sin(t * angle) / sinAngle;
        return from.scale(w1).add(to.scale(w2)).normalize();
    }

    /** 罗德里格旋转公式。 */
    private static Vec3 rotateAroundAxis(Vec3 v, Vec3 axis, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        Vec3 parallel = axis.scale(axis.dot(v));
        Vec3 perpendicular = v.subtract(parallel);
        Vec3 w = axis.cross(perpendicular);
        return parallel.add(perpendicular.scale(cos)).add(w.scale(sin)).normalize();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (!(this.level() instanceof ServerLevel)) {
            return;
        }
        Entity hit = result.getEntity();
        if (this.isWhitelisted(hit)) {
            return;
        }
        if (hit.isAlive()) {
            hit.hurt(ModDamageTypes.danmaku(this, this.getOwner()), this.damage);
        }
        this.discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        if (this.level() instanceof ServerLevel) {
            this.discard();
        }
    }

    @Override
    public boolean hasGlowEffect() {
        return true;
    }

    // ---------------------------------------------------------------
    // 目标 / 灵敏度
    // ---------------------------------------------------------------

    /** 解析同步过来的 target id，双端可用。失败时返回 null（优雅降级为直线飞行）。 */
    @Nullable
    public Entity getTarget() {
        int id = this.entityData.get(DATA_TARGET_ID);
        if (id == 0) {
            this.cachedTarget = null;
            this.cachedTargetId = 0;
            return null;
        }
        if (this.cachedTarget != null && this.cachedTargetId == id && this.cachedTarget.isAlive()) {
            return this.cachedTarget;
        }
        Entity found = this.level().getEntity(id);
        this.cachedTarget = found;
        this.cachedTargetId = id;
        return found;
    }

    /** 中途换目标，用于编排复杂弹幕。 */
    public void setTarget(@Nullable Entity target) {
        this.entityData.set(DATA_TARGET_ID, target == null ? 0 : target.getId());
        this.cachedTarget = target;
        this.cachedTargetId = target == null ? 0 : target.getId();
    }

    /** 每秒可偏转角度。 */
    public double getSensitivity() {
        return this.entityData.get(DATA_SENSITIVITY);
    }

    public void setSensitivity(double sensitivity) {
        this.entityData.set(DATA_SENSITIVITY, (float) sensitivity);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Sensitivity", (float) this.getSensitivity());
        // target id 是运行时值，重载世界后无意义，不保存
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Sensitivity")) {
            this.setSensitivity(tag.getFloat("Sensitivity"));
        }
    }
}
