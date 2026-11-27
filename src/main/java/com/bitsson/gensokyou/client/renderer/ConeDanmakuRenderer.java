package com.bitsson.gensokyou.client.renderer;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.danmaku.visual.DanmakuGeometry;
import com.bitsson.gensokyou.danmaku.render.DanmakuRenderProbe;
import com.bitsson.gensokyou.entity.ConeDanmaku;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * 鳞形弹幕渲染器：将经典箭头轮廓做成立体、带亮芯的短弹头；弹体正面沿局部 +Z 朝向飞行方向。
 *
 * <p>与细长的锥体不同，该弹头两肩宽、头部圆钝、下缘收成短尖。灰阶贴图提供红壳的
 * 明暗与白色纹理，实体颜色继续决定外壳色相。
 */
public class ConeDanmakuRenderer extends AbstractDanmakuRenderer<ConeDanmaku> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Gensokyou.MODID, "textures/entity/arrowhead_danmaku.png");

    private static final int SIDES = 18;
    private static final float BODY_RADIUS = 0.15F;
    private static final float REAR_Z = -0.105F;
    private static final float BEVEL_Z = -0.018F;
    private static final float FRONT_Z = 0.075F;
    private static final float REAR_SCALE = 0.46F;
    private static final float BEVEL_SCALE = 0.82F;

    private static final float GLOW_SCALE = 1.17F;
    private static final int GLOW_ALPHA = 72;
    private static final int CORE_ALPHA = 235;

    public ConeDanmakuRenderer(EntityRendererProvider.Context context) {
        super(context, TEXTURE);
    }

    /**
     * 本体用 alpha 混合而非 cutout。
     *
     * <p>cutout 只做"alpha &lt; 0.1 就丢弃"，**没有半透明**——近相机淡出需要真正的
     * alpha 衰减，故走 {@link DanmakuRenderTypes#translucent}（alpha 混合 + 不裁背面，
     * 弹头面与侧面的绕序不必纠结正反面）。
     */
    @Override
    protected RenderType baseRenderType() {
        return DanmakuRenderTypes.translucent(this.texture);
    }

    @Override
    public void render(ConeDanmaku entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();

        // 用插值朝向，避免低同步频率下的抖动；箭头壳的正面朝向局部 +Z。
        float yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        float pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());

        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(-pitch));

        int color = entity.getColor();
        // 近相机淡出：弹丸生成在眼睛 = 相机位置，第一帧就贴在镜头上；本体与加法外发光
        // 叠加会造成整屏白闪，而麟弹 10 发/秒 = 10 Hz 频闪。
        // **只改渲染**：判定、轨迹、生成点一个都不动 ⇒ 对准度零影响。
        float fade = this.nearCameraFade(entity, partialTick);
        int bodyAlpha = Math.round(255.0F * fade);
        int glowAlpha = Math.round(GLOW_ALPHA * fade);

        if (bodyAlpha > 0 && DanmakuRenderProbe.effectiveBody()) {
            DanmakuRenderProbe.countBody();
            VertexConsumer consumer = this.getBuffer(bufferSource, this.baseRenderType());
            DanmakuRenderProbe.pushBody();
            try {
                this.renderShape(entity, poseStack, consumer,
                        red(color), green(color), blue(color), bodyAlpha, FULL_BRIGHT);
            } finally {
                DanmakuRenderProbe.pop();
            }
        }

        // 外发光：略放大的加法副本，强化红色外缘而不把弹丸吹成一整团光球。
        if (glowAlpha > 0) {
            poseStack.pushPose();
            poseStack.translate(0.0F, 0.0F, GLOW_OFFSET);
            poseStack.scale(GLOW_SCALE, GLOW_SCALE, GLOW_SCALE);
            VertexConsumer glow = this.getBuffer(bufferSource, DanmakuRenderTypes.additiveGlow(TEXTURE));
            DanmakuRenderProbe.countGlow();
            DanmakuRenderProbe.pushGlow();
            try {
                this.renderShape(entity, poseStack, glow,
                        red(color), green(color), blue(color), glowAlpha, FULL_BRIGHT);
            } finally {
                DanmakuRenderProbe.pop();
            }
            poseStack.popPose();
        }

        // 中心亮芯独立发光，与箭头外缘形成明暗反差。
        if (bodyAlpha > 0) {
            poseStack.pushPose();
            poseStack.translate(0.0F, 0.0F, FRONT_Z + GLOW_OFFSET * 2.0F);
            VertexConsumer core = this.getBuffer(bufferSource, DanmakuRenderTypes.additiveGlow(TEXTURE));
            DanmakuRenderProbe.countCore();
            DanmakuRenderProbe.pushCore();
            try {
                this.renderBrightCore(poseStack.last(), core,
                        Math.round(CORE_ALPHA * fade), FULL_BRIGHT);
            } finally {
                DanmakuRenderProbe.pop();
            }
            poseStack.popPose();
        }

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    protected void renderShape(ConeDanmaku entity, PoseStack poseStack, VertexConsumer consumer,
                                int r, int g, int b, int a, int light) {
        PoseStack.Pose pose = poseStack.last();

        double[] outline = DanmakuGeometry.arrowheadOutline(BODY_RADIUS);
        this.renderSideBand(consumer, pose, outline, REAR_SCALE, REAR_Z,
                BEVEL_SCALE, BEVEL_Z, r, g, b, a, light);
        this.renderSideBand(consumer, pose, outline, BEVEL_SCALE, BEVEL_Z,
                1.0F, FRONT_Z, r, g, b, a, light);
        this.renderFrontFace(pose, consumer, BODY_RADIUS, FRONT_Z, r, g, b, a, light);
        this.renderBackFace(pose, consumer, outline, REAR_SCALE, REAR_Z, r, g, b, a, light);
    }

    private void renderSideBand(VertexConsumer consumer, PoseStack.Pose pose, double[] outline,
                                float scale0, float z0, float scale1, float z1,
                                int r, int g, int b, int a, int light) {
        for (int i = 0; i < SIDES; i++) {
            int next = (i + 1) % SIDES;
            double x0 = outline[i * 2];
            double y0 = outline[i * 2 + 1];
            double x1 = outline[next * 2];
            double y1 = outline[next * 2 + 1];
            int shade = (i % 3 == 0) ? 168 : 205;
            this.vertex(consumer, pose, (float) (x0 * scale0), (float) (y0 * scale0), z0, 0.5F, 0.5F,
                    r * shade / 255, g * shade / 255, b * shade / 255, a, light);
            this.vertex(consumer, pose, (float) (x1 * scale0), (float) (y1 * scale0), z0, 0.5F, 0.5F,
                    r * shade / 255, g * shade / 255, b * shade / 255, a, light);
            this.vertex(consumer, pose, (float) (x1 * scale1), (float) (y1 * scale1), z1, 0.5F, 0.5F,
                    r * shade / 255, g * shade / 255, b * shade / 255, a, light);
            this.vertex(consumer, pose, (float) (x0 * scale1), (float) (y0 * scale1), z1, 0.5F, 0.5F,
                    r * shade / 255, g * shade / 255, b * shade / 255, a, light);
        }
    }

    private void renderFrontFace(PoseStack.Pose pose, VertexConsumer consumer, float radius, float z,
                                 int r, int g, int b, int a, int light) {
        double[] outline = DanmakuGeometry.arrowheadOutline(radius);
        for (int i = 0; i < SIDES; i++) {
            int next = (i + 1) % SIDES;
            this.vertex(consumer, pose, 0.0F, 0.0F, z, 0.5F, 0.5F, r, g, b, a, light);
            this.faceVertex(consumer, pose, outline[i * 2], outline[i * 2 + 1], radius, z, r, g, b, a, light);
            this.faceVertex(consumer, pose, outline[next * 2], outline[next * 2 + 1], radius, z, r, g, b, a, light);
            // RenderType uses QUADS; repeat the final point to encode this triangle as a degenerate quad.
            this.faceVertex(consumer, pose, outline[next * 2], outline[next * 2 + 1], radius, z, r, g, b, a, light);
        }
    }

    private void renderBackFace(PoseStack.Pose pose, VertexConsumer consumer, double[] outline,
                                float scale, float z, int r, int g, int b, int a, int light) {
        for (int i = 0; i < SIDES; i++) {
            int next = (i + 1) % SIDES;
            this.vertex(consumer, pose, 0.0F, 0.0F, z, 0.5F, 0.5F, r, g, b, a, light);
            this.vertex(consumer, pose, (float) (outline[next * 2] * scale),
                    (float) (outline[next * 2 + 1] * scale), z,
                    0.5F, 0.5F, r, g, b, a, light);
            this.vertex(consumer, pose, (float) (outline[i * 2] * scale),
                    (float) (outline[i * 2 + 1] * scale), z,
                    0.5F, 0.5F, r, g, b, a, light);
            // Keep each triangle fan wedge on the QUADS vertex boundary.
            this.vertex(consumer, pose, (float) (outline[i * 2] * scale),
                    (float) (outline[i * 2 + 1] * scale), z,
                    0.5F, 0.5F, r, g, b, a, light);
        }
    }

    private void faceVertex(VertexConsumer consumer, PoseStack.Pose pose, double x, double y, float radius,
                            float z, int r, int g, int b, int a, int light) {
        float u = (float) (0.5D + x / (2.0D * radius));
        float v = (float) (0.5D - y / (2.0D * radius));
        this.vertex(consumer, pose, (float) x, (float) y, z, u, v, r, g, b, a, light);
    }

    /** A cropped white inset follows the source sprite's central highlight without whitening its red edge. */
    private void renderBrightCore(PoseStack.Pose pose, VertexConsumer consumer, int alpha, int light) {
        float halfWidth = BODY_RADIUS * 0.50F;
        float halfHeight = BODY_RADIUS * 0.25F;
        float centerU = 0.5F;
        float centerV = 11.0F / 16.0F;
        for (int i = 0; i < 8; i++) {
            int next = (i + 1) % 8;
            double angle0 = 2.0D * Math.PI * i / 8.0D;
            double angle1 = 2.0D * Math.PI * next / 8.0D;
            float x0 = (float) Math.cos(angle0);
            float y0 = (float) Math.sin(angle0);
            float x1 = (float) Math.cos(angle1);
            float y1 = (float) Math.sin(angle1);
            float u0 = centerU + x0 * 0.25F;
            float v0 = centerV - y0 * 2.0F / 16.0F;
            float u1 = centerU + x1 * 0.25F;
            float v1 = centerV - y1 * 2.0F / 16.0F;
            this.vertex(consumer, pose, 0.0F, 0.0F, 0.0F, centerU, centerV,
                    255, 255, 255, alpha, light);
            this.vertex(consumer, pose, x0 * halfWidth, y0 * halfHeight, 0.0F, u0, v0,
                    255, 255, 255, alpha, light);
            this.vertex(consumer, pose, x1 * halfWidth, y1 * halfHeight, 0.0F, u1, v1,
                    255, 255, 255, alpha, light);
            // The translucent entity render type consumes quads; duplicate the last vertex for this fan wedge.
            this.vertex(consumer, pose, x1 * halfWidth, y1 * halfHeight, 0.0F, u1, v1,
                    255, 255, 255, alpha, light);
        }
    }
}
