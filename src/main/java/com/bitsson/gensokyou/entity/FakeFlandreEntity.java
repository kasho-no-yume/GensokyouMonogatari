package com.bitsson.gensokyou.entity;

import com.bitsson.gensokyou.config.GensokyouConfig;
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
                .add(Attributes.MAX_HEALTH, GensokyouConfig.FLANDRE_MAX_HEALTH.getDefault() / 10D)
                .add(Attributes.ATTACK_DAMAGE, GensokyouConfig.FLANDRE_ATTACK_DAMAGE.getDefault() / 4D);
    }

    @Override
    protected void applyRuntimeStats() {
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(GensokyouConfig.FLANDRE_MAX_HEALTH.get() / 10D);
        setHealth(getMaxHealth());
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(GensokyouConfig.FLANDRE_ATTACK_DAMAGE.get() / 4D);
    }
}
