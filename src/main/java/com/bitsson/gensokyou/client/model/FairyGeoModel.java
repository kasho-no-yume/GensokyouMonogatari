package com.bitsson.gensokyou.client.model;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.entity.FairyEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** 小妖精的 GeckoLib 模型：Bedrock geo + 动画 + cutout 贴图。 */
public class FairyGeoModel extends GeoModel<FairyEntity> {
    private static final ResourceLocation MODEL =
            Gensokyou.id("geo/entity/lesser_fairy.geo.json");
    private static final ResourceLocation TEXTURE =
            Gensokyou.id("textures/entity/lesser_fairy.png");
    private static final ResourceLocation ANIMATION =
            Gensokyou.id("animations/entity/lesser_fairy.animation.json");

    @Override
    public ResourceLocation getModelResource(FairyEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(FairyEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(FairyEntity animatable) {
        return ANIMATION;
    }
}
