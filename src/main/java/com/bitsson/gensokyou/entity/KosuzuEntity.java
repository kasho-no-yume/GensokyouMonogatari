package com.bitsson.gensokyou.entity;

import com.bitsson.gensokyou.village.VillageRole;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public final class KosuzuEntity extends VillageNpcEntity {
    public KosuzuEntity(EntityType<? extends KosuzuEntity> type, Level level) { super(type, level); }
    @Override public VillageRole villageRole() { return VillageRole.KOSUZU; }
}
