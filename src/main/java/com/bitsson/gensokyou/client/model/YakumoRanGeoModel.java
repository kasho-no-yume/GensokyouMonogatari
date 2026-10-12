package com.bitsson.gensokyou.client.model;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.entity.YakumoRanEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * 八云蓝的 GeckoLib 模型。
 *
 * <p>资产来自 {@code C:\Users\spdimdriver\Documents\ran} 的 Blockbench Bedrock 工程，
 * 落地为 {@code yakumo_ran.geo.json} / {@code yakumo_ran.animation.json} /
 * {@code yakumo_ran.png}（暗化贴图，残影）。
 * 首版只有 {@code animation.yakumo_ran.idle}（4 秒摆尾），walk/cast 兼用它。
 */
public class YakumoRanGeoModel extends GeoModel<YakumoRanEntity> {
    private static final ResourceLocation MODEL =
            Gensokyou.id("geo/entity/yakumo_ran.geo.json");
    private static final ResourceLocation TEXTURE =
            Gensokyou.id("textures/entity/yakumo_ran.png");
    private static final ResourceLocation ANIMATION =
            Gensokyou.id("animations/entity/yakumo_ran.animation.json");

    @Override
    public ResourceLocation getModelResource(YakumoRanEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(YakumoRanEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(YakumoRanEntity animatable) {
        return ANIMATION;
    }
}
