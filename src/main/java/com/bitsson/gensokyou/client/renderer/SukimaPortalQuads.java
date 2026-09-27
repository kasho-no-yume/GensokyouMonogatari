package com.bitsson.gensokyou.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * Billboard quad 绘制工具：隙间传送门 BER 与未来传送门类技能 ER 共用。
 *
 * <p>在<b>当前局部坐标系</b>原点绘制一张 z=0 的对称 quad（宽 2×halfW、高 2×halfH），
 * UV 全幅 0..1 映射后叠加相位偏移（REPEAT wrap 下循环滚动）。不施加任何朝向变换——
 * billboard 朝向由调用方负责：BER 用 {@code dispatcher.camera.rotation()} +
 * Y 轴 180°，ER 用 {@code entityRenderDispatcher.cameraOrientation()} + 同样翻转。
 *
 * <p>顶点排布与 {@link BillboardRenderer} 一致：底左/底右/顶右/顶左外向 CCW，
 * V=0 对应贴图顶部；本类不引用任何项目方块实体/渲染器类型。
 */
public final class SukimaPortalQuads {

    private SukimaPortalQuads() {
    }

    /**
     * 便捷入口：以隙间传送门 RenderType（REPEAT wrap，可滚动 UV）绘制。
     */
    public static void draw(PoseStack poseStack, MultiBufferSource bufferSource,
                            ResourceLocation texture, float halfW, float halfH,
                            float uPhase, float vPhase, int tint, int alpha, int light) {
        draw(poseStack, bufferSource, SukimaPortalRenderTypes.portal(texture),
                halfW, halfH, uPhase, vPhase, tint, alpha, light);
    }

    /**
     * 绘制一张 billboard quad（任意 RenderType，如描边层的 cutout）。
     *
     * @param renderType 渲染类型；UV 滚动依赖其贴图 wrap 为 REPEAT
     * @param halfW      半宽（格）；quad 横跨 [-halfW, +halfW]
     * @param halfH      半高（格）；quad 纵跨 [-halfH, +halfH]
     * @param uPhase     U 相位（幅），取模到 [0,1) 后叠加；递增 = 内容向 -U（左）流动
     * @param vPhase     V 相位（幅），取模到 [0,1) 后叠加；递增 = 内容向 -V（上）流动（吸入感）
     * @param tint       染色 0xRRGGBB（0xFFFFFF = 原色）
     * @param alpha      不透明度 0..255
     * @param light      光照（{@link LightTexture#FULL_BRIGHT} = 全亮度）
     */
    public static void draw(PoseStack poseStack, MultiBufferSource bufferSource,
                            RenderType renderType, float halfW, float halfH,
                            float uPhase, float vPhase, int tint, int alpha, int light) {
        float u0 = wrap(uPhase);
        float v0 = wrap(vPhase);
        drawUvRange(poseStack, bufferSource, renderType, halfW, halfH,
                u0, v0, u0 + 1.0F, v0 + 1.0F, tint, alpha, light);
    }

    /**
     * 以**显式 UV 区间**绘制一张 billboard quad（宽 2×halfW、高 2×halfH，z=0）。
     *
     * <p>供需要只取贴图某一段的几何使用——例如眼睑描边沿眼中线切成上下两片、各自独立
     * 开合：给上片 {@code v ∈ [0, vTip]}、下片 {@code v ∈ [vTip, 1]}，配合被压扁的 quad
     * 即得「眼睑开合」而非整幅缩放。
     *
     * <p>本类不引用任何项目方块实体/渲染器类型；UV 区间作为通用参数传入。
     */
    public static void drawUvRange(PoseStack poseStack, MultiBufferSource bufferSource,
                                   RenderType renderType, float halfW, float halfH,
                                   float u0, float v0, float u1, float v1,
                                   int tint, int alpha, int light) {
        VertexConsumer consumer = bufferSource.getBuffer(renderType);
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();

        int r = (tint >> 16) & 0xFF;
        int g = (tint >> 8) & 0xFF;
        int b = tint & 0xFF;

        // V=0 对应贴图顶部：quad 底边取 v1、顶边取 v0
        vertex(consumer, matrix, pose, -halfW, -halfH, u0, v1, r, g, b, alpha, light);
        vertex(consumer, matrix, pose, halfW, -halfH, u1, v1, r, g, b, alpha, light);
        vertex(consumer, matrix, pose, halfW, halfH, u1, v0, r, g, b, alpha, light);
        vertex(consumer, matrix, pose, -halfW, halfH, u0, v0, r, g, b, alpha, light);
    }

    /** 相位取模到 [0,1)：端点 UV 保持 1.0 整幅跨度，REPEAT wrap 在采样时回绕。 */
    private static float wrap(float phase) {
        return phase - (float) Math.floor(phase);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose pose,
                               float x, float y, float u, float v,
                               int r, int g, int b, int a, int light) {
        consumer.addVertex(matrix, x, y, 0.0F)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);
    }
}
