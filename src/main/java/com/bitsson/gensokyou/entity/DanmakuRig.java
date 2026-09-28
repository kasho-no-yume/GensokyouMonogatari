package com.bitsson.gensokyou.entity;

import com.bitsson.gensokyou.danmaku.motion.RigOrbit;
import com.bitsson.gensokyou.registry.ModEntityTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * 编队装置（rig）——一批弹共享的<b>环绕中心与运动参数</b>。
 *
 * <p>装置<b>无渲染、无碰撞、不判伤、不被拾取</b>。它在世界里唯一的作用是：
 * 让挂在它上面的弹能只凭「（装置网络 id, 自身相位角）」两个数就算出自己的位置。
 *
 * <p><b>为什么必须是实体</b>——因为它要承载 SynchedEntityData。弹的带宽需求是
 * 「每弹 2 个数」，前提是 rig 的参数只存<b>一份</b>。若把参数复制进每颗弹，
 * 48 颗弹就是 48 份，队形规模一大带宽就跟着涨，这条需求当场作废。
 * 实体自带的 id 同步与数据同步正好提供了「一份参数 + 无歧义归属」。
 *
 * <p><b>为什么装置不是 BOSS</b>——外层中心是装置上的三个标量而非实体引用。
 * 让 rig 依附 BOSS 实体会把「BOSS 传送了弹跟不跟」「BOSS 死了弹怎么办」全引进来，
 * 而编排只需要「第几 tick 转到哪」。装置的生命周期由符卡阶段管，与 BOSS 实体解耦。
 *
 * <p><b>位置是时间的纯函数</b>：见 {@link RigOrbit}。装置每 tick 用
 * 「解析终点 − 当前坐标」设置速度再交给原版位移，故位置<b>不累积误差</b>，
 * 且双端逐位一致。位置包因此只是安全网，丢掉也不影响正确性。
 */
public class DanmakuRig extends Entity {

    /** 位置定标：1/256 格。2 的幂，故 double 表示与还原都精确。 */
    private static final double POSITION_SCALE = 256.0D;
    /** 角度定标：1/1000 度/tick。 */
    private static final double RATE_SCALE = 1000.0D;
    /** 归属轨道号在 NBT/诊断里的占位值。 */
    public static final int NO_TRACK = -1;

