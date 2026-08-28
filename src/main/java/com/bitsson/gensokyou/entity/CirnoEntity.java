package com.bitsson.gensokyou.entity;

import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.registry.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class CirnoEntity extends BigFairyEntity {

    public CirnoEntity(EntityType<? extends BigFairyEntity> type, Level level) {
        super(type, level);
        this.xpReward = 50;
    }

    @Override
    protected int shotIntervalTicks() {
        return GensokyouConfig.FAIRY_SHOT_INTERVAL.get();
    }

    @Override
    protected int shotCount() {
        return 5;
    }

    @Override
    protected double spreadDegrees() {
        return 30D;
    }

    @Override
    protected double danmakuDamage() {
        return GensokyouConfig.BIG_FAIRY_DAMAGE.get() * 1.5D;
    }

    @Override
    protected double maxHealthValue() {
        return GensokyouConfig.BIG_FAIRY_MAX_HEALTH.get() * 2.5D;
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource damageSource, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, damageSource, recentlyHit);
        int bpoints = getRandom().nextInt(3);
        if (bpoints > 0) {
            spawnAtLocation(new ItemStack(ModItems.BPOINT.get(), bpoints));
        }
        spawnAtLocation(new ItemStack(ModItems.YEN.get(), 8 + getRandom().nextInt(17)));
    }
}
