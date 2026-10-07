package com.bitsson.gensokyou.client.renderer;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.danmaku.render.DanmakuRenderProbe;
import com.bitsson.gensokyou.entity.KnifeDanmaku;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * 飞刀渲染器：手搓苦无模型（菱形截面刀刃 + 护手 + 柄），长端朝向速度方向，无外发光。
 *
 * <p>贴图 {@code textures/entity/knife_danmaku.png} 为 16×16 图集：
 * <ul>
 *   <li>刃：左上 8×8（u 沿刃长：基部暗 → 尖端亮）</li>
 *   <li>护手：右上 rows 0-1</li>
 *   <li>柄：右上 rows 2-9（u 沿柄长带缠绳条纹）</li>
 *   <li>柄尾：右上 rows 10-11</li>
 * </ul>
 * 全部几何为四顶点 quad；局部 +Z = 刀尖 = 飞行方向。
 */
public class KnifeDanmakuRenderer extends AbstractDanmakuRenderer<KnifeDanmaku> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Gensokyou.MODID, "textures/entity/knife_danmaku.png");

    // ---------------- 几何（局部 +Z = 刀尖） ----------------
    private static final float BLADE_BASE_Z = -0.10F;
    private static final float BLADE_MID_Z = 0.30F;
    private static final float TIP_RING_Z = 0.59F;
    /** 刀尖几何端点（用于插墙锚点换算，勿随意改动：实体侧 TIP_OFFSET 依赖此值）。 */
    private static final float BLADE_TIP_Z = 0.60F;
    private static final float BLADE_HX = 0.035F;
    private static final float BLADE_HY = 0.080F;
    private static final float MID_SCALE = 0.55F;
    private static final float TIP_RING_SCALE = 0.12F;
    private static final float GUARD_Z0 = -0.18F;
    private static final float GUARD_Z1 = -0.10F;
    private static final float GUARD_HALF = 0.075F;
    private static final float HANDLE_Z0 = -0.58F;
    private static final float HANDLE_Z1 = -0.18F;
    private static final float HANDLE_HALF = 0.032F;

    // ---------------- UV 图集区域 ----------------
    private static final float BLADE_U0 = 0.0F;
    private static final float BLADE_U1 = 7.0F / 16.0F;
    private static final float BLADE_V0 = 0.5F / 16.0F;
    private static final float BLADE_V1 = 7.5F / 16.0F;
    private static final float GUARD_U0 = 8.5F / 16.0F;
    private static final float GUARD_U1 = 15.5F / 16.0F;
    private static final float GUARD_V0 = 0.5F / 16.0F;
    private static final float GUARD_V1 = 1.5F / 16.0F;
    private static final float HANDLE_U0 = 8.5F / 16.0F;
    private static final float HANDLE_U1 = 15.5F / 16.0F;
    private static final float HANDLE_V0 = 2.5F / 16.0F;
    private static final float HANDLE_V1 = 9.5F / 16.0F;
    private static final float POMMEL_V0 = 10.5F / 16.0F;
    private static final float POMMEL_V1 = 11.5F / 16.0F;

    public KnifeDanmakuRenderer(EntityRendererProvider.Context context) {
        super(context, TEXTURE);
    }

    @Override
    public void render(KnifeDanmaku entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();

        // 用插值朝向，避免低同步频率下的抖动；yaw 取正值使局部 Z+ 对准速度方向
        float yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        float pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());

        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(-pitch));

        int color = entity.getColor();
        if (DanmakuRenderProbe.effectiveBody()) {
            DanmakuRenderProbe.countBody();
            VertexConsumer consumer = this.getBuffer(bufferSource, this.baseRenderType());
            DanmakuRenderProbe.pushBody();
            try {
                this.renderShape(entity, poseStack, consumer,
                        red(color), green(color), blue(color), 255, packedLight);
            } finally {
                DanmakuRenderProbe.pop();
            }
        }

        // 飞刀不加发光层

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    protected void renderShape(KnifeDanmaku entity, PoseStack poseStack, VertexConsumer consumer,
                                int r, int g, int b, int a, int light) {
        PoseStack.Pose pose = poseStack.last();

        this.renderBlade(poseStack, consumer, r, g, b, a, light);
        this.renderGuard(poseStack, consumer, r, g, b, a, light);
        this.renderHandle(poseStack, consumer, r, g, b, a, light);
    }

    // ---------------- 刀刃：菱形截面，基部环 → 中部环 → 尖端环 ----------------

    private void renderBlade(PoseStack poseStack, VertexConsumer consumer,
                             int r, int g, int b, int a, int light) {
        PoseStack.Pose pose = poseStack.last();
        float hx0 = BLADE_HX;
        float hy0 = BLADE_HY;
        float hx1 = BLADE_HX * MID_SCALE;
        float hy1 = BLADE_HY * MID_SCALE;
        float hx2 = BLADE_HX * TIP_RING_SCALE;
        float hy2 = BLADE_HY * TIP_RING_SCALE;

        // 每个环的 4 个顶点：上(0,+hy) 右(+hx,0) 下(0,-hy) 左(-hx,0)
        float[][] r0 = {{0, hy0}, {hx0, 0}, {0, -hy0}, {-hx0, 0}};
        float[][] r1 = {{0, hy1}, {hx1, 0}, {0, -hy1}, {-hx1, 0}};
        float[][] r2 = {{0, hy2}, {hx2, 0}, {0, -hy2}, {-hx2, 0}};

        float uBase = BLADE_U0;
        float uMid = Mth.lerp((BLADE_MID_Z - BLADE_BASE_Z) / (BLADE_TIP_Z - BLADE_BASE_Z), BLADE_U0, BLADE_U1);
        float uTip = BLADE_U1;

        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            float v0 = Mth.lerp(i / 4.0F, BLADE_V0, BLADE_V1);
            float v1 = Mth.lerp((i + 1) / 4.0F, BLADE_V0, BLADE_V1);

            // 基部环 → 中部环
            this.vertex(consumer, pose, r0[i][0], r0[i][1], BLADE_BASE_Z, uBase, v0, r, g, b, a, light);
            this.vertex(consumer, pose, r0[j][0], r0[j][1], BLADE_BASE_Z, uBase, v1, r, g, b, a, light);
            this.vertex(consumer, pose, r1[j][0], r1[j][1], BLADE_MID_Z, uMid, v1, r, g, b, a, light);
            this.vertex(consumer, pose, r1[i][0], r1[i][1], BLADE_MID_Z, uMid, v0, r, g, b, a, light);

            // 中部环 → 尖端环（视觉收束成尖）
            this.vertex(consumer, pose, r1[i][0], r1[i][1], BLADE_MID_Z, uMid, v0, r, g, b, a, light);
            this.vertex(consumer, pose, r1[j][0], r1[j][1], BLADE_MID_Z, uMid, v1, r, g, b, a, light);
            this.vertex(consumer, pose, r2[j][0], r2[j][1], TIP_RING_Z, uTip, v1, r, g, b, a, light);
            this.vertex(consumer, pose, r2[i][0], r2[i][1], TIP_RING_Z, uTip, v0, r, g, b, a, light);
        }
    }

    // ---------------- 护手：方形短箍 ----------------

    private void renderGuard(PoseStack poseStack, VertexConsumer consumer,
                             int r, int g, int b, int a, int light) {
        PoseStack.Pose pose = poseStack.last();
        float h = GUARD_HALF;

        // 四个侧面
        this.quad(poseStack, consumer, -h, h, GUARD_Z0, h, h, GUARD_Z0, h, h, GUARD_Z1, -h, h, GUARD_Z1,
                GUARD_U0, GUARD_U1, GUARD_V0, GUARD_V1, r, g, b, a, light);
        this.quad(poseStack, consumer, h, -h, GUARD_Z0, -h, -h, GUARD_Z0, -h, -h, GUARD_Z1, h, -h, GUARD_Z1,
                GUARD_U0, GUARD_U1, GUARD_V0, GUARD_V1, r, g, b, a, light);
        this.quad(poseStack, consumer, h, h, GUARD_Z0, h, -h, GUARD_Z0, h, -h, GUARD_Z1, h, h, GUARD_Z1,
                GUARD_U0, GUARD_U1, GUARD_V0, GUARD_V1, r, g, b, a, light);
        this.quad(poseStack, consumer, -h, -h, GUARD_Z0, -h, h, GUARD_Z0, -h, h, GUARD_Z1, -h, -h, GUARD_Z1,
                GUARD_U0, GUARD_U1, GUARD_V0, GUARD_V1, r, g, b, a, light);

        // 前后端盖
        this.quad(poseStack, consumer, -h, h, GUARD_Z1, h, h, GUARD_Z1, h, -h, GUARD_Z1, -h, -h, GUARD_Z1,
                GUARD_U0, GUARD_U1, GUARD_V0, GUARD_V1, r, g, b, a, light);
        this.quad(poseStack, consumer, -h, -h, GUARD_Z0, h, -h, GUARD_Z0, h, h, GUARD_Z0, -h, h, GUARD_Z0,
                GUARD_U0, GUARD_U1, GUARD_V0, GUARD_V1, r, g, b, a, light);
    }

    // ---------------- 柄：细方柱 + 柄尾箍 ----------------

    private void renderHandle(PoseStack poseStack, VertexConsumer consumer,
                              int r, int g, int b, int a, int light) {
        PoseStack.Pose pose = poseStack.last();
        float h = HANDLE_HALF;

        this.quad(poseStack, consumer, -h, h, HANDLE_Z0, h, h, HANDLE_Z0, h, h, HANDLE_Z1, -h, h, HANDLE_Z1,
                HANDLE_U0, HANDLE_U1, HANDLE_V0, HANDLE_V1, r, g, b, a, light);
        this.quad(poseStack, consumer, h, -h, HANDLE_Z0, -h, -h, HANDLE_Z0, -h, -h, HANDLE_Z1, h, -h, HANDLE_Z1,
                HANDLE_U0, HANDLE_U1, HANDLE_V0, HANDLE_V1, r, g, b, a, light);
        this.quad(poseStack, consumer, h, h, HANDLE_Z0, h, -h, HANDLE_Z0, h, -h, HANDLE_Z1, h, h, HANDLE_Z1,
                HANDLE_U0, HANDLE_U1, HANDLE_V0, HANDLE_V1, r, g, b, a, light);
        this.quad(poseStack, consumer, -h, -h, HANDLE_Z0, -h, h, HANDLE_Z0, -h, h, HANDLE_Z1, -h, -h, HANDLE_Z1,
                HANDLE_U0, HANDLE_U1, HANDLE_V0, HANDLE_V1, r, g, b, a, light);

        this.quad(poseStack, consumer, -h, -h, HANDLE_Z0, h, -h, HANDLE_Z0, h, h, HANDLE_Z0, -h, h, HANDLE_Z0,
                HANDLE_U0, HANDLE_U1, POMMEL_V0, POMMEL_V1, r, g, b, a, light);
    }

    /** 输出一个四顶点 quad（四角坐标 + UV 矩形）。 */
    private void quad(PoseStack poseStack, VertexConsumer consumer,
                      float x0, float y0, float z0, float x1, float y1, float z1,
                      float x2, float y2, float z2, float x3, float y3, float z3,
                      float u0, float u1, float v0, float v1,
                      int r, int g, int b, int a, int light) {
        PoseStack.Pose pose = poseStack.last();
        this.vertex(consumer, pose, x0, y0, z0, u0, v0, r, g, b, a, light);
        this.vertex(consumer, pose, x1, y1, z1, u1, v0, r, g, b, a, light);
        this.vertex(consumer, pose, x2, y2, z2, u1, v1, r, g, b, a, light);
        this.vertex(consumer, pose, x3, y3, z3, u0, v1, r, g, b, a, light);
    }
}
