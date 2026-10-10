package com.bitsson.gensokyou.client.renderer;

import com.bitsson.gensokyou.client.GensokyouTextures;
import com.bitsson.gensokyou.entity.spell.WebFieldEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/**
 * 網符『蜘蛛の巣』表现（add-player-spellcards 5.3）：在减速立方体空间里绘制三张交叉的大蜘蛛网
 * （水平面 + 两张竖直面），把"一个空间被网住"读出来。灰白半透明。
 */
public class SpiderWebRenderer extends EntityRenderer<WebFieldEntity> {

    private static final int R = 210, G = 214, B = 220;

    public SpiderWebRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(WebFieldEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        float edge = entity.edge();
        if (edge <= 0.05F) {
            return;
        }
        float h = edge * 0.5F;
        int alpha = 150;
        VertexConsumer c = bufferSource.getBuffer(DanmakuRenderTypes.translucent(GensokyouTextures.SPIDER_WEB));

        plane(poseStack, c, null, 0F, h, R, G, B, alpha);      // XY
        plane(poseStack, c, Axis.XP, -90F, h, R, G, B, alpha); // XZ（水平）
        plane(poseStack, c, Axis.YP, 90F, h, R, G, B, alpha);  // YZ

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public net.minecraft.resources.ResourceLocation getTextureLocation(WebFieldEntity entity) {
        return GensokyouTextures.SPIDER_WEB;
    }

    private void plane(PoseStack poseStack, VertexConsumer c, Axis axis, float degrees, float h,
                       int r, int g, int b, int a) {
        poseStack.pushPose();
        if (axis != null) {
            poseStack.mulPose(axis.rotationDegrees(degrees));
        }
        PoseStack.Pose pose = poseStack.last();
        FxGeometry.vertex(c, pose, -h, -h, 0F, 0F, 1F, r, g, b, a);
        FxGeometry.vertex(c, pose, h, -h, 0F, 1F, 1F, r, g, b, a);
        FxGeometry.vertex(c, pose, h, h, 0F, 1F, 0F, r, g, b, a);
        FxGeometry.vertex(c, pose, -h, h, 0F, 0F, 0F, r, g, b, a);
        poseStack.popPose();
    }
}
