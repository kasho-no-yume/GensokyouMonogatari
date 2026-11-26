package com.bitsson.gensokyou.client.renderer;

import com.bitsson.gensokyou.client.model.VillageNpcGeoModel;
import com.bitsson.gensokyou.entity.VillageNpcEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class VillageNpcGeoRenderer<T extends VillageNpcEntity> extends GeoEntityRenderer<T> {
    public VillageNpcGeoRenderer(EntityRendererProvider.Context context) {
        super(context, new VillageNpcGeoModel<>());
        withScale(.9F); shadowRadius = .4F;
    }
}
