package com.bitsson.gensokyou.client.screen;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.menu.DanmakuAssemblyBenchMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 弹幕方术装配台界面：武器输入槽 + 三核槽 + 玩家物品栏。
 *
 * <p>背景底图 {@code textures/gui/danmaku_assembly_bench.png}（武器主题面板）；
 * 不可装入的核（武器缺失或武器等级不足）叠加半透明灰显。
 */
public class DanmakuAssemblyBenchScreen extends AbstractContainerScreen<DanmakuAssemblyBenchMenu> {

    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_HEIGHT = 166;
    private static final ResourceLocation BACKGROUND =
            Gensokyou.id("textures/gui/danmaku_assembly_bench.png");
    private static final int GRAY_FILL = 0x80808080;

    public DanmakuAssemblyBenchScreen(DanmakuAssemblyBenchMenu menu, Inventory inventory, Component title) {
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
        graphics.drawString(this.font, this.title, 8, 4, 0xFF404040, false);
        // 武器输入槽标注（槽下方）
        graphics.drawString(this.font, Component.translatable("gui.gensokyou.assembly.weapon"),
                DanmakuAssemblyBenchMenu.WEAPON_SLOT_X, 42, 0xFF6B4A24, false);
        // 三核槽槽位标注（嵌槽下方）
        graphics.drawString(this.font, Component.translatable("gui.gensokyou.weapon.slot_bullet"),
                DanmakuAssemblyBenchMenu.CORE_SLOT_XS[0], 54, 0xFF6B4A24, false);
        graphics.drawString(this.font, Component.translatable("gui.gensokyou.weapon.slot_level"),
                DanmakuAssemblyBenchMenu.CORE_SLOT_XS[1], 54, 0xFF6B4A24, false);
        graphics.drawString(this.font, Component.translatable("gui.gensokyou.weapon.slot_amp"),
                DanmakuAssemblyBenchMenu.CORE_SLOT_XS[2], 54, 0xFF6B4A24, false);
    }

    /** 不可装入的核叠加半透明灰色遮罩（原版槽位渲染后、悬停物之前执行）。 */
    @Override
    protected void renderSlot(GuiGraphics graphics, Slot slot) {
        super.renderSlot(graphics, slot);
        ItemStack stack = slot.getItem();
        if (stack.isEmpty() || !menu.isCoreSlot(slot)) {
            return;
        }
        if (!menu.coreStackAllowed(stack)) {
            graphics.fill(leftPos + slot.x, topPos + slot.y,
                    leftPos + slot.x + 16, topPos + slot.y + 16, GRAY_FILL);
        }
    }
}
