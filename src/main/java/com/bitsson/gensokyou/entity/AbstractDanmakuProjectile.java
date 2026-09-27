package com.bitsson.gensokyou.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
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
 *
 * <p><b>四种弹幕的生成示例：</b>
 *
 * <pre>{@code
 * // 球型（0 = 随机色，服务端取色后同步）
 * SphereDanmaku sphere = new SphereDanmaku(level, owner, 4.0F, 0, 0.4F, DanmakuWhitelists.FAIRY);
 * sphere.shoot(dir.x, dir.y, dir.z, 1.0F, 0F);
 *
 * // 飞刀（穿透实体，每个目标只判伤一次，无发光）
 * KnifeDanmaku knife = new KnifeDanmaku(level, owner, 3.0F, DanmakuWhitelists.FAIRY);
 * knife.shoot(dir.x, dir.y, dir.z, 1.5F, 0F);
 *
 * // 灵符（追踪目标；灵敏度 = 每秒可偏转角度）
 * TalismanDanmaku talisman = new TalismanDanmaku(level, owner, 4.0F, 0xFF0000, target, 90.0, whitelist);
 * talisman.shoot(dir.x, dir.y, dir.z, 0.8F, 0F);
 *
 * // 激光（延迟 1 秒警告线，持续 3 秒，激活期每 5 tick 判伤）
 * LaserDanmaku laser = new LaserDanmaku(level, origin, dir, 2.0F, 0x00FFFF,
 *         20.0, 0.25, 1.0, 3.0, owner, whitelist);
 * level.addFreshEntity(laser);
 * }</pre>
 *
 * <p>飞行途中改向（弹幕阵列编排）示例：
 * <pre>{@code bullet.setVelocity(newDirection.scale(newSpeed)); }</pre>
 * 注意用 {@code setVelocity} 而非 {@code setDeltaMovement}：前者会把速度变更同步给客户端。
 *
 * <p><b>四种扩展行为</b>（{@code add-remnant-touhou-bosses}）：曲射、分裂、悬停、溜め。
 * 它们以<b>行为开关</b>形态落在本基类上而非各自新增弹幕实体类型——新实体类型要付注册表项、
 * {@code EntityType}、渲染器、model JSON、lang 键、生成管线六份成本，四种行为乘不起。
 * 全部参数走 {@link SynchedEntityData} 下发，两端按同一规则推进运动学。
 *
 * <pre>{@code
 * // 悬停：径向喷出后定住 20 tick（构成"网"的静止节点，仍可造成伤害）
 * bullet.configureHover(20);
 *
 * // 分裂：到达 30 tick 散成 5 发
 * bullet.configureSplit(30, 5);
 *
 * // 溜め：完全静止，玩家进入 1.5 格内触发
 * bullet.configureMine(1.5D);
 *
 * // 曲射：绕水平轴 90°/s 偏转
 * bullet.configureCurve(new Vec3(0, 1, 0), 90.0D);
 * }</pre>
 */
public abstract class AbstractDanmakuProjectile extends Projectile {
    /** 最大存活时间：60 秒。 */
    /**
     * 弹丸存活上限（tick）。{@code FirePattern.lifetimeSeconds <= 0} 表示"不覆盖"，
     * 此时沿用本值。公开它是为了让 tooltip 能报出<b>真实</b>存活与有效距离
     * （否则不覆盖的核会显示成 0.00s / 0.00 格）。
     */
    public static final int MAX_LIFETIME_TICKS = 1200;

    /** 溜め弹的待命 tick：出生后这段时间内不触发，给玩家读"哪里埋了东西"的机会。 */
    public static final int MINE_ARM_TICKS = 20;

    /** 悬停/溜め弹的接触判定外扩（格）。定住后 moveVector 追踪失效，改用 AABB 相交。 */
    private static final double STATIONARY_HIT_INFLATE = 0.35D;

    /** 存活时间，发射方可按核覆写（散弹等短射程行为）。 */
    private int lifetimeTicks = MAX_LIFETIME_TICKS;

    /**
     * 位置纠偏阈值（平方）。双端运动学一致时误差极小，
     * 只有真正偏离（如客户端漏收 spawn 后的参数）才需要硬纠正。
     */
    private static final double POSITION_CORRECTION_THRESHOLD_SQR = 1.0D;

