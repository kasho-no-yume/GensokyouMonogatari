package com.bitsson.gensokyou.entity;

import com.bitsson.gensokyou.client.ClientEntityUuidIndex;
import com.bitsson.gensokyou.danmaku.DanmakuBudget;
import com.bitsson.gensokyou.danmaku.DanmakuTargetRef;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 灵符型弹幕。
 *
 * <p>特性：扁平长方体、默认红色、可指定追踪目标。指定目标后每 tick 朝目标偏转，
 * 偏转上限 = 灵敏度(度/秒) ÷ 20。未指定目标时行为与普通弹幕一致。
 *
 * <p>目标身份以 UUID 同步（<b>不是</b> entity network id —— 网络 id 会被复用，
 * 而目标引用可以跨越目标的整个生命周期，复用会导致静默错追），
 * 客户端据此执行<b>相同</b>的偏转计算，因此不依赖位置包也能表现出平滑转弯。
 *
 * <p>「是否已永久丢失目标」是<b>服务端的一次决策</b>，经同步位下发：
 * 客户端在收到该位之前继续按「仍持有目标」表现，不自行得出该结论
 * （见 {@code danmaku-target-state}）。
 */
public class TalismanDanmaku extends AbstractDanmakuProjectile {
    private static final int DEFAULT_COLOR = 0xFF0000;

    /** 追踪目标的 UUID，空表示无目标。双端查找都可用。 */
    private static final EntityDataAccessor<Optional<UUID>> DATA_TARGET_UUID =
            SynchedEntityData.defineId(TalismanDanmaku.class, EntityDataSerializers.OPTIONAL_UUID);

    /**
     * 是否已<b>永久</b>丢失目标（不可逆）。
     *
     * <p>只由服务端置位。它与「目标身份为空」是两个独立事实：
     * 前者对<b>所有</b>追踪者成立（跨追踪周期不可逆），
     * 后者只表达「此刻没有目标 id 可用」。两者都必须同步、且都必须入存档。
     */
    private static final EntityDataAccessor<Boolean> DATA_TARGET_LOST =
            SynchedEntityData.defineId(TalismanDanmaku.class, EntityDataSerializers.BOOLEAN);

