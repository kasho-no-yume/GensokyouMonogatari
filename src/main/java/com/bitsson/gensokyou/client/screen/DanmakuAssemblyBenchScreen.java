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
        // 标题 y=8：面板顶部由贴图绘制，y=2..5 是贴图的外框实色带，y=4 会把标题压在边框上
        // （与仪式核心界面同一处理，见 RitualCoreScreen#renderLabels）。
        graphics.drawString(this.font, this.title, 8, 8, 0xFF404040, false);
        // 槽位标注：**统一一行 y=60**（此前武器标注 y=42、三核标注 y=55，两行错开看着歪），
        // 且各自**水平居中于自己的槽**。
        drawSlotLabel(graphics, Component.translatable("gui.gensokyou.assembly.weapon"),
                DanmakuAssemblyBenchMenu.WEAPON_SLOT_X);
        // 三核槽标注（弹核在球心，等级核在球左，增幅核在球右）
        drawSlotLabel(graphics, Component.translatable("gui.gensokyou.weapon.slot_bullet"),
                DanmakuAssemblyBenchMenu.CORE_SLOT_XS[0]);
        drawSlotLabel(graphics, Component.translatable("gui.gensokyou.weapon.slot_level"),
                DanmakuAssemblyBenchMenu.CORE_SLOT_XS[1]);
        drawSlotLabel(graphics, Component.translatable("gui.gensokyou.weapon.slot_amp"),
                DanmakuAssemblyBenchMenu.CORE_SLOT_XS[2]);
    }

    /**
     * 槽位标注：统一 y=60；x 让文字在 18px 槽上水平居中。
     *
     * <p>此前标注以槽的<b>左边</b>为起点左对齐 —— 2 字标注（18px）看着居中，
     * 3 字标注（27px）就左偏 4.5px，同一行里三段标注参差不齐。
     *
     * <p>槽框画在槽坐标 −1 处，故框心 = 槽坐标 + 7.5；整数取 +8。
     */
    private void drawSlotLabel(GuiGraphics graphics, Component text, int slotX) {
        graphics.drawString(this.font, text, slotX + 8 - this.font.width(text) / 2, 60, 0xFF7A4A5E, false);
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
