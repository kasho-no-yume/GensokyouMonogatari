package com.bitsson.gensokyou.client.renderer;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.entity.SphereDanmaku;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class SphereDanmakuRenderer extends AbstractDanmakuRenderer<SphereDanmaku> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Gensokyou.MODID, "textures/entity/sphere_danmaku.png");

    /** 白色亮核相对缩放：缩小绘制同一张渐变贴图，加法混合下叠出白心。 */
    private static final float CORE_SCALE = 0.55F;

    /** 白色亮核透明度。 */
    private static final int CORE_ALPHA = 235;

    public SphereDanmakuRenderer(EntityRendererProvider.Context context) {
        super(context, TEXTURE);
    }

    /**
     * 本体层用常规 alpha 混合：渐变贴图的柔边（半透明衰减）必须真混合才能表现，
     * cutout 会把抗锯齿边缘切成锯齿。
     */
    @Override
    protected RenderType baseRenderType() {
        return DanmakuRenderTypes.translucent(this.texture);
    }

    @Override
    public void render(SphereDanmaku entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();

        float size = entity.getSize();
        poseStack.translate(0.0D, size * 0.5D, 0.0D);
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));

        // 弹幕自发光体，本体不吃环境光（与激光/发光层一致，暗处保持可读）
        int color = entity.getColor();
        VertexConsumer consumer = bufferSource.getBuffer(this.baseRenderType());
        this.renderShape(entity, poseStack, consumer,
                red(color), green(color), blue(color), 255, FULL_BRIGHT);

        // 外发光（1.35×，实体色，加法）与亮核（0.55×，恒白，加法）共用同一
        // RenderType（additiveGlow 本贴图），连续写入不触发缓冲冲刷。
        this.renderGlow(entity, poseStack, bufferSource);

        poseStack.pushPose();
        poseStack.scale(CORE_SCALE, CORE_SCALE, CORE_SCALE);
        VertexConsumer core = bufferSource.getBuffer(this.glowRenderType());
        this.renderShape(entity, poseStack, core, 255, 255, 255, CORE_ALPHA, FULL_BRIGHT);
        poseStack.popPose();

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    protected void renderShape(SphereDanmaku entity, PoseStack poseStack, VertexConsumer consumer,
                                int r, int g, int b, int a, int light) {
        float half = entity.getSize() * 0.5F;
        PoseStack.Pose pose = poseStack.last();
        this.vertex(consumer, pose, -half, -half, 0.0F, 0.0F, 1.0F, r, g, b, a, light);
        this.vertex(consumer, pose, half, -half, 0.0F, 1.0F, 1.0F, r, g, b, a, light);
        this.vertex(consumer, pose, half, half, 0.0F, 1.0F, 0.0F, r, g, b, a, light);
        this.vertex(consumer, pose, -half, half, 0.0F, 0.0F, 0.0F, r, g, b, a, light);
    }
}
