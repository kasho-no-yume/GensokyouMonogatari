package com.bitsson.gensokyou.client.renderer;

import com.bitsson.gensokyou.danmaku.render.DanmakuRenderProbe;
import com.bitsson.gensokyou.danmaku.visual.DanmakuVisualProfile;
import com.bitsson.gensokyou.entity.AbstractDanmakuProjectile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * 弹幕渲染器基类。
 *
 * <p>提供颜色拆解、顶点输出、以及外发光层（放大的半透明自发光副本）的公共实现。
 */
public abstract class AbstractDanmakuRenderer<T extends AbstractDanmakuProjectile> extends EntityRenderer<T> {
    /** 自发光层使用满亮度，不受环境光影响。 */
    protected static final int FULL_BRIGHT = 0xF000F0;

    /**
     * 外发光放大倍率的<b>历史默认值</b>（1.35）。
     *
     * <p>保留仅为文档参照：实际值由 {@code DanmakuVisualProfile} 的 {@code glowScale}
     * 逐档案给出。改前它是本类的 {@code static final}，全模组唯一。
     */
    protected static final float LEGACY_GLOW_SCALE = 1.35F;

    /**
     * 外发光透明度的<b>历史默认值</b>（110）。同上，实际值由档案的 {@code glowAlpha} 给出。
     */
    protected static final int LEGACY_GLOW_ALPHA = 110;

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

    /**
     * 外发光透明度。实际值由档案的 {@code glowAlpha} 给出，见 {@link #LEGACY_GLOW_ALPHA}。
     *
     * @deprecated 保留仅为文档参照，勿用于渲染。
     */
    @Deprecated
    protected static final int GLOW_ALPHA = LEGACY_GLOW_ALPHA;

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
     * 渲染世界坐标偏移（格）。
     *
     * <p>原版 {@code EntityRenderDispatcher.render} 在调用 {@link #render} 之前把
     * {@code this.getRenderOffset(entity, partialTicks)} 加到已插值的世界位置上，
     * 画完再减回去。这正是「视觉位置与模拟位置分离」在原版里留好的唯一接缝——把纠偏
     * 写在各 renderer 的 {@code render()} 里做局部平移也可以，但那样每个子类都要记得
     * 调一次，漏掉一个就是「某一种弹照旧抖」。
     *
     * <p><b>与 {@code partialTick} 的关系（design 决策 3 / 任务 3.3）</b>：
     * <ul>
     *   <li>世界位置由原版按 {@code Mth.lerp(partialTick, xOld, x)} 插值——<b>一层</b>，
     *       插的是<b>模拟位置</b>；</li>
     *   <li>偏移量由本状态容器按 {@code lerp(previousOffset, offset)} 插值——也是
     *       <b>一层</b>，插的是<b>纠偏量</b>。</li>
     * </ul>
     * 两者作用于不同的量，不构成双重平滑。若把纠偏写进模拟位置，那才会得到两层
     * 作用在同一坐标上的平滑——那正是要避免的。
     */
    @Override
    public Vec3 getRenderOffset(T entity, float partialTicks) {
        return entity.renderOffset(partialTicks);
    }

    /**
     * 本帧的视觉位置（格）。供需要<b>绝对坐标</b>的几何使用——激光的方块裁剪起点
     * 就是这种：它要在世界里做一次 raycast，起点错一格，光束与实体中心就错位。
     *
     * <p>用 {@code xOld → x} 手工插值而不是 {@code position().lerp(xOld, partialTick)}：
     * 后者会重新分配一个向量，而它在每帧每弹都会被调用。
     */
    protected Vec3 renderPosition(T entity, float partialTicks) {
        return new Vec3(
                Mth.lerp(partialTicks, entity.xOld, entity.getX()),
                Mth.lerp(partialTicks, entity.yOld, entity.getY()),
                Mth.lerp(partialTicks, entity.zOld, entity.getZ()))
                .add(entity.renderOffset(partialTicks));
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
                // 不再走 Pose 重载：那会每顶点 new 一个 Vector3f。
                // 弹幕全是 emissive 材质 + 满亮度光照，法线是常量（0,1,0），用常量就行。
                .setNormal(0.0F, 1.0F, 0.0F);
        DanmakuRenderProbe.countVertex();
    }

    /** getBuffer 计数包装，优于直接调用 {@code bufferSource.getBuffer}。 */
    protected VertexConsumer getBuffer(MultiBufferSource bufferSource, RenderType type) {
        DanmakuRenderProbe.countBuffer();
        return bufferSource.getBuffer(type);
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
     *
     * <p>放大倍率与 alpha 由<b>视觉档案</b>给出，MUST NOT 回到本类的常量——改前
     * {@code GLOW_SCALE} / {@code GLOW_ALPHA} 是全模组唯一的 {@code static final}，
     * 致使「按弹调整发光半径」在架构上不可能。
     *
     * <p>隐藏态由 {@code dim < 1.0} 表达。本层是<b>加法混合</b>，故 MUST
     * <b>同时压暗 alpha 与 RGB</b>——只降 alpha 几乎看不出变化：
     * <pre>
     *   加法贡献 = rgb × alpha
     *   辉光是软渐变的【外圈】，其贴图自身 alpha 在峰值外缘已掉到 ~0.2，
     *   故层 alpha 110 → 55 换算到实际贡献只是 22 → 11（差 11/255，肉眼不可见）。
     *   同时把 RGB 也按 dim 压暗，贡献按 dim² 下降，辉光才真的暗下去。
     * </pre>
     * 实机反馈证实了这一点：先只压暗 alpha 时，辉光那一圈「完全没有变暗的效果」。
     *
     * <p>渲染类型 MUST 不变：辉光若不写深度，墙内每一层都参与叠加，反而更亮。
     * 理由见 {@code SphereDanmakuRenderer#baseRenderType}。
     *
     * @param dim 暗态系数（1.0 = 常态）
     */
    protected void renderGlow(T entity, PoseStack poseStack, DanmakuVisualProfile.Profile profile,
                              int color, float dim, MultiBufferSource bufferSource) {
        if (!entity.hasGlowEffect()) {
            return;
        }
        if (!DanmakuRenderProbe.effectiveGlow()) {
            return;
        }
        DanmakuRenderProbe.countGlow();
        int alpha = (int) Math.round(profile.glowAlpha() * dim);
        int dimmed = dimColor(color, dim);

        poseStack.pushPose();
        this.offsetGlow(poseStack);
        float scale = profile.glowScale();
        poseStack.scale(scale, scale, scale);

        VertexConsumer consumer = this.getBuffer(bufferSource, this.glowRenderType());
        DanmakuRenderProbe.pushGlow();
        try {
            this.renderShape(entity, poseStack, consumer,
                    red(dimmed), green(dimmed), blue(dimmed), alpha, FULL_BRIGHT);
        } finally {
            DanmakuRenderProbe.pop();
        }

        poseStack.popPose();
    }

    /**
     * 按 {@code dim} 压暗一个 0xRRGGBB 颜色。
     *
     * <p>供<b>加法混合</b>层使用：加法的贡献是 {@code rgb × alpha}，故要让加法层
     * 真的暗下去，MUST 同时压 alpha 与 rgb。alpha 混合层（本体）用不到这个方法——
     * 对它而言降低 alpha 就等于降低亮度。
     */
    protected static int dimColor(int color, float dim) {
        if (dim >= 1.0F) {
            return color;
        }
        int r = (int) Math.round(red(color) * dim);
        int g = (int) Math.round(green(color) * dim);
        int b = (int) Math.round(blue(color) * dim);
        return (r << 16) | (g << 8) | b;
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
