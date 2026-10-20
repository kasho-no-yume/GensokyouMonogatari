package com.bitsson.gensokyou.client.renderer;

import com.bitsson.gensokyou.Gensokyou;
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
 * 锥形弹幕渲染器：手搓 3D 锥体（菱形截面 × 3 段收束），局部 <b>+Z = 尖端 = 飞行方向</b>。
 *
 * <p>与 {@code KnifeDanmakuRenderer} 同一范式：朝向取实体的 yaw/pitch
 * （由父类每 tick 由速度写入），故锥体永远指向运动方向；静止时保持最后朝向。
 *
 * <p><b>为什么 MUST 3D 而非 billboard</b>：弹体有明确朝向，billboard 会让它永远正对相机，
 * 于是"这一发朝哪飞"这一信息丢失，锥尖与速度方向的关系也读不出来。
 *
 * <p>几何（总长 0.45 格、总宽 0.2 格）：
 * <pre>
 *   环0 尾端  z = -0.225  半宽 0.055   钝
 *   环1 腰身  z = -0.025  半宽 0.100   最宽 → 总宽 0.2 格
 *   环2 收束  z = +0.140  半宽 0.055
 *   环3 尖端  z = +0.225  半宽 0.008   平滑尖，不留死面
 * </pre>
 *
 * <p>贴图 {@code textures/entity/cone_danmaku.png} 为 16×16 灰度图集：
 * 横向 4 组（每组 4 列）分别对应 4 个侧面，纵向是"尖端亮 → 尾端暗"的长度渐变；
 * 色相由实体顶点色给出（贴图只提供明暗）。
 */
public class ConeDanmakuRenderer extends AbstractDanmakuRenderer<ConeDanmaku> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Gensokyou.MODID, "textures/entity/cone_danmaku.png");

    // ---------------- 几何（局部 +Z = 尖端） ----------------

    /** 尾端环 z（钝）。 */
    private static final float TAIL_Z = -0.225F;
    /** 腰身环 z（最宽）。 */
    private static final float WAIST_Z = -0.025F;
    /** 收束环 z。 */
    private static final float CONE_Z = 0.140F;
    /** 尖端环 z（平滑尖）。 */
    private static final float TIP_Z = 0.225F;

    private static final float TAIL_HW = 0.055F;
    private static final float WAIST_HW = 0.100F;
    private static final float CONE_HW = 0.055F;
    private static final float TIP_HW = 0.008F;

    // ---------------- 发光 ----------------

    /**
     * 外发光：放大的加法副本。
     *
     * <p>东方弹幕普遍"自带亮度"——要么本体高亮，要么外圈发光。锥弹是锐利的 3D 多面体，
     * 单靠顶点色不够亮，故补一层加法发光：本体给轮廓，发光给"亮起来"的观感。
     */
    private static final float GLOW_SCALE = 1.7F;
    private static final int GLOW_ALPHA = 95;

    public ConeDanmakuRenderer(EntityRendererProvider.Context context) {
        super(context, TEXTURE);
    }

    /**
     * 本体用 alpha 混合而非 cutout。
     *
     * <p>cutout 只做"alpha &lt; 0.1 就丢弃"，**没有半透明**——近相机淡出需要真正的
     * alpha 衰减，故走 {@link DanmakuRenderTypes#translucent}（alpha 混合 + 不裁背面，
     * 锥体的绕序不必纠结正反面）。
     */
    @Override
    protected RenderType baseRenderType() {
        return DanmakuRenderTypes.translucent(this.texture);
    }

    @Override
    public void render(ConeDanmaku entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();

        // 用插值朝向，避免低同步频率下的抖动；yaw 取正值使局部 Z+ 对准速度方向
        float yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        float pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());

        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(-pitch));

        int color = entity.getColor();
        // 近相机淡出：弹丸生成在眼睛 = 相机位置，第一帧就贴在镜头上；1.7 倍的加法
        // 外发光叠上去就是一次整屏白闪，而麟弹 10 发/秒 = 10 Hz 频闪。
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

        // 外发光：放大的加法副本（东方弹幕的"亮"主要来自这一层）
        if (glowAlpha > 0) {
            poseStack.pushPose();
            poseStack.translate(0.0F, 0.0F, GLOW_OFFSET);
            poseStack.scale(GLOW_SCALE, GLOW_SCALE, GLOW_SCALE);
            VertexConsumer glow = this.getBuffer(bufferSource, DanmakuRenderTypes.additiveGlow(TEXTURE));
            this.renderShape(entity, poseStack, glow,
                    red(color), green(color), blue(color), glowAlpha, FULL_BRIGHT);
            poseStack.popPose();
        }

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    protected void renderShape(ConeDanmaku entity, PoseStack poseStack, VertexConsumer consumer,
                                int r, int g, int b, int a, int light) {
        PoseStack.Pose pose = poseStack.last();

        float[] zs = {TAIL_Z, WAIST_Z, CONE_Z, TIP_Z};
        float[] hws = {TAIL_HW, WAIST_HW, CONE_HW, TIP_HW};

        // 3 段收束 × 4 个侧面
        for (int seg = 0; seg < 3; seg++) {
            float z0 = zs[seg];
            float z1 = zs[seg + 1];
            float h0 = hws[seg];
            float h1 = hws[seg + 1];
            float v0 = vOf(z0);
            float v1 = vOf(z1);
            for (int face = 0; face < 4; face++) {
                int next = (face + 1) % 4;
                float u0 = face * 0.25F;
                float u1 = (face + 1) * 0.25F;
                this.vertex(consumer, pose, edgeX(face, h0), edgeY(face, h0), z0, u0, v0, r, g, b, a, light);
                this.vertex(consumer, pose, edgeX(next, h0), edgeY(next, h0), z0, u1, v0, r, g, b, a, light);
                this.vertex(consumer, pose, edgeX(next, h1), edgeY(next, h1), z1, u1, v1, r, g, b, a, light);
                this.vertex(consumer, pose, edgeX(face, h1), edgeY(face, h1), z1, u0, v1, r, g, b, a, light);
            }
        }

        // 尾端封盖（菱形，取最暗的一档）
        this.vertex(consumer, pose, 0.0F, TAIL_HW, TAIL_Z, 0.0F, 1.0F, r, g, b, a, light);
        this.vertex(consumer, pose, TAIL_HW, 0.0F, TAIL_Z, 0.25F, 1.0F, r, g, b, a, light);
        this.vertex(consumer, pose, 0.0F, -TAIL_HW, TAIL_Z, 0.5F, 1.0F, r, g, b, a, light);
        this.vertex(consumer, pose, -TAIL_HW, 0.0F, TAIL_Z, 0.75F, 1.0F, r, g, b, a, light);
    }

    /** 菱形截面的第 i 条棱（0 上、1 右、2 下、3 左）的横坐标。 */
    private static float edgeX(int i, float halfWidth) {
        return switch (i) {
            case 1 -> halfWidth;
            case 3 -> -halfWidth;
            default -> 0.0F;
        };
    }

    /** 菱形截面的第 i 条棱的纵坐标。 */
    private static float edgeY(int i, float halfWidth) {
        return switch (i) {
            case 0 -> halfWidth;
            case 2 -> -halfWidth;
            default -> 0.0F;
        };
    }

    /** 长度方向的贴图 V：尖端 v=0（亮），尾端 v=1（暗）。 */
    private static float vOf(float z) {
        return (TIP_Z - z) / (TIP_Z - TAIL_Z);
    }
}
