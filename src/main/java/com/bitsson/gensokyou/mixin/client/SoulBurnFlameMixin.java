package com.bitsson.gensokyou.mixin.client;

import com.bitsson.gensokyou.client.FoxPossessionClient;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 把正被狐火附身生物的<b>原版着火特效</b>改成蓝焰：照搬原版
 * {@code EntityRenderDispatcher#renderFlame} 的叠层 billboard 几何与顶点格式，
 * 只把 {@code ModelBakery.FIRE_0/FIRE_1} 换成原版灵魂火 sprite
 * （{@code minecraft:block/soul_fire_0/1}）。这样生物自己 displaying fire，狐狸本体不参与。
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class SoulBurnFlameMixin {

    private static final ResourceLocation SOUL_FIRE_0 =
            ResourceLocation.fromNamespaceAndPath("minecraft", "block/soul_fire_0");
    private static final ResourceLocation SOUL_FIRE_1 =
            ResourceLocation.fromNamespaceAndPath("minecraft", "block/soul_fire_1");

    @Inject(method = "renderFlame", at = @At("HEAD"), cancellable = true)
    private void gensokyou$soulBurnFlame(PoseStack poseStack, MultiBufferSource buffer,
                                          Entity entity, Quaternionf quaternion, CallbackInfo ci) {
        if (!FoxPossessionClient.isSoulBurning(entity)) {
            return;
        }
        TextureAtlasSprite fire0 = sprite(SOUL_FIRE_0);
        TextureAtlasSprite fire1 = sprite(SOUL_FIRE_1);
        if (fire0 == null || fire1 == null) {
            return; // 让原版橙色火焰兜底
        }
        poseStack.pushPose();
        float f = entity.getBbWidth() * 1.4F;
        poseStack.scale(f, f, f);
        float half = 0.5F;
        float down = 0.0F;
        float remaining = entity.getBbHeight() / f;
        float z = 0.0F;
        poseStack.mulPose(quaternion);
        poseStack.translate(0.0F, 0.0F, 0.3F - (float) ((int) remaining) * 0.02F);
        float back = 0.0F;
        int i = 0;
        RenderType renderType = Sheets.cutoutBlockSheet();
        for (PoseStack.Pose pose = poseStack.last(); remaining > 0.0F; i++) {
            TextureAtlasSprite sprite = i % 2 == 0 ? fire0 : fire1;
            float u0 = sprite.getU0();
            float v0 = sprite.getV0();
            float u1 = sprite.getU1();
            float v1 = sprite.getV1();
            if (i / 2 % 2 == 0) {
                float swap = u1;
                u1 = u0;
                u0 = swap;
            }
            VertexConsumer vc = buffer.getBuffer(renderType);
            fireVertex(pose, vc, -half, -down, back, u1, v1);
            fireVertex(pose, vc, half, -down, back, u0, v1);
            fireVertex(pose, vc, half, 1.4F - down, back, u0, v0);
            fireVertex(pose, vc, -half, 1.4F - down, back, u1, v0);
            remaining -= 0.45F;
            down -= 0.45F;
            half *= 0.9F;
            back -= 0.03F;
        }
        poseStack.popPose();
        ci.cancel(); // 已用蓝焰替代原版橙色火焰
    }

    private static TextureAtlasSprite sprite(ResourceLocation id) {
        return Minecraft.getInstance().getModelManager()
                .getAtlas(TextureAtlas.LOCATION_BLOCKS).getSprite(id);
    }

    /** 与原版 {@code fireVertex} 完全一致的顶点输出（NEW_ENTITY 格式）。 */
    private static void fireVertex(PoseStack.Pose pose, VertexConsumer buffer,
                                   float x, float y, float z, float texU, float texV) {
        buffer.addVertex(pose, x, y, z)
                .setColor(-1)
                .setUv(texU, texV)
                .setUv1(0, 10)
                .setLight(240)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}
