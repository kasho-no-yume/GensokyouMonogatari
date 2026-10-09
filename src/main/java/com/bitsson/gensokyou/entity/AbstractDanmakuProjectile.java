package com.bitsson.gensokyou.entity;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.danmaku.DanmakuBudget;
import com.bitsson.gensokyou.danmaku.DanmakuBudget.RemovalCause;
import com.bitsson.gensokyou.danmaku.DanmakuHitScan;
import com.bitsson.gensokyou.danmaku.DanmakuLegTargetPush;
import com.bitsson.gensokyou.danmaku.DanmakuLifetime;
import com.bitsson.gensokyou.danmaku.motion.DanmakuAge;
import com.bitsson.gensokyou.danmaku.motion.DanmakuCorrection;
import com.bitsson.gensokyou.danmaku.motion.DanmakuLegMotion;
import com.bitsson.gensokyou.danmaku.motion.DanmakuRandomState;
import com.bitsson.gensokyou.danmaku.motion.DanmakuSpeedProfile;
import com.bitsson.gensokyou.danmaku.motion.DanmakuTrackKinds;
import com.bitsson.gensokyou.danmaku.render.DanmakuMotionState;
import com.bitsson.gensokyou.danmaku.render.DanmakuRenderState;
import com.bitsson.gensokyou.danmaku.render.DanmakuResyncQueue;
import com.bitsson.gensokyou.danmaku.render.DanmakuSampleCheck;
import com.bitsson.gensokyou.danmaku.render.DanmakuSyncStats;
import com.bitsson.gensokyou.danmaku.visual.DanmakuPhase;
import com.bitsson.gensokyou.danmaku.motion.FormationFrame;
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

