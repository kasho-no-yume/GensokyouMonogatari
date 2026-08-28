package com.bitsson.gensokyou.entity;

import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.registry.ModDamageTypes;
import com.bitsson.gensokyou.registry.ModEntityTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public class DanmakuProjectile extends ThrowableProjectile {
    private static final String TAG_DAMAGE = "Damage";
    private static final int MAX_LIFETIME_TICKS = 200;

    private float damage = 4.0F;

    public DanmakuProjectile(EntityType<? extends DanmakuProjectile> type, Level level) {
        super(type, level);
    }

    public DanmakuProjectile(Level level, LivingEntity owner, float damage) {
        super(ModEntityTypes.DANMAKU.get(), owner, level);
        this.damage = damage;
    }

    public static DanmakuProjectile basic(Level level, LivingEntity owner) {
        return new DanmakuProjectile(level, owner, GensokyouConfig.DANMAKU_BASE_DAMAGE.get().floatValue());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected double getDefaultGravity() {
        return 0D;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount > MAX_LIFETIME_TICKS) {
            discard();
        }
        if (level().isClientSide && tickCount % 2 == 0) {
            level().addParticle(ParticleTypes.END_ROD,
                    getX(), getY() + 0.1D, getZ(), 0D, 0D, 0D);
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (level() instanceof ServerLevel serverLevel) {
            Entity hit = result.getEntity();
            if (hit != getOwner() && hit.isAlive()) {
                hit.hurt(ModDamageTypes.danmaku(hit, getOwner()), damage);
            }
            discard();
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (level() instanceof ServerLevel) {
            discard();
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (level() instanceof ServerLevel && result.getType() == HitResult.Type.MISS) {
            discard();
        }
    }

    public void setDamage(float damage) {
        this.damage = damage;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat(TAG_DAMAGE, damage);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.damage = tag.getFloat(TAG_DAMAGE);
    }
}
