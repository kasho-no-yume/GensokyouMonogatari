package com.bitsson.gensokyou.entity;

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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

import java.util.Set;

/**
 * 普通球型弹幕。
 *
 * <p>特性：不指定颜色时随机取色（服务端决定并同步）、命中实体或方块即消失、有外发光。
 */
public class SphereDanmaku extends AbstractDanmakuProjectile {
    /** 球体直径，渲染需要，必须同步。 */
    private static final EntityDataAccessor<Float> DATA_SIZE =
            SynchedEntityData.defineId(SphereDanmaku.class, EntityDataSerializers.FLOAT);

    public SphereDanmaku(EntityType<? extends SphereDanmaku> type, Level level) {
        super(type, level);
    }

    /**
     * @param color 传 0 表示由服务端随机取色
     * @param size  球体直径
     */
    public SphereDanmaku(Level level, LivingEntity owner, float damage, int color, float size,
                         Set<EntityType<?>> whitelist) {
        super(ModEntityTypes.SPHERE_DANMAKU.get(), owner, level);
        this.damage = damage;
        this.setWhitelist(whitelist);
        this.setSize(size);
        this.setColor(color == 0 ? randomColor() : color);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SIZE, 0.4F);
    }

    /** 随机取一个明亮饱和的颜色，避免出现接近黑色的弹幕。 */
    private int randomColor() {
        float hue = this.random.nextFloat();
        return java.awt.Color.HSBtoRGB(hue, 0.85F, 1.0F) & 0xFFFFFF;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (!(this.level() instanceof ServerLevel)) {
            return;
        }
        Entity hit = result.getEntity();
        if (this.isWhitelisted(hit)) {
            return;
        }
        if (hit.isAlive()) {
            // directEntity=弹本体（原版投射物惯例；汲取据此区分武器弹），attacker=owner 归属不变
            hit.hurt(ModDamageTypes.danmaku(this, this.getOwner()), this.damage);
        }
        this.discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        if (this.level() instanceof ServerLevel) {
            this.discard();
        }
    }

    @Override
    public boolean hasGlowEffect() {
        return true;
    }

    public float getSize() {
        return this.entityData.get(DATA_SIZE);
    }

    public void setSize(float size) {
        this.entityData.set(DATA_SIZE, size);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Size", this.getSize());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Size")) {
            this.setSize(tag.getFloat("Size"));
        }
    }
}
