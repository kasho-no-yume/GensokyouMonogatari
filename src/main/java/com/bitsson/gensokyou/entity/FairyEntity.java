package com.bitsson.gensokyou.entity;

import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.entity.goal.FanDanmakuGoal;
import com.bitsson.gensokyou.registry.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsTargetGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class FairyEntity extends Monster {
    private boolean statsApplied = false;

    public FairyEntity(EntityType<? extends FairyEntity> type, Level level) {
        super(type, level);
        this.xpReward = 5;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, GensokyouConfig.FAIRY_MAX_HEALTH.getDefault())
                .add(Attributes.ATTACK_DAMAGE, GensokyouConfig.FAIRY_DAMAGE.getDefault())
                .add(Attributes.MOVEMENT_SPEED, 0.25D);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MoveTowardsTargetGoal(this, 0.85D, 24F));
        goalSelector.addGoal(3, new FanDanmakuGoal(this,
                shotIntervalTicks(), shotCount(),
                spreadDegrees(), GensokyouConfig.DANMAKU_SPEED.get(), (float) danmakuDamage()));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6D));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    protected int shotIntervalTicks() {
        return GensokyouConfig.FAIRY_SHOT_INTERVAL.get();
    }

    protected int shotCount() {
        return 1;
    }

    protected double spreadDegrees() {
        return 0D;
    }

    protected double danmakuDamage() {
        return GensokyouConfig.FAIRY_DAMAGE.get();
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide || statsApplied) {
            return;
        }
        statsApplied = true;
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(maxHealthValue());
        setHealth(getMaxHealth());
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(danmakuDamage());
    }

    protected double maxHealthValue() {
        return GensokyouConfig.FAIRY_MAX_HEALTH.get();
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource damageSource, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, damageSource, recentlyHit);
        if (getRandom().nextFloat() < GensokyouConfig.FAIRY_PPOINT_CHANCE.get().floatValue()) {
            spawnAtLocation(new ItemStack(ModItems.PPOINT.get()));
        }
        if (getRandom().nextFloat() < GensokyouConfig.FAIRY_YEN_CHANCE.get().floatValue()) {
            spawnAtLocation(new ItemStack(ModItems.YEN.get(), 1 + getRandom().nextInt(2)));
        }
    }
}