    /** 弹幕颜色（0xRRGGBB），客户端渲染需要，必须同步。 */
    private static final EntityDataAccessor<Integer> DATA_COLOR =
            SynchedEntityData.defineId(AbstractDanmakuProjectile.class, EntityDataSerializers.INT);

    /**
     * 阵营标记（预留）：0 = 中性/未分类。
     *
     * <p>为后续「友军不误伤 / 敌我辨识」能力预留的同步字段。当前<b>不写入、不读取</b>，
     * 不影响渲染与判伤，恒为默认值；后续能力在此挂载服务端赋值与客户端消费。
     */
    private static final EntityDataAccessor<Integer> DATA_FACTION =
            SynchedEntityData.defineId(AbstractDanmakuProjectile.class, EntityDataSerializers.INT);

    // ------------------------------------------------------------------
    // 扩展行为：曲射 / 分裂 / 悬停 / 溜め
    // ------------------------------------------------------------------

    /** 行为开关位。0 = 全关（默认）。 */
    private static final EntityDataAccessor<Byte> DATA_FLAGS =
            SynchedEntityData.defineId(AbstractDanmakuProjectile.class, EntityDataSerializers.BYTE);
    /** 悬停 tick：到达该 tick 速度归零。0 = 不悬停。 */
    private static final EntityDataAccessor<Integer> DATA_HOVER_TICK =
            SynchedEntityData.defineId(AbstractDanmakuProjectile.class, EntityDataSerializers.INT);
    /** 分裂 tick：到达该 tick 散成 N 发并消失。-1 = 不分裂。 */
    private static final EntityDataAccessor<Integer> DATA_SPLIT_TICK =
            SynchedEntityData.defineId(AbstractDanmakuProjectile.class, EntityDataSerializers.INT);
    /** 分裂发数。0 = 不分裂。 */
    private static final EntityDataAccessor<Integer> DATA_SPLIT_COUNT =
            SynchedEntityData.defineId(AbstractDanmakuProjectile.class, EntityDataSerializers.INT);
    /** 曲射轴：两个 16 位无符号半字打包 (yawDeg, pitchDeg)，各 0..360。 */
    private static final EntityDataAccessor<Integer> DATA_CURVE_AXIS =
            SynchedEntityData.defineId(AbstractDanmakuProjectile.class, EntityDataSerializers.INT);
    /** 曲射角速度（度/秒，带符号 = 旋向）。0 = 不曲射。 */
    private static final EntityDataAccessor<Float> DATA_CURVE_RATE =
            SynchedEntityData.defineId(AbstractDanmakuProjectile.class, EntityDataSerializers.FLOAT);
    /** 溜め触发半径（格）。0 = 不是溜め。 */
    private static final EntityDataAccessor<Float> DATA_MINE_RADIUS =
            SynchedEntityData.defineId(AbstractDanmakuProjectile.class, EntityDataSerializers.FLOAT);

    public static final int FLAG_CURVE = 1;
    public static final int FLAG_SPLIT = 2;
    public static final int FLAG_HOVER = 4;
    public static final int FLAG_MINE = 8;

    /**
     * 一次性触发守卫。刻意用<b>普通字段</b>而非同步字段：两端 tick 次数相同，
     * 故由 {@code tickCount} 派生的守卫值天然一致。分裂/悬停的幂等性由此保证。
     */
    private boolean splitFired = false;

    /** 伤害值，仅服务端使用。 */
    protected float damage = 4.0F;

    /**
     * 发射时定值的暴击系数（player-attribute-suite：命中不 re-roll，随弹 NBT 持久化）。
     * 伤害已含该系数，字段作审计/未来按系数触发的特效消费。
     */
    private float critMult = 1.0F;

