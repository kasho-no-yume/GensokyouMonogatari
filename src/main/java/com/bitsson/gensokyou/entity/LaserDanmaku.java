package com.bitsson.gensokyou.entity;

import com.bitsson.gensokyou.danmaku.DanmakuBudget;
import com.bitsson.gensokyou.danmaku.motion.DanmakuSpeedProfile;
import com.bitsson.gensokyou.danmaku.motion.FormationFrame;
import com.bitsson.gensokyou.danmaku.motion.Rotation;
import com.bitsson.gensokyou.danmaku.render.DanmakuMotionState;
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
 * <p><b>姿态（位置 + 方向）是年龄的纯函数</b>：可以静止，也可以绕枢轴旋转与平移，
 * 全部由已同步的参数在双端各自闭式求出，零逐 tick 包。旋转的扫过角度由一个
 * <b>分段线性角速度曲线</b>的闭式积分给出（复用 {@link DanmakuSpeedProfile} 的
 * {@code travelAt}，只是把「格」重解释为「度」），因此支持「以一定角速度扫射、
 * 在 N tick 内线性减到 0、然后停住」。姿态参数复用基类既有同步字段
 * （见 {@link #configureLaserMotion}），故不新增 accessor、不改运动输入块布局。
 *
 * <p><b>隐藏态</b>复用基类 {@code DanmakuPhase}（周期/占空比/相位，年龄推导），
 * 但<b>仅作用于激活期火柱</b>：压暗渲染、跳过实体判伤；延迟期预警线不受影响
 * （预警是玩法信息，见 {@code danmaku-laser-motion} design D6）。
 *
 * <p>所有渲染参数均通过 SynchedEntityData 同步，客户端据此独立推算阶段与姿态，
 * 无需额外的状态包。
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

    // 「是否穿墙」形态位不再有自己的 accessor：它决定长度，故 MUST 进运动指纹，
    // 现由基类的 FLAG_LASER_PIERCE（DATA_FLAGS 的空闲位）承载 —— 指纹覆盖顺带成立，
    // 且不再与 P_BURST_AT 共用下标。见 danmaku-laser-motion design D10。

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

    /** 是否为穿墙形态（{@code false} = 被遮挡，默认）。由行为标志位承载。 */
    public boolean piercesBlocks() {
        return this.hasBehaviorFlag(FLAG_LASER_PIERCE);
    }

    public void setPiercesBlocks(boolean piercesBlocks) {
        if (this.piercesBlocks() != piercesBlocks) {
            this.setBehaviorFlag(FLAG_LASER_PIERCE, piercesBlocks);
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
    }

    @Override
    public void tick() {
        // 不调用 super.tick()：激光不做原版位移/扫掠，姿态由 applyLaserPose() 解析给出。
        this.baseTick();

        // 绝对寿命（服务端游戏时间）：与基类同一条存在性判据，避免冻结的激光永不销毁。
        // 激光的寿命 = 延迟 + 持续，覆写见 absoluteLifetimeTicks()。
        if (this.isExpiredByGameTime()) {
            this.discard(DanmakuBudget.RemovalCause.EXPIRED);
            return;
        }

        // 姿态先于判伤：本 tick 的位置与方向 MUST 是最新的，否则判伤用的是上一 tick 的射线。
        this.applyLaserPose();

        // 隐藏态仅免除实体判伤（延迟期预警线与方块裁剪不受影响）。
        if (this.getPhase() == Phase.ACTIVE
                && !this.isActiveHidden()
                && this.level() instanceof ServerLevel
                && (this.age() - this.getDelayTicks()) % DAMAGE_INTERVAL_TICKS == 0) {
            this.damageEntitiesInBeam();
        }

        if (this.age() >= this.getDelayTicks() + this.getDurationTicks()) {
            this.discard(DanmakuBudget.RemovalCause.BEAM_ENDED);
        }
    }

    /** 激光的绝对寿命 = 延迟 + 持续（与 {@link #tick()} 的阶段终点一致）。 */
    @Override
    protected int absoluteLifetimeTicks() {
        return this.getDelayTicks() + this.getDurationTicks();
    }

    /** 对当前射线覆盖范围内的实体判伤，离开射线的实体不再受伤。 */
    private void damageEntitiesInBeam() {
        Vec3 start = this.position();
        Vec3 end = start.add(this.getCurrentDirection().scale(this.getActualLength()));

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
        double length = clipLength(this.position(), this.getCurrentDirection());
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
     *
     * <p><b>移动激光</b>：起点与方向都取「与绘制同一时刻」的插值值（见
     * {@link #getRenderDirection}），否则光束会从上一 tick 的位置/朝向裁，
     * 末端抖动。方向逐帧变 ⇒ 绕过按年龄缓存。
     */
    public double getRenderLength(float partialTick) {
        if (this.piercesBlocks()) {
            return this.getMaxLength();
        }
        Vec3 origin = this.renderOrigin(partialTick);
        Vec3 direction = this.getRenderDirection(partialTick);
        if (!this.hasLaserMotion()
                && this.renderCacheTick == this.age()
                && this.renderCacheOrigin.distanceToSqr(origin) < 1.0E-6D) {
            return this.renderCacheLength;
        }
        double length = clipLength(origin, direction);
        this.renderCacheLength = length;
        this.renderCacheOrigin = origin;
        // 键为年龄而非 tickCount：重建后年龄连续，缓存不会因错位而每 tick 重算。
        this.renderCacheTick = this.age();
        return length;
    }

    private double clipLength(Vec3 start, Vec3 direction) {
        double maxLength = this.getMaxLength();
        Vec3 end = start.add(direction.scale(maxLength));

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
        Vec3 dir = this.getCurrentDirection();
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
        // 形态位（穿墙）不再占格：它在 DATA_FLAGS 的 FLAG_LASER_PIERCE 位，
        // 由基类写进 params[P_FLAGS]，指纹覆盖不变。
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
        // 形态位（穿墙）由 super.applyMotionParams() 从 params[P_FLAGS] 写回 DATA_FLAGS，
        // 此处无需重复读取。
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

    // ---------------------------------------------------------------
    // 姿态（旋转 + 平移）—— 年龄的纯函数
    // ---------------------------------------------------------------

    /**
     * 配置激光运动：绕枢轴旋转 + 沿方向平移。任一可缺省即只做另一种。
     *
     * <p><b>复用基类既有同步字段</b>（见 {@code danmaku-laser-motion} design D3）：
     * <ul>
     *   <li>枢轴与出生点 → 编队帧的 center / offset（{@link #bindToFrame}）；</li>
     *   <li>旋转轴 → 编队帧的 axis（yaw/pitch）；</li>
     *   <li>角速度曲线 → 速率曲线字段（{@link #configureAngularProfile}，重解释为度）；</li>
     *   <li>平移方向 → 运动轴；平移速率 → 编队帧的推进速率。</li>
     * </ul>
     * 这些字段本就全在运动指纹里，故姿态输入自动获得指纹覆盖，且随存档持久化。
     *
     * <p>MUST 在 {@code addFreshEntity} <b>之前</b>调用：枢轴与偏移是对出生点的一次快照，
     * 之后位置每 tick 由解析式改写，再调就取不到出生点了。
     *
     * @param pivotOffset      枢轴相对出生点的偏移；零向量 = 绕出生点原地旋转
     * @param rotationAxis     旋转轴（单位向量，内部归一化）；null / 零向量 = 不旋转
     * @param angularProfile   角速度曲线（度/tick；其 {@code travelAt} 即扫过角度）。
     *                         恒定角速度用 {@code DanmakuSpeedProfile.constant(ω)}；
     *                         「扫射后慢慢停」用 {@code decelerateAndHold(ω, N)}。null = 不旋转
     * @param translationDir   平移方向；null / 零向量 = 不平移
     * @param translationSpeed 平移速率（格/tick）；≤ 0 = 不平移
     */
    public void configureLaserMotion(Vec3 pivotOffset, Vec3 rotationAxis,
                                     DanmakuSpeedProfile angularProfile,
                                     Vec3 translationDir, double translationSpeed) {
        Vec3 origin = this.position();
        Vec3 pivot = origin.add(pivotOffset == null ? Vec3.ZERO : pivotOffset);
        Vec3 offset = origin.subtract(pivot);
        boolean rotates = rotationAxis != null && rotationAxis.lengthSqr() > 1.0E-9D
                && angularProfile != null;
        Vec3 axis = rotates ? rotationAxis.normalize() : new Vec3(0.0D, 1.0D, 0.0D);
        double[] yawPitch = Rotation.anglesFromAxis(axis);
        // 编队帧：center = 枢轴，offset = 出生点 − 枢轴，axis = 旋转轴，不自转/呼吸/公转。
        // 本激光只借用它的 center/offset/axis 三个分量，不调用 framePositionAt。
        FormationFrame frame = new FormationFrame(
                pivot.x, pivot.y, pivot.z, offset.x, offset.y, offset.z,
                yawPitch[0], yawPitch[1], 0.0D, 1.0D, 0.0D, 0.0D);
        this.bindToFrame(frame);
        if (rotates) {
            this.configureAngularProfile(angularProfile);
        }
        if (translationDir != null && translationDir.lengthSqr() > 1.0E-9D
                && translationSpeed > 0.0D) {
            this.setMotionAxis(translationDir);
            this.configureAdvanceSpeed(translationSpeed);
        }
    }

    /** 是否配置了激光运动（旋转或平移）。未配置 ⇒ 静止，保持生成姿态。 */
    public boolean hasLaserMotion() {
        return this.hasFormationFrame();
    }

    /** 本 tick 是否处于<b>激活期</b>的隐藏态（延迟期不隐藏，见类注释）。 */
    public boolean isActiveHidden() {
        return this.getPhase() == Phase.ACTIVE && this.isHidden();
    }

    /** 扫过的角度（度）—— 角速度曲线的闭式积分；未挂曲线为 0。 */
    private double sweptDegrees(int age) {
        return this.hasSpeedProfile() ? this.speedProfile().travelAt(age) : 0.0D;
    }

    /** 旋转轴（由编队帧的 yaw/pitch 还原）。 */
    private Vec3 rotationAxis() {
        FormationFrame frame = this.formationFrame();
        return Rotation.axisFromAngles(frame.axisYawDeg(), frame.axisPitchDeg());
    }

    /**
     * 第 {@code age} tick 的方向 = 基准方向绕旋转轴扫过 {@code θ(age)}。
     *
     * <p>基准方向存于 {@link #getLaserDirection()}（它是<b>输入</b>，进运动指纹）；
     * 本方法是<b>输出</b>。二者在静止激光上相等。
     */
    private Vec3 directionAt(int age) {
        if (!this.hasLaserMotion()) {
            return this.getLaserDirection();
        }
        double theta = this.sweptDegrees(age);
        if (theta == 0.0D) {
            return this.getLaserDirection();
        }
        Vec3 rotated = Rotation.about(this.getLaserDirection(), this.rotationAxis(),
                Math.toRadians(theta));
        return rotated.lengthSqr() < 1.0E-12D ? this.getLaserDirection() : rotated.normalize();
    }

    /** 当前（本 tick）方向——判伤与包围盒用它：权威、整数年龄。 */
    public Vec3 getCurrentDirection() {
        return this.directionAt(this.age());
    }

    /**
     * <b>渲染</b>方向：与位置插值取<b>同一时刻</b>。
     *
     * <p>原版按 {@code Mth.lerp(partialTick, xOld, x)} 把位置从上一 tick 插到本 tick，
     * 若方向仍取整数年龄的 {@link #getCurrentDirection()}，就会「位置平滑、朝向每 tick 跳一下」
     * ——旋转激光看起来一顿一顿的（20 tick/s 的角步）。本方法在 {@code age−1} 与 {@code age}
     * 两个方向之间按 {@code partialTick} 插值，使朝向与位置同步。
     */
    public Vec3 getRenderDirection(float partialTick) {
        if (!this.hasLaserMotion()) {
            return this.getLaserDirection();
        }
        float p = Mth.clamp(partialTick, 0.0F, 1.0F);
        Vec3 previous = this.directionAt(this.age() - 1);
        Vec3 current = this.directionAt(this.age());
        Vec3 lerped = previous.lerp(current, p);
        return lerped.lengthSqr() < 1.0E-12D ? current : lerped.normalize();
    }

    /**
     * <b>渲染</b>起点：原版的世界位置插值（{@code xOld → x}）加纠偏量。
     *
     * <p>与 {@code AbstractDanmakuRenderer#renderPosition} 同一口径——被遮挡形态的视觉裁剪
     * MUST 从「画出来的那条光束」的起点裁，否则移动激光的末端会对不上。
     */
    private Vec3 renderOrigin(float partialTick) {
        return new Vec3(
                Mth.lerp(partialTick, this.xOld, this.getX()),
                Mth.lerp(partialTick, this.yOld, this.getY()),
                Mth.lerp(partialTick, this.zOld, this.getZ()))
                .add(this.renderOffset(partialTick));
    }

    /** 第 {@code age} tick 的出生点位置（旋转 + 平移的闭式解）。 */
    private Vec3 laserOriginAt(int age) {
        FormationFrame frame = this.formationFrame();
        Vec3 rotatedOffset = Rotation.about(frame.offset(), this.rotationAxis(),
                Math.toRadians(this.sweptDegrees(age)));
        Vec3 advance = this.motionAxis().scale(this.advanceSpeed() * age);
        return frame.center().add(rotatedOffset).add(advance);
    }

    /**
     * 把本 tick 的解析姿态落到实体上。
     *
     * <p>位置每 tick 被<b>重置</b>为解析值，误差不累积（与编队帧同一纪律）。
     * 速度写成「本 tick 与上一 tick 的解析位置之差」，使位置纠偏的容差按真实速度取值。
     * 方向<b>不写回</b> {@code DATA_DIR_*}——那是基准方向（输入），当前方向由
     * {@link #getCurrentDirection()} 现算。
     */
    private void applyLaserPose() {
        if (!this.hasLaserMotion()) {
            return;
        }
        int age = this.age();
        Vec3 origin = this.laserOriginAt(age);
        Vec3 previous = this.laserOriginAt(Math.max(0, age - 1));
        this.setDeltaMovement(origin.subtract(previous));
        this.setPos(origin.x, origin.y, origin.z);
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
