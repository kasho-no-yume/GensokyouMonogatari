package com.bitsson.gensokyou.entity.spell;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

/**
 * 玩家符卡持续型宿主实体基类（add-player-spellcards D4）。
 *
 * <p>持久化：宿主 UUID（可空=定点场）+ 剩余 tick + 半径；双端各自定位
 * （参照 {@code OrbitYinYangOrb}）。子类实现 {@link #serverTick(ServerLevel)} /
 * {@link #clientTick()} / {@link #onExpire(ServerLevel)}。
 */
public abstract class AbstractSpellFieldEntity extends Entity {

    private static final String TAG_HOST = "Host";
    private static final String TAG_TICKS_LEFT = "TicksLeft";
    private static final String TAG_RADIUS = "Radius";

    private static final EntityDataAccessor<Optional<UUID>> DATA_HOST =
            SynchedEntityData.defineId(AbstractSpellFieldEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Float> DATA_RADIUS =
            SynchedEntityData.defineId(AbstractSpellFieldEntity.class, EntityDataSerializers.FLOAT);

    /** 剩余寿命（tick）；子类构造时写入。 */
    protected int ticksLeft;

    protected AbstractSpellFieldEntity(EntityType<? extends AbstractSpellFieldEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setInvulnerable(true);
    }

    protected void setHost(Player host) {
        entityData.set(DATA_HOST, host == null ? Optional.empty() : Optional.of(host.getUUID()));
    }

    protected Player host() {
        return entityData.get(DATA_HOST).map(level()::getPlayerByUUID).orElse(null);
    }

    /** 宿主 UUID（无宿主为空）。 */
    public Optional<UUID> hostId() {
        return entityData.get(DATA_HOST);
    }

    protected void setRadius(float radius) {
        entityData.set(DATA_RADIUS, radius);
    }

    public float radius() {
        return entityData.get(DATA_RADIUS);
    }

    /** 剩余寿命 tick（客户端渲染淡出用）。 */
    public int remainingTicks() {
        return ticksLeft;
    }

    /**
     * 剔除箱按场半径放大：判定箱只有 0.1，若不放大，玩家稍一抬头/移开视线，
     * 视锥就剔除整座法阵/光墙/蛛网（渲染器根本不执行）。高度至少留到光墙上限。
     */
    @Override
    public net.minecraft.world.phys.AABB getBoundingBoxForCulling() {
        float r = Math.max(1F, radius());
        return super.getBoundingBoxForCulling().inflate(r, Math.max(r, 3.5D), r);
    }

    /**
     * 渲染距离上限：原版默认 = 判定箱尺寸 × 64，而本类判定箱仅 0.1 → 6.4 格外整场消失。
     * 按场半径放宽（半径 4~5 的场至少 60+ 格可见）。
     */
    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double limit = radius() * 4.0D + 56.0D;
        return distance < limit * limit;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_HOST, Optional.empty());
        builder.define(DATA_RADIUS, 4F);
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel serverLevel) {
            serverTick(serverLevel);
            if (--ticksLeft <= 0) {
                onExpire(serverLevel);
                discard();
            }
        } else {
            clientTick();
        }
    }

    /** 服务端逐 tick；寿命耗尽前每 tick 调用。 */
    protected void serverTick(ServerLevel level) {
    }

    /** 寿命耗尽（结算/清理）时调用一次。 */
    protected void onExpire(ServerLevel level) {
    }

    /** 客户端逐 tick（纯表现，禁止改世界）。 */
    protected void clientTick() {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        entityData.get(DATA_HOST).ifPresent(uuid -> tag.putUUID(TAG_HOST, uuid));
        tag.putInt(TAG_TICKS_LEFT, ticksLeft);
        tag.putFloat(TAG_RADIUS, entityData.get(DATA_RADIUS));
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID(TAG_HOST)) {
            entityData.set(DATA_HOST, Optional.of(tag.getUUID(TAG_HOST)));
        }
        this.ticksLeft = tag.getInt(TAG_TICKS_LEFT);
        entityData.set(DATA_RADIUS, tag.getFloat(TAG_RADIUS));
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }
}