    /** 是否主武器发射（灵力汲取只对武器弹生效，符卡等其他来源除外）。 */
    private boolean fromWeapon = false;

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
        builder.define(DATA_FACTION, 0);
        builder.define(DATA_FLAGS, (byte) 0);
        builder.define(DATA_HOVER_TICK, 0);
        builder.define(DATA_SPLIT_TICK, -1);
        builder.define(DATA_SPLIT_COUNT, 0);
        builder.define(DATA_CURVE_AXIS, 0);
        builder.define(DATA_CURVE_RATE, 0.0F);
        builder.define(DATA_MINE_RADIUS, 0.0F);
    }

    @Override
    public void tick() {
        super.tick();

        if (this.tickCount > this.lifetimeTicks) {
            this.discard();
            return;
        }

        // 溜め：完全静止。它<b>不</b>做接触判伤（那是溜め弹的语义：埋着，等人踩），
        // 故直接早退，两端的位移均为零，行为天然一致。
        if (isMine()) {
            this.setDeltaMovement(Vec3.ZERO);
            this.updateRotationFromVelocity();
            this.tickMine();
            return;
        }

        // 悬停：到达 tick 后定住。定住后 moveVector 追踪会失效（零向量 = MISS），
        // 故必须改走 AABB 相交，否则「网」的静止节点会变成打不到人的摆设。
        boolean stationary = false;
        if (isHovering() && this.tickCount >= hoverTick()) {
            this.setDeltaMovement(Vec3.ZERO);
            stationary = true;
        }

        if (!this.splitFired && isSplitting() && this.tickCount >= splitTick()) {
            this.splitFired = true;
            this.fireSplit();
            return;
        }

        Vec3 velocity = this.getDeltaMovement();

        // 曲射：绕指定轴旋转速度矢量。用 setDeltaMovement 而非 setVelocity——
        // 后者每 tick 都会发运动包（见 setVelocity 的文档）。
        if (isCurving()) {
            velocity = rotateAbout(velocity, curveAxis(), curveRate() / 20.0D);
            this.setDeltaMovement(velocity);
        }

        if (stationary) {
            this.checkStationaryEntityHit();
            return;
        }

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

    // ------------------------------------------------------------------
    // 扩展行为实现
    // ------------------------------------------------------------------

    private int flags() {
        return this.entityData.get(DATA_FLAGS) & 0xFF;
    }

    private void addFlag(int flag) {
        this.entityData.set(DATA_FLAGS, (byte) (flags() | flag));
    }

    public boolean isCurving() {
        return (flags() & FLAG_CURVE) != 0 && Math.abs(curveRate()) > 1.0E-4F;
    }

    public boolean isSplitting() {
        return (flags() & FLAG_SPLIT) != 0 && splitCount() > 0;
    }

    public boolean isHovering() {
        return (flags() & FLAG_HOVER) != 0 && hoverTick() > 0;
    }

    public boolean isMine() {
        return (flags() & FLAG_MINE) != 0 && mineRadius() > 0.0F;
    }

    public int hoverTick() {
        return this.entityData.get(DATA_HOVER_TICK);
    }

    public int splitTick() {
        return this.entityData.get(DATA_SPLIT_TICK);
    }

    public int splitCount() {
        return this.entityData.get(DATA_SPLIT_COUNT);
    }

    public float curveRate() {
        return this.entityData.get(DATA_CURVE_RATE);
    }

    public float mineRadius() {
        return this.entityData.get(DATA_MINE_RADIUS);
    }

    /** 曲射轴。打包为 (yaw, pitch) 两个角度，转出单位向量。 */
    public Vec3 curveAxis() {
        int packed = this.entityData.get(DATA_CURVE_AXIS);
        float yawDeg = (packed >>> 16) & 0xFFFF;
        float pitchDeg = packed & 0xFFFF;
        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(pitchDeg);
        double cp = Math.cos(pitch);
        return new Vec3(-Math.sin(yaw) * cp, -Math.sin(pitch), Math.cos(yaw) * cp);
    }

    /**
     * 曲射配置。{@code axis} 会被归一化为 (yaw, pitch) 打包下发，
     * {@code rateDegPerSec} 的符号决定旋向。
     */
    public void configureCurve(Vec3 axis, double rateDegPerSec) {
        if (axis.lengthSqr() < 1.0E-9D || Math.abs(rateDegPerSec) < 1.0E-4D) {
            return;
        }
        Vec3 unit = axis.normalize();
        double yawDeg = Mth.wrapDegrees(Math.toDegrees(Math.atan2(-unit.x, unit.z)));
        double pitchDeg = Mth.wrapDegrees(Math.toDegrees(Math.asin(Mth.clamp(-unit.y, -1.0D, 1.0D))));
        int yawPart = (Mth.clamp((int) Math.round(yawDeg), 0, 0xFFFF)) & 0xFFFF;
        int pitchPart = (Mth.clamp((int) Math.round(pitchDeg), 0, 0xFFFF)) & 0xFFFF;
        this.entityData.set(DATA_CURVE_AXIS, (yawPart << 16) | pitchPart);
        this.entityData.set(DATA_CURVE_RATE, (float) rateDegPerSec);
        this.addFlag(FLAG_CURVE);
    }

    /**
     * 悬停配置：到达 {@code tick} 后速度归零并定住。
     *
     * <p>定住后仍会做 AABB 接触判伤（见 {@link #checkStationaryEntityHit}），
     * 故悬停弹是「网的静止节点」而不是打不到人的装饰。
     */
    public void configureHover(int tick) {
        if (tick <= 0) {
            return;
        }
        this.entityData.set(DATA_HOVER_TICK, tick);
        this.addFlag(FLAG_HOVER);
    }

    /** 分裂配置：到达 {@code tick} 散成 {@code count} 发。{@code count <= 1} 时不生效。 */
    public void configureSplit(int tick, int count) {
        if (tick < 0 || count <= 1) {
            return;
        }
        this.entityData.set(DATA_SPLIT_TICK, tick);
        this.entityData.set(DATA_SPLIT_COUNT, count);
        this.addFlag(FLAG_SPLIT);
    }

    /** 溜め配置：完全静止，玩家进入 {@code radius} 格内触发。 */
    public void configureMine(double radius) {
        if (radius <= 0.0D) {
            return;
        }
        this.entityData.set(DATA_MINE_RADIUS, (float) radius);
        this.addFlag(FLAG_MINE);
    }

    /** 触发分裂。仅服务端有意义（子弹是服务端新建实体），两端都会执行以保持时序一致。 */
    private void fireSplit() {
        int count = Math.max(2, splitCount());
        this.spawnSplitChildren(count);
        this.discard();
    }

    /**
     * 生成子弹幕。默认不生成——各弹种的外形参数（尺寸、颜色）子类才知道，
     * 故由 {@link SphereDanmaku} / {@link KnifeDanmaku} 各自覆写。
     */
    protected void spawnSplitChildren(int count) {
    }

    /** 溜め触发判定。仅服务端结算。 */
    private void tickMine() {
        if (!(this.level() instanceof ServerLevel server)) {
            return;
        }
        if (this.tickCount < MINE_ARM_TICKS) {
            return;
        }
        double radiusSq = this.mineRadius() * this.mineRadius();
        AABB box = this.getBoundingBox().inflate(this.mineRadius());
        List<Entity> candidates = server.getEntities(this, box, this::canHitEntity);
        for (Entity candidate : candidates) {
            if (candidate.distanceToSqr(this) <= radiusSq) {
                this.onMineTriggered(candidate);
                return;
            }
        }
    }

    /**
     * 溜め被踩时的结算。默认：若本弹声明了分裂则炸成一圈子弹，否则只消失。
     * 子类可覆写为范围判伤等。
     */
    protected void onMineTriggered(Entity trigger) {
        if (isSplitting()) {
            this.spawnSplitChildren(Math.max(2, splitCount()));
        }
        this.discard();
    }

    /**
     * 定住弹的接触判伤。零速度下 {@code getHitResultOnMoveVector} 恒为 MISS，
     * 故改用外扩 AABB 取最近命中者，与运动弹的判伤入口保持一致。
     */
    private void checkStationaryEntityHit() {
        List<Entity> candidates = this.level().getEntities(
                this, this.getBoundingBox().inflate(STATIONARY_HIT_INFLATE), this::canHitEntity);
        Entity nearest = null;
        double best = Double.MAX_VALUE;
        for (Entity candidate : candidates) {
            double dist = candidate.distanceToSqr(this.position());
            if (dist < best) {
                best = dist;
                nearest = candidate;
            }
        }
        if (nearest != null) {
            this.onHitEntity(new EntityHitResult(nearest, nearest.position().subtract(
                    this.position().add(0.0D, this.getBbHeight() * 0.5D, 0.0D))));
        }
    }

    /** 绕任意轴旋转向量（罗德里格公式）。轴须为单位向量。 */
    private static Vec3 rotateAbout(Vec3 vec, Vec3 axis, double angleRad) {
        double dot = axis.dot(vec);
        Vec3 cross = axis.cross(vec);
        return vec.scale(Math.cos(angleRad))
                .add(cross.scale(Math.sin(angleRad)))
                .add(axis.scale(dot * (1.0D - Math.cos(angleRad))));
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

    /**
     * 直接设置速度向量。
     *
     * <p>客户端在独立模拟运动学，服务端若静默改速，客户端会按旧方向继续飞，
     * 直到位置误差超阈值被硬拽——表现为「先错后跳」。
     * 设置 hurtMarked 让原版在下个 tick 下发运动包，客户端立刻对齐。
     *
     * <p>注意：实体内部每 tick 的转向（如灵符追踪、曲射）应直接调 setDeltaMovement，
     * 不要走本接口，否则会每 tick 发一次运动包。
     */
    public void setVelocity(Vec3 velocity) {
        this.setDeltaMovement(velocity);
        this.updateRotationFromVelocity();
        this.hurtMarked = true;
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

    /** 发射时 roll 出的暴击系数（1=未暴击），仅服务端使用。 */
    public float getCritMult() {
        return this.critMult;
    }

    public void setCritMult(float critMult) {
        this.critMult = critMult;
    }

    /** 是否主武器发射。灵力汲取仅对武器弹生效。 */
    public boolean isFromWeapon() {
        return this.fromWeapon;
    }

    public void setFromWeapon(boolean fromWeapon) {
        this.fromWeapon = fromWeapon;
    }

    /** 覆写存活时间（tick），用于短射程发射行为。 */
    public void setLifetimeTicks(int ticks) {
        this.lifetimeTicks = Math.max(1, ticks);
    }

    public int getColor() {
        return this.entityData.get(DATA_COLOR);
    }

    public void setColor(int color) {
        this.entityData.set(DATA_COLOR, color);
    }

    /** 阵营标记（预留，见 {@link #DATA_FACTION}）；当前无任何消费方。 */
    public int getFaction() {
        return this.entityData.get(DATA_FACTION);
    }

    /** 阵营标记（预留）；当前无任何写入方。 */
    public void setFaction(int faction) {
        this.entityData.set(DATA_FACTION, faction);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Damage", this.damage);
        tag.putInt("Color", this.getColor());
        if (this.critMult != 1.0F) {
            tag.putFloat("CritMult", this.critMult);
        }
        if (this.fromWeapon) {
            tag.putBoolean("FromWeapon", true);
        }
        tag.putByte("Flags", this.entityData.get(DATA_FLAGS));
        tag.putInt("HoverTick", hoverTick());
        tag.putInt("SplitTick", splitTick());
        tag.putInt("SplitCount", splitCount());
        tag.putInt("CurveAxis", this.entityData.get(DATA_CURVE_AXIS));
        tag.putFloat("CurveRate", curveRate());
        tag.putFloat("MineRadius", mineRadius());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.damage = tag.getFloat("Damage");
        if (tag.contains("Color")) {
            this.setColor(tag.getInt("Color"));
        }
        if (tag.contains("CritMult")) {
            this.critMult = tag.getFloat("CritMult");
        }
        this.fromWeapon = tag.getBoolean("FromWeapon");
        if (tag.contains("Flags")) {
            this.entityData.set(DATA_FLAGS, tag.getByte("Flags"));
        }
        if (tag.contains("HoverTick")) {
            this.entityData.set(DATA_HOVER_TICK, tag.getInt("HoverTick"));
        }
        if (tag.contains("SplitTick")) {
            this.entityData.set(DATA_SPLIT_TICK, tag.getInt("SplitTick"));
        }
        if (tag.contains("SplitCount")) {
            this.entityData.set(DATA_SPLIT_COUNT, tag.getInt("SplitCount"));
        }
        if (tag.contains("CurveAxis")) {
            this.entityData.set(DATA_CURVE_AXIS, tag.getInt("CurveAxis"));
        }
        if (tag.contains("CurveRate")) {
            this.entityData.set(DATA_CURVE_RATE, tag.getFloat("CurveRate"));
        }
        if (tag.contains("MineRadius")) {
            this.entityData.set(DATA_MINE_RADIUS, tag.getFloat("MineRadius"));
        }
    }
}
