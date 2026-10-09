package com.bitsson.gensokyou.client.model;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.entity.YamameEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * 黑谷山女（土蜘蛛）的 GeckoLib 模型。
 *
 * <p>资产来自 {@code F:/blockbench/kurodani/}（Blockbench Bedrock 工程），
 * 落地为 {@code yamame.geo.json} / {@code yamame.animation.json} / {@code yamame.png}。
 * 动画含 idle / walk / cast 三条（内部标识仍为 {@code animation.kurodani.*}）。
 */
public class YamameGeoModel extends GeoModel<YamameEntity> {
    private static final ResourceLocation MODEL =
            Gensokyou.id("geo/entity/yamame.geo.json");
    private static final ResourceLocation TEXTURE =
            Gensokyou.id("textures/entity/yamame.png");
    private static final ResourceLocation ANIMATION =
            Gensokyou.id("animations/entity/yamame.animation.json");

    @Override
    public ResourceLocation getModelResource(YamameEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(YamameEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(YamameEntity animatable) {
        return ANIMATION;
    }
}
