package com.bitsson.gensokyou.client.renderer;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.entity.KnifeDanmaku;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * 飞刀渲染器：长端始终朝向速度方向，无外发光。
 */
public class KnifeDanmakuRenderer extends AbstractDanmakuRenderer<KnifeDanmaku> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Gensokyou.MODID, "textures/entity/knife_danmaku.png");

    private static final float LENGTH = 1.2F;
    private static final float WIDTH = 0.18F;

    public KnifeDanmakuRenderer(EntityRendererProvider.Context context) {
        super(context, TEXTURE);
    }

    @Override
    public void render(KnifeDanmaku entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();

        // 用插值朝向，避免低同步频率下的抖动
        // yaw 取正值：绕 Y+ 旋转 atan2(dx,dz) 才能让局部 Z+ 对准速度方向
        float yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        float pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());

        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(-pitch));

        int color = entity.getColor();
        VertexConsumer consumer = bufferSource.getBuffer(this.baseRenderType());
        this.renderShape(entity, poseStack, consumer,
                red(color), green(color), blue(color), 255, packedLight);

        // 飞刀不加发光层

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    protected void renderShape(KnifeDanmaku entity, PoseStack poseStack, VertexConsumer consumer,
                                int r, int g, int b, int a, int light) {
        PoseStack.Pose pose = poseStack.last();
        float halfLength = LENGTH * 0.5F;
        float halfWidth = WIDTH * 0.5F;

        // 水平面
        this.vertex(consumer, pose, -halfWidth, 0.0F, -halfLength, 0.0F, 0.0F, r, g, b, a, light);
        this.vertex(consumer, pose, halfWidth, 0.0F, -halfLength, 1.0F, 0.0F, r, g, b, a, light);
        this.vertex(consumer, pose, halfWidth, 0.0F, halfLength, 1.0F, 1.0F, r, g, b, a, light);
        this.vertex(consumer, pose, -halfWidth, 0.0F, halfLength, 0.0F, 1.0F, r, g, b, a, light);

        // 竖直面，构成十字截面，任意视角都能看到
        this.vertex(consumer, pose, 0.0F, -halfWidth, -halfLength, 0.0F, 0.0F, r, g, b, a, light);
        this.vertex(consumer, pose, 0.0F, halfWidth, -halfLength, 1.0F, 0.0F, r, g, b, a, light);
        this.vertex(consumer, pose, 0.0F, halfWidth, halfLength, 1.0F, 1.0F, r, g, b, a, light);
        this.vertex(consumer, pose, 0.0F, -halfWidth, halfLength, 0.0F, 1.0F, r, g, b, a, light);
    }
}
