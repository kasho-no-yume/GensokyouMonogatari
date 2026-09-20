package com.bitsson.gensokyou.entity;

import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.entity.goal.FanDanmakuGoal;
import com.bitsson.gensokyou.registry.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Cirno：大妖精的强化个体，五连扇形弹幕。 */
public class CirnoEntity extends BigFairyEntity {

    public CirnoEntity(EntityType<? extends BigFairyEntity> type, Level level) {
        super(type, level);
        this.xpReward = 50;
    }

    @Override
    protected void addAttackGoals() {
        this.goalSelector.addGoal(3, new FanDanmakuGoal(this,
                GensokyouConfig.FAIRY_SHOT_INTERVAL.get(), 5, 30D,
                GensokyouConfig.DANMAKU_SPEED.get(),
                (float) (GensokyouConfig.BIG_FAIRY_DAMAGE.get() * 1.5D)));
    }

    @Override
    protected double maxHealthValue() {
        return GensokyouConfig.BIG_FAIRY_MAX_HEALTH.get() * 2.5D;
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource damageSource, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, damageSource, recentlyHit);
        int bpoints = this.getRandom().nextInt(3);
        if (bpoints > 0) {
            this.spawnAtLocation(new ItemStack(ModItems.BPOINT.get(), bpoints));
        }
        this.spawnAtLocation(new ItemStack(ModItems.YEN.get(), 8 + this.getRandom().nextInt(17)));
    }
}
