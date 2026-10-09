package com.bitsson.gensokyou.client.renderer;

import com.bitsson.gensokyou.entity.SpiritBombEntity;
import com.bitsson.gensokyou.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

/**
 * 灵力引爆器的客户端渲染：把它画成 {@code gensokyou:spirit_bomb} 方块。
 *
 * <p><b>形态选择</b>：引爆器本体是实体（{@code SpiritBombEntity} + GUI + 自定义爆炸，
 * 见 design.md D9），不是方块实体。但"实体"只描述逻辑；外观完全可以用我方块的模型来表达。
 * 这里渲染 {@link ModBlocks#SPIRIT_BOMB} 的方块状态——一枚刻着金色符文、顶带引信的绯红火种。
 *
 * <p>早先版本拿原版 {@code Blocks.TNT} 顶替，玩家看到的是错误的红白 TNT 贴图；现在换成了
 * mod 自己的模型。更进一步的多圈旋转符文环 / 随倒计时脉动的内芯属于立绘级特效，后续由
 * 美术侧补（gen_tex.py 的 16x16 ASCII 路线做不了，需要 Blockbench 或图像生成）。
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
                ModBlocks.SPIRIT_BOMB.get().defaultBlockState(),
                poseStack, buffer, packedLight,
                LevelRenderer.getLightColor(Minecraft.getInstance().level, entity.blockPosition()));
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(SpiritBombEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
