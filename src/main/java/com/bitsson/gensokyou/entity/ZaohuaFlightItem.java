package com.bitsson.gensokyou.entity;

import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.registry.ModEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * 源初造化飞行原料：服务端权威存在、双端按同一确定性曲线解算轨迹（OrbitYinYang 范式）。
 * 相位时钟取关卡绝对 gameTime（重载天然续算），服务端不移动实体、仅在会话异常时
 * 按曲线解算当前位置就地掉落（防吞件）；正常收尾由行为按 id 移除并投放产物。
 */
public class ZaohuaFlightItem extends ItemEntity {

    private static final String TAG_ORIGIN = "Origin";
    private static final String TAG_CORE = "Core";
    private static final String TAG_INDEX = "Index";
    private static final String TAG_TOTAL = "Total";
    private static final String TAG_DURATION = "Duration";
    private static final String TAG_SESSION = "Session";
    private static final String TAG_START = "StartGameTime";

    /** 曲线版本：两端不一致时最坏为视觉差，掉落以服务端解算为准。 */
    public static final int PATH_VERSION = 1;

    /** 全程绕核圈数。 */
    private static final double ROTATIONS = 1.5D;
    private static final Vector3f TRAIL_PURPLE = new Vector3f(0.62F, 0.32F, 0.86F);

