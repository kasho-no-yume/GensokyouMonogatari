package com.bitsson.gensokyou.client.renderer;

import com.bitsson.gensokyou.client.GensokyouTextures;
import com.bitsson.gensokyou.entity.spell.FoxServantEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/**
 * 式神『狐の従者』表现（add-player-spellcards 5.2）：蓝紫色燃烧的狐火——
 * 用 {@code aura_flame} 灰度火纹交叉面片着蓝紫 tint（与水晶灵焰同技法）。
 * 附身态（{@link FoxServantEntity#STATE_POSSESSING}）本体不显，火焰转移到目标身上（见实体 clientTick）。
 */
public class FoxServantRenderer extends EntityRenderer<FoxServantEntity> {

    private static final int R = 150, G = 90, B = 235;
    private static final float HEIGHT = 1.35F;
    private static final float HALF_WIDTH = 0.38F;
    private static final int PLANES = 4;

    public FoxServantRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(FoxServantEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        if (entity.state() == FoxServantEntity.STATE_POSSESSING) {
            return;
        }
        float time = entity.tickCount + partialTick;
        VertexConsumer c = bufferSource.getBuffer(DanmakuRenderTypes.additiveGlow(GensokyouTextures.AURA_FLAME));
        poseStack.pushPose();
        poseStack.translate(0D, -0.15D, 0D);
        float v0 = time * 0.5F;
        float v1 = v0 + HEIGHT;
        FxGeometry.emitCrossPlanes(poseStack, c, PLANES, HALF_WIDTH, HEIGHT, v0, v1, R, G, B, 210, 0);
        // 内焰：更亮更小
        FxGeometry.emitCrossPlanes(poseStack, c, PLANES, HALF_WIDTH * 0.55F, HEIGHT * 0.7F,
                v0 * 1.3F, v0 * 1.3F + HEIGHT, 220, 190, 255, 180, 0);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public net.minecraft.resources.ResourceLocation getTextureLocation(FoxServantEntity entity) {
        return GensokyouTextures.AURA_FLAME;
    }
}
