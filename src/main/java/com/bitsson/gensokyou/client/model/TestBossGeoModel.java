package com.bitsson.gensokyou.client.model;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.entity.BalanceTestBossEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** 测试 BOSS 的 GeckoLib 模型：复用妖精的 geo/动画/贴图（占位）。 */
public class TestBossGeoModel extends GeoModel<BalanceTestBossEntity> {
    private static final ResourceLocation MODEL =
            Gensokyou.id("geo/entity/lesser_fairy.geo.json");
    private static final ResourceLocation TEXTURE =
            Gensokyou.id("textures/entity/lesser_fairy.png");
    private static final ResourceLocation ANIMATION =
            Gensokyou.id("animations/entity/lesser_fairy.animation.json");

    @Override
    public ResourceLocation getModelResource(BalanceTestBossEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(BalanceTestBossEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(BalanceTestBossEntity animatable) {
        return ANIMATION;
    }
}
