package com.bitsson.gensokyou.client.renderer;

import com.bitsson.gensokyou.entity.spell.FoxServantEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;

/**
 * 式神『狐の従者』本体：环绕/冲锋时是一团蓝色 soul fire 火舌。
 *
 * <p><b>附身态本体不渲染</b>——被附身生物自己显示蓝火（走原版着火渲染路径，
 * 见 {@code SoulBurnFlameMixin} + {@code FoxPossessionClient}），
 * 式神本体留在宿主身边待命，不挪到目标坐标、也不在这里画目标的火。
 */
public class FoxServantRenderer extends EntityRenderer<FoxServantEntity> {

    private static final ResourceLocation SOUL_FIRE_0 =
            ResourceLocation.fromNamespaceAndPath("minecraft", "block/soul_fire_0");
    private static final ResourceLocation SOUL_FIRE_1 =
            ResourceLocation.fromNamespaceAndPath("minecraft", "block/soul_fire_1");

    private static final float SELF_WIDTH = 0.85F;
    private static final float SELF_HEIGHT = 1.45F;
    private static final int SELF_LAYERS = 3;

    public FoxServantRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(FoxServantEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        if (entity.state() == FoxServantEntity.STATE_POSSESSING) {
            // 火已经在目标生物身上绘制，本体不显形
            return;
        }
        TextureAtlasSprite fire0 = sprite(SOUL_FIRE_0);
        TextureAtlasSprite fire1 = sprite(SOUL_FIRE_1);
        if (fire0 == null || fire1 == null) {
            super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
            return;
        }
        VertexConsumer c = bufferSource.getBuffer(DanmakuRenderTypes.additiveGlow(TextureAtlas.LOCATION_BLOCKS));
        Quaternionf camRot = entityRenderDispatcher.cameraOrientation();
        poseStack.pushPose();
        poseStack.translate(0D, -0.2D, 0D);
        emitSoulFire(c, poseStack, camRot, fire0, fire1, SELF_WIDTH, SELF_HEIGHT, SELF_LAYERS);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    /** 取原版图集里的灵魂火动画 sprite（UV 每帧由原版图集自动更新）。 */
    private static TextureAtlasSprite sprite(ResourceLocation id) {
        return Minecraft.getInstance().getModelManager()
                .getAtlas(TextureAtlas.LOCATION_BLOCKS).getSprite(id);
    }

    /** 面向相机的叠层火舌，交替使用两张灵魂火 sprite；顶点色用白（贴图本身是蓝色）。 */
    private static void emitSoulFire(VertexConsumer c, PoseStack pose, Quaternionf camRot,
                                     TextureAtlasSprite fire0, TextureAtlasSprite fire1,
                                     float width, float height, int layers) {
        pose.pushPose();
        pose.mulPose(camRot);
        PoseStack.Pose p = pose.last();
        float half = width * 0.5F;
        float y = 0F;
        float z = 0F;
        for (int i = 0; i < layers; i++) {
            TextureAtlasSprite s = i % 2 == 0 ? fire0 : fire1;
            float u0 = s.getU0();
            float u1 = s.getU1();
            float v0 = s.getV0();
            float v1 = s.getV1();
            if (i / 2 % 2 == 0) {
                float swap = u0;
                u0 = u1;
                u1 = swap;
            }
            float layerH = height / layers * 1.3F;
            FxGeometry.vertex(c, p, -half, y, z, u1, v1, 255, 255, 255, 220);
            FxGeometry.vertex(c, p, half, y, z, u0, v1, 255, 255, 255, 220);
            FxGeometry.vertex(c, p, half, y + layerH, z, u0, v0, 255, 255, 255, 220);
            FxGeometry.vertex(c, p, -half, y + layerH, z, u1, v0, 255, 255, 255, 220);
            y += layerH * 0.62F;
            half *= 0.84F;
            z -= 0.02F;
        }
        pose.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(FoxServantEntity entity) {
        return SOUL_FIRE_0;
    }
}
