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

    /**
     * 亮核层沿 billboard 局部 +Z 的推离量（格）。
     *
     * <p>取 {@link #GLOW_OFFSET} 的两倍，使亮核与外发光也<b>不共面</b>（共面会重现
     * 中心/高光处的间歇性暗条）。外发光在外、本体居中、亮核最内，三层严格由远及近。
     */
    private static final float CORE_OFFSET = AbstractDanmakuRenderer.GLOW_OFFSET * 2.0F;

    /** 白色亮核透明度。 */
    private static final int CORE_ALPHA = 235;

    public SphereDanmakuRenderer(EntityRendererProvider.Context context) {
        super(context, TEXTURE);
    }

    /**
     * 本体层用写深度的常规 alpha 混合：渐变贴图的柔边（半透明衰减）必须真混合才能表现，
     * cutout 会把抗锯齿边缘切成锯齿；写深度则保证后画的半透明地形（水）与云层
     * 被本体正确遮挡（不再盖住位于其前方的弹幕）。
     */
    @Override
    protected RenderType baseRenderType() {
        return DanmakuRenderTypes.translucentDepth(this.texture);
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

        // 外发光（1.35×，实体色，加法 + 写深度）沿局部 +Z 推离 0.004 格，与本体不共面。
        this.renderGlow(entity, poseStack, bufferSource);

        // 亮核（0.55×，恒白，加法）。**必须用不写深度的 decorativeRenderType**：
        // ① 与外发光分属不同 RenderType → getBuffer 换类型即结算上一批，两层不再同批，
        //    避开 additiveSolid 的 sortOnUpload 批内重排（重排会让靠后那层被整片拒掉）；
        // ② 不写深度的层永远不会被别的层拒掉，可见性不依赖提交顺序；
        // ③ 亮核 0.55× 严格包含在外发光 1.35× 之内（同贴图），其深度写入本就被完全覆盖。
        poseStack.pushPose();
        poseStack.translate(0.0D, 0.0D, CORE_OFFSET);
        poseStack.scale(CORE_SCALE, CORE_SCALE, CORE_SCALE);
        VertexConsumer core = bufferSource.getBuffer(this.decorativeRenderType());
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
