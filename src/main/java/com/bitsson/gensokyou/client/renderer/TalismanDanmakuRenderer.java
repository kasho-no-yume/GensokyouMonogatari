package com.bitsson.gensokyou.client.renderer;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.entity.TalismanDanmaku;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * 灵符渲染器：扁平长方体，面向摄像机，带外发光。
 */
public class TalismanDanmakuRenderer extends AbstractDanmakuRenderer<TalismanDanmaku> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Gensokyou.MODID, "textures/entity/talisman_danmaku.png");

    private static final float WIDTH = 0.34F;
    private static final float HEIGHT = 0.52F;

    public TalismanDanmakuRenderer(EntityRendererProvider.Context context) {
        super(context, TEXTURE);
    }

    @Override
    public void render(TalismanDanmaku entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();

        poseStack.translate(0.0D, HEIGHT * 0.5D, 0.0D);
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));

        int color = entity.getColor();
        VertexConsumer consumer = bufferSource.getBuffer(this.baseRenderType());
        this.renderShape(entity, poseStack, consumer,
                red(color), green(color), blue(color), 255, packedLight);

        this.renderGlow(entity, poseStack, bufferSource);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    protected void renderShape(TalismanDanmaku entity, PoseStack poseStack, VertexConsumer consumer,
                                int r, int g, int b, int a, int light) {
        PoseStack.Pose pose = poseStack.last();
        float halfWidth = WIDTH * 0.5F;
        float halfHeight = HEIGHT * 0.5F;

        this.vertex(consumer, pose, -halfWidth, -halfHeight, 0.0F, 0.0F, 1.0F, r, g, b, a, light);
        this.vertex(consumer, pose, halfWidth, -halfHeight, 0.0F, 1.0F, 1.0F, r, g, b, a, light);
        this.vertex(consumer, pose, halfWidth, halfHeight, 0.0F, 1.0F, 0.0F, r, g, b, a, light);
        this.vertex(consumer, pose, -halfWidth, halfHeight, 0.0F, 0.0F, 0.0F, r, g, b, a, light);
    }
}
