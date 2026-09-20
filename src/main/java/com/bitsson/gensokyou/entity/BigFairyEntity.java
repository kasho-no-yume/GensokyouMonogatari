package com.bitsson.gensokyou.entity;

import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.entity.goal.FanDanmakuGoal;
import com.bitsson.gensokyou.registry.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** 大妖精（小 BOSS）：飞行 + 三连扇形弹幕，血量远高于小妖精。 */
public class BigFairyEntity extends FairyEntity {

    public BigFairyEntity(EntityType<? extends BigFairyEntity> type, Level level) {
        super(type, level);
        this.xpReward = 20;
    }

    @Override
    protected void addAttackGoals() {
        this.goalSelector.addGoal(3, new FanDanmakuGoal(this,
                GensokyouConfig.BIG_FAIRY_SHOT_INTERVAL.get(), 3, 20D,
                GensokyouConfig.DANMAKU_SPEED.get(),
                GensokyouConfig.BIG_FAIRY_DAMAGE.get().floatValue()));
    }

    @Override
    protected double maxHealthValue() {
        return GensokyouConfig.BIG_FAIRY_MAX_HEALTH.get();
    }

    @Override
    protected double attributeDamage() {
        return GensokyouConfig.BIG_FAIRY_DAMAGE.get();
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource damageSource, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, damageSource, recentlyHit);
        this.spawnAtLocation(new ItemStack(ModItems.GUIDE_BOOK.get()));
        this.spawnAtLocation(new ItemStack(ModItems.YEN.get(), 4 + this.getRandom().nextInt(9)));
    }
}
