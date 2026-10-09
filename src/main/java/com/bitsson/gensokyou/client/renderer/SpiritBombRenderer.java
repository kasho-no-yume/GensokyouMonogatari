package com.bitsson.gensokyou.client.renderer;

import com.bitsson.gensokyou.entity.SpiritBombEntity;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * 灵力引爆器的客户端渲染：以物品形态渲染一个旋转的 TNT 纹理方块。
 *
 * <p><b>为什么用物品精灵图而不是自定义模型</b>：真正的视觉规格是多圈旋转符文环 +
 * 随倒计时加速的脉动内芯 + 颜色按强度渐变（见 design.md D9），那是 32x32/64x64 的
 * 立绘级资产，超出 16x16 ASCII 的表达力。这里先用 {@code Blocks.TNT} 的方块模型 +
 * 我们的物品贴图顶住，保证"看得见、认得出、不崩"；正式特效由美术侧补
 * （gen_tex.py 的 ASCII 路线做不了，需要 Blockbench 或图像生成）。
 */
public class SpiritBombRenderer extends EntityRenderer<SpiritBombEntity> {

    private final BlockRenderDispatcher blockRenderer;

    public SpiritBombRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.blockRenderer = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(SpiritBombEntity entity, float entityYaw, float partialTick,
                       com.mojang.blaze3d.vertex.PoseStack poseStack,
                       net.minecraft.client.renderer.MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        poseStack.translate(-0.5D, 0.0D, -0.5D);
        blockRenderer.renderSingleBlock(
                net.minecraft.world.level.block.Blocks.TNT.defaultBlockState(),
                poseStack, buffer, packedLight,
                net.minecraft.client.renderer.LevelRenderer.getLightColor(
                        net.minecraft.client.Minecraft.getInstance().level, entity.blockPosition()));
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(SpiritBombEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    /** 供调试读取当前用于占位的方块。 */
    public static ItemStack placeholderStack() {
        return new ItemStack(Blocks.TNT.asItem());
    }
}
