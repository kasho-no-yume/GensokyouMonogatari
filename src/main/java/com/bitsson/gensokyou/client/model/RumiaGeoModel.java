package com.bitsson.gensokyou.client.model;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.entity.RumiaEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * 露米娅的 GeckoLib 模型。
 *
 * <p>资产来自 {@code F:\blockbench\rumia}（Blockbench Bedrock 工程），落地为
 * {@code rumia.geo.json} / {@code rumia.animation.json} / {@code rumia.png}。
 * 动画含 idle / walk / cast 三条（内部标识 {@code animation.rumia.*}）。
 */
public class RumiaGeoModel extends GeoModel<RumiaEntity> {
    private static final ResourceLocation MODEL =
            Gensokyou.id("geo/entity/rumia.geo.json");
    private static final ResourceLocation TEXTURE =
            Gensokyou.id("textures/entity/rumia.png");
    private static final ResourceLocation ANIMATION =
            Gensokyou.id("animations/entity/rumia.animation.json");

    @Override
    public ResourceLocation getModelResource(RumiaEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(RumiaEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(RumiaEntity animatable) {
        return ANIMATION;
    }
}
