package com.bitsson.gensokyou.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

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

    /**
     * 闪电弧折线顶点：等长分段 + 垂直双向随机抖动，抖动幅度沿正弦包络、<b>两端归零</b>。
     *
     * <p>供<b>两处</b>共用：万象共鸣塔的持续放电弧（{@code RitualCoreRenderer#renderBolts}）
     * 与结界崩解的径向光柱（{@code SukimaPortalRenderer}）。抽到这里而不是各写一份，
     * 是因为"抖动折线"这个形态本身已经实机验证过，复制一份等于复制一份待回归的代码。
     *
     * <p>返回 {@code [x,y,z]*(segments+1)}，供 {@link #emitAlignedBeam} 逐段消费。
     * 结果完全由 {@code seed} 决定：同一 seed 恒给出同一条折线，所以"重掷形状"可以安全地
     * 按节奏切换而不产生抖动。
     *
     * @param segLen      目标段长（格）；实际段数为 {@code clamp(dist/segLen, 4, maxSegments)}
     * @param jitter      垂直抖动幅度（格）
     * @param maxSegments 段数上限（防长距离通道段数无界增长）
     */
    public static float[] buildBoltPoints(float ax, float ay, float az, float bx, float by, float bz,
                                           float segLen, float jitter, int maxSegments, long seed) {
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int segments = Mth.clamp((int) (dist / Math.max(0.1F, segLen)), 4, maxSegments);
        // 垂直正交基：u 取水平垂向，v = dir×u，均需归一（v 长度含 |d| 因子，勿遗漏）
        float ux, uy, uz;
        if (Math.sqrt(dx * dx + dz * dz) > 1.0E-4F) {
            ux = -dz;
            uy = 0.0F;
            uz = dx;
        } else {
            ux = 1.0F;
            uy = 0.0F;
            uz = 0.0F;
        }
        float ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux /= ul;
        uy /= ul;
        uz /= ul;
        float vx = dy * uz - dz * uy;
        float vy = dz * ux - dx * uz;
        float vz = dx * uy - dy * ux;
        float vl = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (vl < 1.0E-4F) {
            vx = 0.0F;
            vy = 1.0F;
            vz = 0.0F;
        } else {
            vx /= vl;
            vy /= vl;
            vz /= vl;
        }
        RandomSource random = RandomSource.create(seed);
        float[] pts = new float[(segments + 1) * 3];
        for (int i = 0; i <= segments; i++) {
            float t = i / (float) segments;
            float amp = i == 0 || i == segments ? 0.0F
                    : jitter * Mth.sin(t * (float) Math.PI);
            float o1 = random.nextFloat() * 2.0F - 1.0F;
            float o2 = random.nextFloat() * 2.0F - 1.0F;
            pts[i * 3] = Mth.lerp(t, ax, bx) + (ux * o1 + vx * o2) * amp;
            pts[i * 3 + 1] = Mth.lerp(t, ay, by) + (uy * o1 + vy * o2) * amp;
            pts[i * 3 + 2] = Mth.lerp(t, az, bz) + (uz * o1 + vz * o2) * amp;
        }
        return pts;
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
     * 端点十字光斑（落雷点、激光终点等）：三轴三张全幅贴图面片交叉，径向渐变贴图下任意角度呈光晕。
     *
     * <p><b>⚠️ 只适用于"单个点的高亮"。</b>因为它发的是<b>轴对齐</b>方片（不是 billboard），
     * 所以：① 掠射角会被透视压成扁椭圆/薄片；② 拿它堆 N 份凑体积时，每份都自带
     * <b>完整</b>的径向渐变，于是 N 份叠起来是"一堆各自成形的球"而<b>不是</b>一个球
     * （加法叠加只会让 N 个球心一起变亮，永不合并）；③ 拿它排成水平环时，XZ 面片被压扁、
     * XY/YZ 面片立成板，整圈读作"被拉长的椭圆"。
     *
     * <p>要画<b>体积</b>（光球、灵气场）或要每团都是<b>正圆</b>（烟、雾），
     * 用 {@link #emitBillboard}——同心分层或 camera-facing 精灵。
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
     * **正对摄像机**的单张面片（billboard）。
     *
     * <p>与 {@link #emitCrossGlow} 的根本区别：那张发的是<b>三张轴对齐方片</b>（XY/XZ/YZ），
     * 靠"任意角度都有正对分量"来冒充体积。代价是<b>它只在正对时才是圆的</b>——
     * 掠射角会被透视压成扁椭圆/薄片。这是上一版结界崩解被读成
     * 「一堆小球」和「一圈被拉长的椭圆」的唯一原因：蓄能球是 40 个斐波那球点各带 3 张
     * 轴对齐方片，屏幕上是 120 个各自带完整径向渐变的亮心；烟环在无旋转的世界系里发同样的
     * 轴对齐方片，平视时 XZ 面片被压扁、XY/YZ 面片立成板。
     *
     * <p>本方法按传入的摄像机旋转把面片摆正，于是<b>无论从哪个角度看每一片都是正圆</b>，
     * 且多片叠加能积成平滑的径向衰减（这正是"读作一个球/一团烟"的前提）。
     *
     * <p>刻意<b>不</b>在本类里取摄像机：保持"不依赖摄像机向量"的既有约定，
     * 旋转由调用方传入（渲染器手里有 {@code dispatcher.camera}）。
     *
     * @param halfW 半宽（沿摄像机右向量）；传 {@code halfH == halfW} 即正圆
     * @param halfH 半高（沿摄像机上向量）
     */
    public static void emitBillboard(VertexConsumer c, PoseStack.Pose pose, Quaternionfc camRot,
                                     float cx, float cy, float cz,
                                     float halfW, float halfH,
                                     int r, int g, int b, int a) {
        if (halfW <= 0F || halfH <= 0F || a <= 0) {
            return;
        }
        // 摄像机右/上向量：把局部 X、Y 轴转到世界，即得到面片所在平面的两条轴。
        // ⚠️ 调用方的局部系 MUST 只有平移、没有旋转（否则这里要把 pose 的旋转逆掉）。
        //    结界崩解的演出段正好满足：传入姿态仅平移，故世界方向即可直接当局部方向用。
        Vector3f right = new Vector3f(1.0F, 0.0F, 0.0F);
        Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F);
        camRot.transform(right);
        camRot.transform(up);
        float rx = right.x() * halfW, ry = right.y() * halfW, rz = right.z() * halfW;
        float ux = up.x() * halfH, uy = up.y() * halfH, uz = up.z() * halfH;
        vertex(c, pose, cx - rx - ux, cy - ry - uy, cz - rz - uz, 0.0F, 1.0F, r, g, b, a);
        vertex(c, pose, cx + rx - ux, cy + ry - uy, cz + rz - uz, 1.0F, 1.0F, r, g, b, a);
        vertex(c, pose, cx + rx + ux, cy + ry + uy, cz + rz + uz, 1.0F, 0.0F, r, g, b, a);
        vertex(c, pose, cx - rx + ux, cy - ry + uy, cz - rz + uz, 0.0F, 0.0F, r, g, b, a);
    }

    /**
     * 贴地辉光：单张水平面全幅贴图（加法），用于"地面被炙烤"的地面光斑。
     * 与 {@link #emitCrossGlow}（三轴交叉、呈体积光晕）不同，此面片只铺一个水平面，
     * 配合贴地火舌给出火床地面感。调用方负责 translate 到光斑中心。
     */
    public static void emitGroundGlow(PoseStack pose, VertexConsumer c,
                                      float cx, float cy, float cz, float half,
                                      int r, int g, int b, int a) {
        if (half <= 0F || a <= 0) {
            return;
        }
        pose.pushPose();
        pose.translate(cx, cy, cz);
        pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
        PoseStack.Pose p = pose.last();
        vertex(c, p, -half, -half, 0.0F, 0.0F, 1.0F, r, g, b, a);
        vertex(c, p, half, -half, 0.0F, 1.0F, 1.0F, r, g, b, a);
        vertex(c, p, half, half, 0.0F, 1.0F, 0.0F, r, g, b, a);
        vertex(c, p, -half, half, 0.0F, 0.0F, 0.0F, r, g, b, a);
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
