package com.bitsson.gensokyou.entity;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.registry.ModEntityTypes;
import com.bitsson.gensokyou.spirit.ModAttachments;
import com.bitsson.gensokyou.spirit.SpiritPowerData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 灵力引爆器：可放置的引爆物，右键开 GUI 设定参数后消耗玩家灵力启动。
 *
 * <p><b>与 {@code PrimedTnt} 的关系</b>：刻意不复用 TNT 实体——它要自己的参数
 * （起爆时间 / 强度 / 半径三项可调）与自己的扣费口径（玩家灵力池而非掉落物），
 * 复用只会被 TNT 的 fuse 语义绑住。但爆炸本身仍走原版 {@code Level.explode}，
 * 保证伤害 / 粒子 / 音效的表现与原版一致。
 *
 * <p><b>三态</b>：
 * <ul>
 *   <li>{@link #STATE_DORMANT} 沉睡：未设定参数，可被破坏收回；</li>
 *   <li>{@link #STATE_ARMED} 待爆：已收到启动指令、正在倒计时，不可收回；</li>
 *   <li>已引爆：实体自行移除，不给回物品。</li>
 * </ul>
 */
public class SpiritBombEntity extends Entity {

    public static final int STATE_DORMANT = 0;
    public static final int STATE_ARMED = 1;

    private static final EntityDataAccessor<Integer> DATA_STATE =
            SynchedEntityData.defineId(SpiritBombEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_FUSE =
            SynchedEntityData.defineId(SpiritBombEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_POWER =
            SynchedEntityData.defineId(SpiritBombEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_RADIUS =
            SynchedEntityData.defineId(SpiritBombEntity.class, EntityDataSerializers.INT);

    /** 启动者：用于扣费与"离线也能起爆"的归属判定。 */
    private java.util.UUID owner;

    public SpiritBombEntity(EntityType<? extends SpiritBombEntity> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_STATE, STATE_DORMANT);
        builder.define(DATA_FUSE, 0);
        builder.define(DATA_POWER, 4.0F);
        builder.define(DATA_RADIUS, 6);
    }

    // ------------------------------------------------------------------ 参数访问

    public int getBombState() {
        return this.entityData.get(DATA_STATE);
    }

    public void setBombState(int state) {
        this.entityData.set(DATA_STATE, state);
    }

    public int getFuseTicks() {
        return this.entityData.get(DATA_FUSE);
    }

    public void setFuseTicks(int ticks) {
        this.entityData.set(DATA_FUSE, Math.max(0, ticks));
    }

    public float getPower() {
        return this.entityData.get(DATA_POWER);
    }

    public void setPower(float power) {
        this.entityData.set(DATA_POWER, (float) clamp(power, 1.0D, 12.0D));
    }

    public int getBlastRadius() {
        return this.entityData.get(DATA_RADIUS);
    }

    public void setBlastRadius(int radius) {
        this.entityData.set(DATA_RADIUS, (int) clamp(radius, 1, 24));
    }

    public java.util.UUID getOwner() {
        return owner;
    }

    public void setOwner(java.util.UUID owner) {
        this.owner = owner;
    }

    // ------------------------------------------------------------------ 启动

    /**
     * 尝试启动：从启动者灵力池扣费，不足则拒绝。
     *
     * <p><b>全有全无</b>：先 {@code canCover} 再 {@code payCost}，同 tick 判定与扣费同口径，
     * 不会出现"显示够、扣下去失败"的半启动状态。
     */
    public boolean arm(ServerLevel level, ServerPlayer player) {
        if (getBombState() == STATE_ARMED) {
            return false;
        }
        long cost = estimatedCost();
        SpiritPowerData data = ModAttachments.get(player);
        if (data.current() < cost) {
            return false;
        }
        ModAttachments.set(player, data.withAddedCurrent(-cost));
        setOwner(player.getUUID());
        setBombState(STATE_ARMED);
        return true;
    }

    /** 估算灵力消耗：{@code BASE × (强度/4)² × (半径/6)}。 */
    public long estimatedCost() {
        long base = GensokyouConfig.SPIRIT_BOMB_BASE_COST.get();
        double powerFactor = Math.pow(getPower() / 4.0D, 2);
        double radiusFactor = getBlastRadius() / 6.0D;
        return Math.round(base * powerFactor * radiusFactor);
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        if (getBombState() != STATE_ARMED) {
            return;
        }
        int fuse = getFuseTicks();
        if (fuse <= 0) {
            detonate();
            return;
        }
        setFuseTicks(fuse - 1);
    }

    private void detonate() {
        if (!(level() instanceof ServerLevel server)) {
            discard();
            return;
        }
        // 全掉落爆炸：ExplosionInteraction.BLOCK 才会掉全部被毁方块
        server.explode(this, getX(), getY(), getZ(), getPower(), Level.ExplosionInteraction.BLOCK);
        // 半径由爆炸威力之外的第二参数控制，原版 API 不直接支持，故显式做二次清扫
        applyRadiusDamage(server, getBlastRadius());
        capDroppedProducts();
        discard();
    }

    /**
     * 掉落物实体数上限保护。
     *
     * <p><b>为什么必须有这一步</b>：{@code ExplosionInteraction.BLOCK} 会让每个被毁方块
     * 各自生成一个物品实体。半径 24、强度 12 的一次爆炸可以产生数千个实体，
     * 服务端在同一个 tick 里处理不了，直接卡死。这里在爆炸后的同一 tick 内
     * 把超出上限的部分丢并告警——保命优先于保物资。
     */
    private void capDroppedProducts() {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        int cap = GensokyouConfig.SPIRIT_BOMB_MAX_PRODUCTS.get();
        var items = server.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                getBoundingBox().inflate(getBlastRadius() + 4.0D));
        if (items.size() <= cap) {
            return;
        }
        int culled = 0;
        for (var item : items) {
            if (items.size() - culled <= cap) {
                break;
            }
            item.discard();
            culled++;
        }
        Gensokyou.LOGGER.warn("Spirit bomb at {} produced {} item entities; culled {} over the cap of {}",
                blockPosition(), items.size(), culled, cap);
    }

    /**
     * 半径的二次伤害。
     *
     * <p><b>为什么需要这一步</b>：原版 {@code explosion(power)} 的破坏范围由 power 单值决定，
     * 没有独立半径参数。用户要的是"强度与半径分开调"，
     * 故在既有爆炸之外按 radius 补一段伤害与击退。
     */
    private void applyRadiusDamage(ServerLevel level, int radius) {
        Vec3 center = position();
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(radius))) {
            double distance = target.distanceTo(this);
            if (distance > radius) {
                continue;
            }
            float falloff = (float) (1.0D - distance / Math.max(1, radius));
            target.hurt(level.damageSources().explosion(this, ownerEntity(level)),
                    getPower() * 2.0F * falloff);
            Vec3 knock = target.position().subtract(center).normalize().scale(falloff * 1.5D);
            target.push(knock.x, Math.max(0.2D, knock.y), knock.z);
        }
    }

    private Entity ownerEntity(ServerLevel level) {
        if (owner == null) {
            return this;
        }
        return level.getEntity(owner);
    }

    // ------------------------------------------------------------------ 掉落 / 交互

    /**
     * 沉睡态可收回：破坏即掉回物品；已启动不掉。
     *
     * <p>走 {@link #remove} 而不是 {@code hurt}/{@code kill}：
     * 后者会触发"实体死亡"的掉落表与音效链路，对本实体没有意义。
     */
    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!level().isClientSide && reason == Entity.RemovalReason.KILLED
                && getBombState() == STATE_DORMANT) {
            spawnAtLocation(new ItemStack(com.bitsson.gensokyou.registry.ModItems.SPIRIT_BOMB.get()));
        }
        super.remove(reason);
    }

    // ------------------------------------------------------------------ 持久化

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("BombState", getBombState());
        tag.putInt("FuseTicks", getFuseTicks());
        tag.putFloat("Power", getPower());
        tag.putInt("BlastRadius", getBlastRadius());
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setBombState(tag.getInt("BombState"));
        setFuseTicks(tag.getInt("FuseTicks"));
        setPower(tag.getFloat("Power"));
        setBlastRadius(tag.getInt("BlastRadius"));
        if (tag.hasUUID("Owner")) {
            owner = tag.getUUID("Owner");
        }
    }

    @Override
    protected boolean canAddPassenger(net.minecraft.world.entity.Entity passenger) {
        return false;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
