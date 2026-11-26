package com.bitsson.gensokyou.entity;

import com.bitsson.gensokyou.village.VillageRole;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public final class RinnosukeEntity extends VillageNpcEntity {
    public RinnosukeEntity(EntityType<? extends RinnosukeEntity> type, Level level) { super(type, level); }
    @Override public VillageRole villageRole() { return VillageRole.RINNOSUKE; }
}
