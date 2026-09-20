package com.bitsson.gensokyou.entity;

import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.entity.goal.FairyLaserGoal;
import com.bitsson.gensokyou.entity.goal.FairyNetGoal;
import com.bitsson.gensokyou.entity.goal.FairySingleShotGoal;
import com.bitsson.gensokyou.entity.goal.HoverAboveTargetGoal;
import com.bitsson.gensokyou.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;

/**
 * 小妖精：全程飞行的东方系敌对怪。
 *
 * <p>悬停于玩家上方，按刷新时 roll 定的变体发射弹幕（单发 / 3×3 网 / 激光）。
 * 对非弹幕伤害天然减免（{@link TouhouMonster}）。模型与动画走 GeckoLib。
 */
public class FairyEntity extends FlyingMob implements Enemy, TouhouMonster, GeoEntity {
    private static final int VARIANT_UNSET = -1;

    private static final EntityDataAccessor<Integer> DATA_VARIANT =
            SynchedEntityData.defineId(FairyEntity.class, EntityDataSerializers.INT);

    private static final RawAnimation IDLE_ANIM =
            RawAnimation.begin().thenLoop("animation.lesser_fairy.idle");
    private static final RawAnimation FLY_ANIM =
            RawAnimation.begin().thenLoop("animation.lesser_fairy.fly");
    private static final RawAnimation CAST_ANIM =
            RawAnimation.begin().thenPlay("animation.lesser_fairy.cast");

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    private boolean statsApplied = false;

    public FairyEntity(EntityType<? extends FairyEntity> type, Level level) {
        super(type, level);
        this.xpReward = 5;
        this.moveControl = new FairyMoveControl(this);
        this.setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, GensokyouConfig.FAIRY_MAX_HEALTH.getDefault())
                .add(Attributes.ATTACK_DAMAGE, GensokyouConfig.FAIRY_DAMAGE.getDefault())
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FLYING_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new HoverAboveTargetGoal(this));
        this.addAttackGoals();
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** 子类覆写以替换攻击方式（大妖精/Cirno 走扇形）。 */
    protected void addAttackGoals() {
        this.goalSelector.addGoal(3, new FairySingleShotGoal(this));
        this.goalSelector.addGoal(3, new FairyNetGoal(this));
        this.goalSelector.addGoal(3, new FairyLaserGoal(this));
    }

    // ------------------------------------------------------------------
    // 变体
    // ------------------------------------------------------------------

    public FairyVariant getVariant() {
        int value = this.entityData.get(DATA_VARIANT);
        FairyVariant[] values = FairyVariant.values();
        if (value < 0 || value >= values.length) {
            return FairyVariant.SINGLE;
        }
        return values[value];
    }

    private void setVariant(FairyVariant variant) {
        this.entityData.set(DATA_VARIANT, variant.ordinal());
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType spawnType, @Nullable SpawnGroupData spawnGroupData) {
        this.setVariant(FairyVariant.roll(level.getRandom()));
        return super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_VARIANT, VARIANT_UNSET);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("FairyVariant", this.entityData.get(DATA_VARIANT));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("FairyVariant")) {
            this.entityData.set(DATA_VARIANT, tag.getInt("FairyVariant"));
        }
    }

    // ------------------------------------------------------------------
    // 属性（子类可覆写）
    // ------------------------------------------------------------------

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide) {
            return;
        }
        if (!this.statsApplied) {
            this.statsApplied = true;
            this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(this.maxHealthValue());
            this.setHealth(this.getMaxHealth());
            this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(this.attributeDamage());
        }
        if (this.entityData.get(DATA_VARIANT) == VARIANT_UNSET) {
            this.setVariant(FairyVariant.roll(this.getRandom()));
        }
    }

    protected double maxHealthValue() {
        return GensokyouConfig.FAIRY_MAX_HEALTH.get();
    }

    protected double attributeDamage() {
        return GensokyouConfig.FAIRY_DAMAGE.get();
    }

    // ------------------------------------------------------------------
    // GeckoLib
    // ------------------------------------------------------------------

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 5, this::animationPredicate)
                .triggerableAnim("cast", CAST_ANIM));
    }

    private PlayState animationPredicate(AnimationState<FairyEntity> state) {
        if (state.isMoving()) {
            return state.setAndContinue(FLY_ANIM);
        }
        return state.setAndContinue(IDLE_ANIM);
    }

    /** 攻击时触发一次 cast 动画（服务端调用会同步到客户端）。 */
    public void triggerCast() {
        this.triggerAnim("controller", "cast");
    }

    /**
     * 让模型（身体 + 头）水平朝向给定坐标。
     *
     * <p>GeckoLib 用 {@code yBodyRot} 渲染模型，而 {@code LookControl} 只改 {@code yHeadRot}，
     * 因此必须显式写入 yaw 才能让模型转向。
     */
    public void faceTowards(double x, double z) {
        double dx = x - this.getX();
        double dz = z - this.getZ();
        if (dx * dx + dz * dz < 1.0E-6D) {
            return;
        }
        float yaw = (float) (Mth.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        this.setYRot(yaw);
        this.setYHeadRot(yaw);
        this.yBodyRot = yaw;
    }

    /** 妖精弹幕速度 = 全局弹幕速度 × 妖精倍率。 */
    public float bulletSpeed() {
        return (float) (GensokyouConfig.DANMAKU_SPEED.get() * GensokyouConfig.FAIRY_DANMAKU_SPEED_MULT.get());
    }

    // ------------------------------------------------------------------
    // 掉落
    // ------------------------------------------------------------------

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource damageSource, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, damageSource, recentlyHit);
        this.spawnAtLocation(new ItemStack(ModItems.MEMORY_FRAGMENT.get()));
        if (this.getRandom().nextFloat() < GensokyouConfig.FAIRY_PPOINT_CHANCE.get().floatValue()) {
            this.spawnAtLocation(new ItemStack(ModItems.PPOINT.get()));
        }
        if (this.getRandom().nextFloat() < GensokyouConfig.FAIRY_BPOINT_CHANCE.get().floatValue()) {
            this.spawnAtLocation(new ItemStack(ModItems.BPOINT.get()));
        }
    }
}
