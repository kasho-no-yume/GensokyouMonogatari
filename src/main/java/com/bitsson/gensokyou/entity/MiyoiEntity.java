package com.bitsson.gensokyou.entity;

import com.bitsson.gensokyou.village.VillageRole;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public final class MiyoiEntity extends VillageNpcEntity {
    public MiyoiEntity(EntityType<? extends MiyoiEntity> type, Level level) { super(type, level); }
    @Override public VillageRole villageRole() { return VillageRole.MIYOI; }
}