    private static final EntityDataAccessor<Integer> DATA_TRACK =
            SynchedEntityData.defineId(DanmakuRig.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_CENTER_X =
            SynchedEntityData.defineId(DanmakuRig.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_CENTER_Y =
            SynchedEntityData.defineId(DanmakuRig.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_CENTER_Z =
            SynchedEntityData.defineId(DanmakuRig.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PLANE_YAW =
            SynchedEntityData.defineId(DanmakuRig.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PLANE_PITCH =
            SynchedEntityData.defineId(DanmakuRig.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ORBIT_RADIUS =
            SynchedEntityData.defineId(DanmakuRig.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ORBIT_HEIGHT =
            SynchedEntityData.defineId(DanmakuRig.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ORBIT_RATE =
            SynchedEntityData.defineId(DanmakuRig.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SPIN_RATE =
            SynchedEntityData.defineId(DanmakuRig.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_FORMATION_RADIUS =
            SynchedEntityData.defineId(DanmakuRig.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_FORMATION_HEIGHT =
            SynchedEntityData.defineId(DanmakuRig.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_FORMATION_YAW =
            SynchedEntityData.defineId(DanmakuRig.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_FORMATION_PITCH =
            SynchedEntityData.defineId(DanmakuRig.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_LIFETIME =
            SynchedEntityData.defineId(DanmakuRig.class, EntityDataSerializers.INT);

    public DanmakuRig(EntityType<? extends DanmakuRig> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setInvulnerable(true);
    }

    public DanmakuRig(Level level, RigOrbit orbit, int trackIndex, int lifetimeTicks) {
        this(ModEntityTypes.DANMAKU_RIG.get(), level);
        applyOrbit(orbit);
        this.entityData.set(DATA_TRACK, trackIndex);
        this.entityData.set(DATA_LIFETIME, Math.max(1, lifetimeTicks));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_TRACK, NO_TRACK);
        builder.define(DATA_CENTER_X, 0);
        builder.define(DATA_CENTER_Y, 0);
        builder.define(DATA_CENTER_Z, 0);
        // 默认水平面：曲射轴约定下 (0,0) 是竖直面，水平环绕必须写 -90。
        builder.define(DATA_PLANE_YAW, 0);
        builder.define(DATA_PLANE_PITCH, (int) RigOrbit.HORIZONTAL_PITCH_DEG);
        builder.define(DATA_ORBIT_RADIUS, 0);
        builder.define(DATA_ORBIT_HEIGHT, 0);
        builder.define(DATA_ORBIT_RATE, 0);
        builder.define(DATA_SPIN_RATE, 0);
        builder.define(DATA_FORMATION_RADIUS, 0);
        builder.define(DATA_FORMATION_HEIGHT, 0);
        builder.define(DATA_FORMATION_YAW, 0);
        builder.define(DATA_FORMATION_PITCH, (int) RigOrbit.HORIZONTAL_PITCH_DEG);
        builder.define(DATA_LIFETIME, 200);
    }

    /**
     * 把轨道参数写进同步数据。
     *
     * <p><b>先量化再同步</b>：双端拿到的是同一批整数，故各自的推导结果逐位相同。
     * 若直接同步 float/double，两端的字面量可能相差一 ulp，乘上 tick 数后
     * 在几百 tick 外放大成肉眼可见的角度差。
     */
    public void applyOrbit(RigOrbit orbit) {
        this.entityData.set(DATA_CENTER_X, scale(orbit.centerX(), POSITION_SCALE));
        this.entityData.set(DATA_CENTER_Y, scale(orbit.centerY(), POSITION_SCALE));
        this.entityData.set(DATA_CENTER_Z, scale(orbit.centerZ(), POSITION_SCALE));
        this.entityData.set(DATA_PLANE_YAW, (int) Math.round(orbit.planeYawDeg()));
        this.entityData.set(DATA_PLANE_PITCH, (int) Math.round(orbit.planePitchDeg()));
        this.entityData.set(DATA_ORBIT_RADIUS, scale(orbit.orbitRadius(), POSITION_SCALE));
        this.entityData.set(DATA_ORBIT_HEIGHT, scale(orbit.orbitHeight(), POSITION_SCALE));
        this.entityData.set(DATA_ORBIT_RATE, scale(orbit.orbitRateDegPerTick(), RATE_SCALE));
        this.entityData.set(DATA_SPIN_RATE, scale(orbit.spinRateDegPerTick(), RATE_SCALE));
        this.entityData.set(DATA_FORMATION_RADIUS, scale(orbit.formationRadius(), POSITION_SCALE));
        this.entityData.set(DATA_FORMATION_HEIGHT, scale(orbit.formationHeight(), POSITION_SCALE));
        this.entityData.set(DATA_FORMATION_YAW, (int) Math.round(orbit.formationYawDeg()));
        this.entityData.set(DATA_FORMATION_PITCH, (int) Math.round(orbit.formationPitchDeg()));
    }

    /**
     * 本装置当前的轨道参数。
     *
     * <p>双端都只读自己的同步数据重建，<b>不读对方</b>，故不依赖任何跨端调用顺序。
     */
    public RigOrbit orbit() {
        return new RigOrbit(
                unscale(this.entityData.get(DATA_CENTER_X), POSITION_SCALE),
                unscale(this.entityData.get(DATA_CENTER_Y), POSITION_SCALE),
                unscale(this.entityData.get(DATA_CENTER_Z), POSITION_SCALE),
                this.entityData.get(DATA_PLANE_YAW),
                this.entityData.get(DATA_PLANE_PITCH),
                unscale(this.entityData.get(DATA_ORBIT_RADIUS), POSITION_SCALE),
                unscale(this.entityData.get(DATA_ORBIT_HEIGHT), POSITION_SCALE),
                unscale(this.entityData.get(DATA_ORBIT_RATE), RATE_SCALE),
                unscale(this.entityData.get(DATA_SPIN_RATE), RATE_SCALE),
                unscale(this.entityData.get(DATA_FORMATION_RADIUS), POSITION_SCALE),
                unscale(this.entityData.get(DATA_FORMATION_HEIGHT), POSITION_SCALE),
                this.entityData.get(DATA_FORMATION_YAW),
                this.entityData.get(DATA_FORMATION_PITCH));
    }

    /** 本装置归属的轨道号。 */
    public int trackIndex() {
        return this.entityData.get(DATA_TRACK);
    }

    /** 弹是否仍应挂在它上面。装置一到寿命即自杀，弹据此脱钩。 */
    public boolean expired() {
        return this.tickCount >= this.entityData.get(DATA_LIFETIME);
    }

    @Override
    public void tick() {
        // 位置同样走「解析终点 − 当前坐标」：既让原版位移照常工作（复用同一套
        // 碰撞/推挤管线），又保证位置每 tick 被重置成解析值，不累积误差。
        RigOrbit orbit = orbit();
        net.minecraft.world.phys.Vec3 next = orbit.rigPositionAt(this.tickCount + 1);
        this.setDeltaMovement(next.subtract(this.position()));
        super.tick();
        if (this.expired()) {
            this.discard();
        }
    }

    // ---------------------------------------------------------------
    // 「看不见、碰不到、不伤人」
    // ---------------------------------------------------------------

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return false;
    }

    /**
     * 不可碰撞。
     *
     * <p>1.21.1 的签名是<b>无参</b>的（早期教程里的 {@code canBeCollidedWith(Entity)} 已不存在）。
     */
    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    // ---------------------------------------------------------------
    // 持久化
    // ---------------------------------------------------------------

    /**
     * 轨道参数全在 {@code entityData} 里，由 {@code SynchedEntityData} 自行写入/读出
     * （vanilla 的 {@code save}/{@code restore} 已包办这一段），故本类无需额外落盘任何标量。
     *
     * <p>{@link Entity} 把这两个方法声明为<b>抽象</b>（方法体为空），所以这里
     * <b>不能</b>调 {@code super}——直接实现为空即可。
     */
    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    private static int scale(double value, double scale) {
        return (int) Math.round(value * scale);
    }

    private static double unscale(int value, double scale) {
        return value / scale;
    }
}
