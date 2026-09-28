package com.bitsson.gensokyou.client.renderer;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.danmaku.visual.DanmakuColorMode;
import com.bitsson.gensokyou.danmaku.visual.DanmakuGeometry;
import com.bitsson.gensokyou.danmaku.visual.DanmakuVisualProfile;
import com.bitsson.gensokyou.entity.SphereDanmaku;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * 球型弹幕渲染器。
 *
 * <p>三层结构与改前完全相同，<b>但每一层的参数都改为由视觉档案给出</b>：
 * 核心缩放 / 核心 alpha / 发光倍率 / 发光 alpha 此前是本类与
 * {@code AbstractDanmakuRenderer} 里的 {@code static final}，全模组唯一，
 * 既不可按档案区分、也不可在运行期调整。
 *
 * <p>三层由远及近严格排序，且分属三个不同 {@code RenderType}：
 * <ol>
 *   <li><b>本体</b>——{@code translucentDepth}（alpha 混合 + 写深度）</li>
 *   <li><b>外发光</b>——{@code additiveSolid}（加法混合 + 写深度，1.35× 默认）</li>
 *   <li><b>亮核</b>——{@code decorativeRenderType()}（加法混合 + <b>不写深度</b>，0.55× 默认）</li>
 * </ol>
 *
 * <p>亮核 MUST 用不写深度的类型，原因见上两条链接处的注释（写深度的加法混合会进入
 * 同一批 MeshData 并被 {@code sortQuads} 按质心距离重排，靠后那层会被整片拒掉）。
 *
 * <p>隐藏态时本体层与发光层 MUST 一并切到不写深度的类型——只降 alpha 是不够的：
 * 写深度的半透明弹即使半透明也会把身后的弹挖出方洞，密集弹幕墙隐藏时呈现为黑方块。
 * 亮核层本就不写深度，无需切换。
 */
public class SphereDanmakuRenderer extends AbstractDanmakuRenderer<SphereDanmaku> {

    /**
     * 亮核相对本体的默认缩放。取自改前 {@code SphereDanmakuRenderer.CORE_SCALE}，
     * 现由档案逐份给出，此常量只作文档参照。
     */
    private static final float LEGACY_CORE_SCALE = 0.55F;

    /**
     * 亮核层沿 billboard 局部 +Z 的推离量（格）。
     *
     * <p>取 {@link #GLOW_OFFSET} 的两倍，使亮核与外发光也<b>不共面</b>（共面会重现
     * 中心/高光处的间歇性暗条）。外发光在外、本体居中、亮核最内，三层严格由远及近。
     */
    private static final float CORE_OFFSET = AbstractDanmakuRenderer.GLOW_OFFSET * 2.0F;

    public SphereDanmakuRenderer(EntityRendererProvider.Context context) {
        super(context, DanmakuVisualProfile.DEFAULT.texture());
    }

    /**
     * 本体层用写深度的常规 alpha 混合：渐变贴图的柔边（半透明衰减）必须真混合才能表现，
     * cutout 会把抗锯齿边缘切成锯齿；写深度则保证后画的半透明地形（水）与云层
     * 被本体正确遮挡（不再盖住位于其前方的弹幕）。
     *
     * <p><b>隐藏态 MUST 沿用同一类型，MUST NOT 换成不写深度的类型。</b>
     * 试过换，结论是错的：隐藏态的核心用途是<b>高密度弹幕墙</b>，而「不写深度」会让
     * 墙内<b>每一层都参与混合与加法叠加</b>——400 颗弹的 50% alpha 逐层累加、400 层辉光
     * 逐层相加，结果是<b>饱和的白</b>，比常态更亮。写深度则让深度缓冲剔除身后的层，
     * 叠加量被限制在一两层内，压暗才读得出来。
     */
    @Override
    protected RenderType baseRenderType() {
        return DanmakuRenderTypes.translucentDepth(this.texture);
    }