import javax.annotation.Nullable;
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

    /**
     * 弹幕渲染距离上限：直接跟随客户端视距，而不是原版公式里的
     * 「AABB 平均 × 64 × viewScale」——那把弹幕的 38~60 格和玩家视距彻底脱钩，
     * 是「远处的弹忽然就消失了」的根因。
     */
    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        double viewDist = renderViewDistanceBlocks();
        if (viewDist <= 0.0) {
            return super.shouldRenderAtSqrDistance(distanceSqr);
        }
        return distanceSqr < viewDist * viewDist;
    }

    /**
     * 客户端视距对应格数；客户端状态未就绪时返回非正数，调用方退回原版公式。
     */
    protected double renderViewDistanceBlocks() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc == null || mc.options == null) {
            return 0.0;
        }
        int chunks = mc.options.renderDistance().get();
        return Math.max(1, chunks) * 16.0;
    }

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
    /**
     * 是否声明了「越过发射点即销毁」这一终止条件。
     *
     * <p>默认 <b>false</b>：反向加速是纯运动，弹退回去继续飞完全合法，
     * 不该一到原点就消失。销毁是一条独立规则，不是速率曲线的固有属性。
     */
    // ---- 相位隐藏（显隐是 Behaviour 的一轴，与弹种正交，故住在基类上）----
    private static final EntityDataAccessor<Integer> DATA_PHASE_PERIOD =
            SynchedEntityData.defineId(AbstractDanmakuProjectile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PHASE_DUTY =
            SynchedEntityData.defineId(AbstractDanmakuProjectile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PHASE_OFFSET =
            SynchedEntityData.defineId(AbstractDanmakuProjectile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_DIES_AT_ORIGIN =
            SynchedEntityData.defineId(AbstractDanmakuProjectile.class, EntityDataSerializers.BOOLEAN);
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
    private static final EntityDataAccessor<Boolean> DATA_HAS_FRAME =
            SynchedEntityData.defineId(AbstractDanmakuProjectile.class, EntityDataSerializers.BOOLEAN);
    // 编队帧 12 个分量。注意 1.21.1 的 defineId 只有 2 参重载（无名字参数）。
    private static final EntityDataAccessor<Integer> DATA_FRAME_CX =
            SynchedEntityData.defineId(AbstractDanmakuProjectile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_FRAME_CY =
            SynchedEntityData.defineId(AbstractDanmakuProjectile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_FRAME_CZ =
            SynchedEntityData.defineId(AbstractDanmakuProjectile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_FRAME_OX =
            SynchedEntityData.defineId(AbstractDanmakuProjectile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_FRAME_OY =
            SynchedEntityData.defineId(AbstractDanmakuProjectile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_FRAME_OZ =
            SynchedEntityData.defineId(AbstractDanmakuProjectile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_FRAME_AXIS_YAW =
            SynchedEntityData.defineId(AbstractDanmakuProjectile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_FRAME_AXIS_PITCH =
            SynchedEntityData.defineId(AbstractDanmakuProjectile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_FRAME_ROT_RATE =
            SynchedEntityData.defineId(AbstractDanmakuProjectile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_FRAME_SCALE_BASE =
            SynchedEntityData.defineId(AbstractDanmakuProjectile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_FRAME_SCALE_AMP =
            SynchedEntityData.defineId(AbstractDanmakuProjectile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_FRAME_SCALE_PERIOD =
            SynchedEntityData.defineId(AbstractDanmakuProjectile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_FRAME_ORBIT_YAW =
            SynchedEntityData.defineId(AbstractDanmakuProjectile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_FRAME_ORBIT_PITCH =
            SynchedEntityData.defineId(AbstractDanmakuProjectile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_FRAME_ORBIT_RADIUS =
            SynchedEntityData.defineId(AbstractDanmakuProjectile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_FRAME_ORBIT_RATE =
            SynchedEntityData.defineId(AbstractDanmakuProjectile.class, EntityDataSerializers.INT);
    /**
     * 挂编队帧但**没挂速率曲线**时的推进速率（格/tick）。
     *
     * <p>为什么必须单独存：编队帧每 tick 用解析位置覆写 {@code deltaMovement}，
     * 下一 tick 就再也取不回初速了。而「匀速沿法线行进」是编队弹最常见的用法，
     * 没有它那些弹会在原地钉死。
     */
    private static final EntityDataAccessor<Integer> DATA_FRAME_ADVANCE_SPEED =
            SynchedEntityData.defineId(AbstractDanmakuProjectile.class, EntityDataSerializers.INT);

    // ------------------------------------------------------------------
    // 段式运动（18 个 accessor）
    //
    // 8 个种子 + 8 个打包段参数 + 种子数 + 段表打包字节。
    // **定义在抽象类上 ⇒ 四种弹种都吃这份内存**（18 × 2000 弹 × 约 24 B ≈ 860 KB）。
    // 可接受，但不值得再加。
    //
    // **生成包不受影响**：未使用段运动的弹全是默认值，而 SynchedEntityData 只下发
    // 非默认值 ⇒ 零线上成本。这是把 accessor 定长化（而非按需）的前提。
    // ------------------------------------------------------------------

    /** 发射时抽取的种子 ×8。语义无关——含义由消费方 {@code DanmakuLegMotion} 决定。 */
    private static final EntityDataAccessor<Integer>[] DATA_RANDOM_SEEDS = newAccessorArray(8);

    /** 打包的单段参数 {@code (时长 << 16) | (速率 & 0xFFFF)} ×8。 */
    private static final EntityDataAccessor<Integer>[] DATA_LEGS = newAccessorArray(8);

    /** 有效种子个数，范围 [0, 8]。 */
    private static final EntityDataAccessor<Integer> DATA_RANDOM_SEED_COUNT =
            SynchedEntityData.defineId(AbstractDanmakuProjectile.class, EntityDataSerializers.INT);

    /**
     * 段数与段类型打包成一个字节：低 4 位段数、高 2 位段类型。
     *
     * <p>合成一个 accessor 是为了保住 18 个的预算 —— 逐段类型需要额外 8 个位。
     */
    private static final EntityDataAccessor<Integer> DATA_LEG_COUNT_AND_KIND =
            SynchedEntityData.defineId(AbstractDanmakuProjectile.class, EntityDataSerializers.INT);

    /** 泛型数组无法直接初始化，逐个填充。 */
    private static EntityDataAccessor<Integer>[] newAccessorArray(int size) {
        @SuppressWarnings("unchecked")
        EntityDataAccessor<Integer>[] array = new EntityDataAccessor[size];
        for (int i = 0; i < size; i++) {
            array[i] = SynchedEntityData.defineId(AbstractDanmakuProjectile.class,
                    EntityDataSerializers.INT);
        }
        return array;
    }

    /** 是否挂了段式运动（段数 > 0）。 */
    public boolean hasLegMotion() {
        return DanmakuLegMotion.legCountOf(this.entityData.get(DATA_LEG_COUNT_AND_KIND)) > 0;
    }

    /** 段数，0 表示未使用段式运动。 */
    public int legCount() {
        return DanmakuLegMotion.legCountOf(this.entityData.get(DATA_LEG_COUNT_AND_KIND));
    }

    /** 段类型。未使用段式运动时返回 {@code FIXED}（无害的默认值）。 */
    public DanmakuLegMotion.Kind legKind() {
        return DanmakuLegMotion.kindOf(this.entityData.get(DATA_LEG_COUNT_AND_KIND));
    }

    public int randomSeedCount() {
        return Math.max(0, Math.min(DanmakuRandomState.MAX_SEEDS,
                this.entityData.get(DATA_RANDOM_SEED_COUNT)));
    }

    /** 第 index 个种子；越界返回 0（与 {@code DanmakuRandomState} 的降级一致）。 */
    public int randomSeed(int index) {
        if (index < 0 || index >= DanmakuRandomState.MAX_SEEDS) {
            return 0;
        }
        return this.entityData.get(DATA_RANDOM_SEEDS[index]);
    }

    /** 第 index 段的打包参数；越界返回 0。 */
    public int packedLeg(int index) {
        if (index < 0 || index >= DanmakuLegMotion.MAX_LEGS) {
            return 0;
        }
        return this.entityData.get(DATA_LEGS[index]);
    }

    /**
     * 写入完整的段式运动输入（种子 + 段表 + 段数 + 段类型）。
     *
     * <p><b>调用即定死方向</b>：{@link #legMotion()} 由本方法写入的字节构造，
     * 而 {@code DanmakuLegMotion} 在<b>构造期</b>就把段方向解出并缓存 ——
     * 每 tick 路径不读种子（纪律见 {@code DanmakuLegMotion} 的类注释）。
     */
    public void setLegMotion(@Nullable DanmakuLegMotion motion, DanmakuRandomState seeds) {
        DanmakuRandomState source = seeds == null ? DanmakuRandomState.empty() : seeds;
        this.entityData.set(DATA_RANDOM_SEED_COUNT, source.size());
        for (int i = 0; i < DanmakuRandomState.MAX_SEEDS; i++) {
            this.entityData.set(DATA_RANDOM_SEEDS[i], source.at(i));
        }
        int legs = motion == null ? 0 : motion.legCount();
        DanmakuLegMotion.Kind kind = motion == null
                ? DanmakuLegMotion.Kind.FIXED : motion.kind();
        this.entityData.set(DATA_LEG_COUNT_AND_KIND, DanmakuLegMotion.packLegCountAndKind(legs, kind));
        for (int i = 0; i < DanmakuLegMotion.MAX_LEGS; i++) {
            this.entityData.set(DATA_LEGS[i], motion == null ? 0 : motion.packedLegAt(i));
        }
        this.invalidateLegMotionCache();
    }

    /** 段式运动的构造期缓存。方向只在<b>首次构造或输入变化</b>时重解。 */
    @Nullable
    private DanmakuLegMotion legMotionCache;
    private int legMotionCacheKey = Integer.MIN_VALUE;

    private void invalidateLegMotionCache() {
        this.legMotionCache = null;
        this.legMotionCacheKey = Integer.MIN_VALUE;
    }

    /**
     * 本弹的段式运动形态，由已同步的种子与段表在<b>首次访问时</b>构造并缓存。
     *
     * <p>返回 {@code null} 表示未使用段式运动。
     *
     * <p><b>纪律</b>：段方向在<b>构造期</b>从种子解出并存为字段，之后每 tick 只读缓存。
     * 若把它改成每次调用都重建，那么「同一份同步输入」在两端重建出的对象方向一致
     * 但对象不同 —— 真正的风险是任何在构造后改动种子再重建的路径，
     * 那会让同一条弹在不同时刻拥有不同轨迹。缓存的存在让「方向在生成时定死」成为结构事实。
     */
    @Nullable
    public DanmakuLegMotion legMotion() {
        if (!this.hasLegMotion()) {
            return null;
        }
        int key = this.legMotionInputKey();
        if (this.legMotionCache == null || this.legMotionCacheKey != key) {
            int[] packed = new int[DanmakuLegMotion.MAX_LEGS];
            for (int i = 0; i < DanmakuLegMotion.MAX_LEGS; i++) {
                packed[i] = this.packedLeg(i);
            }
            this.legMotionCache = DanmakuLegMotion.fromSpec(this.legCount(), packed,
                    this.randomState(), this.getDeltaMovement().lengthSqr() > 1.0E-12D
                            ? this.getDeltaMovement().normalize() : new Vec3(0.0D, 0.0D, 1.0D),
                    this.legKind());
            this.legMotionCacheKey = key;
        }
        return this.legMotionCache;
    }

    /** 已同步的种子，供 {@link #legMotion()} 构造用。 */
    public DanmakuRandomState randomState() {
        int[] seeds = new int[DanmakuRandomState.MAX_SEEDS];
        for (int i = 0; i < DanmakuRandomState.MAX_SEEDS; i++) {
            seeds[i] = this.randomSeed(i);
        }
        return DanmakuRandomState.of(seeds, this.randomSeedCount());
    }

    /** 段式运动输入的指纹键：输入一变即重建缓存。 */
    private int legMotionInputKey() {
        int key = this.entityData.get(DATA_LEG_COUNT_AND_KIND) * 31 + this.randomSeedCount();
        for (int i = 0; i < DanmakuRandomState.MAX_SEEDS; i++) {
            key = key * 31 + this.randomSeed(i);
        }
        for (int i = 0; i < DanmakuLegMotion.MAX_LEGS; i++) {
            key = key * 31 + this.packedLeg(i);
        }
        return key;
    }

    /**
     * 本弹的服务端追踪目标（{@code TARGET} 段的方向来源）。
     *
     * <p>刻意复用 {@code DATA_BURST_TARGET} 那套既有语义，而不是为段式运动
     * 新造一套目标选择 —— 档三的代价应花在「下发」上，不是「发明语义」上。
     *
     * @return 目标实体；无目标、已移除、或查不到时返回 {@code null}
     */
    @Nullable
    public Entity targetEntity() {
        int id = this.entityData.get(DATA_BURST_TARGET);
        if (id < 0 || !(this.level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        Entity found = serverLevel.getEntity(id);
        return found != null && found.isAlive() ? found : null;
    }

    // ------------------------------------------------------------------
    // 径向爆散
    //
    // 「一团弹保持队形飞一段 → 停住 → 各自朝外炸开」。四项参数：
    // 爆散年龄、径向速率、「自身即参考点」时的瞄准速率、目标实体 id。
    //
    // <p>参考点<b>不另存</b>：它就是编队帧的中心（{@code DATA_FRAME_CX..CZ}），
    // 而爆散时「弹到参考点的偏移」正是它在帧里的偏移。于是爆散与编队共用一份数据，
    // 不需要第二套参考点同步。
    // ------------------------------------------------------------------

    /** 爆散年龄（tick）。{@code <= 0} = 无爆散。 */
    private static final EntityDataAccessor<Integer> DATA_BURST_AT =
            SynchedEntityData.defineId(AbstractDanmakuProjectile.class, EntityDataSerializers.INT);
    /** 径向爆散速率（格/tick），float 位模式。 */
    private static final EntityDataAccessor<Integer> DATA_BURST_RADIAL =
            SynchedEntityData.defineId(AbstractDanmakuProjectile.class, EntityDataSerializers.INT);
    /** 弹自身即参考点时改用「朝目标射出」的速率（格/tick），float 位模式。 */
    private static final EntityDataAccessor<Integer> DATA_BURST_AIM =
            SynchedEntityData.defineId(AbstractDanmakuProjectile.class, EntityDataSerializers.INT);
    /**
     * 爆散时瞄准的目标实体 id。
     *
     * <p>刻意只存 id 而非世界坐标：坐标在爆散那一刻才知道，那时弹已在天上飞了两秒，
     * 存一份快照等于把「它朝哪飞」在出生时就定死。id 让两端各自在爆散时刻就地解析，
     * 于是爆散方向是「弹自身位置 + 参考点 + 目标当前位置」的纯函数。
     */
    private static final EntityDataAccessor<Integer> DATA_BURST_TARGET =
            SynchedEntityData.defineId(AbstractDanmakuProjectile.class, EntityDataSerializers.INT);
    /**
     * 爆散是否<b>已发生</b>。
     *
     * <p>它 MUST 是独立的一位，<b>MUST NOT</b> 用「编队帧没了」来代替：帧的缺失有歧义——
     * 「爆散后被解除」与「从来没绑上」是两件事，而后者会让弹从出生起就被判为已结算，
     * 于是速率曲线与换向双双被跳过（症状：花一路直飞，既不停也不散）。
     */
    private static final EntityDataAccessor<Boolean> DATA_BURST_FIRED =
            SynchedEntityData.defineId(AbstractDanmakuProjectile.class, EntityDataSerializers.BOOLEAN);

    /**
     * 位置纠偏阈值（平方）。<b>已不再使用</b>——判据改为速度相对
     * （见 {@link DanmakuCorrection#accepts} 与 {@link #lerpTo}）。固定阈值与速度无关，
     * 会把弹幕按 {@code v = 1/δ} 劈成「永久滞后」与「每包硬拽」两种失败。
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

    // ------------------------------------------------------------------
    // 年龄
    // ------------------------------------------------------------------

    /**
     * 本弹的年龄（tick）——<b>全部运动学与终止判据的唯一自变量</b>。
     *
     * <p>三条求值路径，优先级从高到低：
     * <ol>
     *   <li>客户端已接受完整快照 → {@code anchorAge + (tickCount − 接受时的 tickCount)}</li>
     *   <li>否则按原有基准：服务端读存档恢复值，客户端读配对包值</li>
     * </ol>
     *
     * <p><b>第 ① 条里那个「减」是本次变更的关键</b>。旧写法无条件加满
     * {@code tickCount}，于是「实体被丢掉又重新拿到」时，客户端已经跑过的那些 tick
     * 会被<b>再叠加一次</b>到服务端给的年龄上 ⇒ 年龄凭空前跳 ⇒ 解析轨迹整体错位。
     * 用「接受快照时的本地计数」作参照点，重复包幂等、新包重锚，两者都不再叠加。
     */
    public int age() {
        if (this.level().isClientSide && this.ageAnchored) {
            // 除以速率：客户端的本地 tick 未必与服务器游戏时间同速，而年龄跟随的是
            // 服务器时间。速率 1.0（时钟尚未建立）时与旧写法逐位相同。
            return DanmakuAge.at(this.anchorAge, this.tickCount - this.anchorTick,
                    com.bitsson.gensokyou.danmaku.render.DanmakuClientClock.rate());
        }
        return DanmakuAge.at(this.ageBasis(), this.tickCount);
    }

    /**
     * 本端的年龄基准。0 = 本次会话新发射；非 0 = 由存档或重新获取而来。
     *
     * <p>两侧判据同构，故诊断读数可跨端直接对比。
     */
    public int ageBasis() {
        return this.level().isClientSide ? this.peerAge : this.restoredAge;
    }

    /**
     * 写入客户端年龄基准。仅由配对包处理器调用；收到后不再变更。
     *
     * <p>MUST NOT 走 {@code SynchedEntityData}：配对 bundle 携带的 entityData 是
     * {@code ServerEntity} 构造时的快照而非配对时刻的值，客户端首个 tick 会以未更新的
     * 基准调用 {@code setPos(解析位置)}，产生可见闪跳。
     */
    public void seedPeerAge(int age) {
        if (this.level().isClientSide) {
            if (this.peerAgeSeeded) {
                // 同一客户端实体被二次配对：本地 tickCount 仍在累加，而基准被改写为服务端
                // 此刻的年龄 ⇒ 客户端年龄凭空前跳。这是「客户端超前」的候选机制之一，
                // 故单独计数而不是静默覆盖。
                DanmakuBudget.recordRepeatSeed(this.hasFormationFrame());
            }
            this.peerAgeSeeded = true;
            if (this.peerAge != age) {
                this.peerAge = Math.max(0, age);
            }
        }
    }

    /** 弹的年龄来源分类，供诊断用。 */
    public static final int SOURCE_FRESH = 0;
    public static final int SOURCE_REBUILT = 1;
    /** 仅客户端：实体已存在却从未收到配对包——本机制失效的直接证据。 */
    public static final int SOURCE_UNSEEDED = 2;
    /** 仅客户端：年龄由完整快照锚定，时间对应关系已知。 */
    public static final int SOURCE_SNAPSHOT = 3;

    /**
     * 本弹的年龄来源。
     *
     * <p><b>三分类而非两分类</b>：用「基准非 0」判重建，在配对包没送达时会把失效的弹
     * 误判成「正常新发射」——而那恰恰是最需要被看见的失败。健康状态下
     * {@link #SOURCE_UNSEEDED} 应恒为 0。
     */
    public int ageSource() {
        if (this.level().isClientSide) {
            if (this.ageAnchored) {
                return SOURCE_SNAPSHOT;
            }
            if (!this.peerAgeSeeded) {
                return SOURCE_UNSEEDED;
            }
            return this.peerAge != 0 ? SOURCE_REBUILT : SOURCE_FRESH;
        }
        return this.restoredAge != 0 ? SOURCE_REBUILT : SOURCE_FRESH;
    }

    /** 客户端是否已收到过配对包。供诊断与测试断言。 */
    public boolean isPeerAgeSeeded() {
        return this.peerAgeSeeded;
    }

    // ------------------------------------------------------------------
    // 年龄（age）——全部运动学与终止判据的自变量。规则见 DanmakuAge。
    // ------------------------------------------------------------------

    /** 服务端：由存档恢复的年龄基准，只读权威。正常发射恒为 0。 */
    private int restoredAge = 0;

    /**
     * 服务端：绝对寿命的「出生游戏时间」。{@link DanmakuLifetime#UNSET_BIRTH} = 尚未初始化，
     * 将在首个服务端 tick / 首次判据调用时按「当前游戏时间 − 年龄」惰性补上。
     *
     * <p><b>为什么存在性要单独记游戏时间</b>：{@code age()} 由 tickCount 驱动，而超出模拟
     * 距离的区块不做实体 tick ⇒ 年龄冻结、寿命永不判到期。绝对寿命以服务端游戏时间为钟，
     * 冻结时间照常计入。二者解耦：年龄管运动连续性，本字段管存在性。
     */
    private long birthGameTime = DanmakuLifetime.UNSET_BIRTH;

    /** 客户端：本次配对时由服务端下发的年龄基准，收到后不再变更。 */
    private int peerAge = 0;

    /**
     * 客户端年龄是否已被写过；配合 {@link #peerAgeSeeds} 用于发现「重复配对」。
     */
    private boolean peerAgeSeeded = false;

    // ------------------------------------------------------------------
    // 客户端年龄锚点（danmaku-render-state）
    // ------------------------------------------------------------------

    /**
     * 客户端年龄是否由完整快照锚定。
     *
     * <p>与 {@link #peerAgeSeeded} 并存而不取代它：配对包是<b>单发</b>的轻量兜底
     * （只带年龄），快照才带时间锚点与运动状态。两者同时存在时以快照为准，
     * 因为只有它能同时解决「年龄」与「相位」两件事。
     */
    private boolean ageAnchored = false;

    /** 快照给出的年龄。 */
    private int anchorAge = 0;

    /** 接受快照时的本地 {@code tickCount}。年龄以它为参照点推进，而不是从 0 起算。 */
    private int anchorTick = 0;

    /** 本次追踪周期的服务端令牌。同一周期内重复推送 MUST 被幂等丢弃。 */
    private long trackingToken = Long.MIN_VALUE;

    /**
     * 服务端：已配对次数，与 {@link #trackingToken} 一起区分「新追踪周期」与「重复包」。
     */
    private int trackingPairings = 0;

    /**
     * 重复配对日志的全局配额。
     *
     * <p>计数 MUST 永远全量记（{@code /gs_boss danmaku} 的 {@code integrity[pairing]}），
     * 但日志 MUST 限量：一次故障能刷出成千上万行，把真正的其它日志埋掉，
     * 于是「这条诊断存在」反而变成了「没人能读到日志」。
     *
     * <p>配额用完就静默。真要看细节时按 {@code id} 单独查，而不是让全世界的日志陪葬。
     */
    private static final java.util.concurrent.atomic.AtomicInteger REPEAT_PAIRING_LOG_BUDGET =
            new java.util.concurrent.atomic.AtomicInteger(8);

    /**
     * 客户端状态容器（模拟历史 / 权威样本 / 视觉偏移 / 失步生命周期）。
     *
     * <p><b>刻意用普通字段而不是 {@code SynchedEntityData}</b>：它<b>只</b>在客户端
     * 存在，且是渲染层的私有状态。放进同步字段会让服务端也持有它，并让每一次
     * 偏移变化都变成一次网络写——那正是本变更要消灭的东西。
     */
    private com.bitsson.gensokyou.danmaku.render.DanmakuRenderState clientRenderState = null;

    /**
     * 最近一次写回的运动输入块。给子类在 {@link #onMotionParamsApplied()} 里读自己那几格。
     *
     * <p>只保存引用、不复制：写回是一趟性的，事后无需再访问。
     */
    private int[] lastAppliedParams = null;

    /**
     * 一次性触发守卫。刻意用<b>普通字段</b>而非同步字段：年龄连续之后双端在同一年龄上
     * 推进，故由年龄派生的守卫值天然一致。分裂/悬停的幂等性由此保证。
     *
     * <p><b>此模式仅适用于「可由年龄派生」的状态。</b>不可派生的状态
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
        builder.define(DATA_HAS_FRAME, false);
        builder.define(DATA_FRAME_CX, 0);
        builder.define(DATA_FRAME_CY, 0);
        builder.define(DATA_FRAME_CZ, 0);
        builder.define(DATA_FRAME_OX, 0);
        builder.define(DATA_FRAME_OY, 0);
        builder.define(DATA_FRAME_OZ, 0);
        builder.define(DATA_FRAME_AXIS_YAW, 0);
        builder.define(DATA_FRAME_AXIS_PITCH, 0);
        builder.define(DATA_FRAME_ROT_RATE, 0);
        builder.define(DATA_FRAME_SCALE_BASE, 1000);
        builder.define(DATA_FRAME_SCALE_AMP, 0);
        builder.define(DATA_FRAME_SCALE_PERIOD, 0);
        builder.define(DATA_FRAME_ORBIT_YAW, 0);
        builder.define(DATA_FRAME_ORBIT_PITCH, 0);
        builder.define(DATA_FRAME_ORBIT_RADIUS, 0);
        builder.define(DATA_FRAME_ORBIT_RATE, 0);
        builder.define(DATA_FRAME_ADVANCE_SPEED, 0);
        builder.define(DATA_BURST_AT, 0);
        builder.define(DATA_BURST_RADIAL, 0);
        builder.define(DATA_BURST_AIM, 0);
        builder.define(DATA_BURST_TARGET, -1);
        builder.define(DATA_BURST_FIRED, false);
        builder.define(DATA_HAS_PROFILE, false);
        builder.define(DATA_PHASE_PERIOD, 0);
        builder.define(DATA_PHASE_DUTY, 100);
        builder.define(DATA_PHASE_OFFSET, 0);
        builder.define(DATA_DIES_AT_ORIGIN, false);
        builder.define(DATA_AXIS_X, 0.0F);
        builder.define(DATA_AXIS_Y, 0.0F);
        builder.define(DATA_AXIS_Z, 1.0F);
        // 段式运动：全部默认值为「未使用」，故不上线、不占带宽。
        builder.define(DATA_RANDOM_SEED_COUNT, 0);
        builder.define(DATA_LEG_COUNT_AND_KIND, 0);
        for (EntityDataAccessor<Integer> seed : DATA_RANDOM_SEEDS) {
            builder.define(seed, 0);
        }
        for (EntityDataAccessor<Integer> leg : DATA_LEGS) {
            builder.define(leg, 0);
        }
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
        super.tick();

        if (this.age() > getLifetimeTicks()) {
            this.discard(RemovalCause.LIFETIME);
            return;
        }

        // 绝对寿命（服务端游戏时间）：冻结后恢复 tick 的弹会在这一 tick 立即判死，
        // 避免「远处冻结 → 玩家回来 → 成片复活继续飞」。仅服务端。
        if (!this.level().isClientSide && this.isExpiredByGameTime()) {
            this.discard(RemovalCause.EXPIRED);
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
        //
        // <p>「零速率」本身即判 stationary，而不只是悬停：速率曲线把速度压到 0 的
        // 期间（定住等待 N tick 再发射）位移同样为零，扫掠恒 MISS，于是弹会静默地
        // 穿过玩家而不掉血——玩家看到弹穿身而过却毫无反馈，读作判定坏了。
        // 故凡是使速率为零的成因都走 AABB 接触判伤，而不是只对悬停生效。
        boolean stationary = false;
        if (isHovering() && this.age() >= hoverTick()) {
            this.setDeltaMovement(Vec3.ZERO);
            stationary = true;
        } else if (this.hasSpeedProfile() && !this.hasFormationFrame()
                && !this.timedTurnSettled()
                && Math.abs(this.speedProfile().speedAt(this.age())) < 1.0E-6D) {
            // 速率曲线的零速段：速度已被下面的 profile 分支清零，这里只标记判伤方式。
            //
            // <p>刻意排除编队弹：编队弹的位置由帧每 tick 重新给出，「零速」不等于「不动」
            // （自转/呼吸段照样在动），此时按 stationary 提前 return 会冻结编队。
            stationary = true;
        }

        if (!this.splitFired && isSplitting() && this.age() >= splitTick()) {
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
        //
        // <p>爆散发生后 MUST NOT 再套曲线：爆散把曲线压到 0 的那一段用来「停住」，
        // 换向之后弹已改向并解除编队（或清掉曲线），若继续套曲线会把它重新按回 0
        // —— 表现为「花炸开了一下又缩回去停住」。
        if (this.hasSpeedProfile() && !this.timedTurnSettled()) {
            DanmakuSpeedProfile profile = speedProfile();
            velocity = alongAxis(velocity, profile.speedAt(this.age()));
            this.setDeltaMovement(velocity);
            if (this.entityData.get(DATA_DIES_AT_ORIGIN)
                    && profile.returnedToOrigin(this.age())) {
                this.discard(RemovalCause.RETURNED_TO_ORIGIN);
                return;
            }
        }

        // 段式运动：方向与速率都由「年龄 → 段」纯函数给出。
        //
        // <p>放在编队帧<b>之后</b>：编队帧每 tick 覆写位置，段式与之同时挂载时
        // 段式只是多余的一层，反过来放在前面会被编队帧覆盖掉。
        // 两者同时挂载属于内容表的误配，判档时 {@code DanmakuTrackKinds} 会判
        // {@code CLOSED_FORM}（位置由帧决定）。
        //
        // <p><b>纪律</b>：本分支只读 {@link #legMotion()} 的<b>构造期缓存</b>与年龄，
        // MUST NOT 读种子 accessor —— 段方向在生成时定死（见 {@code DanmakuLegMotion}
        // 的类注释）。这是本变更最容易被无声破坏的一条。
        //
        // <p>零速率段（悬停）走 AABB 接触判伤，与速率曲线的零速段同一条路径 ——
        // 否则「弹穿身而过却不掉血」会重新出现。
        DanmakuLegMotion legMotion = this.legMotion();
        if (legMotion != null && !this.hasFormationFrame()) {
            int age = this.age();
            if (legMotion.requiresServerDecisionAt(age) && !this.level().isClientSide) {
                // TARGET 段的方向依赖发射之后才发生的事实，只能由服务端在该段起始处
                // 下发一次权威快照。客户端 MUST NOT 自行求解（见 danmaku-leg-motion spec）。
                DanmakuLegTargetPush.request(this, legMotion);
            }
            Vec3 direction = legMotion.directionAt(age);
            double speed = legMotion.speedAt(age);
            velocity = direction.scale(speed);
            this.setDeltaMovement(velocity);
            if (Math.abs(speed) < 1.0E-6D) {
                stationary = true;
            }
        }

        // 编队帧接管位置：把速度换成「解析终点 − 当前坐标」，末尾那句 setPos 正好落在终点。
        //
        // <p>MUST 在扫掠<b>之前</b>改 velocity：扫掠读的是 getDeltaMovement()，
        // 若先扫后改，本 tick 的位移段与被检测的段就不是同一段，会漏判一次命中。
        //
        // <p>推进项沿用记录的初始轴而非当前速度——当前速度此刻已被编队帧覆写，
        // 拿它算推进等于自我反馈。
        if (this.hasFormationFrame()) {
            // 推进项的行程。**没有速率曲线时 MUST 回落到发射速度**，不能取 0：
            // 编队帧每 tick 都会用解析位置覆写 deltaMovement，于是「初速」在这一刻就失效了。
            // 取 0 的话，挂编队但用普通匀速行进的弹会在原地钉死——现象是「花完全不动、
            // 环只在平面里转」，而日志干净、无任何报错。
            //
            // 未修项（danmaku-age-continuity / design 决策 4）：advance 随年龄无界增长，
            // 本变更刻意不碰——修它要引入「总行程上限」或把推进项并入速率曲线，
            // 是另一个设计问题。
            int tick = this.age();
            double advance = this.hasSpeedProfile()
                    ? this.speedProfile().travelAt(tick)
                    : unscale(this.entityData.get(DATA_FRAME_ADVANCE_SPEED)) * tick;
            Vec3 next = this.framePositionThisTick().add(this.axis().scale(advance));
            velocity = next.subtract(this.position());
            this.setDeltaMovement(velocity);
        }

        // 径向爆散：到年龄后改写速度方向，并<b>解除编队帧的接管</b>。
        //
        // <p>解除是必需的：爆散之后每颗弹各走各的，若帧仍在每 tick 写位置，
        // 它们会被拽回队形里继续转 —— 现象是「炸开了又缩回去」。
        // 解除之后弹的位置由它自己的速度决定，回到「解析终点 − 当前坐标」的常规路径。
        if (this.isBursting() && !this.timedTurnSettled()) {
            Vec3 burst = this.burstVelocityThisTick();
            if (burst != null) {
                velocity = burst;
                this.setDeltaMovement(velocity);
            if (this.isReclaiming()) {
                // 重瞄没有编队帧可解除，故以「清掉曲线」为结算标记。
                this.entityData.set(DATA_HAS_PROFILE, false);
            } else {
                this.entityData.set(DATA_HAS_FRAME, false);
            }
            this.entityData.set(DATA_BURST_FIRED, true);

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
        configureSpeedProfile(profile, false);
    }

    /**
     * 挂速率曲线，并可声明「越过发射点即销毁」。
     *
     * @param diesAtOrigin 勾上后弹在越过发射点时销毁。默认不勾——反向加速本身
     *                     不蕴含销毁（编队花后撤就需要它继续飞）
     */
    public void configureSpeedProfile(DanmakuSpeedProfile profile, boolean diesAtOrigin) {
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
        this.entityData.set(DATA_DIES_AT_ORIGIN, diesAtOrigin);
     * 挂上编队帧。此后本弹的位置不再只由「初速 × tick」决定，而由
     * {@link FormationFrame} 的纯函数唯一确定。
     *
     * <p>MUST 在 {@code setDirection} <b>之后</b>调用：编队帧会接管位置，
     * 几何给的初速随即失效，留着它只会让人以为速度仍然算数。
     *
     * @param frame 编队帧（已烘入本弹的出生偏移与编队参考点）
     */
    // ---------------------------------------------------------------
    // 相位隐藏态（显隐轴）
    // ---------------------------------------------------------------

    /**
     * 设定相位隐藏。
     *
     * @param periodTicks 周期（tick）。≤ 0 关闭
     * @param duty        可见期占空比，(0,1]。1 = 恒可见
     * @param phaseOffset 相位偏移（tick），逐弹错峰用
     */
    public void configurePhaseHide(int periodTicks, double duty, int phaseOffset) {
        this.entityData.set(DATA_PHASE_PERIOD, Math.max(0, periodTicks));
        this.entityData.set(DATA_PHASE_DUTY,
                (int) Math.round(Math.min(1.0D, Math.max(0.0D, duty)) * 100.0D));
        this.entityData.set(DATA_PHASE_OFFSET, phaseOffset);
    }

    /**
     * 本 tick 是否处于隐藏态。
     *
     * <p>隐藏态下渲染 alpha 降至档案的 {@code hiddenAlpha}，且
     * {@link #canHitEntity} 对本弹返回 false——即
     * {@link DanmakuHitScan} 找不到任何命中，弹<b>既不判伤也不销毁</b>，
     * 且玩家可从其上直接穿过。
     *
     * <p><b>方块碰撞不受影响</b>：隐藏态只关掉实体判定，方块判定仍照常进行，
     * 故弹撞上方块仍会消失。
     */
    public boolean isHidden() {
        return DanmakuPhase.isHidden(this.age(), phasePeriodTicks(), phaseDuty(), phaseOffset());
    }

    /**
     * 命中判定的总闸：白名单 + 隐藏态。
     *
     * <p>「隐藏态不判伤且不销毁」的全部实现就在这里——本方法返回 false 后，
     * {@link DanmakuHitScan} 找不到实体命中，而<b>方块分支独立于本谓词</b>，
     * 故弹照常撞墙消失。这正是需求要的语义：隐藏 ≠ 无碰撞，只是不伤人。
     *
     * <p>放在基类而非球弹上：显隐是 {@code Behaviour} 的一轴，与弹种正交。
     * 留在球弹上会让「激光配相位隐藏」静默失效——行为被无声丢弃，
     * 现象是「激光一直亮着、完全没有闪烁」，且日志干净、没有任何报错。
     */
    @Override
    protected boolean canHitEntity(Entity target) {
        return !this.isHidden() && super.canHitEntity(target) && !this.isWhitelisted(target);
    }

    private int phasePeriodTicks() {
        return this.entityData.get(DATA_PHASE_PERIOD);
    }

    private double phaseDuty() {
        return this.entityData.get(DATA_PHASE_DUTY) / 100.0D;
    }

    private int phaseOffset() {
        return this.entityData.get(DATA_PHASE_OFFSET);
    }

    public void bindToFrame(FormationFrame frame) {
        if (frame == null) {
            return;
        }
        // <b>刻意不再判 {@code frame.active()}</b>：那个方法回答的是「平面内有没有动作」
        // （自转 / 呼吸 / 公转），而「要不要编队」已由声明侧 {@code Formation#active()} 决定。
        // 早先在这里再判一次，于是「只要参考点、不要平面内动作」的帧被静默丢弃——
        // 而径向爆散恰恰需要这种帧（爆散方向 = 弹自身位置 → 参考点的连线）。
        // 症状是弹既不减速（速率曲线分支被误判为已结算而跳过）也不爆散，一路直飞。
        //
        // 代价：一个全零参数的帧会被绑上并占 16 个同步整数。这是声明者主动要求的，
        // 而「声明了却绑不上」是更坏的失败模式。
        this.entityData.set(DATA_FRAME_CX, scale(frame.centerX()));
        this.entityData.set(DATA_FRAME_CY, scale(frame.centerY()));
        this.entityData.set(DATA_FRAME_CZ, scale(frame.centerZ()));
        this.entityData.set(DATA_FRAME_OX, scale(frame.offsetX()));
        this.entityData.set(DATA_FRAME_OY, scale(frame.offsetY()));
        this.entityData.set(DATA_FRAME_OZ, scale(frame.offsetZ()));
        this.entityData.set(DATA_FRAME_AXIS_YAW, (int) Math.round(frame.axisYawDeg()));
        this.entityData.set(DATA_FRAME_AXIS_PITCH, (int) Math.round(frame.axisPitchDeg()));
        this.entityData.set(DATA_FRAME_ROT_RATE, scale(frame.rotRateDegPerTick()));
        this.entityData.set(DATA_FRAME_SCALE_BASE, scale(frame.scaleBase()));
        this.entityData.set(DATA_FRAME_SCALE_AMP, scale(frame.scaleAmp()));
        this.entityData.set(DATA_FRAME_SCALE_PERIOD,
                (int) Math.round(frame.scalePeriodTicks()));
        this.entityData.set(DATA_FRAME_ORBIT_YAW, (int) Math.round(frame.orbitAxisYawDeg()));
        this.entityData.set(DATA_FRAME_ORBIT_PITCH, (int) Math.round(frame.orbitAxisPitchDeg()));
        this.entityData.set(DATA_FRAME_ORBIT_RADIUS, scale(frame.orbitRadius()));
        this.entityData.set(DATA_FRAME_ORBIT_RATE, scale(frame.orbitRateDegPerTick()));
        this.entityData.set(DATA_HAS_FRAME, true);
        // 记下发射速度：它是「没有速率曲线时」的推进速率来源。
        // 编队弹的 deltaMovement 下一 tick 就被解析位置覆写，届时已无从取回初速。
        this.entityData.set(DATA_FRAME_ADVANCE_SPEED, scale(this.getSpeed()));
        // 编队弹的「沿弹道推进」项要用**初始**方向，而速度每 tick 都会被编队帧覆写，
        // 故必须趁现在记下。若本弹同时挂了速率曲线，两者共用这一份轴。
        Vec3 velocity = this.getDeltaMovement();
        if (velocity.lengthSqr() > 1.0E-9D) {
            this.setAxis(velocity.normalize());
        }
    }

    /** 本弹是否挂了编队帧。 */
    public boolean hasFormationFrame() {
        return this.entityData.get(DATA_HAS_FRAME);
    }

    // ------------------------------------------------------------------
    // 径向爆散
    // ------------------------------------------------------------------

    /**
     * 挂上径向爆散。
     *
     * <p><b>要求本弹已挂编队帧</b>：爆散方向是「弹自身位置 → 参考点」的连线，
     * 而参考点就是编队帧的中心。没帧就没有参考点，径向无从定义——
     * 该约束由 {@code TrackLint} 静态拒绝，不靠运行期报错。
     *
     * @param atAge     爆散年龄（tick）
     * @param radialSpeed 径向爆散速率（格/tick）
     * @param aimSpeed  弹自身即参考点时改用「朝目标射出」的速率（格/tick）
     * @param targetId  目标实体 id；{@code <= 0} = 无目标（此时爆散退化为沿原方向）
     */
    public void configureBurst(int atAge, double radialSpeed, double aimSpeed, int targetId) {
        this.entityData.set(DATA_BURST_AT, Math.max(0, atAge));
        this.entityData.set(DATA_BURST_RADIAL, scale(radialSpeed));
        this.entityData.set(DATA_BURST_AIM, scale(aimSpeed));
        this.entityData.set(DATA_BURST_TARGET, targetId);
    }

    /** 爆散年龄（tick）。{@code <= 0} = 无爆散。 */
    public int burstAtTick() {
        return this.entityData.get(DATA_BURST_AT);
    }

    /** 本弹是否已挂径向爆散。 */
    public boolean isBursting() {
        return burstAtTick() > 0;
    }

    /**
     * 本弹是否走「定时重瞄」（而非径向爆散）。
     *
     * <p>两者共用同一组同步参数，判据是「径向速率为零」：爆散 MUST 有径向分量，
     * 重瞄的方向只由目标决定、没有径向分量。
     */
    public boolean isReclaiming() {
        return burstAtTick() > 0
                && Math.abs(unscale(this.entityData.get(DATA_BURST_RADIAL))) < 1.0E-6D;
    }

    /**
     * 定时换向是否<b>已结算</b>。
     *
     * <p>换向前半段的速率曲线把速度压到 0（「停住等待」），换向之后弹要带着新方向飞走，
     * 于是那条曲线必须<b>同时退场</b>，否则它会在下一 tick 把速度重新按回 0——
     * 现象是「花炸开了一下又缩回去停住」「环扑出来一下又定住」。
     *
     * <p>两种换向的<b>结算标记不同</b>，因为它们各自能解除的东西不同：
     * <ul>
     *   <li>径向爆散解除<b>编队帧</b>（爆散之后弹各走各的，帧不该再写位置）；</li>
     *   <li>定时重瞄<b>没有帧可解除</b>，故以「曲线被清除」为标记。</li>
     * </ul>
     * 早先两者都用「帧没了」判定，于是重瞄弹（本来就没帧）在出生当 tick 就被判为已结算——
     * 曲线从未生效、换向也永不触发，现象是「环一出生就朝你扑，且完全不减速」。
     */
    private boolean timedTurnSettled() {
        if (burstAtTick() <= 0) {
            return false;
        }
        return isReclaiming() ? !this.hasSpeedProfile() : burstFired();
    }

    /**
     * 爆散是否<b>已经发生</b>。
     *
     * <p>独立的一位同步位，<b>不是</b>「编队帧没了」——帧的缺失有歧义：
     * 「爆散后被解除」与「从来没绑上」是两件事。早先用后者当前者，
     * 于是一颗从没绑上帧的弹从出生 tick 起就被判为已结算，
     * 速率曲线与换向双双被跳过：花一路直飞，既不停也不散开。
     */
    private boolean burstFired() {
        return this.entityData.get(DATA_BURST_FIRED);
    }

    /**
     * 爆散当 tick 求出新的速度矢量；不该爆散时返回 {@code null}。
     *
     * <p><b>方向逐发不同</b>，因为它取「弹自身位置 → 参考点」的连线。
     * 弹恰好落在参考点上（花心）时连线退化，此时改用「朝目标射出」——
     * 这条退化不是特例分支，而是同一条规则的边界：参考点自身没有「远离自己」的方向。
     */
    private Vec3 burstVelocityThisTick() {
        int at = burstAtTick();
        if (at <= 0 || this.age() < at || timedTurnSettled()) {
            return null;
        }
        double aimSpeed = unscale(this.entityData.get(DATA_BURST_AIM));
        if (isReclaiming()) {
            Vec3 toward = directionToBurstTarget();
            return toward == null ? null : toward.scale(aimSpeed);
        }
        Vec3 reference = frameCenterThisTick();
        Vec3 offset = this.position().subtract(reference);
        if (offset.lengthSqr() > 1.0E-4D) {
            return offset.normalize().scale(unscale(this.entityData.get(DATA_BURST_RADIAL)));
        }
        // 弹自身即参考点（花心）：连线退化，改按「朝目标射出」。
        // 这不是特例分支，而是同一条规则的边界——参考点自身没有「远离自己」的方向。
        Vec3 toward = directionToBurstTarget();
        return toward == null ? null : toward.scale(aimSpeed);
    }

    /** 编队参考点在本 tick 的世界坐标 = 帧中心 + 沿弹道的推进量。 */
    private Vec3 frameCenterThisTick() {
        Vec3 center = new Vec3(
                unscale(this.entityData.get(DATA_FRAME_CX)),
                unscale(this.entityData.get(DATA_FRAME_CY)),
                unscale(this.entityData.get(DATA_FRAME_CZ)));
        int tick = this.age();
        double advance = this.hasSpeedProfile()
                ? this.speedProfile().travelAt(tick)
                : unscale(this.entityData.get(DATA_FRAME_ADVANCE_SPEED)) * tick;
        return center.add(this.axis().scale(advance));
    }

    /** 朝爆散目标的方向；无目标或目标已不在世界里时返回 {@code null}。 */
    private Vec3 directionToBurstTarget() {
        int id = this.entityData.get(DATA_BURST_TARGET);
        if (id <= 0) {
            return null;
        }
        Entity target = this.level().getEntity(id);
        if (target == null) {
            return null;
        }
        Vec3 to = target.position().subtract(this.position());
        return to.lengthSqr() < 1.0E-6D ? null : to.normalize();
    }

    /** 读回当前编队帧。 */
    public FormationFrame formationFrame() {
        return new FormationFrame(
                unscale(this.entityData.get(DATA_FRAME_CX)),
                unscale(this.entityData.get(DATA_FRAME_CY)),
                unscale(this.entityData.get(DATA_FRAME_CZ)),
                unscale(this.entityData.get(DATA_FRAME_OX)),
                unscale(this.entityData.get(DATA_FRAME_OY)),
                unscale(this.entityData.get(DATA_FRAME_OZ)),
                this.entityData.get(DATA_FRAME_AXIS_YAW),
                this.entityData.get(DATA_FRAME_AXIS_PITCH),
                unscale(this.entityData.get(DATA_FRAME_ROT_RATE)),
                unscale(this.entityData.get(DATA_FRAME_SCALE_BASE)),
                unscale(this.entityData.get(DATA_FRAME_SCALE_AMP)),
                this.entityData.get(DATA_FRAME_SCALE_PERIOD),
                this.entityData.get(DATA_FRAME_ORBIT_YAW),
                this.entityData.get(DATA_FRAME_ORBIT_PITCH),
                unscale(this.entityData.get(DATA_FRAME_ORBIT_RADIUS)),
                unscale(this.entityData.get(DATA_FRAME_ORBIT_RATE)));
    }

    /**
     * 编队帧在本 tick 贡献的<b>位置</b>（不含沿弹道推进那一项）。
     *
     * <p>直接用 {@link #age()}，MUST NOT 再对本 tick 编号做加减换算。
     *
     * <p><b>为什么是 age() 而不是 tickCount ± 1</b>：原版 {@code Entity.tick()} 与
     * {@code baseTick()} <b>都不</b>自增 {@code tickCount}——自增发生在
     * {@code Level.tickNonPassenger} 调用 {@code entity.tick()} <b>之前</b>
     * （{@code ServerLevel} 与 {@code ClientLevel} 皆如此）。故在本方法所处的 tick 体内，
     * {@code tickCount} <b>已经是本 tick 的编号</b>，再加一会让整条编队轨迹偏一 tick，
     * 且 {@code framePositionAt(0)} 永远用不上（「出生即收拢」这个既定语义随之失效）。
     */
    private Vec3 framePositionThisTick() {
        return this.formationFrame().framePositionAt(this.age());
    }

    private static int scale(double value) {
        return (int) Math.round(value * PROFILE_SCALE);
    }

    private static double unscale(int value) {
        return value / PROFILE_SCALE;
    }

    /**
     * 带死因的回收。
     *
     * <p><b>所有主动回收 MUST 走这个重载</b>，而不是原版无参 {@code discard()}。
     * 原版那条路最终落到 {@code remove(DISCARDED)}，于是「寿命到期」「撞方块」
     * 「撞玩家」「分裂」在诊断里完全同形 —— 一次「弹幕莫名消失」能被解释成任何一种，
     * 而猜错方向比查不出来更贵。
     */
    protected void discard(DanmakuBudget.RemovalCause cause) {
        if (this.level() instanceof ServerLevel) {
            DanmakuBudget.recordRemoval(cause, this.age());
        }
        this.discard();
    }

    /** 主动触发分裂。仅服务端有意义（子弹是服务端新建实体），两端都会执行以保持时序一致。 */
    private void fireSplit() {
        int count = Math.max(2, splitCount());
        this.spawnSplitChildren(count);
        this.discard(RemovalCause.SPLIT);
    }

    protected void spawnSplitChildren(int count) {
    }

    private void tickMine() {
        if (!(this.level() instanceof ServerLevel server) || this.age() < MINE_ARM_TICKS) {
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
        this.discard(RemovalCause.MINE);
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
     * 原版位置包入口。<b>本方法 MUST NOT 改写模拟位置。</b>
     *
     * <p>改前的实现是：误差超过「速度 × 滞后窗口」就 {@code setPos} 硬拽。对解析式
     * 弹（编队帧 / 曲射）这构成一个正反馈：
     *
     * <pre>
     *   硬拽 ⇒ position 被换成服务端坐标
     *         ⇒ 本弹下一 tick 的速度 = 解析终点 − 被换掉的坐标（一条巨大向量）
     *         ⇒ 误差投影失真 ⇒ 看起来更大 ⇒ 更该硬拽
     * </pre>
     *
     * <p>实测症状正是这个环：rebuilt 弹的偏移在 {@code −4} 与 {@code +8} 之间双峰翻转，
     * 而恒定偏移不可能产生符号翻转；现象读作「每几个 tick 被拽一下」。
     *
     * <p><b>为什么不能只调阈值</b>：阈值只能判断误差大小，无法阻止「纠偏污染下一 tick
     * 运动段」这件事本身。位置纠偏与模拟推进 MUST 是两件事。
     *
     * <p>本包没有服务器采样时刻，既分不清「正常传输延迟」与「年龄基准错位」，也无法在
     * 曲射弹上反推唯一相位，所以它<b>只</b>进诊断层。真正的纠偏数据来自带时间的校准样本
     * （{@link #applyCalibrationSample}），真正能改写模拟坐标的只有完整快照
     * （{@link #applyDanmakuSnapshot}）。
     */
    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps) {
        if (!this.level().isClientSide) {
            return;
        }
        Vec3 sample = new Vec3(x, y, z);
        Vec3 error = sample.subtract(this.position());
        Vec3 velocity = this.getDeltaMovement();
        this.clientRenderState().recordLegacyPosition(sample, this.position());
        DanmakuSyncStats.recordLegacySample();

        // 投影滞后仍然记录，但它是「空间误差在速度方向上的读数」，
        // <b>不是</b>两端年龄的直接差值。取年龄差需要时间锚点，见 DanmakuSampleCheck。
        double speedSqr = velocity.lengthSqr();
        if (speedSqr > 1.0E-9D) {
            double lagTicks = error.dot(velocity) / speedSqr;
            DanmakuBudget.recordLag(lagTicks);
            DanmakuBudget.recordAgeOffset(lagTicks, this.ageSource());
            DanmakuBudget.recordAgeValue(this.age());
        }
    }

    // ------------------------------------------------------------------
    // 客户端状态：模拟 / 权威样本 / 渲染（danmaku-render-state）
    // ------------------------------------------------------------------

    /**
     * 本弹的客户端状态容器。首次访问时创建。
     *
     * <p>返回 {@code null} 表示服务端侧——服务端<b>没有</b>渲染状态，位置纠偏对它
     * 没有任何意义。
     */
    public DanmakuRenderState clientRenderState() {
        if (!this.level().isClientSide) {
            return null;
        }
        if (this.clientRenderState == null) {
            this.clientRenderState = new DanmakuRenderState(this.getUUID());
        }
        return this.clientRenderState;
    }

    /** 本帧的视觉偏移（格）。无状态时为零向量。 */
    public Vec3 renderOffset(float partialTick) {
        return this.clientRenderState == null
                ? Vec3.ZERO : this.clientRenderState.renderOffset(partialTick);
    }

    /**
     * 客户端每 tick 的表现更新。
     *
     * <p>挂在 {@link #baseTick()} 上而不是 {@code tickDanmaku()} 上：
     * {@code LaserDanmaku.tick()} 不走基类弹幕 tick，它直接调 {@code baseTick()}——
     * 挂错位置的表现是「球弹平滑、激光照旧按旧路径走」，且不报任何错。
     */
    @Override
    public void baseTick() {
        super.baseTick();
        if (!this.level().isClientSide) {
            return;
        }
        DanmakuRenderState state = this.clientRenderState();
        int revision = DanmakuMotionState.revisionFor(this.age());
        state.clientTick(this.tickCount, this.age(), revision,
                this.position(), this.getDeltaMovement());
        // 指纹不符 = 客户端手上的运动输入不是权威那一份。无论当前是否已判定失步，
        // 都要请求一次带参数的快照；否则参数永远修不回来。
        boolean needsParams = state.needsMotionParamRepair(this.motionFingerprint());
        if (needsParams) {
            state.markRepairRequested();
        }
        if ((state.resyncNeeded() || needsParams) && state.canRequestAgain(this.tickCount)) {
            state.noteRequestSent(this.tickCount);
            DanmakuResyncQueue.submit(this.getId(), needsParams);
        }
    }

    /**
     * 应用一份完整快照：<b>原子</b>替换模拟位置、速度、年龄锚点与（按需）运动输入。
     *
     * <p>顺序 MUST 是「先记下旧位置 → 改写 → 再让状态容器重锚」，因为画面连续性
     * 需要同时知道改前与改后的模拟位置。
     */
    public void applyDanmakuSnapshot(java.util.UUID incomingUuid, long token, long serverGameTime,
                                     int snapshotAge, int revision, int fingerprint,
                                     Vec3 position, Vec3 velocity, int[] motionParams) {
        if (!this.level().isClientSide) {
            return;
        }
        DanmakuRenderState state = this.clientRenderState();
        // 身份先于一切：实体 id 会复用，只有 UUID 能证明「这确实是同一枚弹」。
        if (!DanmakuMotionState.identityMatches(this.getUUID(), incomingUuid)) {
            DanmakuSyncStats.recordSnapshotRejected();
            return;
        }
        Vec3 oldPosition = this.position();
        if (motionParams != null && motionParams.length > 0) {
            this.applyMotionParams(motionParams);
        }
        this.trackingToken = token;
        this.anchorAge = snapshotAge;
        this.anchorTick = this.tickCount;
        this.ageAnchored = true;
        this.setDeltaMovement(velocity);
        this.setPos(position.x, position.y, position.z);
        this.updateRotationFromVelocity();
        boolean applied = state.acceptSnapshot(token, serverGameTime, snapshotAge, revision,
                fingerprint, oldPosition, position, this.tickCount);
        if (applied) {
            state.noteRequestDelivered();
            DanmakuSyncStats.recordSnapshotApplied();
        } else {
            DanmakuSyncStats.recordSnapshotDuplicate();
        }
    }

    /**
     * 收一条校准样本。返回是否升级到了需要恢复。
     *
     * <p>比较与诊断计数都在 {@link DanmakuRenderState#recordCalibration} 内完成，
     * 这里只负责把「速度相关的容差」算出来传下去——容差必须用弹自身速度，
     * 用固定阈值会把弹幕按 {@code v = 1/δ} 劈成「永久滞后」与「每包硬拽」两种失败。
     */
    public boolean applyCalibrationSample(long serverGameTime, int sampleAge, int sequence,
                                          int revision, Vec3 position) {
        if (!this.level().isClientSide) {
            return false;
        }
        double tolerance = DanmakuSampleCheck.toleranceBlocks(
                this.getDeltaMovement().length(),
                GensokyouConfig.DANMAKU_MAX_LAG_TICKS.get(),
                GensokyouConfig.DANMAKU_CORRECTION_FLOOR.get());
        return this.clientRenderState.recordCalibration(serverGameTime, sampleAge, sequence,
                revision, position, tolerance);
    }

    /**
     * 实体移除时清空客户端状态。
     *
     * <p>不清理的后果是下一次重追踪时，新实体会拿到一份属于上一次追踪周期的历史：
     * 旧 tick 编号被当成新编号、旧锚点被当成新锚点，于是「同刻比较」拿旧状态当新状态，
     * 得到一个纯属捏造的误差。
     */
    @Override
    public void remove(Entity.RemovalReason reason) {
        if (this.level().isClientSide && this.clientRenderState != null) {
            this.clientRenderState.clear();
            this.clientRenderState = null;
            this.ageAnchored = false;
            this.anchorAge = 0;
            this.anchorTick = 0;
            this.trackingToken = Long.MIN_VALUE;
        }
        super.remove(reason);
    }

    // ------------------------------------------------------------------
    // 运动输入块（快照的修复口径）
    // ------------------------------------------------------------------

    /**
     * 本弹的运动输入块。
     *
     * <p>布局见 {@link DanmakuMotionState}。子类的专属参数（激光）在
     * {@code P_LASER_BASE} 之后补齐，基类写 0——长度固定，编解码两侧不会错位。
     */
    public int[] motionParams() {
        int[] params = new int[DanmakuMotionState.PARAM_COUNT];
        params[DanmakuMotionState.P_FLAGS] = this.entityData.get(DATA_FLAGS);
        params[DanmakuMotionState.P_HOVER_TICK] = this.entityData.get(DATA_HOVER_TICK);
        params[DanmakuMotionState.P_SPLIT_TICK] = this.entityData.get(DATA_SPLIT_TICK);
        params[DanmakuMotionState.P_SPLIT_COUNT] = this.entityData.get(DATA_SPLIT_COUNT);
        params[DanmakuMotionState.P_CURVE_AXIS] = this.entityData.get(DATA_CURVE_AXIS);
        params[DanmakuMotionState.P_CURVE_RATE] =
                Float.floatToRawIntBits(this.entityData.get(DATA_CURVE_RATE));
        params[DanmakuMotionState.P_MINE_RADIUS] =
                Float.floatToRawIntBits(this.entityData.get(DATA_MINE_RADIUS));
        params[DanmakuMotionState.P_LIFETIME] = this.entityData.get(DATA_LIFETIME);
        params[DanmakuMotionState.P_HAS_PROFILE] = this.hasSpeedProfile() ? 1 : 0;
        int[] profile = {
                this.entityData.get(DATA_PROFILE_V0), this.entityData.get(DATA_PROFILE_P0),
                this.entityData.get(DATA_PROFILE_V1), this.entityData.get(DATA_PROFILE_P1),
                this.entityData.get(DATA_PROFILE_V2), this.entityData.get(DATA_PROFILE_P2),
                this.entityData.get(DATA_PROFILE_V3)};
        System.arraycopy(profile, 0, params, DanmakuMotionState.P_PROFILE_BASE,
                DanmakuMotionState.P_PROFILE_COUNT);
        params[DanmakuMotionState.P_DIES_AT_ORIGIN] =
                this.entityData.get(DATA_DIES_AT_ORIGIN) ? 1 : 0;
        params[DanmakuMotionState.P_AXIS_X] =
                Float.floatToRawIntBits(this.entityData.get(DATA_AXIS_X));
        params[DanmakuMotionState.P_AXIS_Y] =
                Float.floatToRawIntBits(this.entityData.get(DATA_AXIS_Y));
        params[DanmakuMotionState.P_AXIS_Z] =
                Float.floatToRawIntBits(this.entityData.get(DATA_AXIS_Z));
        params[DanmakuMotionState.P_HAS_FRAME] = this.hasFormationFrame() ? 1 : 0;
        int[] frame = {
                this.entityData.get(DATA_FRAME_CX), this.entityData.get(DATA_FRAME_CY),
                this.entityData.get(DATA_FRAME_CZ), this.entityData.get(DATA_FRAME_OX),
                this.entityData.get(DATA_FRAME_OY), this.entityData.get(DATA_FRAME_OZ),
                this.entityData.get(DATA_FRAME_AXIS_YAW), this.entityData.get(DATA_FRAME_AXIS_PITCH),
                this.entityData.get(DATA_FRAME_ROT_RATE), this.entityData.get(DATA_FRAME_SCALE_BASE),
                this.entityData.get(DATA_FRAME_SCALE_AMP), this.entityData.get(DATA_FRAME_SCALE_PERIOD),
                this.entityData.get(DATA_FRAME_ORBIT_YAW), this.entityData.get(DATA_FRAME_ORBIT_PITCH),
                this.entityData.get(DATA_FRAME_ORBIT_RADIUS), this.entityData.get(DATA_FRAME_ORBIT_RATE)};
        System.arraycopy(frame, 0, params, DanmakuMotionState.P_FRAME_BASE,
                DanmakuMotionState.P_FRAME_COUNT);
        params[DanmakuMotionState.P_FRAME_ADVANCE] =
                this.entityData.get(DATA_FRAME_ADVANCE_SPEED);
        params[DanmakuMotionState.P_PHASE_PERIOD] = phasePeriodTicks();
        params[DanmakuMotionState.P_PHASE_DUTY] = (int) Math.round(phaseDuty() * 100.0D);
        params[DanmakuMotionState.P_PHASE_OFFSET] = phaseOffset();
        params[DanmakuMotionState.P_BURST_AT] = this.entityData.get(DATA_BURST_AT);
        params[DanmakuMotionState.P_BURST_RADIAL] = this.entityData.get(DATA_BURST_RADIAL);
        params[DanmakuMotionState.P_BURST_AIM] = this.entityData.get(DATA_BURST_AIM);
        params[DanmakuMotionState.P_BURST_TARGET] = this.entityData.get(DATA_BURST_TARGET);
        params[DanmakuMotionState.P_BURST_FIRED] = this.entityData.get(DATA_BURST_FIRED) ? 1 : 0;
        // 段式运动：种子直接影响运动，MUST 被指纹覆盖 ——
        // 否则两端种子不同无法被检测，而校准只比位置，分叉要累积到肉眼可见才超容差。
        params[DanmakuMotionState.P_RANDOM_SEED_COUNT] = this.randomSeedCount();
        params[DanmakuMotionState.P_RANDOM_LEG_COUNT] = this.entityData.get(DATA_LEG_COUNT_AND_KIND);
        for (int i = 0; i < DanmakuRandomState.MAX_SEEDS; i++) {
            params[DanmakuMotionState.P_RANDOM_SEED_BASE + i] = this.randomSeed(i);
        }
        return params;
    }

    /** 把快照补发的运动输入块写回同步字段。仅客户端。 */
    public void applyMotionParams(int[] params) {
        if (params == null || params.length < DanmakuMotionState.PARAM_COUNT || !this.level().isClientSide) {
            return;
        }
        this.lastAppliedParams = params;
        this.entityData.set(DATA_FLAGS, (byte) params[DanmakuMotionState.P_FLAGS]);
        this.entityData.set(DATA_HOVER_TICK, params[DanmakuMotionState.P_HOVER_TICK]);
        this.entityData.set(DATA_SPLIT_TICK, params[DanmakuMotionState.P_SPLIT_TICK]);
        this.entityData.set(DATA_SPLIT_COUNT, params[DanmakuMotionState.P_SPLIT_COUNT]);
        this.entityData.set(DATA_CURVE_AXIS, params[DanmakuMotionState.P_CURVE_AXIS]);
        this.entityData.set(DATA_CURVE_RATE,
                Float.intBitsToFloat(params[DanmakuMotionState.P_CURVE_RATE]));
        this.entityData.set(DATA_MINE_RADIUS,
                Float.intBitsToFloat(params[DanmakuMotionState.P_MINE_RADIUS]));
        this.entityData.set(DATA_LIFETIME, Math.max(1, params[DanmakuMotionState.P_LIFETIME]));
        this.entityData.set(DATA_HAS_PROFILE, params[DanmakuMotionState.P_HAS_PROFILE] != 0);
        this.entityData.set(DATA_PROFILE_V0, params[DanmakuMotionState.P_PROFILE_BASE]);
        this.entityData.set(DATA_PROFILE_P0, params[DanmakuMotionState.P_PROFILE_BASE + 1]);
        this.entityData.set(DATA_PROFILE_V1, params[DanmakuMotionState.P_PROFILE_BASE + 2]);
        this.entityData.set(DATA_PROFILE_P1, params[DanmakuMotionState.P_PROFILE_BASE + 3]);
        this.entityData.set(DATA_PROFILE_V2, params[DanmakuMotionState.P_PROFILE_BASE + 4]);
        this.entityData.set(DATA_PROFILE_P2, params[DanmakuMotionState.P_PROFILE_BASE + 5]);
        this.entityData.set(DATA_PROFILE_V3, params[DanmakuMotionState.P_PROFILE_BASE + 6]);
        this.entityData.set(DATA_DIES_AT_ORIGIN, params[DanmakuMotionState.P_DIES_AT_ORIGIN] != 0);
        this.entityData.set(DATA_AXIS_X, Float.intBitsToFloat(params[DanmakuMotionState.P_AXIS_X]));
        this.entityData.set(DATA_AXIS_Y, Float.intBitsToFloat(params[DanmakuMotionState.P_AXIS_Y]));
        this.entityData.set(DATA_AXIS_Z, Float.intBitsToFloat(params[DanmakuMotionState.P_AXIS_Z]));
        this.entityData.set(DATA_HAS_FRAME, params[DanmakuMotionState.P_HAS_FRAME] != 0);
        this.setFrameParams(params);
        this.entityData.set(DATA_FRAME_ADVANCE_SPEED, params[DanmakuMotionState.P_FRAME_ADVANCE]);
        this.entityData.set(DATA_PHASE_PERIOD, Math.max(0, params[DanmakuMotionState.P_PHASE_PERIOD]));
        this.entityData.set(DATA_PHASE_DUTY, Mth.clamp(params[DanmakuMotionState.P_PHASE_DUTY], 0, 100));
        this.entityData.set(DATA_PHASE_OFFSET, params[DanmakuMotionState.P_PHASE_OFFSET]);
        this.entityData.set(DATA_BURST_AT, params[DanmakuMotionState.P_BURST_AT]);
        this.entityData.set(DATA_BURST_RADIAL, params[DanmakuMotionState.P_BURST_RADIAL]);
        this.entityData.set(DATA_BURST_AIM, params[DanmakuMotionState.P_BURST_AIM]);
        this.entityData.set(DATA_BURST_TARGET, params[DanmakuMotionState.P_BURST_TARGET]);
        this.entityData.set(DATA_BURST_FIRED, params[DanmakuMotionState.P_BURST_FIRED] != 0);
        // 段式运动输入。种子数先写、段表字节后写 —— 两者独立，不互相推断：
        // 「有种子无段表」与「有段表无种子」是两个不同的残缺存档，都要能读。
        this.entityData.set(DATA_RANDOM_SEED_COUNT, Math.max(0, Math.min(
                DanmakuRandomState.MAX_SEEDS, params[DanmakuMotionState.P_RANDOM_SEED_COUNT])));
        for (int i = 0; i < DanmakuRandomState.MAX_SEEDS; i++) {
            this.entityData.set(DATA_RANDOM_SEEDS[i], params[DanmakuMotionState.P_RANDOM_SEED_BASE + i]);
        }
        this.entityData.set(DATA_LEG_COUNT_AND_KIND, params[DanmakuMotionState.P_RANDOM_LEG_COUNT]);
        for (int i = 0; i < DanmakuLegMotion.MAX_LEGS; i++) {
            this.entityData.set(DATA_LEGS[i],
                    params[DanmakuMotionState.P_RANDOM_LEG_COUNT + 1 + i]);
        }
        // 同步字段变了 ⇒ 构造期缓存的方向不再对应当前输入，MUST 失效
        this.invalidateLegMotionCache();
        this.onMotionParamsApplied();
    }

    /**
     * 编队帧十六个定标整数的回写。抽成覆写点，便于子类在末尾补自己的专属字段。
     */
    protected void setFrameParams(int[] params) {
        int b = DanmakuMotionState.P_FRAME_BASE;
        this.entityData.set(DATA_FRAME_CX, params[b]);
        this.entityData.set(DATA_FRAME_CY, params[b + 1]);
        this.entityData.set(DATA_FRAME_CZ, params[b + 2]);
        this.entityData.set(DATA_FRAME_OX, params[b + 3]);
        this.entityData.set(DATA_FRAME_OY, params[b + 4]);
        this.entityData.set(DATA_FRAME_OZ, params[b + 5]);
        this.entityData.set(DATA_FRAME_AXIS_YAW, params[b + 6]);
        this.entityData.set(DATA_FRAME_AXIS_PITCH, params[b + 7]);
        this.entityData.set(DATA_FRAME_ROT_RATE, params[b + 8]);
        this.entityData.set(DATA_FRAME_SCALE_BASE, params[b + 9]);
        this.entityData.set(DATA_FRAME_SCALE_AMP, params[b + 10]);
        this.entityData.set(DATA_FRAME_SCALE_PERIOD, params[b + 11]);
        this.entityData.set(DATA_FRAME_ORBIT_YAW, params[b + 12]);
        this.entityData.set(DATA_FRAME_ORBIT_PITCH, params[b + 13]);
        this.entityData.set(DATA_FRAME_ORBIT_RADIUS, params[b + 14]);
        this.entityData.set(DATA_FRAME_ORBIT_RATE, params[b + 15]);
    }

    /** 运动输入块写回后的收尾钩子。子类在此补自己的专属字段（激光方向、长度、阶段）。 */
    protected void onMotionParamsApplied() {
    }

    /** 最近一次写回的运动输入块。可能为 null。 */
    protected int[] lastAppliedParams() {
        return this.lastAppliedParams;
    }

    /** 运动输入指纹。纯 int 运算，双端逐位一致。 */
    public int motionFingerprint() {
        return DanmakuMotionState.fingerprint(this.motionParams());
    }

    /** 本次追踪周期的令牌（服务端分配，客户端镜像）。 */
    public long trackingToken() {
        return this.trackingToken;
    }

    /** 服务端：记录一次配对并返回本周期的新令牌。 */
    public long noteTracking() {
        this.trackingPairings++;
        if (this.trackingPairings > 1) {
            // 计数永远记，日志只打开头几次
            DanmakuBudget.recordRepeatPairing(this.hasFormationFrame());
            if (REPEAT_PAIRING_LOG_BUDGET.getAndDecrement() > 0) {
                Gensokyou.LOGGER.info(
                        "[danmaku-track] REPEAT id={} pairing#{} age={} restored={} tick={} frame={}",
                        this.getId(), this.trackingPairings, this.age(),
                        this.restoredAge, this.tickCount, this.hasFormationFrame());
            }
        }
        // 令牌只在配对时前进：同一周期内重复推送保持不变，客户端据此幂等丢弃。
        this.trackingToken++;
        if (this.trackingToken == Long.MIN_VALUE) {
            this.trackingToken = 0L;
        }
        return this.trackingToken;
    }

    /**
     * 服务端：结束一个追踪周期。
     *
     * <p><b>为什么这个方法 MUST 存在</b>——{@code trackingPairings} 不清零的话，
     * 「重复配对」这个诊断把两件完全不同的事混在一起数：
     *
     * <ul>
     *   <li><b>异常</b>：同一追踪周期内 {@code StartTracking} 触发两次 —— 真 bug，
     *       客户端会被重新锚定一次，离散跳变就来自这里；</li>
     *   <li><b>正常</b>：玩家飞远 → 弹幕脱离追踪 → 玩家回来 → 重新配对。
     *       这在弹幕海战里每秒都在发生，而且完全正确。</li>
     * </ul>
     *
     * <p>两者累加出来的数字（本项目实测到 2727）无法回答「到底有没有 bug」这个问题，
     * 于是这条诊断本身就不可信了。实测 2727、而方法注释写着「健康状态 MUST 为 0」——
     * 这两件事同时成立唯一可能的解释是计数口径错了，而不是游戏有几千次重复配对。
     *
     * <p>清零的时机 MUST 是 {@code StopTracking}，而不是下一次 {@code StartTracking}：
     * 在后者清零的话，「第二次配对」和「上一周期的第一次配对」就无法区分。
     */
    public void endTrackingPeriod() {
        this.trackingPairings = 0;
    }

    /** 本追踪周期内这是第几次配对（服务端权威，客户端镜像）。 */
    public int trackingPairings() {
        return this.trackingPairings;
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

    // ---------------------------------------------------------------
    // 绝对寿命（存在性）：以服务端游戏时间为钟，见 DanmakuLifetime
    // ---------------------------------------------------------------

    /**
     * 本弹的绝对寿命（tick）。默认取常规寿命；子类可覆写
     * （激光 = 延迟 + 持续，插墙飞刀 = 插驻时长）。
     */
    protected int absoluteLifetimeTicks() {
        return this.getLifetimeTicks();
    }

    /** 服务端：已记录的出生游戏时间；未设置时为 {@link DanmakuLifetime#UNSET_BIRTH}。 */
    public long birthGameTime() {
        return this.birthGameTime;
    }

    /**
     * 服务端：取出生游戏时间；未设置时按「当前游戏时间 − 年龄」惰性补上。
     *
     * <p>惰性补上是旧存档缺键与「构造早于入世界」的统一回退：让弹从已恢复年龄处继续
     * 正常寿命，行为不劣于本变更之前。
     */
    public long resolveBirthGameTime() {
        if (this.birthGameTime == DanmakuLifetime.UNSET_BIRTH) {
            this.birthGameTime = DanmakuLifetime.fallbackBirth(this.level().getGameTime(), this.age());
        }
        return this.birthGameTime;
    }

    /** 服务端：把出生游戏时间重设为当前时刻（供延长寿命的路径使用，如插墙飞刀重新计时）。 */
    protected void resetBirthGameTimeNow() {
        this.birthGameTime = this.level().getGameTime();
    }

    /**
     * 是否已过绝对寿命。<b>仅服务端</b>返回有意义的值；客户端恒 false——客户端由服务端
     * 移除包收敛，且本地游戏时间与服务器不同步。
     */
    public boolean isExpiredByGameTime() {
        if (this.level().isClientSide) {
            return false;
        }
        long now = this.level().getGameTime();
        return DanmakuLifetime.overdue(now, this.resolveBirthGameTime(), this.absoluteLifetimeTicks());
    }

    /**
     * 服务端清理扫入口：若已过绝对寿命则带死因回收，返回是否已回收。
     *
     * <p>存在的理由：冻结弹自身不 tick，{@link #tickDanmaku()} 的自检不跑；清理扫是唯一
     * 能在玩家离开期间回收它们、释放实体计数上限（{@code DanmakuBudget}）的路径。
     */
    public boolean expireIfOverdue() {
        if (this.isExpiredByGameTime()) {
            this.discard(RemovalCause.EXPIRED);
            return true;
        }
        return false;
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
        // 径向爆散：爆散年龄与两个速率必须入盘。缺了它们，重载后的弹永远停在花上不炸开——
        // 而「不炸开」不报错、不崩，只是花一直悬在半空。
        if (this.isBursting()) {
            tag.putInt("BurstAt", burstAtTick());
            tag.putDouble("BurstRadial", unscale(this.entityData.get(DATA_BURST_RADIAL)));
            tag.putDouble("BurstAim", unscale(this.entityData.get(DATA_BURST_AIM)));
            tag.putInt("BurstTarget", this.entityData.get(DATA_BURST_TARGET));
            tag.putBoolean("BurstFired", this.entityData.get(DATA_BURST_FIRED));
        }
        // 年龄：存的是「保存那一刻的真实年龄」，不是基准。读档时 tickCount 归零，
        // 基准必须补上这个差，否则双端自变量从此不等（位置存了、年龄没存）。
        // 缺键时读入 0，即退化为本变更之前的行为——不比迁移前更差。
        tag.putInt("Age", this.age());
        // 出生游戏时间：绝对寿命的锚点。写解析后的值，保证冻结弹重载后仍按真实经过时间判死。
        tag.putLong("BirthGameTime", this.resolveBirthGameTime());
        if (DanmakuAge.axisNeedsPersistence(this.hasSpeedProfile(), this.hasFormationFrame())) {
            // 速率曲线与其轴：7 个 double + 3 个轴分量。
            // 缺了它们，重载后的弹会沿原速直飞——返程弹变成永动机，
            // 而这种故障只在存档重进时显形，没人能把两者联系起来。
            //
            // 条件是「挂曲线 **或** 挂编队」而非「挂曲线」：bindToFrame 同样写方向轴，
            // 写盘条件若窄于写轴条件，「有帧无曲线」的弹读档后方向轴就回落 (0,0,1)，
            // 沿弹道推进项指向世界 +Z。单一写入点，避免同一 NBT 键写两遍。
            if (this.hasSpeedProfile()) {
                DanmakuSpeedProfile profile = this.speedProfile();
                tag.putBoolean("DiesAtOrigin", this.entityData.get(DATA_DIES_AT_ORIGIN));
                tag.putDouble("SpV0", profile.v0());
                tag.putDouble("SpP0", profile.p0());
                tag.putDouble("SpV1", profile.v1());
                tag.putDouble("SpP1", profile.p1());
                tag.putDouble("SpV2", profile.v2());
                tag.putDouble("SpP2", profile.p2());
                tag.putDouble("SpV3", profile.v3());
            }
            tag.putFloat("SpAxisX", this.entityData.get(DATA_AXIS_X));
            tag.putFloat("SpAxisY", this.entityData.get(DATA_AXIS_Y));
            tag.putFloat("SpAxisZ", this.entityData.get(DATA_AXIS_Z));
        }
        if (phasePeriodTicks() > 0) {
            tag.putInt("PhasePeriod", phasePeriodTicks());
            tag.putInt("PhaseDuty", this.entityData.get(DATA_PHASE_DUTY));
            tag.putInt("PhaseOffset", phaseOffset());
        }
        if (this.hasFormationFrame()) {
            // 12 个定标整数。缺了它们，重载后的编队弹会退回「各自直飞」，
            // 表现为「一整队弹在读档瞬间散架」——而那只在读档时显形，没人能联想到。
            tag.putInt("FrameCx", this.entityData.get(DATA_FRAME_CX));
            tag.putInt("FrameCy", this.entityData.get(DATA_FRAME_CY));
            tag.putInt("FrameCz", this.entityData.get(DATA_FRAME_CZ));
            tag.putInt("FrameOx", this.entityData.get(DATA_FRAME_OX));
            tag.putInt("FrameOy", this.entityData.get(DATA_FRAME_OY));
            tag.putInt("FrameOz", this.entityData.get(DATA_FRAME_OZ));
            tag.putInt("FrameAyaw", this.entityData.get(DATA_FRAME_AXIS_YAW));
            tag.putInt("FrameApitch", this.entityData.get(DATA_FRAME_AXIS_PITCH));
            tag.putInt("FrameRot", this.entityData.get(DATA_FRAME_ROT_RATE));
            tag.putInt("FrameSb", this.entityData.get(DATA_FRAME_SCALE_BASE));
            tag.putInt("FrameSa", this.entityData.get(DATA_FRAME_SCALE_AMP));
            tag.putInt("FrameSp", this.entityData.get(DATA_FRAME_SCALE_PERIOD));
            tag.putInt("FrameOyaw", this.entityData.get(DATA_FRAME_ORBIT_YAW));
            tag.putInt("FrameOpitch", this.entityData.get(DATA_FRAME_ORBIT_PITCH));
            tag.putInt("FrameOr", this.entityData.get(DATA_FRAME_ORBIT_RADIUS));
            tag.putInt("FrameOrate", this.entityData.get(DATA_FRAME_ORBIT_RATE));
            tag.putInt("FrameAdv", this.entityData.get(DATA_FRAME_ADVANCE_SPEED));
        }
        // 段式运动：种子与段表入盘，使「重载后轨迹与从未卸载时逐位相同」可成立。
        // 两类键**独立**写入与读取，MUST NOT 互相推断 —— 「有种子无段表」与
        // 「有段表无种子」都是合法的残缺存档（见读侧注释）。
        if (this.hasLegMotion()) {
            tag.putInt("RandomSeedCount", this.randomSeedCount());
            tag.putInt("LegCountAndKind", this.entityData.get(DATA_LEG_COUNT_AND_KIND));
            for (int i = 0; i < DanmakuRandomState.MAX_SEEDS; i++) {
                tag.putInt("RandomSeed" + i, this.randomSeed(i));
            }
            for (int i = 0; i < DanmakuLegMotion.MAX_LEGS; i++) {
                tag.putInt("Leg" + i, this.packedLeg(i));
            }
        }
        // 速度：只有「位置不由速度决定」的弹种之外才需要。
        //
        // <p>编队弹与速率曲线弹读档后能自愈（rig 覆写位置 / alongAxis 回落到方向轴），
        // 给它们写速度既没用又误导 —— 读的人会以为速度是它们的权威。
        // 剩下的<b>直线弹与曲射弹</b>的速度只存在于 deltaMovement 里，缺了它就永久冻结：
        // 四只 BOSS 的普通弹全是 Behaviour.NONE + 匀速直线，症状是「退出重进后原地
        // 不动，约 60 秒后按寿命消失」。见 DanmakuTrackKinds 的逐条依据。
        DanmakuTrackKinds.writeVelocity(tag, velocityPersistenceNeeded(),
                this.getDeltaMovement());
    }

    /**
     * 本弹是否需要把速度写入存档。判据的唯一真相在 {@link DanmakuTrackKinds}。
     *
     * <p>读侧也 MUST 用它：写侧按「挂帧或挂曲线」决定要不要写，读侧若按别的条件读，
     * 就会出现「写了没读」或「没写却读」—— 后者让一个陈旧存档把别人的速度安到这枚弹上。
     */
    private boolean velocityPersistenceNeeded() {
        return DanmakuTrackKinds.needsVelocityPersistence(
                this.isCurving(), this.hasSpeedProfile(), this.hasFormationFrame(),
                this.hasLegMotion());
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
        // 缺键（旧存档）时保持 0：读档得到的飞行中弹退化为本变更之前的行为。
        this.restoredAge = Math.max(0, tag.getInt("Age"));
        // 缺键（旧存档）⇒ 保持哨兵 ⇒ 首次判据时按「当前游戏时间 − 已恢复年龄」回退。
        this.birthGameTime = tag.contains("BirthGameTime")
                ? tag.getLong("BirthGameTime")
                : DanmakuLifetime.UNSET_BIRTH;
        this.entityData.set(DATA_DIES_AT_ORIGIN, tag.getBoolean("DiesAtOrigin"));
        if (tag.contains("PhasePeriod")) {
            this.entityData.set(DATA_PHASE_PERIOD, tag.getInt("PhasePeriod"));
            this.entityData.set(DATA_PHASE_DUTY, tag.getInt("PhaseDuty"));
            this.entityData.set(DATA_PHASE_OFFSET, tag.getInt("PhaseOffset"));
        }
        if (tag.contains("SpAxisX") || tag.contains("SpAxisY") || tag.contains("SpAxisZ")) {
            // 方向轴：写盘条件是「挂曲线 或 挂编队」，读入必须同宽，
            // 否则「有帧无曲线」的弹读档后回落 (0,0,1)，推进项指向世界 +Z。
            this.entityData.set(DATA_AXIS_X, tag.getFloat("SpAxisX"));
            this.entityData.set(DATA_AXIS_Y, tag.getFloat("SpAxisY"));
            this.entityData.set(DATA_AXIS_Z, tag.getFloat("SpAxisZ"));
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
            this.entityData.set(DATA_HAS_PROFILE, true);
        }
        if (tag.contains("FrameSp")) {
            this.entityData.set(DATA_FRAME_CX, tag.getInt("FrameCx"));
            this.entityData.set(DATA_FRAME_CY, tag.getInt("FrameCy"));
            this.entityData.set(DATA_FRAME_CZ, tag.getInt("FrameCz"));
            this.entityData.set(DATA_FRAME_OX, tag.getInt("FrameOx"));
            this.entityData.set(DATA_FRAME_OY, tag.getInt("FrameOy"));
            this.entityData.set(DATA_FRAME_OZ, tag.getInt("FrameOz"));
            this.entityData.set(DATA_FRAME_AXIS_YAW, tag.getInt("FrameAyaw"));
            this.entityData.set(DATA_FRAME_AXIS_PITCH, tag.getInt("FrameApitch"));
            this.entityData.set(DATA_FRAME_ROT_RATE, tag.getInt("FrameRot"));
            this.entityData.set(DATA_FRAME_SCALE_BASE, tag.getInt("FrameSb"));
            this.entityData.set(DATA_FRAME_SCALE_AMP, tag.getInt("FrameSa"));
            this.entityData.set(DATA_FRAME_SCALE_PERIOD, tag.getInt("FrameSp"));
            this.entityData.set(DATA_FRAME_ORBIT_YAW, tag.getInt("FrameOyaw"));
            this.entityData.set(DATA_FRAME_ORBIT_PITCH, tag.getInt("FrameOpitch"));
            this.entityData.set(DATA_FRAME_ORBIT_RADIUS, tag.getInt("FrameOr"));
            this.entityData.set(DATA_FRAME_ORBIT_RATE, tag.getInt("FrameOrate"));
        this.entityData.set(DATA_FRAME_ADVANCE_SPEED, tag.getInt("FrameAdv"));
        this.entityData.set(DATA_HAS_FRAME, true);
        }
        if (tag.contains("BurstAt")) {
            this.configureBurst(tag.getInt("BurstAt"), tag.getDouble("BurstRadial"),
                    tag.getDouble("BurstAim"), tag.getInt("BurstTarget"));
            // 缺键（旧存档）时保持 false：读档得到的爆散弹会重放一次爆散，
            // 表现为「已炸开的花瓣又聚回去炸一次」——比静默不重放更容易察觉，且不丢命。
            this.entityData.set(DATA_BURST_FIRED, tag.getBoolean("BurstFired"));
        }
        // 段式运动：逐键判存在，缺键退化为「未使用段式运动」。
        //
        // <p><b>两类键 MUST NOT 互相推断</b>：「有种子无段表」与「有段表无种子」
        // 是两个不同的残缺存档，各自有确定含义 ——
        // 前者是「抽了签但没写段表」（退化为无段运动，方向固定），
        // 后者是「有段表但没抽签」（段方向全部退化为常量种子 0 的结果）。
        // 任何一侧缺失都只降级自己那半，MUST NOT 让另一半消失。
        //
        // <p>缺键（旧存档）⇒ 段数为 0 ⇒ {@code hasLegMotion()} 为假 ⇒
        // 走与本变更之前完全一致的路径。
        if (tag.contains("LegCountAndKind")) {
            this.entityData.set(DATA_RANDOM_SEED_COUNT, Math.max(0, Math.min(
                    DanmakuRandomState.MAX_SEEDS, tag.getInt("RandomSeedCount"))));
            for (int i = 0; i < DanmakuRandomState.MAX_SEEDS; i++) {
                this.entityData.set(DATA_RANDOM_SEEDS[i], tag.contains("RandomSeed" + i)
                        ? tag.getInt("RandomSeed" + i) : 0);
            }
            this.entityData.set(DATA_LEG_COUNT_AND_KIND, tag.getInt("LegCountAndKind"));
            for (int i = 0; i < DanmakuLegMotion.MAX_LEGS; i++) {
                this.entityData.set(DATA_LEGS[i], tag.contains("Leg" + i) ? tag.getInt("Leg" + i) : 0);
            }
        }
        this.invalidateLegMotionCache();

        // 速度 MUST 最后读：判据依赖 DATA_HAS_FRAME / DATA_HAS_PROFILE，而这两个是
        // 上面按「键是否存在」<b>推断</b>出来的，提前读会拿到尚未推断的 false，
        // 于是给一枚编队弹安上本不该存在的速度。
        // 速度留在最后读：重算速度必须先于位置积分发生，
        // 而 `velocityPersistenceNeeded()` 依赖「是否挂了段式运动」——
        // 它读的是 `DATA_LEG_COUNT_AND_KIND`，故那个键 MUST 在本行之前读完。
        if (velocityPersistenceNeeded()) {
            Vec3 restored = DanmakuTrackKinds.readVelocity(tag, true);
            if (restored.lengthSqr() > 1.0E-12D) {
                this.setDeltaMovement(restored);
            }
        }
    }
}
