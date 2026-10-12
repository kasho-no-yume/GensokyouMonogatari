package com.bitsson.gensokyou.client.renderer;

import com.bitsson.gensokyou.client.model.YakumoRanGeoModel;
import com.bitsson.gensokyou.entity.YakumoRanEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * 八云蓝的 GeckoLib 渲染器（正式模型，退出残影占位通路）。
 *
 * <p>模型自然高约 2.4 格（含帽），作为 T2 正主保持接近 1:1 的体量。缩放值暂定，
 * 待实机与重设后的 hitbox（见 {@code ModEntityTypes.YAKUMO_RAN}）对齐后回写。
 */
public class YakumoRanGeoRenderer extends GeoEntityRenderer<YakumoRanEntity> {
    private static final float SCALE = 1.0F;

    public YakumoRanGeoRenderer(EntityRendererProvider.Context context) {
        super(context, new YakumoRanGeoModel());
        this.withScale(SCALE);
    }
}
