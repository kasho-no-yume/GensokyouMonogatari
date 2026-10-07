package com.bitsson.gensokyou.entity;

import com.bitsson.gensokyou.danmaku.DanmakuBudget;
import com.bitsson.gensokyou.danmaku.render.DanmakuMotionState;
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
    /**
     * 是否为<b>穿墙形态</b>。
     *
     * <p>默认 {@code false} = 被遮挡形态，射程裁剪到第一个方块。
     * 两种形态并存，被遮挡形态的裁剪实现全部保留 ——
     * 它不是遗留物，而是「可被掩体规避」这一类设计的实现。
     *
     * <p>本位<b>决定长度</b>，故 MUST 同时进运动指纹（见 {@link #motionParams()}）：
     * 校准通道只比位置，两端形态不一致若不进指纹就无法被检测。
     */
    private static final EntityDataAccessor<Boolean> DATA_PIERCES_BLOCKS =
            SynchedEntityData.defineId(LaserDanmaku.class, EntityDataSerializers.BOOLEAN);

    /** 客户端渲染用的长度缓存，避免每帧做一次 clip。键为年龄而非 tickCount。 */
    private double cachedLength = -1.0D;
    private int cachedLengthTick = -1;
    /** 视觉裁剪缓存。与上面的权威缓存分开，因为起点与生命周期都不同。 */
    private double renderCacheLength = -1.0D;
    private int renderCacheTick = -1;
    private Vec3 renderCacheOrigin = Vec3.ZERO;

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

    /**
     * 构造一枚<b>指定形态</b>的激光。
     *
     * <p>与默认构造器的区别只在形态位：默认是<b>被遮挡</b>（可被掩体规避）。
     * 穿墙形态 MUST 显式声明，不靠默认值隐式得到。
     */
    public LaserDanmaku(Level level, Vec3 position, Vec3 direction, float damage, int color,
                        double maxLength, double radius, double delayTime, double duration,
                        @Nullable LivingEntity owner, Set<EntityType<?>> whitelist,
                        boolean piercesBlocks) {
        this(level, position, direction, damage, color, maxLength, radius, delayTime, duration,
                owner, whitelist);
        this.setPiercesBlocks(piercesBlocks);
    }

    /** 是否为穿墙形态（{@code false} = 被遮挡，默认）。 */
    public boolean piercesBlocks() {
        return this.entityData.get(DATA_PIERCES_BLOCKS);
    }

    public void setPiercesBlocks(boolean piercesBlocks) {
        if (this.entityData.get(DATA_PIERCES_BLOCKS) != piercesBlocks) {
            this.entityData.set(DATA_PIERCES_BLOCKS, piercesBlocks);
            // 形态一变长度就变，两套缓存都必须失效 ——
            // 否则穿墙形态会沿用被遮挡形态裁出来的长度（反之亦然）。
            this.cachedLengthTick = -1;
            this.renderCacheTick = -1;
        }
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
        builder.define(DATA_PIERCES_BLOCKS, false);
    }

    @Override
    public void tick() {
        // 不调用 super.tick()：激光静止，不需要移动与命中判定
        this.baseTick();

        if (this.getPhase() == Phase.ACTIVE
                && this.level() instanceof ServerLevel
                && (this.age() - this.getDelayTicks()) % DAMAGE_INTERVAL_TICKS == 0) {
            this.damageEntitiesInBeam();
        }

        if (this.age() >= this.getDelayTicks() + this.getDurationTicks()) {
            this.discard(DanmakuBudget.RemovalCause.BEAM_ENDED);
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

    /** 当前阶段，由年龄与同步过来的时长推算，双端一致。 */
    public Phase getPhase() {
        int delay = this.getDelayTicks();
        int duration = this.getDurationTicks();
        if (this.age() < delay) {
            return Phase.DELAY;
        }
        if (this.age() < delay + duration) {
            return Phase.ACTIVE;
        }
        return Phase.DONE;
    }

    /**
     * 判伤长度。
     *
     * <p><b>被遮挡形态</b>（默认）：{@code min(最大长度, 到第一个方块的距离)}。
     * <b>穿墙形态</b>：恒为 {@link #getMaxLength()}。
     *
     * <p><b>这是权威输入</b>：判伤只在 {@code ServerLevel} 跑（见 {@link #tick}），
     * 所以本方法永远是服务端权威值。
     * 客户端的<b>视觉</b>长度必须走 {@link #getRenderLength(float)}，不能用这个——
     * 改成一个方法会同时把渲染状态喂进玩法判定。
     *
     * <p>结果按年龄缓存，渲染每帧调用也不会重复 clip。
     */
    public double getActualLength() {
        if (this.piercesBlocks()) {
            // 穿墙形态不查询世界：长度只由发射方给定的标量决定。
            return this.getMaxLength();
        }
        if (this.cachedLengthTick == this.age() && this.cachedLength >= 0.0D) {
            return this.cachedLength;
        }
        double length = clipLength(this.position());
        this.cachedLength = length;
        this.cachedLengthTick = this.age();
        return length;
    }

    /**
     * 客户端<b>视觉</b>长度：穿墙形态等于 {@link #getMaxLength()}；
     * 被遮挡形态则从渲染起点重新裁剪。
     *
     * <p>起点带纠偏量，而判伤的起点不带——两者可以相差一个偏移的上限。起点不同，
     * 裁到的方块就不同：光束末端会与地面/墙角错开一截，看起来像「光穿进了墙里」
     * 或「光够不到墙角」。被遮挡形态的视觉与权威因此 MUST 是两次裁剪。
     *
     * <p><b>逐形态不变量</b>：对同一形态，视觉长度 MUST 等于判伤长度。
     * 穿墙形态两者都恒为 {@code maxLength}（起点差异不影响长度），
     * 故该不变量自动成立。
     */
    public double getRenderLength(float partialTick) {
        if (this.piercesBlocks()) {
            return this.getMaxLength();
        }
        Vec3 origin = this.position().add(this.renderOffset(partialTick));
        if (this.renderCacheTick == this.age() && this.renderCacheOrigin.distanceToSqr(origin) < 1.0E-6D) {
            return this.renderCacheLength;
        }
        double length = clipLength(origin);
        this.renderCacheLength = length;
        this.renderCacheOrigin = origin;
        // 键为年龄而非 tickCount：重建后年龄连续，缓存不会因错位而每 tick 重算。
        this.renderCacheTick = this.age();
        return length;
    }

    private double clipLength(Vec3 start) {
        double maxLength = this.getMaxLength();
        Vec3 end = start.add(this.getLaserDirection().scale(maxLength));

        BlockHitResult hit = this.level().clip(new ClipContext(
                start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));

        return (hit.getType() == HitResult.Type.BLOCK)
                ? start.distanceTo(hit.getLocation())
                : maxLength;
    }

    /**
     * 渲染用的包围盒必须覆盖整条光束，否则实体在视锥外时整条激光会被剔除。
     *
     * <p><b>视觉包围盒与逻辑碰撞箱是分开的两件事</b>：本方法只参与视锥剔除与
     * {@code shouldRenderAtSqrDistance}，不参与任何判定。这里额外并入渲染起点，
     * 是因为起点带纠偏量——不并的话，纠偏生效时光束末端可能在视锥外被整条裁掉。
     *
     * <p>纠偏量有上限（见 {@code DanmakuCorrectionBudget}），故并入的是一个有界偏移，
     * 不会把包围盒撑成大到失去剔除意义。
     */
    @Override
    protected AABB makeBoundingBox() {
        Vec3 pos = this.position().add(this.renderOffset(0.0F));
        Vec3 dir = this.getLaserDirection();
        double length = this.getMaxLength();
        double radius = Math.max(this.getRadius(), 0.25D);
        return new AABB(pos, pos.add(dir.scale(length))).inflate(radius);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        // 长激光即使中心点很远也应该渲染；上限同时不低于客户端视距
        // （弹幕渲染上限 MUST 与视距一致，见 AbstractDanmakuProjectile）
        double range = this.getMaxLength() + 64.0D;
        double viewDist = this.renderViewDistanceBlocks();
        if (viewDist > 0.0) {
            range = Math.max(range, viewDist);
        }
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
    // 运动输入块（快照修复口径）
    // ---------------------------------------------------------------

    /**
     * 激光专属的运动输入：方向、长度、粗细、延迟与持续。
     *
     * <p>这五项<b>决定整段光束</b>，缺一项就无法从「位置对了」推出「画面对了」——
     * 起点对、方向错的光束比整体错位更难被玩家读出。
     */
    @Override
    public int[] motionParams() {
        int[] params = super.motionParams();
        Vec3 dir = this.getLaserDirection();
        int b = DanmakuMotionState.P_LASER_BASE;
        params[b] = Float.floatToRawIntBits((float) dir.x);
        params[b + 1] = Float.floatToRawIntBits((float) dir.y);
        params[b + 2] = Float.floatToRawIntBits((float) dir.z);
        params[b + 3] = Float.floatToRawIntBits((float) this.getMaxLength());
        params[b + 4] = Float.floatToRawIntBits((float) this.getRadius());
        params[b + 5] = this.getDelayTicks();
        params[b + 6] = this.getDurationTicks();
        // 形态位进指纹：它决定长度，两端不一致必须可被检测（校准只比位置）。
        params[b + 7] = this.piercesBlocks() ? 1 : 0;
        return params;
    }

    @Override
    protected void onMotionParamsApplied() {
        int b = DanmakuMotionState.P_LASER_BASE;
        int[] params = this.lastAppliedParams();
        if (params == null || params.length <= b + DanmakuMotionState.P_LASER_COUNT) {
            return;
        }
        this.setLaserDirection(new Vec3(
                Float.intBitsToFloat(params[b]),
                Float.intBitsToFloat(params[b + 1]),
                Float.intBitsToFloat(params[b + 2])));
        this.entityData.set(DATA_MAX_LENGTH, Float.intBitsToFloat(params[b + 3]));
        this.entityData.set(DATA_RADIUS, Math.max(0.05F, Float.intBitsToFloat(params[b + 4])));
        this.entityData.set(DATA_DELAY_TICKS, Math.max(0, params[b + 5]));
        this.entityData.set(DATA_DURATION_TICKS, Math.max(1, params[b + 6]));
        // 缺这一位（旧快照）⇒ 0 ⇒ 被遮挡，与默认形态一致
        this.setPiercesBlocks(params[b + 7] != 0);
        // 两套长度缓存的键都含年龄，参数一变必须失效，否则沿用旧方向的裁剪结果。
        this.cachedLengthTick = -1;
        this.renderCacheTick = -1;
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
        return Math.min(1.0F, (float) this.age() / delay);
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
        tag.putBoolean("PiercesBlocks", this.piercesBlocks());
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
        // 缺键（旧存档）⇒ 被遮挡形态，与默认一致。
        // 用 setPiercesBlocks 而非直接 set：它负责让两套长度缓存失效。
        this.setPiercesBlocks(tag.getBoolean("PiercesBlocks"));
    }
}
