package com.bitsson.gensokyou.client.screen;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.client.SakuraPalette;
import com.bitsson.gensokyou.client.widget.SakuraButton;
import com.bitsson.gensokyou.network.DialogActionPayload;
import com.bitsson.gensokyou.network.DialogClosePayload;
import com.bitsson.gensokyou.network.DialogSyncPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * 对话界面（全 mod 首个非菜单纯 Screen）：樱符和纸面板 + 长文本自动换行分页 + 底部选项按钮。
 * 选项点击仅回报服务端（C2S），界面推进/关闭由服务端 S2C 同步驱动（含关闭哨兵包）。
 * 绘制顺序手动控制（背景→面板→控件）：1.21.1 的 Screen.render 会先重绘背景，
 * 若在其后叠加面板会被二次背景盖糊，故不调 super.render。
 *
 * <h2>樱符化（v2）</h2>
 * <ul>
 *   <li>底图 = 烘焙的 300×200 和纸面板（{@code textures/gui/dialog.png}，1:1 逻辑像素），
 *       纸底 + 樱粉外框 + 左上樱花枝。
 *   <li>标题 = <b>对话对象的名字</b>（NPC 名），拿不到才退回通用的「对话」。
 *   <li>正文 / 标题走<b>墨色</b>（浅纸上的字，对比度 12）；翻页箭头与选项按钮走樱符皮肤。
 *   <li>页码提到<b>标题行</b>（◀ 箭头左侧）—— 原版把页码画在 {@code top+22}，
 *       与正文首行同一行，而正文一行最长 33 个汉字 ≈ 297px &gt; 272px 的正文宽
 *       → 页码直接压在句号上（本地目检抓到的实机 bug）。
 * </ul>
 */
public class DialogScreen extends Screen {

    private static final int PANEL_W = 300;
    private static final int PANEL_H = 200;
    private static final int TEXT_W = PANEL_W - 28;
    private static final int TEXT_TOP = 22;
    /** 标题行基线（与 {@code titleLabelY} 一致）。 */
    private static final int TITLE_Y = 8;
    /** 正文颜色：墨色（浅纸上的字）。 */
    private static final int COLOR_TEXT = SakuraPalette.INK;
    /** 标题颜色：樱深红。 */
    private static final int COLOR_TITLE = SakuraPalette.ROSE;
    /** 页码颜色：樱粉暗调。 */
    private static final int COLOR_PAGE = SakuraPalette.PINK_DARK;
    /** 翻页箭头尺寸（樱符九宫格按钮）。 */
    private static final int ARROW_W = 20;
    private static final int ARROW_H = 16;
    /** 选项按钮宽（居中）。 */
    private static final int OPTION_W = 220;
    private static final int OPTION_H = 20;
    private static final int OPTION_PITCH = 24;

    /**
     * 樱符面板底图（300×200，1:1 逻辑像素）。
     * 纸底 / 外框 / 樱花枝都烘焙在贴图里；文字与按钮是运行期绘制。
     */
    private static final ResourceLocation BACKGROUND =
            Gensokyou.id("textures/gui/dialog.png");

    private final int entityId;
    private Component text;
    private List<DialogSyncPayload.Option> options = List.of();
    private List<FormattedCharSequence> lines = List.of();
    private int page;
    private int linesPerPage = 6;
    private int pages = 1;
    private Button prevButton;
    private Button nextButton;

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

    /**
     * 抬头文字：优先用<b>对话对象的名字</b>（NPC 名），拿不到才退回通用的「对话」。
     *
     * <p>实体名由客户端本地查（对话包只带 entityId），不额外发包。
     */
    private Component header() {
        if (this.minecraft != null && this.minecraft.level != null) {
            Entity entity = this.minecraft.level.getEntity(entityId);
            if (entity != null) {
                return entity.getDisplayName();
            }
        }
        return this.title;
    }

    @Override
    protected void init() {
        super.init();
        this.lines = this.font.split(this.text, TEXT_W);
        int left = width / 2 - PANEL_W / 2;
        int top = height / 2 - PANEL_H / 2;
        int buttonsTop = top + PANEL_H - 8 - options.size() * OPTION_PITCH;
        int lineH = this.font.lineHeight + 4;
        this.linesPerPage = Math.max(1, (buttonsTop - 10 - (top + TEXT_TOP)) / lineH);
        this.pages = Math.max(1, (lines.size() + linesPerPage - 1) / linesPerPage);
        this.page = Math.min(page, pages - 1);

        // 翻页箭头（樱符皮肤）：标题行右侧
        this.prevButton = addRenderableWidget(SakuraButton.of(Component.literal("◀"),
                b -> {
                    page = Math.max(0, page - 1);
                    rebuildWidgets();
                }, left + PANEL_W - 54, top + 4, ARROW_W, ARROW_H));
        this.nextButton = addRenderableWidget(SakuraButton.of(Component.literal("▶"),
                b -> {
                    page = Math.min(pages - 1, page + 1);
                    rebuildWidgets();
                }, left + PANEL_W - 32, top + 4, ARROW_W, ARROW_H));
        this.prevButton.visible = pages > 1;
        this.nextButton.visible = pages > 1;

        int y = buttonsTop;
        for (int i = 0; i < options.size(); i++) {
            final int index = i;
            addRenderableWidget(SakuraButton.of(options.get(i).label(),
                    b -> choose(index),
                    width / 2 - OPTION_W / 2, y, OPTION_W, OPTION_H));
            y += OPTION_PITCH;
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

        // 底图：樱符和纸面板（300×200，1:1 逻辑像素）
        graphics.blit(BACKGROUND, left, top, 0, 0, PANEL_W, PANEL_H, PANEL_W, PANEL_H);

        // 抬头：对话对象的名字（居中）
        Component header = header();
        graphics.drawCenteredString(this.font, header, left + PANEL_W / 2, top + TITLE_Y, COLOR_TITLE);

        // 页码：提到标题行（◀ 箭头左侧）—— 不再与正文首行同高
        if (pages > 1) {
            Component pageText = Component.translatable("gui.gensokyou.dialog.page", page + 1, pages);
            graphics.drawString(this.font, pageText,
                    left + PANEL_W - 58 - this.font.width(pageText), top + TITLE_Y, COLOR_PAGE, false);
        }

        // 正文
        int y = top + TEXT_TOP;
        int start = page * linesPerPage;
        for (int i = start; i < Math.min(start + linesPerPage, lines.size()); i++) {
            graphics.drawString(this.font, lines.get(i), left + 14, y, COLOR_TEXT, false);
            y += this.font.lineHeight + 4;
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
