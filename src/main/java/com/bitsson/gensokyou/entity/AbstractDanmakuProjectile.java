package com.bitsson.gensokyou.entity;

import com.bitsson.gensokyou.danmaku.DanmakuBudget;
import com.bitsson.gensokyou.danmaku.DanmakuHitScan;
import com.bitsson.gensokyou.danmaku.motion.DanmakuSpeedProfile;
import com.bitsson.gensokyou.danmaku.motion.RigOrbit;
import com.bitsson.gensokyou.danmaku.motion.Rotation;
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

    /**
     * 存活时长（tick）。发射方可按核覆写（散弹等短射程行为）。
     *
     * <p><b>必须是同步字段</b>，不得退回普通字段：它由发射方给定、<b>不可由
     * {@code tickCount} 派生</b>，因此不满足下方 {@link #splitFired} 那条注释所确立的
     * 「普通字段可用」的前提。曾经是普通字段，后果有二：短寿命弹（散弹）重载后回落成
     * 60s 弹，且客户端永远不知道覆写值。
     */
    private static final EntityDataAccessor<Integer> DATA_LIFETIME =
            SynchedEntityData.defineId(AbstractDanmakuProjectile.class, EntityDataSerializers.INT);

    // ---- 速率曲线（两段：v0→v1 用 p0 tick，v1→v2 用 p1 tick，之后保持 v2）----
    // 只用四则运算，不用 Math.sin/cos：双端各自推进同一颗弹，超越函数不保证跨平台一致。
    private static final EntityDataAccessor<Integer> DATA_PROFILE_V0 =
            SynchedEntityData.defineId(AbstractDanmakuProjectile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PROFILE_P0 =
            SynchedEntityData.defineId(AbstractDanmakuProjectile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PROFILE_V1 =
            SynchedEntityData.defineId(AbstractDanmakuProjectile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PROFILE_P1 =
            SynchedEntityData.defineId(AbstractDanmakuProjectile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PROFILE_V2 =
            SynchedEntityData.defineId(AbstractDanmakuProjectile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PROFILE_P2 =
            SynchedEntityData.defineId(AbstractDanmakuProjectile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PROFILE_V3 =
            SynchedEntityData.defineId(AbstractDanmakuProjectile.class, EntityDataSerializers.INT);
    /** 是否有速率曲线（false = 恒速，走旧路径）。 */
    private static final EntityDataAccessor<Boolean> DATA_HAS_PROFILE =
            SynchedEntityData.defineId(AbstractDanmakuProjectile.class, EntityDataSerializers.BOOLEAN);

    /**
     * 运动方向轴（单位向量）。
     *
     * <p><b>为什么必须单独存</b>——速率曲线会把速度降到 0，而零速时
     * {@code deltaMovement} 退化为零向量、<b>方向不可恢复</b>。等速率回升到负值
     * （「停住后反向」）时若没有这份轴，就无从知道该往哪反。
     *
     * <p>只在挂曲线时维护；无曲线时方向隐含于 {@code deltaMovement}，不额外占带宽。
     */
    private static final EntityDataAccessor<Float> DATA_AXIS_X =
            SynchedEntityData.defineId(AbstractDanmakuProjectile.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_AXIS_Y =
            SynchedEntityData.defineId(AbstractDanmakuProjectile.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_AXIS_Z =
            SynchedEntityData.defineId(AbstractDanmakuProjectile.class, EntityDataSerializers.FLOAT);

    /** 曲线标量的定点缩放：存成 int，避免逐弹同步浮点。 */
    private static final double PROFILE_SCALE = 1000.0D;

    // ---- 编队装置引用 ----
    /**
     * 所属装置的<b>网络 id</b>；0 = 不挂装置（自由飞行）。
     *
     * <p><b>为什么是网络 id 而不是装置序号</b>：一个符卡可有 1~3 条并发轨道、
     * 各自持有装置。序号在「多装置并存」时需要额外一张序号→实体的映射表，而那张表
     * 本身又得同步；网络 id 由实体的生成包直接带过来，归属天然无歧义、零额外带宽。
     *
     * <p><b>为什么只同步这一个 int 加一个相位角</b>（需求「带宽不随队形规模增长」）：
     * 装置的轨道参数只存在装置上那一份。48 颗弹各自复制一份参数就是 48 份带宽，
     * 队形一大需求就作废了。
     */
    private static final EntityDataAccessor<Integer> DATA_RIG_ID =
            SynchedEntityData.defineId(AbstractDanmakuProjectile.class, EntityDataSerializers.INT);
    /** 自身相位角，弧度，定标为 1/1000 弧度。 */
    private static final EntityDataAccessor<Integer> DATA_RIG_PHASE =
            SynchedEntityData.defineId(AbstractDanmakuProjectile.class, EntityDataSerializers.INT);
    /** 相位角定标。 */
    private static final double PHASE_SCALE = 1000.0D;

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
     *
     * <p><b>此模式仅适用于「可由 {@code tickCount} 派生」的状态。</b>不可派生的状态
     * （如 {@code DATA_LIFETIME}，由发射方给定）MUST 走 {@link SynchedEntityData}
     * 并纳入存档。
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
        builder.define(DATA_LIFETIME, MAX_LIFETIME_TICKS);
        builder.define(DATA_PROFILE_V0, 0);
        builder.define(DATA_PROFILE_P0, 0);
        builder.define(DATA_PROFILE_V1, 0);
        builder.define(DATA_PROFILE_P1, 0);
        builder.define(DATA_PROFILE_V2, 0);
        builder.define(DATA_PROFILE_P2, 0);
        builder.define(DATA_PROFILE_V3, 0);
        builder.define(DATA_RIG_ID, 0);
        builder.define(DATA_RIG_PHASE, 0);
        builder.define(DATA_HAS_PROFILE, false);
        builder.define(DATA_AXIS_X, 0.0F);
        builder.define(DATA_AXIS_Y, 0.0F);
        builder.define(DATA_AXIS_Z, 1.0F);
    }

    @Override
    public void tick() {
        long started = System.nanoTime();
        try {
            tickDanmaku();
        } finally {
            DanmakuBudget.recordTickNanos(System.nanoTime() - started);
        }
    }

    private void tickDanmaku() {
        // 挂装置的弹：先把速度设成「解析终点 − 当前坐标」，再让 super.tick() 的位移
        // 把它推到终点。
        //
        // <p>这个顺序是 rig 存在的意义所在：位移后位置<b>逐位等于</b>解析值，
        // 于是不累积误差、lerpTo 永远看不到误差（也就不需要纠偏包），
        // 而原版的碰撞/扫掠管线一行都不用改。
        //
        // <p>MUST 在 super.tick() 之前：之后位置已经是终点了，再设速度会变成下一 tick 的。
        if (this.entityData.get(DATA_RIG_ID) != 0) {
            applyRigMotion();
        }
        super.tick();

        if (this.tickCount > getLifetimeTicks()) {
            this.discard();
            return;
        }

        // 溜め：完全静止。它<b>不</b>做接触判伤（那是溜め弹的语义：埋着，等人踩），
        // 故直接早退，两端的位移均为零，行为天然一致。
        //
        // <p>MINE 与挂装置互斥（静态判据见 {@code Behaviour} 的 {@code Rig}）：溜め弹
        // 的语义是「原地埋着等人踩」，而装置弹的位置由装置决定，两者无法同时成立。
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

        // 速率曲线：只改速率、不改方向，故曲线内含「回头」时位移会变成负的，
        // 弹沿原路飞回发射点。回到即销毁。
        if (this.hasSpeedProfile()) {
            DanmakuSpeedProfile profile = speedProfile();
            velocity = alongAxis(velocity, profile.speedAt(this.tickCount));
            this.setDeltaMovement(velocity);
            if (profile.returnedToOrigin(this.tickCount)) {
                this.discard();
                return;
            }
        }

        if (stationary) {
            this.checkStationaryEntityHit();
            return;
        }

        HitResult hitResult = DanmakuHitScan.sweep(this, LivingEntity.class, this::canHitEntity);
        if (hitResult != null) {
            this.onHit(hitResult);
            if (this.isRemoved()) {
                return;
            }
        }

        // 位移手工推进：弹幕的碰撞判定已在上面的扫掠里做完，原版 move() 的
        // 实体推挤/台阶处理对「按脚本编排的弹幕」只会造成位置漂移。
        this.setPos(this.getX() + velocity.x, this.getY() + velocity.y, this.getZ() + velocity.z);
        this.updateRotationFromVelocity();
    }

    // ---------------------------------------------------------------
    // 行为标志位
    // ---------------------------------------------------------------

    private int flags() {
        return this.entityData.get(DATA_FLAGS) & 0xFF;
    }

    private void addFlag(int flag) {
        this.entityData.set(DATA_FLAGS, (byte) (this.flags() | flag));
    }

    public boolean isCurving() {
        return (this.flags() & FLAG_CURVE) != 0 && Math.abs(this.curveRate()) > 1.0E-4F;
    }

    public boolean isSplitting() {
        return (this.flags() & FLAG_SPLIT) != 0 && this.splitCount() > 0;
    }

    public boolean isHovering() {
        return (this.flags() & FLAG_HOVER) != 0 && this.hoverTick() > 0;
    }

    public boolean isMine() {
        return (this.flags() & FLAG_MINE) != 0 && this.mineRadius() > 0.0F;
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

    /**
     * 曲射轴。
     *
     * <p>存的是<b>打包的两个角度</b>（高 16 位 = 下转，低 16 位 = 俯仰）而非三轴向量：
     * 角度取值有界、量级一致，不会出现「某个客户端算出 1e-17 分量的轴导致整条
     * 曲射塌成一条直线」。
     */
    public Vec3 curveAxis() {
        int packed = this.entityData.get(DATA_CURVE_AXIS);
        float yawDeg = (packed >>> 16) & 0xFFFF;
        float pitchDeg = packed & 0xFFFF;
        return Rotation.axisFromAngles(yawDeg, pitchDeg);
    }

    public void configureCurve(Vec3 axis, double rateDegPerSec) {
        if (axis.lengthSqr() < 1.0E-9D || Math.abs(rateDegPerSec) < 1.0E-4D) {
            return;
        }
        Vec3 unit = axis.normalize();
        double yawDeg = Mth.wrapDegrees(Math.toDegrees(Math.atan2(-unit.x, unit.z)));
        double pitchDeg = Mth.wrapDegrees(Math.toDegrees(Math.asin(Mth.clamp(-unit.y, -1.0D, 1.0D))));
        int yawPart = Mth.clamp((int) Math.round(yawDeg), 0, 0xFFFF) & 0xFFFF;
        int pitchPart = Mth.clamp((int) Math.round(pitchDeg), 0, 0xFFFF) & 0xFFFF;
        this.entityData.set(DATA_CURVE_AXIS, (yawPart << 16) | pitchPart);
        this.entityData.set(DATA_CURVE_RATE, (float) rateDegPerSec);
        this.addFlag(FLAG_CURVE);
    }

    public void configureHover(int tick) {
        if (tick > 0) {
            this.entityData.set(DATA_HOVER_TICK, tick);
            this.addFlag(FLAG_HOVER);
        }
    }

    public void configureSplit(int tick, int count) {
        if (tick >= 0 && count > 1) {
            this.entityData.set(DATA_SPLIT_TICK, tick);
            this.entityData.set(DATA_SPLIT_COUNT, count);
            this.addFlag(FLAG_SPLIT);
        }
    }

    public void configureMine(double radius) {
        if (radius <= 0.0D) {
            return;
        }
        this.entityData.set(DATA_MINE_RADIUS, (float) radius);
        this.addFlag(FLAG_MINE);
    }

    public void configureSpeedProfile(DanmakuSpeedProfile profile) {
        if (profile == null || !profile.varies()) {
            return;
        }
        this.entityData.set(DATA_PROFILE_V0, scale(profile.v0()));
        this.entityData.set(DATA_PROFILE_P0, scale(profile.p0()));
        this.entityData.set(DATA_PROFILE_V1, scale(profile.v1()));
        this.entityData.set(DATA_PROFILE_P1, scale(profile.p1()));
        this.entityData.set(DATA_PROFILE_V2, scale(profile.v2()));
        this.entityData.set(DATA_PROFILE_P2, scale(profile.p2()));
        this.entityData.set(DATA_PROFILE_V3, scale(profile.v3()));
        // 趁速度非零时记下方向轴：曲线会把速率降到 0，零速时方向不可恢复。
        Vec3 velocity = this.getDeltaMovement();
        if (velocity.lengthSqr() > 1.0E-9D) {
            this.setAxis(velocity.normalize());
        }
        this.entityData.set(DATA_HAS_PROFILE, true);
    }

    public boolean hasSpeedProfile() {
        return this.entityData.get(DATA_HAS_PROFILE);
    }

    public DanmakuSpeedProfile speedProfile() {
        return new DanmakuSpeedProfile(
                unscale(this.entityData.get(DATA_PROFILE_V0)),
                unscale(this.entityData.get(DATA_PROFILE_P0)),
                unscale(this.entityData.get(DATA_PROFILE_V1)),
                unscale(this.entityData.get(DATA_PROFILE_P1)),
                unscale(this.entityData.get(DATA_PROFILE_V2)),
                unscale(this.entityData.get(DATA_PROFILE_P2)),
                unscale(this.entityData.get(DATA_PROFILE_V3)));
    }

    private void setAxis(Vec3 axis) {
        this.entityData.set(DATA_AXIS_X, (float) axis.x);
        this.entityData.set(DATA_AXIS_Y, (float) axis.y);
        this.entityData.set(DATA_AXIS_Z, (float) axis.z);
    }

    private Vec3 axis() {
        return new Vec3(this.entityData.get(DATA_AXIS_X), this.entityData.get(DATA_AXIS_Y),
                this.entityData.get(DATA_AXIS_Z));
    }

    /**
     * 把速度矢量重定向到该弹的轴、速率改为 {@code speed}（可负 = 回头）。
     *
     * <p>速度矢量非零时以它为准（曲射已在本 tick 改过方向），否则回落到记录的轴。
     */
    private Vec3 alongAxis(Vec3 velocity, double speed) {
        Vec3 dir = velocity.lengthSqr() > 1.0E-9D ? velocity.normalize() : axis();
        if (dir.lengthSqr() < 1.0E-9D) {
            return Vec3.ZERO;
        }
        return dir.normalize().scale(speed);
    }

    // ---------------------------------------------------------------
    // 编队装置（rig）
    // ---------------------------------------------------------------

    /**
     * 挂到装置上。此后本弹的位置不再由自身速度决定，而由
     * 「装置的 tick + 本弹的相位角」唯一确定。
     *
     * <p>MUST 在 {@code setDirection} <b>之后</b>调用：装置会接管位置，
     * 几何给的初速随即失效，留着它只会让人以为速度仍然算数。
     *
     * @param rig      装置实体
     * @param phaseRad 自身相位角（弧度）
     */
    public void bindToRig(DanmakuRig rig, double phaseRad) {
        this.entityData.set(DATA_RIG_ID, rig.getId());
        this.entityData.set(DATA_RIG_PHASE, (int) Math.round(phaseRad * PHASE_SCALE));
    }

    /** 本弹是否挂在装置上。 */
    public boolean isRigBound() {
        return this.entityData.get(DATA_RIG_ID) != 0;
    }

    /** 自身相位角（弧度）。 */
    public double rigPhase() {
        return this.entityData.get(DATA_RIG_PHASE) / PHASE_SCALE;
    }

    /**
     * 解析出所属装置。
     *
     * <p><b>装置没了就脱钩</b>（需求「不残留引用失效装置的子弹」）：把 rig id 清零，
     * 本弹保留当前速度继续自由飞行。
     *
     * <p>之所以选「自由飞行」而不是「一并销毁」：装置随符卡阶段结束而销毁，
     * 此时场上还有半屏弹在飞，全部突然消失读作「BOSS 放空了」；
     * 让它们沿当前动量飞完则是「这一轮到此为止」。
     */
    private DanmakuRig resolveRig() {
        int id = this.entityData.get(DATA_RIG_ID);
        if (id == 0) {
            return null;
        }
        if (!(this.level().getEntity(id) instanceof DanmakuRig rig) || rig.isRemoved()) {
            this.entityData.set(DATA_RIG_ID, 0);
            return null;
        }
        return rig;
    }

    /**
     * 把本 tick 的位置改由装置决定。
     *
     * <p>速度取「解析终点 − 当前坐标」，随后 {@code super.tick()} 的位移正好落在终点。
     * 于是位置每 tick 被<b>重置</b>成解析值，误差不累积；扫掠、朝向、纠偏阈值
     * 全部沿用既有管线，无需为 rig 写第二套运动。
     */
    private void applyRigMotion() {
        DanmakuRig rig = resolveRig();
        if (rig == null) {
            return;
        }
        if (rig.expired()) {
            this.entityData.set(DATA_RIG_ID, 0);
            return;
        }
        RigOrbit orbit = rig.orbit();
        // 用装置的 tick 而非本弹的 age：后者会让不同时刻加入的弹各转各的，
        // 队形在加入那一瞬就散了。
        int t = rig.tickCount + 1;
        Vec3 next = orbit.bulletPositionAt(t, this.rigPhase());
        this.setDeltaMovement(next.subtract(this.position()));
    }

    private static int scale(double value) {
        return (int) Math.round(value * PROFILE_SCALE);
    }

    private static double unscale(int value) {
        return value / PROFILE_SCALE;
    }

    /** 触发分裂。仅服务端有意义（子弹是服务端新建实体），两端都会执行以保持时序一致。 */
    private void fireSplit() {
        int count = Math.max(2, splitCount());
        this.spawnSplitChildren(count);
        this.discard();
    }

    protected void spawnSplitChildren(int count) {
    }

    private void tickMine() {
        if (!(this.level() instanceof ServerLevel server) || this.tickCount < 20) {
            return;
        }
        double radiusSqr = this.mineRadius() * this.mineRadius();
        AABB box = this.getBoundingBox().inflate(this.mineRadius());
        for (LivingEntity candidate : server.getEntitiesOfClass(LivingEntity.class, box, this::canHitEntity)) {
            if (candidate.distanceToSqr(this) <= radiusSqr) {
                this.onMineTriggered(candidate);
                return;
            }
        }
    }

    protected void onMineTriggered(Entity trigger) {
        if (this.isSplitting()) {
            this.spawnSplitChildren(Math.max(2, this.splitCount()));
        }
        this.discard();
    }

    /** 静止弹的命中判定：零位移下扫掠必然返回 MISS，故改用 AABB 相交。 */
    private void checkStationaryEntityHit() {
        List<LivingEntity> candidates = this.level().getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(0.35D), this::canHitEntity);
        LivingEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity candidate : candidates) {
            double distance = candidate.distanceToSqr(this.position());
            if (distance < best) {
                best = distance;
                nearest = candidate;
            }
        }
        if (nearest != null) {
            this.onHitEntity(new EntityHitResult(nearest,
                    nearest.position().subtract(this.position().add(0.0D, this.getBbHeight() * 0.5D, 0.0D))));
        }
    }

    /**
     * 绕指定轴旋转。
     *
     * <p>委托给 {@link Rotation}：曲射轴与 rig 的公转/自转共用同一条 Rodrigues 实现，
     * 「曲射的手感」与「装置公转的手感」才是同一条曲线族，而不是两处各写一遍的近似。
     */
    private static Vec3 rotateAbout(Vec3 vec, Vec3 axis, double angleRad) {
        return Rotation.about(vec, axis, angleRad);
    }

    protected void updateRotationFromVelocity() {
        Vec3 velocity = this.getDeltaMovement();
        if (velocity.lengthSqr() < 1.0E-7D) {
            return;
        }
        this.setYRot((float) (Mth.atan2(velocity.x, velocity.z) * (180.0D / Math.PI)));
        this.setXRot((float) (Mth.atan2(velocity.y, velocity.horizontalDistance()) * (180.0D / Math.PI)));
    }

    /**
     * 客户端与服务端跑相同的运动学，因此位置包只作为纠偏手段。
     *
     * <p>若无条件接受服务端位置，每个位置包都会把弹幕拽一下，产生可见抖动。
     * 这里只在误差超过阈值时才硬纠正，其余情况信任本地模拟，从而获得完全平滑的轨迹。
     */
    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps) {
        // 误差 MUST 在 setPos 之前取，否则清零。
        Vec3 error = new Vec3(x, y, z).subtract(this.position());
        Vec3 velocity = this.getDeltaMovement();
        if (error.lengthSqr() > POSITION_CORRECTION_THRESHOLD_SQR) {
            DanmakuBudget.recordHardCorrection(velocity.length());
            this.setPos(x, y, z);
            this.setYRot(yaw);
            this.setXRot(pitch);
        }
        // 滞后诊断：把误差投影到速度方向，即得该弹的滞后 tick 数。
        // 静止弹（速度过低）投影无定义，不计入——它们本就无滞后可言。
        double speedSqr = velocity.lengthSqr();
        if (speedSqr > 1.0E-9D) {
            DanmakuBudget.recordLag(error.dot(velocity) / speedSqr);
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
     * <p>注意：实体内部每 tick 的转向（如曲射）应直接调 setDeltaMovement，
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

    public void addVelocity(Vec3 deltaVelocity) {
        this.setVelocity(this.getDeltaMovement().add(deltaVelocity));
    }

    public double getSpeed() {
        return this.getDeltaMovement().length();
    }

    protected boolean isWhitelisted(Entity entity) {
        return entity != null && (entity == this.getOwner() || this.whitelist.contains(entity.getType()));
    }

    public void setWhitelist(Set<EntityType<?>> whitelist) {
        this.whitelist = whitelist == null ? new HashSet<>() : new HashSet<>(whitelist);
    }

    public float getDamage() {
        return this.damage;
    }

    public void setDamage(float damage) {
        this.damage = damage;
    }

    public float getCritMult() {
        return this.critMult;
    }

    public void setCritMult(float critMult) {
        this.critMult = critMult;
    }

    public boolean isFromWeapon() {
        return this.fromWeapon;
    }

    public void setFromWeapon(boolean fromWeapon) {
        this.fromWeapon = fromWeapon;
    }

    public void setLifetimeTicks(int ticks) {
        this.entityData.set(DATA_LIFETIME, Math.max(1, ticks));
    }

    public int getLifetimeTicks() {
        return this.entityData.get(DATA_LIFETIME);
    }

    public int getColor() {
        return this.entityData.get(DATA_COLOR);
    }

    public void setColor(int color) {
        this.entityData.set(DATA_COLOR, color);
    }

    public int getFaction() {
        return this.entityData.get(DATA_FACTION);
    }

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
        tag.putInt("Lifetime", getLifetimeTicks());
        if (this.hasSpeedProfile()) {
            // 速率曲线与其轴：7 个 double + 3 个轴分量。
            // 缺了它们，重载后的弹会沿原速直飞——返程弹变成永动机，
            // 而这种故障只在存档重进时显形，没人能把两者联系起来。
            DanmakuSpeedProfile profile = this.speedProfile();
            tag.putDouble("SpV0", profile.v0());
            tag.putDouble("SpP0", profile.p0());
            tag.putDouble("SpV1", profile.v1());
            tag.putDouble("SpP1", profile.p1());
            tag.putDouble("SpV2", profile.v2());
            tag.putDouble("SpP2", profile.p2());
            tag.putDouble("SpV3", profile.v3());
            tag.putFloat("SpAxisX", this.entityData.get(DATA_AXIS_X));
            tag.putFloat("SpAxisY", this.entityData.get(DATA_AXIS_Y));
            tag.putFloat("SpAxisZ", this.entityData.get(DATA_AXIS_Z));
        }
        if (this.isRigBound()) {
            // rig 引用与相位角：缺了它们，重载后的编队弹会各自为政地直飞，
            // 表现为「一整队弹在读档瞬间散架」。
            tag.putInt("RigId", this.entityData.get(DATA_RIG_ID));
            tag.putInt("RigPhase", this.entityData.get(DATA_RIG_PHASE));
        }
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
        if (tag.contains("Lifetime")) {
            this.entityData.set(DATA_LIFETIME, Math.max(1, tag.getInt("Lifetime")));
        }
        if (tag.contains("SpV3")) {
            // 逐项用 putDouble 写原值：定标整数量化误差不该被存档再吃一次。
            this.entityData.set(DATA_PROFILE_V0, scale(tag.getDouble("SpV0")));
            this.entityData.set(DATA_PROFILE_P0, scale(tag.getDouble("SpP0")));
            this.entityData.set(DATA_PROFILE_V1, scale(tag.getDouble("SpV1")));
            this.entityData.set(DATA_PROFILE_P1, scale(tag.getDouble("SpP1")));
            this.entityData.set(DATA_PROFILE_V2, scale(tag.getDouble("SpV2")));
            this.entityData.set(DATA_PROFILE_P2, scale(tag.getDouble("SpP2")));
            this.entityData.set(DATA_PROFILE_V3, scale(tag.getDouble("SpV3")));
            this.entityData.set(DATA_AXIS_X, tag.getFloat("SpAxisX"));
            this.entityData.set(DATA_AXIS_Y, tag.getFloat("SpAxisY"));
            this.entityData.set(DATA_AXIS_Z, tag.getFloat("SpAxisZ"));
            this.entityData.set(DATA_HAS_PROFILE, true);
        }
        if (tag.contains("RigId")) {
            this.entityData.set(DATA_RIG_ID, tag.getInt("RigId"));
            this.entityData.set(DATA_RIG_PHASE, tag.getInt("RigPhase"));
        }
    }
}
