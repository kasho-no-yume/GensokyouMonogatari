package com.bitsson.gensokyou.client.model;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.entity.VillageNpcEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public final class VillageNpcGeoModel<T extends VillageNpcEntity> extends GeoModel<T> {
    @Override public ResourceLocation getModelResource(T npc) { return Gensokyou.id("geo/entity/" + npc.villageRole().key() + ".geo.json"); }
    @Override public ResourceLocation getTextureResource(T npc) { return Gensokyou.id("textures/entity/" + npc.villageRole().key() + ".png"); }
    @Override public ResourceLocation getAnimationResource(T npc) { return Gensokyou.id("animations/entity/" + npc.villageRole().key() + ".animation.json"); }
}
