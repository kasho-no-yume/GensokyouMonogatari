package com.bitsson.gensokyou.client.screen;

import com.bitsson.gensokyou.network.DialogActionPayload;
import com.bitsson.gensokyou.network.DialogClosePayload;
import com.bitsson.gensokyou.network.DialogSyncPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * 对话界面（全 mod 首个非菜单纯 Screen）：深色面板 + 长文本自动换行分页 + 底部选项按钮。
 * 选项点击仅回报服务端（C2S），界面推进/关闭由服务端 S2C 同步驱动（含关闭哨兵包）。
 * 绘制顺序手动控制（背景→面板→控件）：1.21.1 的 Screen.render 会先重绘背景，
 * 若在其后叠加面板会被二次背景盖糊，故不调 super.render。
 */
public class DialogScreen extends Screen {

    private static final int PANEL_W = 300;
    private static final int PANEL_H = 200;
    private static final int TEXT_W = PANEL_W - 28;
    private static final int TEXT_TOP = 22;
    private static final int COLOR_PANEL = 0xF01A1024;
    private static final int COLOR_BORDER_TOP = 0xFF4A3663;
    private static final int COLOR_BORDER_BOT = 0xFF0B0710;
    private static final int COLOR_TEXT = 0xFFE8DCF5;
    private static final int COLOR_TITLE = 0xFFC9A0F0;

    private final int entityId;
    private Component text;
    private List<DialogSyncPayload.Option> options = List.of();
    private List<FormattedCharSequence> lines = List.of();
    private int page;
    private int linesPerPage = 6;
    private int pages = 1;

    public DialogScreen(DialogSyncPayload payload) {
        super(Component.translatable("gui.gensokyou.dialog.title"));
        this.entityId = payload.entityId();
        this.text = payload.text();
        this.options = payload.options();
    }

    /** 服务端推进：原地刷新内容（不重建 Screen）。 */
    public void setContent(DialogSyncPayload payload) {
        this.text = payload.text();
        this.options = payload.options();
        this.page = 0;
        rebuildWidgets();
    }

    @Override
    protected void init() {
        super.init();
        this.lines = this.font.split(this.text, TEXT_W);
        int left = width / 2 - PANEL_W / 2;
        int top = height / 2 - PANEL_H / 2;
        int buttonsTop = top + PANEL_H - 8 - options.size() * 24;
        int lineH = this.font.lineHeight + 4;
        this.linesPerPage = Math.max(1, (buttonsTop - 10 - (top + TEXT_TOP)) / lineH);
        this.pages = Math.max(1, (lines.size() + linesPerPage - 1) / linesPerPage);
        this.page = Math.min(page, pages - 1);
        if (pages > 1) {
            addRenderableWidget(Button.builder(Component.literal("◀"), b -> {
                page = Math.max(0, page - 1);
                rebuildWidgets();
            }).bounds(left + PANEL_W - 54, top + 4, 20, 16).build());
            addRenderableWidget(Button.builder(Component.literal("▶"), b -> {
                page = Math.min(pages - 1, page + 1);
                rebuildWidgets();
            }).bounds(left + PANEL_W - 32, top + 4, 20, 16).build());
        }
        int y = buttonsTop;
        for (int i = 0; i < options.size(); i++) {
            final int index = i;
            addRenderableWidget(Button.builder(options.get(i).label(), b -> choose(index))
                    .bounds(width / 2 - 110, y, 220, 20).build());
            y += 24;
        }
    }

    private void choose(int index) {
        PacketDistributor.sendToServer(new DialogActionPayload(entityId, index));
    }

    @Override
    public void removed() {
        PacketDistributor.sendToServer(new DialogClosePayload(entityId));
        super.removed();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        int left = width / 2 - PANEL_W / 2;
        int top = height / 2 - PANEL_H / 2;
        graphics.fill(left, top, left + PANEL_W, top + PANEL_H, COLOR_PANEL);
        graphics.fill(left, top, left + PANEL_W, top + 2, COLOR_BORDER_TOP);
        graphics.fill(left, top + PANEL_H - 2, left + PANEL_W, top + PANEL_H, COLOR_BORDER_BOT);
        graphics.fill(left, top, left + 2, top + PANEL_H, COLOR_BORDER_TOP);
        graphics.fill(left + PANEL_W - 2, top, left + PANEL_W, top + PANEL_H, COLOR_BORDER_BOT);
        graphics.drawCenteredString(this.font, this.title, width / 2, top + 8, COLOR_TITLE);
        int y = top + TEXT_TOP;
        int start = page * linesPerPage;
        for (int i = start; i < Math.min(start + linesPerPage, lines.size()); i++) {
            graphics.drawString(this.font, lines.get(i), left + 14, y, COLOR_TEXT, false);
            y += this.font.lineHeight + 4;
        }
        if (pages > 1) {
            graphics.drawString(this.font,
                    Component.translatable("gui.gensokyou.dialog.page", page + 1, pages),
                    left + PANEL_W - 54, top + 22, 0xFF8A7AA0, false);
        }
        for (Renderable renderable : this.renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
