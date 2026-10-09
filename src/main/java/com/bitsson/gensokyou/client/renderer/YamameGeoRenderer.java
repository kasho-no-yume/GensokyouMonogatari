package com.bitsson.gensokyou.client.renderer;

import com.bitsson.gensokyou.client.model.YamameGeoModel;
import com.bitsson.gensokyou.entity.YamameEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * 黑谷山女的 GeckoLib 渲染器。
 *
 * <p>{@code yamame.geo.json} 自然高 29.26 单位（1 格 = 16 单位 ⇒ 1.83 格），
 * 取 {@code SCALE = 0.93} 收到约 1.70 格，与 {@code sized(0.8, 1.7)} 碰撞箱相称。
 */
public class YamameGeoRenderer extends GeoEntityRenderer<YamameEntity> {
    /** 29.26 单位 × 0.93 ≈ 1.70 格。 */
    private static final float SCALE = 0.93F;

    public YamameGeoRenderer(EntityRendererProvider.Context context) {
        super(context, new YamameGeoModel());
        this.withScale(SCALE);
    }
}
