package com.bitsson.gensokyou.client.model;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.entity.ReimuEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** 模型、贴图和动作由 tools/export_reimu_npc.py 从 Blockbench 原稿导出。 */
public class ReimuGeoModel extends GeoModel<ReimuEntity> {
    private static final ResourceLocation MODEL = Gensokyou.id("geo/entity/reimu.geo.json");
    private static final ResourceLocation TEXTURE = Gensokyou.id("textures/entity/reimu.png");
    private static final ResourceLocation ANIMATION = Gensokyou.id("animations/entity/reimu.animation.json");

    @Override
    public ResourceLocation getModelResource(ReimuEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(ReimuEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(ReimuEntity animatable) {
        return ANIMATION;
    }
}
