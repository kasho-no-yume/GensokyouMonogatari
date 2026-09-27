package com.bitsson.gensokyou.client.renderer;

import com.bitsson.gensokyou.entity.AbstractDanmakuProjectile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * 弹幕渲染器基类。
 *
 * <p>提供颜色拆解、顶点输出、以及外发光层（放大的半透明自发光副本）的公共实现。
 */
public abstract class AbstractDanmakuRenderer<T extends AbstractDanmakuProjectile> extends EntityRenderer<T> {
    /** 自发光层使用满亮度，不受环境光影响。 */
    protected static final int FULL_BRIGHT = 0xF000F0;

    /** 外发光放大倍率。 */
    protected static final float GLOW_SCALE = 1.35F;

    /**
     * 外发光层沿本体所在平面的外法向的推离量（格）。
     *
     * <p><b>为什么必须要它</b>：外发光是本体的<b>等比放大</b>，而等比放大<b>不改变所在平面</b>——
     * 放大后仍然与本体共面。共面几何的逐顶点深度并非逐位相同（顶点位置不同 → 投影后差 1~2 ULP），
     * 经 24 位深度缓冲量化后随机跨格，输的那层被<b>整片</b>丢弃，该像素便少了一层加法贡献，
     * 表现为「中心和高光部分有间歇性黑色条纹」。
     *
     * <p><b>量级</b>：须远大于深度量化噪声（~1e-7 格），又远小于一个屏幕像素
     * （5 格距离、1080p 下 1px ≈ 0.0065 格）。0.004 格同时满足两侧。
     */
    protected static final float GLOW_OFFSET = 0.004F;

    /** 外发光透明度。 */
    protected static final int GLOW_ALPHA = 110;

    protected final ResourceLocation texture;

    protected AbstractDanmakuRenderer(EntityRendererProvider.Context context, ResourceLocation texture) {
        super(context);
        this.texture = texture;
    }

    /** 本体渲染用：双面、支持透明裁剪。 */
    protected RenderType baseRenderType() {
        return RenderType.entityCutoutNoCull(this.texture);
    }

    /**
     * 发光层渲染用：加法混合 + 自发光 + 写深度，双面。
     *
     * <p>加法混合使重叠弹幕的辉光亮度叠加，这是东方风格弹幕的关键表现；
     * 写深度让发光同样不被后画的半透明地形（水）与云层覆盖（发光轮廓在水面上
     * 按可见形状占位）。所用 shader 会 discard alpha&lt;0.1 的像素，故不会出现
     * 整块方形深度洞，只会按发光可见轮廓写深度。
     *
     * <p><b>注意</b>：本类型 {@code sortOnUpload} 为 true。共用本类型的多个层会进入
     * <b>同一批</b> {@code MeshData}，绘制前被 {@code MeshData#sortQuads} 按质心到世界原点
     * 的距离<b>重排</b>——提交顺序不成立。因此同批内 MUST 至多一层写深度，且该层 MUST 是同批
     * 中最远的一层；需要严格顺序时 MUST 改用不同的 RenderType（换类型会立即结算上一批）。
     */
    protected RenderType glowRenderType() {
        return DanmakuRenderTypes.additiveSolid(this.texture);
    }

    /**
     * 装饰层渲染用：加法混合 + 自发光 + <b>不写深度</b>，双面。
     *
     * <p>给「必须与其它层共存、又不该被别的层拒掉」的装饰层用（如球弹亮核）。
     * 不写深度的层永远不会被同批/异批的任何层拒掉，可见性因此不依赖提交顺序。
     */
    protected RenderType decorativeRenderType() {
        return DanmakuRenderTypes.additiveGlow(this.texture);
    }

    protected static int red(int color) {
        return (color >> 16) & 0xFF;
    }

    protected static int green(int color) {
        return (color >> 8) & 0xFF;
    }

    protected static int blue(int color) {
        return color & 0xFF;
    }

    /**
     * 输出一个顶点。法线固定朝上即可，弹幕用的都是自发光/裁剪材质，不依赖精确法线。
     */
    protected void vertex(VertexConsumer consumer, PoseStack.Pose pose,
                          float x, float y, float z,
                          float u, float v,
                          int r, int g, int b, int a,
                          int light) {
        consumer.addVertex(pose.pose(), x, y, z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    /**
     * 把当前姿态沿本体所在平面的<b>外法向</b>推离 {@link #GLOW_OFFSET} 格。
     *
     * <p>默认 +Z：billboard 弹幕（{@code cameraOrientation()} + Y 180°）下局部 +Z 朝相机，
     * 与 {@code SukimaPortalRenderer} 用 +Z 偏移把眼睑推到内景之前是同一条约定。
     * 非 billboard 的弹幕（灵符的纸面放平）覆写为 +Y。
     *
     * <p>在<b>父级</b>坐标下平移（先平移后 scale），使偏移量是绝对格数、
     * 不被 {@link #GLOW_SCALE} 缩放。
     */
    protected void offsetGlow(PoseStack poseStack) {
        poseStack.translate(0.0D, 0.0D, GLOW_OFFSET);
    }

    /**
     * 渲染外发光层：在当前变换基础上先沿外法向推离、再放大并以半透明自发光重绘一遍几何。
     * 调用方需保证 poseStack 已处于本体的局部坐标系。
     */
    protected void renderGlow(T entity, PoseStack poseStack, MultiBufferSource bufferSource) {
        if (!entity.hasGlowEffect()) {
            return;
        }

        poseStack.pushPose();
        this.offsetGlow(poseStack);
        poseStack.scale(GLOW_SCALE, GLOW_SCALE, GLOW_SCALE);

        VertexConsumer consumer = bufferSource.getBuffer(this.glowRenderType());
        int color = entity.getColor();
        this.renderShape(entity, poseStack, consumer,
                red(color), green(color), blue(color), GLOW_ALPHA, FULL_BRIGHT);

        poseStack.popPose();
    }

    /**
     * 子类实现具体几何。本体与发光层复用同一份实现，仅颜色/透明度/亮度不同。
     */
    protected abstract void renderShape(T entity, PoseStack poseStack, VertexConsumer consumer,
                                        int r, int g, int b, int a, int light);

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return this.texture;
    }
}
