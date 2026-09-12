package com.bitsson.gensokyou.client.screen;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.client.ClientRitualState;
import com.bitsson.gensokyou.menu.RitualCoreMenu;
import com.bitsson.gensokyou.network.InfoLine;
import com.bitsson.gensokyou.network.RitualInfoPayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 仪式界面（176 宽，与玩家物品栏同宽）：固定头（名称/阶级/状态/灵力）
 * + 头排（电池槽 + 启停按钮）+ 行为自主信息区（InfoLine 逐行渲染）
 * + 底部玩家物品栏。数据来自服务端推送的 RitualInfoPayload（ClientRitualState 暂存）。
 */
public class RitualCoreScreen extends AbstractContainerScreen<RitualCoreMenu> {

    private static final int PANEL_WIDTH = 176;
    /** 信息区高度（含固定头），信息行渲染下缘以此为准。 */
    private static final int INFO_HEIGHT = 150;
    /** 背包区高度：主仓 3 行 + 快捷栏 1 行 + 间隔。 */
    private static final int INVENTORY_HEIGHT = 84;
    private static final int PANEL_HEIGHT = INFO_HEIGHT + INVENTORY_HEIGHT;
    private static final int COLOR_TEXT = 0xFF404040;
    private static final int COLOR_OK = 0xFF2E8B57;
    private static final int COLOR_BAD = 0xFFB22222;
    /** 背景贴图：替换 assets/gensokyou/textures/gui/ritual_core.png 即可整体换肤。 */
    private static final ResourceLocation BACKGROUND =
            Gensokyou.id("textures/gui/ritual_core.png");

    /** 信息行布局：图标行占 18px，纯文本行占 11px。 */
    private static final int INFO_X = 8;
    private static final int INFO_MAX_Y = INFO_HEIGHT - 22;
    private static final int STATE_X = 124;
    /** 启停按钮（头排右列）。 */
    private static final int TOGGLE_BUTTON_X = 120;
    private static final int TOGGLE_BUTTON_W = 50;

    @Nullable
    private Button startButton;
    @Nullable
    private Button stopButton;
    private final Button[] actionButtons = new Button[3];

