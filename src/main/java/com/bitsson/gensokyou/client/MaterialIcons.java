package com.bitsson.gensokyou.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * 材料行的方块图标解析。
 * 有物品形态的方块走 {@link GuiGraphics#renderItem}（与原行为一致）；
 * 无物品形态的方块（盆栽等，`asItem() == Items.AIR`）改画方块自身模型——
 * 否则会退化成一片空白，外加把方块名显示成"空气"。
 * <p>GUI 投影自带 Y 翻转，故此处按 ItemRenderer 的做法：居中模型后以 (16,-16,16) 缩放，
 * 两次翻转相消保证朝向与面剔除都正确。
 */
public final class MaterialIcons {

    private MaterialIcons() {
    }

    /** 在 (x, y) 的 16×16 格位画该方块的图标。 */
    public static void render(GuiGraphics graphics, Block block, int x, int y) {
        if (block.asItem() != Items.AIR) {
            graphics.renderItem(new ItemStack(block), x, y);
            return;
        }
        BlockState state = block.defaultBlockState();
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x + 8.0F, y + 8.0F, 100.0F);
        pose.scale(16.0F, -16.0F, 16.0F);
        pose.translate(-0.5F, -0.5F, -0.5F);
        dispatcher.renderSingleBlock(state, pose, graphics.bufferSource(),
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
        pose.popPose();
    }
}
