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
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
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
    private static final int INFO_Y_START = 60;
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
    /** 信息区滚动状态与可交互行命中矩形（绝对屏幕坐标 x,y,w,h,actionId）。 */
    private int infoScroll;
    private int infoContentHeight;
    private int lastMouseX = -1;
    private int lastMouseY = -1;
    private final List<int[]> interactiveRowHits = new ArrayList<>();
    private final List<TipHit> tipRowHits = new ArrayList<>();

    /** 悬浮明细行命中区（绝对坐标）+ 行数据。 */
    private record TipHit(int x, int y, int w, int h, InfoLine line) {
    }

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
        // renderLabels 处于面板相对 pose 下拿不到鼠标，先缓存绝对坐标供行悬停/命中比对
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        renderInfoTips(graphics, mouseX, mouseY);
    }

    /** 信息行悬浮明细：链接行首行自动带对象名；行为按 tipKey 模板内 \n 分行，不自动换行。 */
    private void renderInfoTips(GuiGraphics graphics, int mouseX, int mouseY) {
        for (TipHit hit : tipRowHits) {
            if (mouseX >= hit.x() && mouseX < hit.x() + hit.w()
                    && mouseY >= hit.y() && mouseY < hit.y() + hit.h()) {
                List<net.minecraft.util.FormattedCharSequence> lines = new ArrayList<>();
                if (hit.line().controlKind() == InfoLine.CONTROL_LINK) {
                    lines.addAll(font.split(Component.translatable(hit.line().textKey()),
                            Integer.MAX_VALUE));
                }
                // 传极大宽度：split 只在模板内的 \n 断行，不做按宽换行
                lines.addAll(font.split(Component.translatable(hit.line().tipKey(),
                        (Object[]) hit.line().tipArgs()), Integer.MAX_VALUE));
                graphics.renderTooltip(font, lines, mouseX, mouseY);
                return;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int[] hit : interactiveRowHits) {
                if (mouseX >= hit[0] && mouseX < hit[0] + hit[2]
                        && mouseY >= hit[1] && mouseY < hit[1] + hit[3]) {
                    send(RitualCoreMenu.BUTTON_ACTION_BASE + hit[4]);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int viewHeight = INFO_MAX_Y - INFO_Y_START;
        boolean overInfo = mouseX >= leftPos + INFO_X && mouseX < leftPos + PANEL_WIDTH - 4
                && mouseY >= topPos + INFO_Y_START && mouseY < topPos + INFO_MAX_Y;
        if (overInfo && infoContentHeight > viewHeight) {
            infoScroll = Mth.clamp((int) (infoScroll - scrollY * 18D),
                    0, infoContentHeight - viewHeight);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
        // 零缓存核心（共鸣塔）不显示"灵力 0/0"行，概要数据由行为信息行替代
        if (info.capacity() > 0) {
            graphics.drawString(font, Component.translatable("gui.gensokyou.ritual.sp",
                    info.stored(), info.capacity()), INFO_X, 26, COLOR_TEXT, false);
        }
        // 电池槽标注（槽本体由菜单协议渲染）
        graphics.drawString(font,
                Component.translatable("gui.gensokyou.ritual.spirit_core_slot"),
                RitualCoreMenu.BATTERY_SLOT_X + 20, RitualCoreMenu.BATTERY_SLOT_Y + 4,
                COLOR_TEXT, false);
        // —— 信息区：行为产出的 InfoLine 逐行渲染（电池槽行下方起笔，避免与槽标注叠行） ——
        renderInfoLines(graphics, font, info.infoLines());
        // 一次性状态消息（启停反馈等）：信息区底部
        if (!info.statusKey().isEmpty()) {
            graphics.drawString(font, Component.translatable(info.statusKey()),
                    INFO_X, INFO_HEIGHT - 12, COLOR_TEXT, false);
        }
    }

    /** InfoLine 渲染：滚动窗口 + 裁剪；图标(18px) + 文本/三态行 + 进度条 + ✓✗。 */
    private void renderInfoLines(GuiGraphics graphics, Font font, List<InfoLine> lines) {
        int viewHeight = INFO_MAX_Y - INFO_Y_START;
        int total = 0;
        for (InfoLine line : lines) {
            total += line.iconItemId().isEmpty() ? 11 : 18;
        }
        this.infoContentHeight = total;
        infoScroll = Mth.clamp(infoScroll, 0, Math.max(0, total - viewHeight));
        interactiveRowHits.clear();
        tipRowHits.clear();
        graphics.enableScissor(leftPos + INFO_X - 4, topPos + INFO_Y_START - 2,
                leftPos + PANEL_WIDTH - 4, topPos + INFO_MAX_Y + 2);
        int y = INFO_Y_START - infoScroll;
        for (InfoLine line : lines) {
            int rowH = line.iconItemId().isEmpty() ? 11 : 18;
            renderInfoLine(graphics, font, line, y, rowH);
            boolean visible = y + rowH > INFO_Y_START && y < INFO_MAX_Y;
            if (line.interactive() && visible) {
                interactiveRowHits.add(new int[]{leftPos + INFO_X - 4,
                        topPos + Math.max(y, INFO_Y_START), PANEL_WIDTH - 8,
                        Math.min(y + rowH, INFO_MAX_Y) - Math.max(y, INFO_Y_START),
                        line.actionId()});
            }
            if (line.tipped() && visible) {
                tipRowHits.add(new TipHit(leftPos + INFO_X - 4, topPos + Math.max(y, INFO_Y_START),
                        PANEL_WIDTH - 8,
                        Math.min(y + rowH, INFO_MAX_Y) - Math.max(y, INFO_Y_START), line));
            }
            y += rowH;
        }
        graphics.disableScissor();
    }

    /** 单行绘制：三态链接行 = 状态标记 + 名称 + 字面尾巴；悬停交互行淡高亮。 */
    private void renderInfoLine(GuiGraphics graphics, Font font, InfoLine line, int y, int rowH) {
        int textY = y + (line.iconItemId().isEmpty() ? 0 : 4);
        int color = line.color() != 0 ? line.color() : COLOR_TEXT;
        int textX = INFO_X;
        if (line.interactive()) {
            int mx = lastMouseX - leftPos;
            int my = lastMouseY - topPos;
            if (mx >= INFO_X - 4 && mx < PANEL_WIDTH - 4 && my >= y && my < y + rowH) {
                graphics.fill(INFO_X - 4, y, PANEL_WIDTH - 4, y + rowH, 0x22FFFFFF);
            }
        }
        if (!line.iconItemId().isEmpty()) {
            ItemStack icon = ClientRitualState.stackFor(line.iconItemId());
            if (!icon.isEmpty()) {
                graphics.renderItem(icon, INFO_X, y);
                textX = INFO_X + 20;
            }
        }
        MutableComponent text;
        if (line.controlKind() == InfoLine.CONTROL_LINK) {
            // 可见行 = 状态标记 + 名称；坐标/上限等明细走悬浮 tip
            String stateKey = switch (line.linkState()) {
                case InfoLine.LINK_IN -> "gui.gensokyou.ritual.link_in";
                case InfoLine.LINK_OUT -> "gui.gensokyou.ritual.link_out";
                default -> "gui.gensokyou.ritual.link_none";
            };
            text = Component.translatable(stateKey)
                    .append(Component.translatable(line.textKey()));
        } else if (!line.textKey().isEmpty()) {
            text = Component.translatable(line.textKey(), (Object[]) line.textArgs());
        } else {
            text = null;
        }
        if (text != null) {
            int maxW = (line.state() != null ? STATE_X - 10 : PANEL_WIDTH - 6) - textX;
            // 横向溢出保护：超宽行按面板可用宽裁断（首行），杜绝画到框外
            if (maxW > 0 && font.width(text) > maxW) {
                var wrapped = font.split(text, maxW);
                graphics.drawString(font, wrapped.get(0), textX, textY, color, false);
            } else {
                graphics.drawString(font, text, textX, textY, color, false);
            }
        }
        if (line.progress() >= 0F) {
            // 注：renderLabels 处于面板相对 pose，坐标 MUST NOT 再叠 leftPos/topPos
            int barX = textX + 52;
            int barW = 40;
            int barY = y + 5;
            graphics.fill(barX, barY, barX + barW, barY + 4, 0xFF202030);
            graphics.fill(barX, barY, barX + (int) (barW * Math.min(1F, line.progress())),
                    barY + 4, 0xFFE8912A);
        }
        if (line.state() != null) {
            String mark = line.state() ? "✓" : "✗";
            int markColor = line.state() ? COLOR_OK : COLOR_BAD;
            graphics.drawString(font, mark, STATE_X - font.width(mark), y + 4, markColor, true);
        }
    }
}
