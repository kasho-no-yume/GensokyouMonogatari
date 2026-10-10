package com.bitsson.gensokyou.client.renderer;

import com.bitsson.gensokyou.client.GensokyouTextures;
import com.bitsson.gensokyou.entity.spell.FlowerGardenEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * 花符『疗愈花园』表现（add-player-spellcards 5.3）：贴地粉色法阵 + 数格高、波动起伏的粉色光墙。
 * 全程客户端几何（零粒子包），相位由 {@code tickCount + partialTick} 本地推导。
 */
public class FlowerGardenRenderer extends EntityRenderer<FlowerGardenEntity> {

    private static final float WALL_HEIGHT = 3.0F;
    private static final int SEGMENTS = 56;
    /** 光墙 U 方向纹理重复次数（绕圈多少段火纹）。 */
    private static final float WALL_U_REPEAT = 6.0F;
    private static final float SCROLL_PER_TICK = 0.35F;

    private static final int CIRCLE_R = 255, CIRCLE_G = 150, CIRCLE_B = 200;
    private static final int WALL_R = 255, WALL_G = 130, WALL_B = 190;

    public FlowerGardenRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(FlowerGardenEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        float radius = entity.radius();
        if (radius <= 0.05F) {
            return;
        }
        float time = entity.tickCount + partialTick;
        renderGroundCircle(poseStack, bufferSource, radius, time);
        renderLightWall(poseStack, bufferSource, radius, time);
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public net.minecraft.resources.ResourceLocation getTextureLocation(FlowerGardenEntity entity) {
        return GensokyouTextures.MAGIC_CIRCLE;
    }

    /** 贴地法阵：缓慢自转的粉色环纹。 */
    private void renderGroundCircle(PoseStack poseStack, MultiBufferSource buffers, float radius, float time) {
        VertexConsumer c = buffers.getBuffer(DanmakuRenderTypes.additiveGlow(GensokyouTextures.MAGIC_CIRCLE));
        poseStack.pushPose();
        poseStack.translate(0D, 0.06D, 0D);
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(time * 0.6F));
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90F));
        float h = radius * 1.35F;
        FxGeometry.vertex(c, poseStack.last(), -h, -h, 0F, 0F, 0F, CIRCLE_R, CIRCLE_G, CIRCLE_B, 200);
        FxGeometry.vertex(c, poseStack.last(), h, -h, 0F, 1F, 0F, CIRCLE_R, CIRCLE_G, CIRCLE_B, 200);
        FxGeometry.vertex(c, poseStack.last(), h, h, 0F, 1F, 1F, CIRCLE_R, CIRCLE_G, CIRCLE_B, 200);
        FxGeometry.vertex(c, poseStack.last(), -h, h, 0F, 0F, 1F, CIRCLE_R, CIRCLE_G, CIRCLE_B, 200);
        poseStack.popPose();
    }

    /** 数格高粉色光墙：圆柱面，逐段 alpha 随时间波动（读作波动的光墙）。 */
    private void renderLightWall(PoseStack poseStack, MultiBufferSource buffers, float radius, float time) {
        VertexConsumer c = buffers.getBuffer(DanmakuRenderTypes.additiveGlow(GensokyouTextures.AURA_FLAME));
        double step = Math.PI * 2D / SEGMENTS;
        float scroll = time * SCROLL_PER_TICK;
        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = i * step;
            double a1 = (i + 1) * step;
            float x0 = (float) (Math.cos(a0) * radius);
            float z0 = (float) (Math.sin(a0) * radius);
            float x1 = (float) (Math.cos(a1) * radius);
            float z1 = (float) (Math.sin(a1) * radius);
            // 波动：沿圆周的行波 + 时间漂移；底部亮、顶部淡
            float wave = 0.55F + 0.45F * Mth.sin((float) (a0 * 4D + time * 0.18D));
            int botA = (int) (150F * wave);
            int topA = (int) (30F * wave);
            float u0 = i / (float) SEGMENTS * WALL_U_REPEAT;
            float u1 = (i + 1) / (float) SEGMENTS * WALL_U_REPEAT;
            float v0 = scroll;
            float v1 = scroll + WALL_HEIGHT * 0.5F;
            PoseStack.Pose p = poseStack.last();
            FxGeometry.vertex(c, p, x0, 0F, z0, u0, v1, WALL_R, WALL_G, WALL_B, botA);
            FxGeometry.vertex(c, p, x1, 0F, z1, u1, v1, WALL_R, WALL_G, WALL_B, botA);
            FxGeometry.vertex(c, p, x1, WALL_HEIGHT, z1, u1, v0, WALL_R, WALL_G, WALL_B, topA);
            FxGeometry.vertex(c, p, x0, WALL_HEIGHT, z0, u0, v0, WALL_R, WALL_G, WALL_B, topA);
        }
    }
}
