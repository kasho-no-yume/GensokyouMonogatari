package com.bitsson.gensokyou.entity;

import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.entity.goal.FanDanmakuGoal;
import com.bitsson.gensokyou.registry.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 琪露诺：占位精英怪，五连扇形弹幕。
 *
 * <p><b>仍是占位实现</b>。她刻意<b>不</b>继承 {@link BigFairyEntity}——后者已正式化为
 * 召唤 BOSS（3 张符卡、距离带自由游走、咒符条血条），让一只野生精英挂在那条继承链上
 * 会把「占位」和「正式设计」搅在一起。等她被正式立项时再单独做。
 */
public class CirnoEntity extends FairyEntity {

    public CirnoEntity(EntityType<? extends CirnoEntity> type, Level level) {
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
