package com.bitsson.gensokyou.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;

public class SkinMobRenderer<T extends Mob> extends HumanoidMobRenderer<T, HumanoidModel<T>> {
    private final ResourceLocation texture;
    private final float scale;

    public SkinMobRenderer(EntityRendererProvider.Context context, float shadowRadius,
                           float scale, ResourceLocation texture) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), shadowRadius);
        this.texture = texture;
        this.scale = scale;
    }

    @Override
    protected void scale(T livingEntity, PoseStack poseStack, float partialTickTime) {
        if (scale != 1F) {
            poseStack.scale(scale, scale, scale);
        }
        super.scale(livingEntity, poseStack, partialTickTime);
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return texture;
    }
}
