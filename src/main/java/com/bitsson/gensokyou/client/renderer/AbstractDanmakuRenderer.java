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
     * 发光层渲染用：加法混合 + 自发光，双面。
     * 加法混合使重叠弹幕的辉光亮度叠加，这是东方风格弹幕的关键表现。
     */
    protected RenderType glowRenderType() {
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
     * 渲染外发光层：在当前变换基础上放大并以半透明自发光重绘一遍几何。
     * 调用方需保证 poseStack 已处于本体的局部坐标系。
     */
    protected void renderGlow(T entity, PoseStack poseStack, MultiBufferSource bufferSource) {
        if (!entity.hasGlowEffect()) {
            return;
        }

        poseStack.pushPose();
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
