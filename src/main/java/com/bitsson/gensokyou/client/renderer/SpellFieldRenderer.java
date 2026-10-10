package com.bitsson.gensokyou.client.renderer;

import com.bitsson.gensokyou.client.GensokyouTextures;
import com.bitsson.gensokyou.entity.spell.AbstractSpellFieldEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * 玩家符卡范围场的可见边界（add-player-spellcards 5.3）：贴地半透明光盘，
 * 半径随实体 {@code radius()} 变化。给花圃/网域/黑暗一个可读的"圈"。
 */
public class SpellFieldRenderer<T extends AbstractSpellFieldEntity> extends EntityRenderer<T> {

    private final int argb;

    public SpellFieldRenderer(EntityRendererProvider.Context context, int argb) {
        super(context);
        this.argb = argb;
    }

    @Override
    public void render(T entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        float radius = entity.radius();
        if (radius <= 0.05F) {
            return;
        }
        int alpha = (argb >>> 24) & 0xFF;
        float a = alpha / 255F;
        float r = ((argb >> 16) & 0xFF) / 255F;
        float g = ((argb >> 8) & 0xFF) / 255F;
        float b = (argb & 0xFF) / 255F;

        poseStack.pushPose();
        poseStack.translate(0D, 0.06D, 0D);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90F));
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityTranslucent(getTextureLocation(entity)));
        vertex(consumer, pose, matrix, -radius, -radius, 0F, 0F, r, g, b, a, packedLight);
        vertex(consumer, pose, matrix, radius, -radius, 1F, 0F, r, g, b, a, packedLight);
        vertex(consumer, pose, matrix, radius, radius, 1F, 1F, r, g, b, a, packedLight);
        vertex(consumer, pose, matrix, -radius, radius, 0F, 1F, r, g, b, a, packedLight);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    private void vertex(VertexConsumer consumer, PoseStack.Pose pose, Matrix4f matrix,
                        float x, float y, float u, float v,
                        float r, float g, float b, float a, int light) {
        consumer.addVertex(matrix, x, y, 0F)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0F, 0F, 1F);
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return GensokyouTextures.SHATTER_GLOW;
    }
}