    public RitualCoreScreen(RitualCoreMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = PANEL_WIDTH;
        this.imageHeight = PANEL_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        // 启停按钮：头排右列（电池槽右侧）
        startButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.gensokyou.ritual.start"), b -> send(0))
                .bounds(leftPos + TOGGLE_BUTTON_X, topPos + 40, TOGGLE_BUTTON_W, 20).build());
        stopButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.gensokyou.ritual.stop"), b -> send(1))
                .bounds(leftPos + TOGGLE_BUTTON_X, topPos + 62, TOGGLE_BUTTON_W, 20).build());
        // 首帧即隐藏，显隐唯一由 containerTick 按服务端 payload 收敛（防非 toggleable 仪式按钮闪现）
        startButton.visible = false;
        stopButton.visible = false;
        // 行为注入操作按钮：启停下方竖排
        for (int i = 0; i < actionButtons.length; i++) {
            final int index = i;
            actionButtons[i] = addRenderableWidget(Button.builder(Component.literal("?"),
                            b -> send(RitualCoreMenu.BUTTON_ACTION_BASE + index))
                    .bounds(leftPos + TOGGLE_BUTTON_X, topPos + 88 + i * 22, TOGGLE_BUTTON_W, 20).build());
            actionButtons[i].visible = false;
        }
    }

    private void send(int buttonId) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, buttonId);
        }
    }

    @Override
    public void containerTick() {
        super.containerTick();
        RitualInfoPayload info = ClientRitualState.latest();
        boolean ours = info != null && info.blockPos().equals(menu.pos());
        boolean showButtons = ours && info.toggleable();
        if (startButton != null) {
            startButton.visible = showButtons && !(ours && info.enabled());
        }
        if (stopButton != null) {
            stopButton.visible = showButtons && ours && info.enabled();
        }
        // 行为注入的自定义操作按钮：随 payload 动态显隐
        for (int i = 0; i < actionButtons.length; i++) {
            Button button = actionButtons[i];
            if (button == null) {
                continue;
            }
            RitualInfoPayload.Action action = ours && i < info.actions().size()
                    ? info.actions().get(i) : null;
            if (action != null) {
                button.setMessage(Component.translatable(action.labelKey()));
                button.visible = true;
            } else {
                button.visible = false;
            }
        }
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
        RitualInfoPayload info = ClientRitualState.latest();
        if (info == null || !info.blockPos().equals(menu.pos())) {
            return;
        }
        var font = this.font;
        if (info.patternId().isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.gensokyou.ritual.no_pattern"),
                    INFO_X, 4, COLOR_BAD, false);
            return;
        }
        // —— 固定头：名称 / 状态 / 阶级 / 灵力 ——
        ResourceLocation id = ResourceLocation.parse(info.patternId());
        graphics.drawString(font,
                Component.translatableWithFallback("jei." + id.getNamespace() + ".ritual." + id.getPath(),
                        id.getPath().replace('_', ' ')), INFO_X, 4, COLOR_TEXT, false);
        Component state = info.enabled()
                ? Component.translatable("gui.gensokyou.ritual.running")
                : Component.translatable("gui.gensokyou.ritual.idle");
        graphics.drawString(font, state, PANEL_WIDTH - 8 - font.width(state), 4,
                info.enabled() ? COLOR_OK : COLOR_BAD, false);
        graphics.drawString(font, Component.translatable("gui.gensokyou.ritual.tier", info.tier()),
                INFO_X, 15, COLOR_TEXT, false);
        graphics.drawString(font, Component.translatable("gui.gensokyou.ritual.sp",
                info.stored(), info.capacity()), INFO_X, 26, COLOR_TEXT, false);
        // 电池槽标注（槽本体由菜单协议渲染）
        graphics.drawString(font,
                Component.translatable("gui.gensokyou.ritual.spirit_core_slot"),
                RitualCoreMenu.BATTERY_SLOT_X + 20, RitualCoreMenu.BATTERY_SLOT_Y + 4,
                COLOR_TEXT, false);
        // —— 信息区：行为产出的 InfoLine 逐行渲染（电池槽行下方起笔，避免与槽标注叠行） ——
        renderInfoLines(graphics, font, info.infoLines(), 60);
        // 一次性状态消息（启停反馈等）：信息区底部
        if (!info.statusKey().isEmpty()) {
            graphics.drawString(font, Component.translatable(info.statusKey()),
                    INFO_X, INFO_HEIGHT - 12, COLOR_TEXT, false);
        }
    }

    /** InfoLine 渲染：图标(18px) + 文本 + 进度条 + ✓✗；y 到 INFO_MAX_Y 即止。 */
    private void renderInfoLines(GuiGraphics graphics, Font font, List<InfoLine> lines, int yStart) {
        int y = yStart;
        for (InfoLine line : lines) {
            if (y > INFO_MAX_Y - 11) {
                break;
            }
            ItemStack icon = line.iconItemId().isEmpty()
                    ? ItemStack.EMPTY : ClientRitualState.stackFor(line.iconItemId());
            int textX = INFO_X;
            if (!icon.isEmpty()) {
                graphics.renderItem(icon, INFO_X, y);
                textX = INFO_X + 20;
            }
            int color = line.color() != 0 ? line.color() : COLOR_TEXT;
            if (!line.textKey().isEmpty()) {
                Component text = Component.translatable(line.textKey(), (Object[]) line.textArgs());
                graphics.drawString(font, text, textX, y + (icon.isEmpty() ? 0 : 4), color, false);
            }
            if (line.progress() >= 0F) {
                int barX = textX + 52;
                int barW = 40;
                int barY = y + 5;
                graphics.fill(leftPos + barX, topPos + barY,
                        leftPos + barX + barW, topPos + barY + 4, 0xFF202030);
                graphics.fill(leftPos + barX, topPos + barY,
                        leftPos + barX + (int) (barW * Math.min(1F, line.progress())),
                        topPos + barY + 4, 0xFFE8912A);
            }
            if (line.state() != null) {
                String mark = line.state() ? "✓" : "✗";
                int markColor = line.state() ? COLOR_OK : COLOR_BAD;
                graphics.drawString(font, mark, STATE_X - font.width(mark), y + 4, markColor, true);
            }
            y += icon.isEmpty() ? 11 : 18;
        }
    }
}
