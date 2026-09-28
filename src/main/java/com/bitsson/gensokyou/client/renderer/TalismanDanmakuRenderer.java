package com.bitsson.gensokyou.client.renderer;

import com.bitsson.gensokyou.danmaku.visual.DanmakuVisualProfile;
import com.bitsson.gensokyou.entity.TalismanDanmaku;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * 灵符渲染器：扁平长方体纸片，带外发光。
 *
 * <p>纸面<b>放平</b>（水平面），长边沿飞行方向（镖式前指，符首朝敌）、
 * 短边横置——一短边朝向射手、对面短边朝向敌人。射手看到的是朝向自己的短边；
 * 纸面随弹道俯仰，但不随视角转动（非 billboard）。
 *
 * <p>UV 约定：u 沿短边横轴（局部 X），v 沿飞行轴（0=符首在前朝敌，1=尾端朝射手）。
 */
public class TalismanDanmakuRenderer extends AbstractDanmakuRenderer<TalismanDanmaku> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(com.bitsson.gensokyou.Gensokyou.MODID, "textures/entity/talisman_danmaku.png");

    /** 短边（沿飞行轴）。 */
    private static final float WIDTH = 0.34F;
    /** 长边（竖直）。 */
    private static final float HEIGHT = 0.52F;

    public TalismanDanmakuRenderer(EntityRendererProvider.Context context) {
        super(context, TEXTURE);
    }

    /**
     * 高清符纸贴图带抗锯齿柔边，cutout 会切成锯齿，改用写深度的常规 alpha 混合：
     * 柔边由 alpha 混合表现，写深度保证后画的水/云被本体正确遮挡。几何、朝向与外发光结构不变。
     */
    @Override
    protected RenderType baseRenderType() {
        return DanmakuRenderTypes.translucentDepth(this.texture);
    }

    @Override
    public void render(TalismanDanmaku entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();

        // 与飞刀一致：局部 +Z 对准飞行方向（yaw 正值 + pitch 取负）
        float yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        float pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());

        poseStack.translate(0.0D, 0.05D, 0.0D);
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(-pitch));

        int color = entity.getColor();
        VertexConsumer consumer = bufferSource.getBuffer(this.baseRenderType());
        this.renderShape(entity, poseStack, consumer,
                red(color), green(color), blue(color), 255, FULL_BRIGHT);

        // 灵符不参与视觉档案体系（它是纸片几何，与球弹的档案语义不同），
        // 故此处传默认档案，只为取其发光层的 1.35× / alpha 110——与改前逐位相同。
        this.renderGlow(entity, poseStack, DanmakuVisualProfile.DEFAULT, color, 1.0F, bufferSource);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    /**
     * 本渲染器<b>不是 billboard</b>：纸面放平于局部 X-Z 水平面，外法向是局部 +Y 而非 +Z。
     * 外发光沿 +Y 推离 0.004 格，使它与纸面不共面。
     *
     * <p><b>已知接受的瑕疵</b>：纸面 {@code noCull} 双面可见，而偏移只有一个方向，
     * 故从<b>正下方</b>观察时外发光落在纸背之后、会被纸面写入的深度拒掉。外发光 alpha
     * 仅 110，实际不可见。若日后见到"灵符仰视时没有光晕"，答案在此，不是新 bug。
     */
    @Override
    protected void offsetGlow(PoseStack poseStack) {
        poseStack.translate(0.0D, GLOW_OFFSET, 0.0D);
    }

    @Override
    protected void renderShape(TalismanDanmaku entity, PoseStack poseStack, VertexConsumer consumer,
                                int r, int g, int b, int a, int light) {
        PoseStack.Pose pose = poseStack.last();
        float halfL = HEIGHT * 0.5F;   // 长边：沿飞行轴（局部 Z，符首朝敌）
        float halfS = WIDTH * 0.5F;    // 短边：横置（局部 X）

        // 纸面放平：位于局部 X-Z 水平面（y=0），noCull 双面可见
        // u 沿 X（短边横轴），v 沿 Z（0=符首在前朝敌，1=尾端朝射手）
        this.vertex(consumer, pose, -halfS, 0.0F, -halfL, 0.0F, 1.0F, r, g, b, a, light);
        this.vertex(consumer, pose, halfS, 0.0F, -halfL, 1.0F, 1.0F, r, g, b, a, light);
        this.vertex(consumer, pose, halfS, 0.0F, halfL, 1.0F, 0.0F, r, g, b, a, light);
        this.vertex(consumer, pose, -halfS, 0.0F, halfL, 0.0F, 0.0F, r, g, b, a, light);
    }
}
