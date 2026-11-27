package com.bitsson.gensokyou.client.renderer;

import com.bitsson.gensokyou.danmaku.render.DanmakuRenderProbe;
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
import net.minecraft.util.Mth;

/**
 * 球型弹幕渲染器。
 *
 * <p><b>四边形几何：恒为单次绘制</b>（单 quad / 单 pass / 单 draw call）。
 * 本体与外辉光由<b>同一张贴图</b>承载（见 {@link DanmakuBulletTextures}），
 * 不再渲染独立的外发光层与亮核层。
 *
 * <p><b>为什么</b>：三层结构（本体 / 外发光 1.35× / 亮核 0.55×）的成本是
 * <b>填率随层数线性增长</b>，不是 draw call 数量问题。历史实测结论是
 * "Double additive 未排序后单体负载仍不可控，只能靠减遍数"，
 * 且 "不做 GPU instancing：fragment/过绘的主因仍在"。
 * 于是旧路径把「本体」与「辉光」一起换成了"halo 烧进贴图"的单层——
 * 而那张贴图把球换成了一个<b>环</b>（实心核只有 43.75% 宽，核外是透明环缝 +
 * 三档硬边台阶）。本渲染器把球与光<b>一起</b>拿回来，且仍然只有一层。
 *
 * <p>星弹使用沿速度方向旋转的立体五角星模型；宽白芯、彩色外缘和实体侧壁由一遍本体绘制完成。
 *
 * <p><b>深度</b>：本体层用写深度的常规 alpha 混合（渐变贴图的柔边必须真混合才能表现，
 * cutout 会把抗锯齿边缘切成锯齿；写深度则保证后画的半透明地形（水）与云层
 * 被本体正确遮挡）。外辉光与本体在同一张贴图里 ⇒ <b>一并写深度</b>，
 * 被弹挖掉的是<b>软圆轮廓</b>（alpha ≥ 0.1 的等值线），不是方块。
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
     *
     * <p><b>仅模型类几何用到</b>；四边形几何的辉光在贴图里，MUST NOT 走这三层。
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

    /**
     * 本体层的渲染类型。
     *
     * <ul>
     *   <li><b>四边形几何</b>：走<b>当前球弹贴图变体</b>（{@link DanmakuBulletTextures}），
     *       并使用<b>线性过滤 + mipmap</b> 的渲染类型——软渐变在最近邻 + 无 mipmap 下
     *       会碎成方块并持续闪烁。</li>
     *   <li><b>五角星模型</b>：走档案自己的高清贴图与线性过滤，不适用球弹的径向贴图。</li>
     * </ul>
     */
    private RenderType bodyRenderType(DanmakuVisualProfile.Profile profile) {
        if (profile.geometry() == DanmakuGeometry.STAR_PRISM) {
            return DanmakuRenderTypes.translucentDepthFiltered(profile.texture());
        }
        if (profile.geometry() == DanmakuGeometry.QUAD) {
            return DanmakuRenderTypes.translucentDepthFiltered(DanmakuBulletTextures.currentLocation());
        }
        return this.baseRenderType();
    }

    @Override
    public void render(SphereDanmaku entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        DanmakuVisualProfile.Profile profile = entity.visualProfile();
        boolean hidden = entity.isHidden();
        // 档案的变色模式由年龄推导，零同步；隐藏态则整体压暗至档案的 hiddenAlpha。
        // MUST 用 age() 而非 tickCount：客户端丢掉又重新拿到该实体时 tickCount 归零，
        // 变色相位与翻滚相位会当场跳一下。
        int color = profile.colorMode().resolve(
                entity.getColor(), entity.age(), profile.colorCycleTicks());

        poseStack.pushPose();

        float size = entity.getSize() * profile.visualScale();
        poseStack.translate(0.0D, size * 0.5D, 0.0D);
        if (profile.geometry() == DanmakuGeometry.STAR_PRISM) {
            // Treat local +Z as the prism's forward axis. Its orientation follows the projectile,
            // never the camera; spin about its travel axis and rock slightly to expose the bevel.
            float yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
            float pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
            poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
            poseStack.mulPose(Axis.XP.rotationDegrees(-pitch));
            float age = entity.age() + partialTick;
            poseStack.mulPose(Axis.ZP.rotationDegrees(age * 3.0F));
            poseStack.mulPose(Axis.XP.rotationDegrees(tumblePitch(age, profile)));
        } else {
            poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        }

        // 一次性闪亮：在闪亮年龄前后把整颗弹<b>等比放大</b>一帧。本体 / 外发光 / 亮核三层
        // 都从这个 poseStack 派生，故一次 scale 三层一起放大——读作「提亮一帧」。
        //
        // <p><b>MUST 用 {@code poseStack.scale}，MUST NOT 只把上面的局部 {@code size}
        // 乘大。</b>{@code renderShape} 读的是 {@code entity.getSize()}（不是局部 size），
        // 只改 size 只会改变 translate 的落点：现象是「弹抖了一下、尺寸没变」。
        float flash = entity.flashPulse();
        if (flash > 0.0F) {
            float flashScale = 1.0F + 0.8F * flash;
            poseStack.scale(flashScale, flashScale, flashScale);
        }

        // 弹幕自发光体，本体不吃环境光（与激光/发光层一致，暗处保持可读）
        // 隐藏态只改 alpha 与暗态系数，渲染类型一律不变——理由见 baseRenderType() 的注释。
        //
        // 本体层是 alpha 混合：对它而言降低 alpha 就等于降低亮度，故 RGB 保持原色
        // （暗态仍须可辨认出颜色，否则「隐藏」会读作「换成另一种弹」）。
        float dim = hidden ? (float)com.bitsson.gensokyou.danmaku.visual.DanmakuHiddenStyle.bodyAlpha(profile) : 1.0F;
        int bodyAlpha = hidden ? profile.hiddenAlpha() : 255;
        // 后窗收束淡出：进入收束年龄段后整体压暗，与相位隐藏叠加（乘性）。
        float collapseFade = entity.collapseFadeFactor();
        if (collapseFade < 1.0F) {
            dim *= collapseFade;
            bodyAlpha = (int) Math.round(bodyAlpha * collapseFade);
        }
        // 近相机淡出：武器弹丸生成在眼睛 = 相机位置，第一帧就贴在镜头上；单发玉的
        // 弹体已加到 0.8 格，在 1 格距离上占屏幕近 1/4。**只改渲染**，判定与轨迹
        // 不动 ⇒ 对准度零影响。第三人称相机在身后数格，天然不触发。
        float nearFade = this.nearCameraFade(entity, partialTick);
        dim *= nearFade;
        bodyAlpha = (int) Math.round(bodyAlpha * nearFade);
        if (DanmakuRenderProbe.effectiveBody() && bodyAlpha > 0) {
            DanmakuRenderProbe.countBody();
            VertexConsumer consumer = this.getBuffer(bufferSource, this.bodyRenderType(profile));
            DanmakuRenderProbe.pushBody();
            try {
                // 闪亮同时「提亮」：本体 RGB 向白推一档（flash=0 时逐位等于原色）。
                float boost = 1.0F + 0.7F * flash;
                this.renderShape(entity, profile, poseStack, consumer,
                        brighten(red(color), boost), brighten(green(color), boost),
                        brighten(blue(color), boost), bodyAlpha, FULL_BRIGHT);
            } finally {
                DanmakuRenderProbe.pop();
            }
        }

        // 外发光（默认 1.35×，实体色，加法）沿局部 +Z 推离，与本体不共面。
        // 加法层：alpha 与 RGB 同时压暗，贡献按 dim² 下降。
        //
        // **四边形几何 MUST NOT 画这一层**：它的辉光已经在贴图里。再叠一层加法辉光
        // 等于把填率翻倍（历史实测：双加法未排序后单体负载仍不可控，只能靠减遍数）。
        // 模型类几何可使用档案分层；星柱把高光烘焙在模型贴图中以保持单通绘制。
        if (profile.geometry() != DanmakuGeometry.QUAD
                && profile.geometry() != DanmakuGeometry.STAR_PRISM) {
            this.renderGlow(entity, poseStack, profile, color, dim, bufferSource);
        }

        // 亮核（默认 0.55×，纯白，加法）。**必须用不写深度的 decorativeRenderType**：
        // ① 与外发光分属不同 RenderType → getBuffer 换类型即结算上一批，两层不再同批，
        //    避开 additiveSolid 的 sortOnUpload 批内重排（重排会让靠后那层被整片拒掉）；
        // ② 不写深度的层永远不会被别的层拒掉，可见性不依赖提交顺序；
        // ③ 亮核严格包含在外发光之内（同形状），其深度写入本就被完全覆盖。
        //
        // 隐藏态 MUST 一并压暗，且与辉光同理 MUST 同时压暗 RGB：该层是纯白加法片，
        // 是三层里视觉最强的一层。曾只压暗 alpha，结果隐藏态【比常态更亮】。
        //
        // **四边形几何同辉光层：MUST NOT 画**（辉光与本体都在贴图里）。
        if (profile.geometry() != DanmakuGeometry.QUAD
                && profile.hasCore() && DanmakuRenderProbe.effectiveCore()) {
            DanmakuRenderProbe.countCore();
            poseStack.pushPose();
            poseStack.translate(0.0D, 0.0D, CORE_OFFSET);
            poseStack.scale(profile.coreScale(), profile.coreScale(), profile.coreScale());
            VertexConsumer core = this.getBuffer(bufferSource, this.decorativeRenderType());
            DanmakuRenderProbe.pushCore();
            try {
                int coreValue = (int) Math.round(255 * dim);
                this.renderShape(entity, profile, poseStack, core,
                        coreValue, coreValue, coreValue,
                        (int) Math.round(profile.coreAlpha() * dim), FULL_BRIGHT);
            } finally {
                DanmakuRenderProbe.pop();
            }
            poseStack.popPose();
        }

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    /**
     * 俯仰摆动角（度）。在飞行姿态上小幅摇摆，显露模型厚度与倒角。
     *
     * <p>纯客户端表现，由<b>年龄</b>推导，故零同步。
     *
     * <p>调用方传入 {@code entity.age() + partialTick}，保证帧间平滑且重新追踪实体后相位连续。
     */
    private static float tumblePitch(float age, DanmakuVisualProfile.Profile profile) {
        if (!profile.hasTumble()) {
            return 0.0F;
        }
        return (float) (profile.tumbleAmpDeg() * Math.sin(
                2.0D * Math.PI * age / profile.tumblePeriod()));
    }

    /** 把单个颜色分量按 {@code factor} 向白推（截到 255）。{@code factor <= 1} 时原样返回。 */
    private static int brighten(int channel, float factor) {
        if (factor <= 1.0F) {
            return channel;
        }
        return Math.min(255, Math.round(channel * factor));
    }

    @Override
    protected void renderShape(SphereDanmaku entity, PoseStack poseStack, VertexConsumer consumer,
                                int r, int g, int b, int a, int light) {
        this.renderShape(entity, entity.visualProfile(), poseStack, consumer, r, g, b, a, light);
    }

    /**
     * 按档案的几何输出形状。本体层与亮核层复用同一实现，仅颜色/透明度不同。
     */
    private void renderShape(SphereDanmaku entity, DanmakuVisualProfile.Profile profile,
                             PoseStack poseStack, VertexConsumer consumer,
                             int r, int g, int b, int a, int light) {
        float half = entity.getSize() * 0.5F * profile.visualScale();
        switch (profile.geometry()) {
            case STAR_PRISM -> DanmakuMeshes.star(poseStack.last(), consumer, half, r, g, b, a, light);
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

}
