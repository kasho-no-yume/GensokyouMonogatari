package com.bitsson.gensokyou.client.screen;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.item.weapon.WeaponSlotsHelper;
import com.bitsson.gensokyou.menu.WeaponCoreMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** 武器装入界面：三核槽 + 玩家物品栏；不可装入的核灰显（悬停提示见核物品 tooltip）。 */
public class WeaponCoreScreen extends AbstractContainerScreen<WeaponCoreMenu> {

    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_HEIGHT = 166;
    /** 背景贴图：替换 assets/gensokyou/textures/gui/weapon_core.png 即可整体换肤。 */
    private static final ResourceLocation BACKGROUND =
            Gensokyou.id("textures/gui/weapon_core.png");
    private static final int GRAY_FILL = 0x80808080;

    public WeaponCoreScreen(WeaponCoreMenu menu, Inventory inventory, Component title) {
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
        graphics.drawString(this.font, this.title, 8, 10, 0xFF404040, false);
    }

    /** 等级不足的核叠加半透明灰色遮罩（原版槽位渲染后、悬停物之前执行）。 */
    @Override
    protected void renderSlot(GuiGraphics graphics, Slot slot) {
        super.renderSlot(graphics, slot);
        ItemStack stack = slot.getItem();
        if (!stack.isEmpty() && !WeaponSlotsHelper.canFit(menu.weapon(), stack)) {
            graphics.fill(leftPos + slot.x, topPos + slot.y,
                    leftPos + slot.x + 16, topPos + slot.y + 16, GRAY_FILL);
        }
    }
}
