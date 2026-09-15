package com.bitsson.gensokyou.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * 仪式运行态特效共享几何（ritual-fx-overhaul D2）：从 {@link LaserDanmakuRenderer}
 * 提炼的中性发射器——方向对齐米字面片、竖直火柱面片、端点十字光斑、单位经纬球。
 * 全部配合 {@link DanmakuRenderTypes} 的加法混合 RenderType（NEW_ENTITY 格式）使用；
 * 自发光固定满亮度。刻意不依赖摄像机向量：单面条带在掠射角会棱边消失，
 * 交叉多面从任意角度都有正对分量（实机验证过的激光路线）。
 */
public final class FxGeometry {

    /** 自发光满亮度（不受环境光影响）。 */
    public static final int FULL_BRIGHT = 0xF000F0;

    /** 单位经纬球切片数（24×24=576 面片；5 阶大半径下仍要够细）。 */
    private static final int SPHERE_STACKS = 24;
    private static final int SPHERE_SLICES = 24;

    private static final double RAD = 180.0D / Math.PI;

    private FxGeometry() {
    }

    /** 输出一个自发光顶点（NEW_ENTITY 格式；法线固定朝上，加法材质不依赖精确法线）。 */
    public static void vertex(VertexConsumer c, PoseStack.Pose pose,
                              float x, float y, float z, float u, float v,
                              int r, int g, int b, int a) {
        c.addVertex(pose.pose(), x, y, z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    /**
     * 沿 a→b 的米字交叉平面光束（激光渲染器已实机验证的几何路线，逐段版）：
     * 局部系内把 +Z 对齐段方向，绕 Z 均布 planes 个矩形面片。
     * U 横跨束宽（0..1）、V 沿长度（v0 起每格约 0.7uv，滚动即能量流）。
     * 闪电弧/雾带共用；调用方 poseStack 处于特效局部系。
     */
    public static void emitAlignedBeam(PoseStack pose, VertexConsumer c,
                                       float ax, float ay, float az, float bx, float by, float bz,
                                       float halfWidth, int planes, float v0,
                                       int r, int g, int b, int a) {
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0E-4F || halfWidth <= 0F || a <= 0) {
            return;
        }
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        pose.pushPose();
        pose.translate(ax, ay, az);
        if (horizontal < 1.0E-6D) {
            pose.mulPose(Axis.XP.rotationDegrees(dy > 0.0F ? -90.0F : 90.0F));
        } else {
            pose.mulPose(Axis.YP.rotationDegrees((float) (Math.atan2(dx, dz) * RAD)));
            pose.mulPose(Axis.XP.rotationDegrees((float) (Math.atan2(-dy, horizontal) * RAD)));
        }
        float step = 180.0F / planes;
        float v1 = v0 + (float) length * 0.7F;
        for (int i = 0; i < planes; i++) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(i * step));
            PoseStack.Pose p = pose.last();
            vertex(c, p, -halfWidth, 0.0F, 0.0F, 0.0F, v0, r, g, b, a);
            vertex(c, p, halfWidth, 0.0F, 0.0F, 1.0F, v0, r, g, b, a);
            vertex(c, p, halfWidth, 0.0F, (float) length, 1.0F, v1, r, g, b, a);
            vertex(c, p, -halfWidth, 0.0F, (float) length, 0.0F, v1, r, g, b, a);
            pose.popPose();
        }
        pose.popPose();
    }

    /**
     * 竖直米字交叉面片（火柱主体）：绕局部竖轴均布 planes 个平面（各 180/planes 步进），
     * 宽度 2*halfWidth、高 height，UV：U 满幅 0..1、V 从 v1(底) 到 v0(顶)（滚动即上升）；
     * 底缘 alpha=baseAlpha、顶缘 alpha=0，叠加纹理向黑渐变收束出火尖。
     * 调用方需把 poseStack 移到柱底中心（局部坐标）。
     */
    public static void emitCrossPlanes(PoseStack pose, VertexConsumer c, int planes,
                                       float halfWidth, float height,
                                       float v0, float v1,
                                       int r, int g, int b, int baseAlpha) {
        float step = 180.0F / planes;
        for (int i = 0; i < planes; i++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(i * step));
            PoseStack.Pose p = pose.last();
            vertex(c, p, -halfWidth, 0.0F, 0.0F, 0.0F, v1, r, g, b, baseAlpha);
            vertex(c, p, halfWidth, 0.0F, 0.0F, 1.0F, v1, r, g, b, baseAlpha);
            vertex(c, p, halfWidth, height, 0.0F, 1.0F, v0, r, g, b, 0);
            vertex(c, p, -halfWidth, height, 0.0F, 0.0F, v0, r, g, b, 0);
            pose.popPose();
        }
    }

    /**
     * 端点十字光斑（落雷点等）：三轴三张全幅贴图面片交叉，径向渐变贴图下任意角度呈光晕。
     */
    public static void emitCrossGlow(PoseStack pose, VertexConsumer c,
                                     float cx, float cy, float cz, float half,
                                     int r, int g, int b, int a) {
        if (half <= 0F || a <= 0) {
            return;
        }
        pose.pushPose();
        pose.translate(cx, cy, cz);
        for (int i = 0; i < 3; i++) {
            pose.pushPose();
            if (i == 1) {
                pose.mulPose(Axis.XP.rotationDegrees(90.0F));
            } else if (i == 2) {
                pose.mulPose(Axis.ZP.rotationDegrees(90.0F));
            }
            PoseStack.Pose p = pose.last();
            vertex(c, p, -half, -half, 0.0F, 0.0F, 1.0F, r, g, b, a);
            vertex(c, p, half, -half, 0.0F, 1.0F, 1.0F, r, g, b, a);
            vertex(c, p, half, half, 0.0F, 1.0F, 0.0F, r, g, b, a);
            vertex(c, p, -half, half, 0.0F, 0.0F, 0.0F, r, g, b, a);
            pose.popPose();
        }
        pose.popPose();
    }

    /**
     * 发射一颗中心在局部原点、半径 {@code radius} 的经纬球（POSITION_COLOR_TEX_LIGHTMAP 格式（多出的 lightmap 槽着色器未声明即忽略），
     * 供 {@link SpiritOrbRenderTypes} 用）。UV=（极角、方位角）参数化，着色器按 Time
     * 滚动采样雾噪声出"气"的流动；法线由 normalize(Position) 推导。
     * 顶点色白 + alpha 承载整体淡入淡出包络。调用方负责 translate 到球心。
     */
    public static void emitUnitSphere(VertexConsumer c, PoseStack.Pose pose,
                                      float radius, int r, int g, int b, int a) {
        final float stepPhi = (float) Math.PI / SPHERE_STACKS;      // 极角
        final float stepTheta = 2.0F * (float) Math.PI / SPHERE_SLICES; // 方位角
        for (int i = 0; i < SPHERE_STACKS; i++) {
            float p0 = i * stepPhi;
            float p1 = p0 + stepPhi;
            float y0 = (float) Math.cos(p0);
            float y1 = (float) Math.cos(p1);
            float rr0 = (float) Math.sin(p0);
            float rr1 = (float) Math.sin(p1);
            float u0 = i * stepPhi / (float) Math.PI;
            float u1 = (i + 1) * stepPhi / (float) Math.PI;
            for (int j = 0; j < SPHERE_SLICES; j++) {
                float t0 = j * stepTheta;
                float t1 = t0 + stepTheta;
                float v0 = j / (float) SPHERE_SLICES;
                float v1 = (j + 1) / (float) SPHERE_SLICES;
                float cx0 = (float) Math.cos(t0);
                float sz0 = (float) Math.sin(t0);
                float cx1 = (float) Math.cos(t1);
                float sz1 = (float) Math.sin(t1);
                orbVertex(c, pose, radius, rr0 * cx0, y0, rr0 * sz0, u0, v0, r, g, b, a);
                orbVertex(c, pose, radius, rr1 * cx0, y1, rr1 * sz0, u1, v0, r, g, b, a);
                orbVertex(c, pose, radius, rr1 * cx1, y1, rr1 * sz1, u1, v1, r, g, b, a);
                orbVertex(c, pose, radius, rr0 * cx1, y0, rr0 * sz1, u0, v1, r, g, b, a);
            }
        }
    }

    private static void orbVertex(VertexConsumer c, PoseStack.Pose pose, float radius,
                                  float nx, float ny, float nz, float u, float v,
                                  int r, int g, int b, int a) {
        // BufferBuilder 强制格式全元素写入（崩过 Missing UV2）：lightmap 槽填满亮度，着色器未声明即忽略
        c.addVertex(pose.pose(), nx * radius, ny * radius, nz * radius)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setLight(FULL_BRIGHT);
    }
}
