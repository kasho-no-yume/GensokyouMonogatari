package com.bitsson.gensokyou.entity;

import com.bitsson.gensokyou.danmaku.DanmakuBudget;
import com.bitsson.gensokyou.danmaku.SplitSpread;
import com.bitsson.gensokyou.danmaku.visual.DanmakuVisualProfile;
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

    /**
     * 视觉档案 id。贴图、几何、各层缩放与 alpha 全由档案决定，故此处只同步一个 int。
     * 越界时 {@code DanmakuVisualProfile#byId} 回落到默认档，绝不抛。
     */
    private static final EntityDataAccessor<Integer> DATA_VISUAL =
            SynchedEntityData.defineId(SphereDanmaku.class, EntityDataSerializers.INT);

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
        builder.define(DATA_VISUAL, DanmakuVisualProfile.defaultId());
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
        this.discard(DanmakuBudget.RemovalCause.ENTITY);
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        if (this.level() instanceof ServerLevel) {
            com.bitsson.gensokyou.danmaku.DanmakuBudget.recordBlockHit();
            this.discard(DanmakuBudget.RemovalCause.BLOCK);
        }
    }

    @Override
    protected void spawnSplitChildren(int count) {
        if (!(this.level() instanceof ServerLevel server)) {
            return;
        }
        Vec3 velocity = this.getDeltaMovement();
        double speed = this.getSpeed();
        // 分布方式按<b>母弹是否在动</b>选，不是按「速度为零时的任意方向近似」：
        //   在动 → 环状：垂直于飞行方向的圆（子弹幕的常规读法）
        //   静止 → 球面：四面八方（需求⑤「停住后炸开」）
        // 此前母弹静止时被强行取 (0,-1,0) 当作「方向」，结果子代均分于一个
        // 水平圆上——那是「环状炸开」而非「四面八方」，且玩家可以站进环心的安全区。
        int emitted = 0;
        for (int i = 0; i < count; i++) {
            // 分裂子代 MUST 同样受弹幕上限约束：否则多重分裂可把上限直接顶穿。
            // 达上限即停止生成剩余子代。
            if (!DanmakuBudget.canEmit(server)) {
                break;
            }
            SphereDanmaku child = new SphereDanmaku(server, this.getOwner() instanceof LivingEntity owner
                    ? owner : null, this.damage, this.getColor(), this.getSize(), Set.of());
            child.setFromWeapon(this.isFromWeapon());
            child.setCritMult(this.getCritMult());
            child.setLifetimeTicks(this.getLifetimeTicks());
            child.moveTo(this.getX(), this.getY(), this.getZ(), 0F, 0F);
            child.setDirection(
                    SplitSpread.forMotion(velocity, speed, i, count, this.getSize()),
                    SplitSpread.childSpeed(speed, this.getSize()));
            server.addFreshEntity(child);
            DanmakuBudget.recordEmit();
            emitted++;
        }
        DanmakuBudget.recordSplit(emitted, count);
    }

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
     * 碰撞箱 = 视觉直径 × 档案的 {@code hitboxScale}。
     *
     * <p>档案把<b>视觉缩放</b>与<b>碰撞缩放</b>拆成两个字段，是为了让「换模型」不必
     * 连带改动判定——模型的视觉尺寸与四边形无关，若继续共用一个 {@code size}，
     * 换模型就会破坏这条刚修好的不变量。默认档案两者皆 1.0，故行为与改前完全一致。
     */
    @Override
    public EntityDimensions getDimensions(Pose pose) {
        float size = Math.max(0.05F, getSize() * visualProfile().hitboxScale());
        return EntityDimensions.scalable(size, size);
    }

    // ------------------------------------------------------------------
    // 视觉档案
    // ------------------------------------------------------------------

    /** 当前视觉档案。越界自动回落到默认档。 */
    public DanmakuVisualProfile.Profile visualProfile() {
        return DanmakuVisualProfile.byId(this.entityData.get(DATA_VISUAL));
    }

    /** 设定视觉档案。传入档案本身（自动解析为 id），避免手写 id 出错。 */
    public void setVisualProfile(DanmakuVisualProfile.Profile profile) {
        for (int i = 0; i < DanmakuVisualProfile.size(); i++) {
            if (DanmakuVisualProfile.byId(i) == profile) {
                this.entityData.set(DATA_VISUAL, i);
                return;
            }
        }
        throw new IllegalArgumentException("档案不在注册表内: " + profile);
    }

    /** 当前档案 id。 */
    public int getVisualId() {
        return this.entityData.get(DATA_VISUAL);
    }

    // ------------------------------------------------------------------
    // 相位隐藏态
    //
    // 已上提到 AbstractDanmakuProjectile：显隐是 Behaviour 的一轴，与弹种正交。
    // 留在球弹上会让「激光配相位隐藏」静默失效——行为被无声丢弃，
    // 现象是「激光一直亮着，完全没有闪烁」，且日志干净。
    // ------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Size", this.getSize());
        tag.putInt("Visual", this.getVisualId());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Size")) {
            this.setSize(tag.getFloat("Size"));
        }
        if (tag.contains("Visual")) {
            this.entityData.set(DATA_VISUAL, tag.getInt("Visual"));
        }
    }
}