    /** 灵敏度：每秒可偏转的角度。 */
    private static final EntityDataAccessor<Float> DATA_SENSITIVITY =
            SynchedEntityData.defineId(TalismanDanmaku.class, EntityDataSerializers.FLOAT);

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
        builder.define(DATA_TARGET_UUID, Optional.empty());
        builder.define(DATA_TARGET_LOST, false);
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
     *
     * <p><b>丢失判定只在服务端做。</b>客户端读到 {@code DATA_TARGET_LOST} 为真之前
     * 继续按「仍持有目标」转向 —— 哪怕客户端本端算出的夹角已经超过阈值。
     *
     * <p>理由是这个判定依赖两个双端天然不同的输入：目标的<b>本端</b>位置（客户端是插值后的，
     * 滞后若干 tick）与弹自身的<b>本端</b>位置。让两端各自得出结论、写入一个不同步的字段，
     * 会产生一个<b>不可证伪也不可撤销</b>的预测源：校准通道只比位置差，
     * 而「多转了 2°」要累积到肉眼可见才超容差；服务端清空目标后客户端会停止转向，
     * 但已经多转的那几帧不会回退。
     *
     * <p>代价是客户端在结论到达前会多转 1~3 帧 —— 这是网络延迟的固有成本
     * （客户端本就应该领先，见 {@code DanmakuSyncProbeTest#delayShowsUpAsAConstantPhaseDeficit}），
     * 相比「两端结论不一致且无法发现」它是可测量、可预期、随延迟收敛的量。
     *
     * <p>阈值取 {@code GensokyouConfig#TALISMAN_TARGET_LOSS_ANGLE_DEG}，
     * 不在此复述其数值（默认值随配置版本变，复述的那个数已经漂过一次）。
     */
    private void tickHoming() {
        if (this.entityData.get(DATA_TARGET_LOST)) {
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

        // 目标落到身后（夹角超过阈值）→ 永久丢失目标。
        // 整段判定（含夹角计算）都在服务端：客户端走下面的转向分支，
        // 即「尚未收到丢失状态 ⇒ 继续按持有目标表现」。
        if (!this.level().isClientSide) {
            double lossAngle = Math.toRadians(GensokyouConfig.TALISMAN_TARGET_LOSS_ANGLE_DEG.get());
            double angle = Math.acos(Mth.clamp(currentDir.dot(targetDir), -1.0D, 1.0D));
            if (angle > lossAngle) {
                this.entityData.set(DATA_TARGET_LOST, true);
                // 目标身份同时清空：让<b>之后</b>才开始追踪的玩家不再把该弹视为有目标
                this.setTarget(null);
                return;
            }
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
        this.discard(DanmakuBudget.RemovalCause.ENTITY);
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        if (this.level() instanceof ServerLevel) {
            this.discard(DanmakuBudget.RemovalCause.BLOCK);
        }
    }

    @Override
    public boolean hasGlowEffect() {
        return true;
    }

    // ---------------------------------------------------------------
    // 目标 / 灵敏度
    // ---------------------------------------------------------------

    /**
     * 解析同步过来的目标 UUID，双端可用。查不到返回 {@code null}
     * （按无目标飞）。
     *
     * <p><b>这里没有按网络 id 的回退</b>，这是刻意的：UUID 查不到就是查不到。
     * 若允许回退到「同 id 的其它实体」，目标死亡后 id 被复用时会静默转而追一个无关实体。
     *
     * <p><b>为什么不再缓存目标实例</b>：原实现缓存实体 + id 以省一次查表，
     * 但缓存正是「id 被复用」这类错追的载体 —— 缓存里那份引用没有任何机制会失效。
     * 索引查询是一次 {@code HashMap#get}（服务端是一次 {@code LevelEntityGetter#get}），
     * 省不下什么，却要付出一整个陈旧引用的类别。
     */
    @Nullable
    public Entity getTarget() {
        return DanmakuTargetRef.resolve(this.entityData.get(DATA_TARGET_UUID), this::findEntityByUuid);
    }

    /** 按 UUID 查实体：服务端有原版 API，客户端走自己的索引。 */
    @Nullable
    private Entity findEntityByUuid(UUID uuid) {
        if (this.level().isClientSide) {
            return ClientEntityUuidIndex.get(uuid);
        }
        if (this.level() instanceof ServerLevel serverLevel) {
            return serverLevel.getEntity(uuid);
        }
        return null;
    }

    /** 中途换目标，用于编排复杂弹幕。{@code null} 表示无目标。 */
    public void setTarget(@Nullable Entity target) {
        this.entityData.set(DATA_TARGET_UUID, DanmakuTargetRef.of(target));
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
        // 「已丢失」不可逆，必须跨存档边界保持：否则读档后服务端会认为这枚弹仍有目标，
        // 转向逻辑重新生效，在存档边界之后发生一次无来由的偏转。
        tag.putBoolean("TargetLost", this.entityData.get(DATA_TARGET_LOST));
        // 目标身份同样入盘：否则会出现「未丢失但目标为空」这个第三种状态——
        // 它既不是丢失、也没有目标，而没有任何需求覆盖它。
        Optional<UUID> targetUuid = this.entityData.get(DATA_TARGET_UUID);
        if (targetUuid.isPresent()) {
            tag.putUUID("TargetUUID", targetUuid.get());
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Sensitivity")) {
            this.setSensitivity(tag.getFloat("Sensitivity"));
        }
        // 与 addAdditionalSaveData 的写入条件同宽：TargetUUID 只在有目标时写，
        // 故只在存在时读。缺键（旧存档）一律退化为该字段的默认值。
        this.entityData.set(DATA_TARGET_LOST, tag.getBoolean("TargetLost"));
        this.entityData.set(DATA_TARGET_UUID,
                tag.hasUUID("TargetUUID") ? Optional.of(tag.getUUID("TargetUUID")) : Optional.empty());
    }
}
