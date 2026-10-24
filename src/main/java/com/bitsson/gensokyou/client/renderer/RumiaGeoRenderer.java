package com.bitsson.gensokyou.client.renderer;

import com.bitsson.gensokyou.client.model.RumiaGeoModel;
import com.bitsson.gensokyou.entity.RumiaEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * 露米娅的 GeckoLib 渲染器。
 *
 * <p>{@code rumia.geo.json} 自然高 28 单位（1 格 = 16 单位 ⇒ 1.75 格），
 * 取 {@code SCALE = 0.85} 收到约 1.49 格，与 {@code sized(0.7, 1.5)} 碰撞箱相称。
 * 实际朝向/比例待实机确认。
 */
public class RumiaGeoRenderer extends GeoEntityRenderer<RumiaEntity> {
    /** 28 单位 × 0.85 ≈ 1.49 格。 */
    private static final float SCALE = 0.85F;

    public RumiaGeoRenderer(EntityRendererProvider.Context context) {
        super(context, new RumiaGeoModel());
        this.withScale(SCALE);
    }
}
