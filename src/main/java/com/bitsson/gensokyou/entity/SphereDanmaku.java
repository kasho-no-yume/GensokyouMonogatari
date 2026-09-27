package com.bitsson.gensokyou.entity;

import com.bitsson.gensokyou.registry.ModDamageTypes;
import com.bitsson.gensokyou.registry.ModEntityTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
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
            if (hit instanceof net.minecraft.world.entity.LivingEntity living) {
                float absorptionBefore = living.getAbsorptionAmount();
                float healthBefore = living.getHealth();
                boolean applied = living.hurt(
                        ModDamageTypes.danmaku(this, this.getOwner()), this.damage);
                float lost = (absorptionBefore - living.getAbsorptionAmount())
                        + (healthBefore - living.getHealth());
                if (applied) {
                    com.bitsson.gensokyou.danmaku.DanmakuBudget.recordEntityHit(lost);
                } else {
                    // 记录「判到了但没掉血」——用于区分判定问题与伤害管线问题
                    com.bitsson.gensokyou.danmaku.DanmakuBudget.recordDamageRejected(
                            living, this.damage, lost > 0.0F);
                    com.bitsson.gensokyou.danmaku.DanmakuBudget.recordEntityHit(lost);
                }
            } else {
                hit.hurt(ModDamageTypes.danmaku(this, this.getOwner()), this.damage);
                com.bitsson.gensokyou.danmaku.DanmakuBudget.recordEntityHit(this.damage);
            }
        }
        this.discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        if (this.level() instanceof ServerLevel) {
            com.bitsson.gensokyou.danmaku.DanmakuBudget.recordBlockHit();
            this.discard();
        }
    }

    @Override
    protected void spawnSplitChildren(int count) {
        if (!(this.level() instanceof ServerLevel server)) {
            return;
        }
        Vec3 base = this.getDeltaMovement();
        double speed = this.getSpeed();
        if (speed < 1.0E-4D) {
            // 母弹已定住（悬停弹分裂）：以自轴均匀取一圈，速度沿用定住前的设定值。
            base = new Vec3(0, -1, 0);
            speed = 0.25D;
        }
        Vec3 normal = base.normalize();
        Vec3 ref = Math.abs(normal.y) < 0.9D ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 right = normal.cross(ref).normalize();
        Vec3 up = normal.cross(right).normalize();
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2.0D * i / count;
            Vec3 dir = right.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
            SphereDanmaku child = new SphereDanmaku(server, this.getOwner() instanceof LivingEntity owner
                    ? owner : null, this.damage, this.getColor(), this.getSize(), Set.of());
            child.setFromWeapon(this.isFromWeapon());
            child.setCritMult(this.getCritMult());
            child.moveTo(this.getX(), this.getY(), this.getZ(), 0F, 0F);
            child.setDirection(dir, speed);
            server.addFreshEntity(child);
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
        // 关键：碰撞箱必须跟着视觉尺寸走。
        //
        // <p>{@code DATA_SIZE} 此前只用于渲染，实体尺寸始终是 {@code EntityType} 里写死的
        // 0.4×0.4。于是 BOSS 那些 0.6~1.0 直径的大慢球<b>看起来穿过了玩家却没伤害</b>——
        // 判定用的是 0.4 的盒子，视觉却是一大颗。这条不修，越大的弹越打不到人。
        this.refreshDimensions();
    }

    /**
     * 碰撞箱 = 视觉直径。此覆写让判定与观感一致，也是「看着打中了却不掉血」的根因修复。
     */
    @Override
    public EntityDimensions getDimensions(Pose pose) {
        float size = Math.max(0.05F, getSize());
        return EntityDimensions.scalable(size, size);
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