    private static final EntityDataAccessor<Long> DATA_ORIGIN =
            SynchedEntityData.defineId(ZaohuaFlightItem.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> DATA_CORE =
            SynchedEntityData.defineId(ZaohuaFlightItem.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> DATA_INDEX =
            SynchedEntityData.defineId(ZaohuaFlightItem.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_TOTAL =
            SynchedEntityData.defineId(ZaohuaFlightItem.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_DURATION =
            SynchedEntityData.defineId(ZaohuaFlightItem.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_SESSION =
            SynchedEntityData.defineId(ZaohuaFlightItem.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> DATA_START =
            SynchedEntityData.defineId(ZaohuaFlightItem.class, EntityDataSerializers.LONG);

    public ZaohuaFlightItem(EntityType<? extends ItemEntity> type, Level level) {
        super(type, level);
    }

    public ZaohuaFlightItem(Level level, BlockPos origin, int index, int total,
                            BlockPos corePos, long sessionId, int durationTicks,
                            long startGameTime, ItemStack stack) {
        this(ModEntityTypes.ZAOHUA_FLIGHT_ITEM.get(), level);
        setItem(stack);
        setPos(origin.getX() + 0.5D, origin.getY() + 1.1D, origin.getZ() + 0.5D);
        entityData.set(DATA_ORIGIN, origin.asLong());
        entityData.set(DATA_CORE, corePos.asLong());
        entityData.set(DATA_INDEX, index);
        entityData.set(DATA_TOTAL, total);
        entityData.set(DATA_DURATION, durationTicks);
        entityData.set(DATA_SESSION, sessionId);
        entityData.set(DATA_START, startGameTime);
        setNoGravity(true);
        setInvulnerable(true);
        setPickUpDelay(durationTicks + 40);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ORIGIN, 0L);
        builder.define(DATA_CORE, 0L);
        builder.define(DATA_INDEX, 0);
        builder.define(DATA_TOTAL, 1);
        builder.define(DATA_DURATION, 100);
        builder.define(DATA_SESSION, 0L);
        builder.define(DATA_START, 0L);
    }

    /**
     * 确定性轨迹（双端同函数）：抬升至环绕面 → 绕核螺旋内收且升高 → 汇聚于核心上方。
     * progress∈[0,1]。
     */
    public static Vec3 pathPos(long coreLong, long originLong, double progress) {
        BlockPos core = BlockPos.of(coreLong);
        BlockPos origin = BlockPos.of(originLong);
        double cx = core.getX() + 0.5D;
        double cz = core.getZ() + 0.5D;
        double cy = core.getY();
        double px = origin.getX() + 0.5D;
        double pz = origin.getZ() + 0.5D;
        double py = origin.getY() + 1.1D;
        double t = Mth.clamp(progress, 0.0D, 1.0D);
        double r0 = Math.max(0.2D, Math.hypot(px - cx, pz - cz));
        double theta0 = Math.atan2(pz - cz, px - cx);
        double orbitY = cy + GensokyouConfig.ZAOHUA_ORBIT_HEIGHT.get();
        double convergeY = cy + GensokyouConfig.ZAOHUA_CONVERGE_Y.get();
        double lift = smoothstep(Mth.clamp(t / 0.35D, 0.0D, 1.0D));
        double climb = smoothstep(Mth.clamp((t - 0.5D) / 0.5D, 0.0D, 1.0D));
        double spiral = smoothstep(Mth.clamp((t - 0.15D) / 0.85D, 0.0D, 1.0D));
        double y = py + (orbitY - py) * lift + (convergeY - orbitY) * climb;
        double r = r0 * (1.0D - spiral);
        double angle = theta0 + spiral * Math.PI * 2D * ROTATIONS;
        return new Vec3(cx + r * Math.cos(angle), y, cz + r * Math.sin(angle));
    }

    private static double smoothstep(double x) {
        return x * x * (3.0D - 2.0D * x);
    }

    /** 当前会话进度 [0,1]（绝对 gameTime 相位，双端一致、重载续算）。 */
    private double progress() {
        long start = entityData.get(DATA_START);
        int duration = Math.max(1, entityData.get(DATA_DURATION));
        return (level().getGameTime() - start) / (double) duration;
    }

    /** 汇聚点（会话完成时服务端投放产物处）。 */
    public static Vec3 convergencePoint(BlockPos corePos) {
        return new Vec3(corePos.getX() + 0.5D,
                corePos.getY() + GensokyouConfig.ZAOHUA_CONVERGE_Y.get(),
                corePos.getZ() + 0.5D);
    }

    @Override
    public void tick() {
        // 不调 super.tick：无重力/无合并/无寿命衰减，位置由确定性曲线接管
        // （tickCount 由 Level#tickNonPassenger 两端统一自增，此处不重复）
        if (level().isClientSide) {
            Vec3 p = pathPos(entityData.get(DATA_CORE), entityData.get(DATA_ORIGIN), progress());
            setPos(p.x, p.y, p.z);
            setYRot((float) (level().getGameTime() * 8.0D % 360.0D));
            if (tickCount % 2 == 0) {
                level().addParticle(new DustParticleOptions(TRAIL_PURPLE, 1.1F),
                        p.x, p.y + 0.1D, p.z, 0.0D, 0.01D, 0.0D);
                level().addParticle(ParticleTypes.END_ROD,
                        p.x, p.y + 0.1D, p.z, 0.0D, 0.0D, 0.0D);
            }
        } else if (tickCount > entityData.get(DATA_DURATION) + 60
                || !sessionAlive()) {
            emergencyDrop();
        }
    }

    /**
     * 位置由客户端确定性曲线独占（纯表现，不同步）：忽略服务端位置包。
     * 1.21.1 的 {@code lerpTo} 直接 {@code setPos}（无插值）；原版 {@code ServerEntity}
     * 每 60 tick 无条件补发一次位置包（{@code tickCount % 60 == 0}），若不忽略，
     * 客户端飞行物会被拉回起点（祭品台）一帧再被曲线拉回。
     */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        // no-op：世界坐标仅由 tick() 的 pathPos 解算
    }

    /** 服务端会话是否仍在保护本代飞行实体。 */
    private boolean sessionAlive() {
        return level().getBlockEntity(BlockPos.of(entityData.get(DATA_CORE)))
                instanceof RitualCoreBlockEntity core
                && core.holdsFlightSession(entityData.get(DATA_SESSION));
    }

    /** 异常终止（结构破坏/会话换代/超时）：按曲线解算当前位置，就地掉落真实物品。 */
    private void emergencyDrop() {
        Vec3 p = pathPos(entityData.get(DATA_CORE), entityData.get(DATA_ORIGIN), progress());
        ItemStack stack = getItem().copy();
        if (!stack.isEmpty()) {
            ItemEntity drop = new ItemEntity(level(), p.x, p.y, p.z, stack);
            drop.setDeltaMovement(0.0D, 0.0D, 0.0D);
            drop.setPickUpDelay(20);
            level().addFreshEntity(drop);
        }
        discard();
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    protected boolean canAddPassenger(net.minecraft.world.entity.Entity passenger) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putLong(TAG_ORIGIN, entityData.get(DATA_ORIGIN));
        tag.putLong(TAG_CORE, entityData.get(DATA_CORE));
        tag.putInt(TAG_INDEX, entityData.get(DATA_INDEX));
        tag.putInt(TAG_TOTAL, entityData.get(DATA_TOTAL));
        tag.putInt(TAG_DURATION, entityData.get(DATA_DURATION));
        tag.putLong(TAG_SESSION, entityData.get(DATA_SESSION));
        tag.putLong(TAG_START, entityData.get(DATA_START));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_ORIGIN, tag.getLong(TAG_ORIGIN));
        entityData.set(DATA_CORE, tag.getLong(TAG_CORE));
        entityData.set(DATA_INDEX, tag.getInt(TAG_INDEX));
        entityData.set(DATA_TOTAL, tag.getInt(TAG_TOTAL));
        entityData.set(DATA_DURATION, tag.getInt(TAG_DURATION));
        entityData.set(DATA_SESSION, tag.getLong(TAG_SESSION));
        entityData.set(DATA_START, tag.getLong(TAG_START));
    }
}
