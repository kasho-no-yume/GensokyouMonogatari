package com.bitsson.gensokyou.entity;

import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.registry.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class BigFairyEntity extends FairyEntity {

    public BigFairyEntity(EntityType<? extends BigFairyEntity> type, Level level) {
        super(type, level);
        this.xpReward = 20;
    }

    @Override
    protected int shotIntervalTicks() {
        return GensokyouConfig.BIG_FAIRY_SHOT_INTERVAL.get();
    }

    @Override
    protected int shotCount() {
        return 3;
    }

    @Override
    protected double spreadDegrees() {
        return 20D;
    }

    @Override
    protected double danmakuDamage() {
        return GensokyouConfig.BIG_FAIRY_DAMAGE.get();
    }

    @Override
    protected double maxHealthValue() {
        return GensokyouConfig.BIG_FAIRY_MAX_HEALTH.get();
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource damageSource, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, damageSource, recentlyHit);
        spawnAtLocation(new ItemStack(ModItems.GUIDE_BOOK.get()));
        spawnAtLocation(new ItemStack(ModItems.YEN.get(), 4 + getRandom().nextInt(9)));
    }
}
