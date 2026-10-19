package com.bitsson.gensokyou.entity;

import com.bitsson.gensokyou.balance.BalanceNumbers;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

public class FakeFlandreEntity extends FlandreEntity {

    public FakeFlandreEntity(EntityType<? extends FlandreEntity> type, Level level) {
        super(type, level);
        markFake();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return FlandreEntity.createAttributes()
                .add(Attributes.MAX_HEALTH, BalanceNumbers.FLANDRE_MAX_HEALTH / 10D)
                .add(Attributes.ATTACK_DAMAGE, BalanceNumbers.FLANDRE_ATTACK_DAMAGE / 4D);
    }

    @Override
    protected void applyRuntimeStats() {
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(BalanceNumbers.FLANDRE_MAX_HEALTH / 10D);
        setHealth(getMaxHealth());
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(BalanceNumbers.FLANDRE_ATTACK_DAMAGE / 4D);
    }
}
