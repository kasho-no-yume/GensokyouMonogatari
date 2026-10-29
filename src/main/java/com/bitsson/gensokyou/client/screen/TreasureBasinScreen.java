package com.bitsson.gensokyou.client.screen;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.menu.TreasureBasinMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * 聚宝盆界面：3×3 盆口 + 玩家背包。
 *
 * <p>底图 {@code textures/gui/treasure_basin.png}（176×166）：铜边框 + 米色纸面，
 * 3×3 凹槽锁死在 (62,17) 起、间距 18，与 {@link TreasureBasinMenu} 的槽坐标一一对应。
 */
public class TreasureBasinScreen extends AbstractContainerScreen<TreasureBasinMenu> {

    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_HEIGHT = 166;
    private static final ResourceLocation BACKGROUND =
            Gensokyou.id("textures/gui/treasure_basin.png");

    public TreasureBasinScreen(TreasureBasinMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = PANEL_WIDTH;
        this.imageHeight = PANEL_HEIGHT;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, PANEL_WIDTH, PANEL_HEIGHT,
                PANEL_WIDTH, PANEL_HEIGHT);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // 标题压在面板顶部留白里（底图 y=0..2 是铜边框，y=6 不会压到线）
        graphics.drawString(this.font, this.title, 8, this.titleLabelY, 0xFF4A3320, false);
        graphics.drawString(this.font, this.playerInventoryTitle, 8, this.inventoryLabelY,
                0xFF6B5334, false);
    }
}
