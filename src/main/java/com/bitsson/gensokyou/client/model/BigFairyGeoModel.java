package com.bitsson.gensokyou.client.model;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.entity.BigFairyEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * 大妖精的 GeckoLib 模型。
 *
 * <p>资产来自 {@code F:/blockbench/touhou_fairies/大妖精/}（Blockbench Bedrock 工程），
 * 落地为 {@code greater_fairy.geo.json} / {@code greater_fairy.animation.json} /
 * {@code greater_fairy.png}。动画含 idle / fly / walk / cast 四条。
 */
public class BigFairyGeoModel extends GeoModel<BigFairyEntity> {
    private static final ResourceLocation MODEL =
            Gensokyou.id("geo/entity/greater_fairy.geo.json");
    private static final ResourceLocation TEXTURE =
            Gensokyou.id("textures/entity/greater_fairy.png");
    private static final ResourceLocation ANIMATION =
            Gensokyou.id("animations/entity/greater_fairy.animation.json");

    @Override
    public ResourceLocation getModelResource(BigFairyEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(BigFairyEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(BigFairyEntity animatable) {
        return ANIMATION;
    }
}