    @Override
    public void render(SphereDanmaku entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        DanmakuVisualProfile.Profile profile = entity.visualProfile();
        boolean hidden = entity.isHidden();
        // 档案的变色模式由 tickCount 推导，零同步；隐藏态则整体压暗至档案的 hiddenAlpha。
        int color = profile.colorMode().resolve(
                entity.getColor(), entity.tickCount, profile.colorCycleTicks());

        poseStack.pushPose();

        float size = entity.getSize() * profile.visualScale();
        poseStack.translate(0.0D, size * 0.5D, 0.0D);
        if (profile.geometry() == DanmakuGeometry.STAR_PRISM) {
            // 偏航锁相机方位（保证五角星轮廓恒可读），俯仰摆动（使厚度成为可见信息）。
            // 完全 billboard 会让厚度永远不可见，故刻意不复用 cameraOrientation()。
            poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            poseStack.mulPose(Axis.XP.rotationDegrees(
                    tumblePitch(entity.tickCount, profile)));
        } else {
            poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        }

        // 弹幕自发光体，本体不吃环境光（与激光/发光层一致，暗处保持可读）
        // 隐藏态只改 alpha 与暗态系数，渲染类型一律不变——理由见 baseRenderType() 的注释。
        //
        // 本体层是 alpha 混合：对它而言降低 alpha 就等于降低亮度，故 RGB 保持原色
        // （暗态仍须可辨认出颜色，否则「隐藏」会读作「换成另一种弹」）。
        float dim = hidden ? profile.hiddenAlpha() / 255.0F : 1.0F;
        int bodyAlpha = hidden ? profile.hiddenAlpha() : 255;
        VertexConsumer consumer = bufferSource.getBuffer(this.baseRenderType());
        this.renderShape(entity, profile, poseStack, consumer,
                red(color), green(color), blue(color), bodyAlpha, FULL_BRIGHT);

        // 外发光（默认 1.35×，实体色，加法）沿局部 +Z 推离，与本体不共面。
        // 加法层：alpha 与 RGB 同时压暗，贡献按 dim² 下降。
        this.renderGlow(entity, poseStack, profile, color, dim, bufferSource);

        // 亮核（默认 0.55×，纯白，加法）。**必须用不写深度的 decorativeRenderType**：
        // ① 与外发光分属不同 RenderType → getBuffer 换类型即结算上一批，两层不再同批，
        //    避开 additiveSolid 的 sortOnUpload 批内重排（重排会让靠后那层被整片拒掉）；
        // ② 不写深度的层永远不会被别的层拒掉，可见性不依赖提交顺序；
        // ③ 亮核严格包含在外发光之内（同形状），其深度写入本就被完全覆盖。
        //
        // 隐藏态 MUST 一并压暗，且与辉光同理 MUST 同时压暗 RGB：该层是纯白加法片，
        // 是三层里视觉最强的一层。曾只压暗 alpha，结果隐藏态【比常态更亮】。
        if (profile.hasCore()) {
            poseStack.pushPose();
            poseStack.translate(0.0D, 0.0D, CORE_OFFSET);
            poseStack.scale(profile.coreScale(), profile.coreScale(), profile.coreScale());
            VertexConsumer core = bufferSource.getBuffer(this.decorativeRenderType());
            int coreValue = (int) Math.round(255 * dim);
            this.renderShape(entity, profile, poseStack, core,
                    coreValue, coreValue, coreValue,
                    (int) Math.round(profile.coreAlpha() * dim), FULL_BRIGHT);
            poseStack.popPose();
        }

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    /**
     * 俯仰摆动角（度）。摆动式（硬币旋转）而非整周翻滚：转到 90° 时五角星投影
     * 退化成一条线，玩家会读作「弹消失」。
     *
     * <p>纯客户端表现，由 {@code tickCount} 推导，故零同步。
     */
    private static float tumblePitch(int tickCount, DanmakuVisualProfile.Profile profile) {
        if (!profile.hasTumble()) {
            return 0.0F;
        }
        return (float) (profile.tumbleAmpDeg() * Math.sin(
                2.0D * Math.PI * tickCount / profile.tumblePeriod()));
    }

    @Override
    protected void renderShape(SphereDanmaku entity, PoseStack poseStack, VertexConsumer consumer,
                                int r, int g, int b, int a, int light) {
        this.renderShape(entity, entity.visualProfile(), poseStack, consumer, r, g, b, a, light);
    }

    /**
     * 按档案的几何输出形状。本体层与亮核层复用同一实现，仅颜色/透明度不同。
     *
     * @param radius 几何外接半径
     */
    private void renderShape(SphereDanmaku entity, DanmakuVisualProfile.Profile profile,
                             PoseStack poseStack, VertexConsumer consumer,
                             int r, int g, int b, int a, int light) {
        float half = entity.getSize() * 0.5F;
        switch (profile.geometry()) {
            case STAR_PRISM -> this.renderStarPrism(profile, poseStack, consumer, r, g, b, a, light);
            default -> this.renderQuad(poseStack, consumer, half, r, g, b, a, light);
        }
    }

    /** 原始四边形：一个 side = getSize() 的面片。 */
    private void renderQuad(PoseStack poseStack, VertexConsumer consumer, float half,
                            int r, int g, int b, int a, int light) {
        PoseStack.Pose pose = poseStack.last();
        this.vertex(consumer, pose, -half, -half, 0.0F, 0.0F, 1.0F, r, g, b, a, light);
        this.vertex(consumer, pose, half, -half, 0.0F, 1.0F, 1.0F, r, g, b, a, light);
        this.vertex(consumer, pose, half, half, 0.0F, 1.0F, 0.0F, r, g, b, a, light);
        this.vertex(consumer, pose, -half, half, 0.0F, 0.0F, 0.0F, r, g, b, a, light);
    }

    /**
     * 五角星柱：前面盖 + 后面盖 + 侧面，共 30 quad = 120 顶点。
     *
     * <p>几何取自 {@link DanmakuGeometry#starPrismOutline}——纯函数，故可单测。
     * 前面盖从中心扇出三角化：五角星是非凸的，但<b>对中心可见</b>（星形多边形），
     * 故扇形三角化合法。
     *
     * <p>法线朝 +Z 与贴图一致；亮核层会等比缩放同一份几何，故两者天然同心。
     */
    private void renderStarPrism(DanmakuVisualProfile.Profile profile, PoseStack poseStack,
                                 VertexConsumer consumer, int r, int g, int b, int a, int light) {
        float radius = profile.visualScale();
        double halfThickness = (float) DanmakuGeometry.starPrismHalfThickness(radius);
        double[] outline = DanmakuGeometry.starPrismOutline(radius);
        PoseStack.Pose pose = poseStack.last();

        for (int i = 0; i < 10; i++) {
            double x0 = outline[i * 2];
            double y0 = outline[i * 2 + 1];
            int j = (i + 1) % 10;
            double x1 = outline[j * 2];
            double y1 = outline[j * 2 + 1];

            // 前面盖（z = +h）：中心 → v_i → v_{i+1}
            this.vertex(consumer, pose, 0.0F, 0.0F, (float) halfThickness, 0.5F, 0.5F, r, g, b, a, light);
            this.vertex(consumer, pose, (float) x0, (float) y0, (float) halfThickness, 0.0F, 0.0F, r, g, b, a, light);
            this.vertex(consumer, pose, (float) x1, (float) y1, (float) halfThickness, 1.0F, 0.0F, r, g, b, a, light);
            this.vertex(consumer, pose, (float) x1, (float) y1, (float) halfThickness, 1.0F, 0.0F, r, g, b, a, light);

            // 后面盖（z = −h）：绕序反向
            this.vertex(consumer, pose, 0.0F, 0.0F, (float) -halfThickness, 0.5F, 0.5F, r, g, b, a, light);
            this.vertex(consumer, pose, (float) x1, (float) y1, (float) -halfThickness, 1.0F, 0.0F, r, g, b, a, light);
            this.vertex(consumer, pose, (float) x0, (float) y0, (float) -halfThickness, 0.0F, 0.0F, r, g, b, a, light);
            this.vertex(consumer, pose, (float) x0, (float) y0, (float) -halfThickness, 0.0F, 0.0F, r, g, b, a, light);

            // 侧面：v_i 前 → v_{i+1} 前 → v_{i+1} 后 → v_i 后
            this.vertex(consumer, pose, (float) x0, (float) y0, (float) halfThickness, 0.0F, 1.0F, r, g, b, a, light);
            this.vertex(consumer, pose, (float) x1, (float) y1, (float) halfThickness, 1.0F, 1.0F, r, g, b, a, light);
            this.vertex(consumer, pose, (float) x1, (float) y1, (float) -halfThickness, 1.0F, 0.0F, r, g, b, a, light);
            this.vertex(consumer, pose, (float) x0, (float) y0, (float) -halfThickness, 0.0F, 0.0F, r, g, b, a, light);
        }
    }
}
