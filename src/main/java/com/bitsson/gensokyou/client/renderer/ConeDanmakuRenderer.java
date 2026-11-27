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
 * 鳞形弹幕渲染器：略带弧度的低面数短锥体，局部 +Z 为尖端与飞行方向。
 *
 * <p>X、Y 采用相同半径形成八边截面，背面白芯向彩色边缘过渡，外缘略微半透明。
 * 主体沿飞行轴收尖，避免平面鳞片或带尾柄的箭矢造型。
 */
public class ConeDanmakuRenderer extends AbstractDanmakuRenderer<ConeDanmaku> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Gensokyou.MODID, "textures/entity/arrowhead_danmaku.png");

    private static final int SIDES = 8;
    private static final float BODY_RADIUS = 0.15F;
    private static final float HALF_LENGTH = 0.17F;
    private static final float CORE_RADIUS = 0.58F;
    private static final DanmakuGeometry.ArrowheadSection[] SECTIONS = DanmakuGeometry.arrowheadSections();

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
        for (int ring = 0; ring < SECTIONS.length - 1; ring++) {
            var first = SECTIONS[ring];
            var second = SECTIONS[ring + 1];
            for (int side = 0; side < SIDES; side++) {
                this.coneVertex(consumer, pose, first, side, r, g, b, a, light);
                this.coneVertex(consumer, pose, first, side + 1, r, g, b, a, light);
                this.coneVertex(consumer, pose, second, side + 1, r, g, b, a, light);
                this.coneVertex(consumer, pose, second, side, r, g, b, a, light);
            }
        }
        // A white central fan and a colored, translucent rim close the rear of the cone.
        for (int side = 0; side < SIDES; side++) {
            this.capVertex(consumer, pose, 0.0F, side, r, g, b, a, light);
            this.capVertex(consumer, pose, CORE_RADIUS, side + 1, r, g, b, a, light);
            this.capVertex(consumer, pose, CORE_RADIUS, side, r, g, b, a, light);
            this.capVertex(consumer, pose, CORE_RADIUS, side, r, g, b, a, light);
            this.capVertex(consumer, pose, CORE_RADIUS, side, r, g, b, a, light);
            this.capVertex(consumer, pose, CORE_RADIUS, side + 1, r, g, b, a, light);
            this.capVertex(consumer, pose, 1.0F, side + 1, r, g, b, a, light);
            this.capVertex(consumer, pose, 1.0F, side, r, g, b, a, light);
        }
    }

    private void coneVertex(VertexConsumer consumer, PoseStack.Pose pose,
                            DanmakuGeometry.ArrowheadSection section, int side,
                            int r, int g, int b, int a, int light) {
        float angle = (float) (Math.PI * 0.5D + Math.PI * 2.0D * side / SIDES);
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        float radius = BODY_RADIUS * (float) section.radius();
        float shade = 0.86F + 0.14F * Math.max(0.0F, sin);
        float white = (float) section.white();
        int brightness = Math.max(r, Math.max(g, b));
        this.vertex(consumer, pose, cos * radius, sin * radius, (float) section.z() * HALF_LENGTH,
                side / (float) SIDES, 0.5F - (float) section.z() * 0.5F,
                highlight(r, brightness, shade, white), highlight(g, brightness, shade, white),
                highlight(b, brightness, shade, white), Math.round(a * (float) section.alpha()), light);
    }

    private void capVertex(VertexConsumer consumer, PoseStack.Pose pose, float radius, int side,
                           int r, int g, int b, int a, int light) {
        float angle = (float) (Math.PI * 0.5D + Math.PI * 2.0D * side / SIDES);
        float x = (float) Math.cos(angle) * radius;
        float y = (float) Math.sin(angle) * radius;
        var tail = SECTIONS[0];
        boolean rim = radius > CORE_RADIUS;
        float white = rim ? (float) tail.white() : 1.0F;
        int brightness = Math.max(r, Math.max(g, b));
        this.vertex(consumer, pose, x * BODY_RADIUS, y * BODY_RADIUS, (float) tail.z() * HALF_LENGTH,
                0.5F + x * 0.5F, 0.5F - y * 0.5F,
                highlight(r, brightness, 0.98F, white), highlight(g, brightness, 0.98F, white),
                highlight(b, brightness, 0.98F, white), rim ? Math.round(a * (float) tail.alpha()) : a, light);
    }

    private static int highlight(int tint, int brightness, float shade, float white) {
        return Math.max(0, Math.min(255, Math.round((tint + (brightness - tint) * white) * shade)));
    }
}
