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
 * 鳞形弹幕渲染器：原作的拱形双翼、白色内纹与中央亮瓣，局部 +Z 为飞行方向。
 *
 * <p>鳞片沿 X-Z 平面展开，Y 方向为真实厚度。前尖、后缘分叉与白芯均由同一模型
 * 提供，不把鳞弹做成带尾柄的箭矢或金属锥体。
 */
public class ConeDanmakuRenderer extends AbstractDanmakuRenderer<ConeDanmaku> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Gensokyou.MODID, "textures/entity/arrowhead_danmaku.png");

    private static final float BODY_RADIUS = 0.15F;
    private static final float HALF_LENGTH = 0.17F;
    private static final float HALF_THICKNESS = 0.035F;
    private static final DanmakuGeometry.ArrowheadSection[] SECTIONS = DanmakuGeometry.arrowheadSections();
    // Two strips retain the white ridge without subdividing the model for smooth curvature.
    private static final float[] FACE_STEPS = {0.0F, 0.50F, 1.0F};

    private static final float GLOW_SCALE = 1.08F;
    private static final int GLOW_ALPHA = 40;

    public ConeDanmakuRenderer(EntityRendererProvider.Context context) {
        super(context, TEXTURE);
    }

    /**
     * 本体用带 mipmap 的 alpha 混合以保留高清纹理与近相机淡出。
     *
     * <p>cutout 只做"alpha &lt; 0.1 就丢弃"，**没有半透明**——近相机淡出需要真正的
     * alpha 衰减，故走带深度缓冲的平滑纹理档；实体完全不透明时仍按通常弹体遮挡。
     */
    @Override
    protected RenderType baseRenderType() {
        return DanmakuRenderTypes.translucentDepthFiltered(this.texture);
    }

    @Override
    public void render(ConeDanmaku entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();

        // 用插值朝向，避免低同步频率下的抖动；局部 +Z 的尖端对准速度方向。
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
            VertexConsumer glow = this.getBuffer(bufferSource, DanmakuRenderTypes.additiveGlowFiltered(TEXTURE));
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

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    protected void renderShape(ConeDanmaku entity, PoseStack poseStack, VertexConsumer consumer,
                                int r, int g, int b, int a, int light) {
        PoseStack.Pose pose = poseStack.last();
        for (int part = -1; part <= 1; part++) {
            for (int ring = 0; ring < SECTIONS.length - 1; ring++) {
                var first = SECTIONS[ring];
                var second = SECTIONS[ring + 1];
                for (int step = 0; step < FACE_STEPS.length - 1; step++) {
                    float t0 = FACE_STEPS[step];
                    float t1 = FACE_STEPS[step + 1];
                    this.arrowFace(consumer, pose, first, second, part, t0, t1, true, r, g, b, a, light);
                    this.arrowFace(consumer, pose, first, second, part, t0, t1, false, r, g, b, a, light);
                }
                for (int edge = 0; edge <= 1; edge++) {
                    boolean joined = first.coreWidth() == first.wingInnerWidth()
                            && second.coreWidth() == second.wingInnerWidth();
                    if (joined && (part == 0 || edge == 0)) continue;
                    this.arrowVertex(consumer, pose, first, part, edge, true, 0.72F, r, g, b, a, light);
                    this.arrowVertex(consumer, pose, first, part, edge, false, 0.72F, r, g, b, a, light);
                    this.arrowVertex(consumer, pose, second, part, edge, false, 0.72F, r, g, b, a, light);
                    this.arrowVertex(consumer, pose, second, part, edge, true, 0.72F, r, g, b, a, light);
                }
            }
            // The two rear wings have visible end faces; the forward end closes at one point.
            this.arrowVertex(consumer, pose, SECTIONS[0], part, 0.0F, true, 0.78F, r, g, b, a, light);
            this.arrowVertex(consumer, pose, SECTIONS[0], part, 1.0F, true, 0.78F, r, g, b, a, light);
            this.arrowVertex(consumer, pose, SECTIONS[0], part, 1.0F, false, 0.78F, r, g, b, a, light);
            this.arrowVertex(consumer, pose, SECTIONS[0], part, 0.0F, false, 0.78F, r, g, b, a, light);
        }
    }

    private void arrowFace(VertexConsumer consumer, PoseStack.Pose pose,
                           DanmakuGeometry.ArrowheadSection first, DanmakuGeometry.ArrowheadSection second,
                           int part, float t0, float t1, boolean top,
                           int r, int g, int b, int a, int light) {
        if ((part < 0) != !top) {
            float swap = t0;
            t0 = t1;
            t1 = swap;
        }
        float shade = top ? 1.0F : 0.94F;
        this.arrowVertex(consumer, pose, first, part, t0, top, shade, r, g, b, a, light);
        this.arrowVertex(consumer, pose, second, part, t0, top, shade, r, g, b, a, light);
        this.arrowVertex(consumer, pose, second, part, t1, top, shade, r, g, b, a, light);
        this.arrowVertex(consumer, pose, first, part, t1, top, shade, r, g, b, a, light);
    }

    private void arrowVertex(VertexConsumer consumer, PoseStack.Pose pose,
                             DanmakuGeometry.ArrowheadSection section, int part, float t, boolean top,
                             float shade, int r, int g, int b, int a, int light) {
        float x = part == 0 ? (float) section.coreWidth() * (t * 2.0F - 1.0F)
                : part * (float) (section.wingInnerWidth() + (section.outerWidth() - section.wingInnerWidth()) * t);
        float y = (top ? 1.0F : -1.0F) * HALF_THICKNESS * (float) section.height();
        float white = part == 0
                ? Math.min(1.0F, Math.max(0.0F, (0.30F - (float) section.z()) / 0.35F))
                        * Math.min(1.0F, (1.0F - Math.abs(t * 2.0F - 1.0F)) / 0.35F)
                : Math.max(0.0F, 1.0F - Math.abs(t - 0.50F) / 0.50F) * 0.94F
                        * Math.min(1.0F, Math.max(0.0F, (0.98F - (float) section.z()) / 0.18F));
        // The source sprite has a pale arch below the colored nose, separate from its lower white core.
        float arch = Math.max(0.0F, 1.0F - Math.abs((float) section.z() - 0.62F) / 0.24F)
                * (part == 0 ? 1.0F : 1.0F - t);
        white = Math.max(white, arch * 0.96F);
        shade *= 1.0F - arch * 0.35F;
        int brightness = Math.max(r, Math.max(g, b));
        this.vertex(consumer, pose, x * BODY_RADIUS, y, (float) section.z() * HALF_LENGTH,
                0.5F + x * 0.5F, 0.5F - (float) section.z() * 0.5F,
                highlight(r, brightness, shade, white), highlight(g, brightness, shade, white),
                highlight(b, brightness, shade, white), a, light);
    }

    private static int highlight(int tint, int brightness, float shade, float white) {
        return Math.max(0, Math.min(255, Math.round((tint + (brightness - tint) * white) * shade)));
    }
}
