package com.bitsson.gensokyou.client.renderer;

import com.bitsson.gensokyou.client.model.ReimuGeoModel;
import com.bitsson.gensokyou.entity.ReimuEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class ReimuGeoRenderer extends GeoEntityRenderer<ReimuEntity> {
    public ReimuGeoRenderer(EntityRendererProvider.Context context) {
        super(context, new ReimuGeoModel());
        // 原稿含发饰共高 31.35 单位，缩放后约 1.76 格。
        this.withScale(0.9F);
        this.shadowRadius = 0.4F;
    }
}
