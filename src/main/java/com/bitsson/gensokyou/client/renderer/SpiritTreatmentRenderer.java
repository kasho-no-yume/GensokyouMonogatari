package com.bitsson.gensokyou.client.renderer;

import com.bitsson.gensokyou.block.entity.SpiritTreatmentBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;

public final class SpiritTreatmentRenderer implements BlockEntityRenderer<SpiritTreatmentBlockEntity> {
    private static final float SCALE = 0.55F;

    public SpiritTreatmentRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(SpiritTreatmentBlockEntity device, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        var core = device.core();
        if (core.isEmpty()) {
            return;
        }
        var level = device.getLevel();
        var pos = device.getBlockPos();
        float bottom = ItemFixedBounds.of(core, level).minY;
        pose.pushPose();
        pose.translate(0.5, 0.76 - SCALE * bottom, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(35));
        pose.scale(SCALE, SCALE, SCALE);
        Minecraft.getInstance().getItemRenderer().renderStatic(core, ItemDisplayContext.FIXED,
                level == null ? light : LevelRenderer.getLightColor(level, pos.above()), overlay,
                pose, buffers, level, (int) pos.asLong());
        pose.popPose();
    }
}
