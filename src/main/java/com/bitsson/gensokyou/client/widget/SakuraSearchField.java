package com.bitsson.gensokyou.client.widget;

import com.bitsson.gensokyou.client.SakuraPalette;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;

/**
 * 樱符主题搜索框：<b>保留原版 {@link EditBox} 的编辑能力，脱掉它的壳</b>。
 *
 * <p>原版 {@code EditBox} 自带一张灰调贴图（{@code widget/field_background}），
 * 在樱符和纸面板上完全脱节。但它的<b>光标闪烁 / 选区 / 剪贴板 / 中文 IME</b>
 * 是原版打磨过的，自写一遍得不偿失 → 故<b>继承 EditBox</b>，
 * 只把「外观」接管：{@code setBordered(false)} 去掉灰盒，凹槽与描边自己画。
 *
 * <h2>为什么控件本身只有 8px 高，而凹槽是 14px</h2>
 * 原版 {@code EditBox#renderWidget} 里：
 * <pre>
 * int j = this.bordered ? this.getX() + 4 : this.getX();
 * int k = this.bordered ? this.getY() + (this.height - 8) / 2 : this.getY();
 * </pre>
 * 即<b>去掉边框后，文字会贴到控件的左上角</b>（不再水平居中）。
 * 故控件 MUST 摆在<b>正文位置</b>（凹槽内缩 4px / 3px），尺寸 (宽−8, 8)：
 * 文字行高 8 = 字形实占 8 行（unifont {@code pixelHeight=16 / oversample=2}），
 * 内缩 3px 后在 14px 凹槽里上下各留 3px → 视觉居中。
 *
 * <p>代价：控件自身的命中区只有 8px 高（凹槽的上下各 3px 点不到）。
 * 故覆写 {@link #clicked} 与 {@link #isMouseOver}，让<b>整条凹槽</b>都可点。
 */
public class SakuraSearchField extends EditBox {

    /** 文字左内缩（与原版 bordered 时的 4px 一致）。 */
    private static final int INSET_X = 4;
    /** 文字垂直内缩：(14 − 8) / 2 = 3。 */
    private static final int INSET_Y = 3;
    /** 正文行高 = 字形实占高度（8 行）。 */
    private static final int TEXT_H = SakuraSlider.GLYPH_H;

    private final Font font;
    /** 凹槽矩形（面板坐标，绝对值）。 */
    private final int wellX;
    private final int wellY;
    private final int wellW;
    private final int wellH;
    @Nullable
    private final Component hint;

    /**
     * 造一个樱符搜索框。
     *
     * @param x       凹槽左沿（面板坐标）
     * @param y       凹槽顶沿（面板坐标）
     * @param width   凹槽宽
     * @param height  凹槽高（建议 14）
     * @param message 无障碍朗读用的名字（不显示在屏幕上）
     * @param hint    空内容时显示的提示（樱粉暗调）
     */
    public static SakuraSearchField of(Font font, int x, int y, int width, int height,
                                       Component message, @Nullable Component hint) {
        return new SakuraSearchField(font, x, y, width, height, message, hint);
    }

    private SakuraSearchField(Font font, int x, int y, int width, int height,
                              Component message, @Nullable Component hint) {
        super(font, x + INSET_X, y + INSET_Y, Math.max(1, width - INSET_X * 2), TEXT_H,
                message);
        this.font = font;
        this.wellX = x;
        this.wellY = y;
        this.wellW = width;
        this.wellH = height;
        this.hint = hint;
        // 脱掉原版的壳：灰盒不要，文字改墨色。
        setBordered(false);
        setTextColor(SakuraPalette.INK);
        setTextColorUneditable(SakuraPalette.PINK_DARK);
    }

    /** 凹槽矩形（面板坐标）。 */
    public int wellX() {
        return wellX;
    }

    public int wellY() {
        return wellY;
    }

    public int wellW() {
        return wellW;
    }

    public int wellH() {
        return wellH;
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        drawWell(g);
        // 提示语自己画：原版的 hint 用的也是 textColor（不是 textColorUneditable），
        // 无法把「提示」与「正文」分成两色。
        if (hint != null && getValue().isEmpty()) {
            g.drawString(font, hint, getX(), getY(), SakuraPalette.PINK_DARK, false);
        }
        super.renderWidget(g, mouseX, mouseY, partialTick);
    }

    /**
     * 凹槽：和纸凹底 + 描边（聚焦时转金）+ 内侧上缘一道暗线（凹陷错觉）。
     *
     * <p><b>为什么由 Java 画而不是烘焙进面板贴图</b>：凹槽是「结构层」，
     * 按 spec MUST 由程序绘制；而且聚焦态要随焦点实时变化，烘焙做不到。
     * 面板贴图里那圈凹槽位（{@code gui_sakura.py} 的 {@code wells}）只负责
     * 把该区域**抹平**，避免 AI 在那儿画东西 —— 由本类盖住即可。
     */
    private void drawWell(GuiGraphics g) {
        int edge = isFocused() ? SakuraPalette.GOLD : SakuraPalette.PINK;
        g.fill(wellX, wellY, wellX + wellW, wellY + wellH, SakuraPalette.RECESS);
        g.fill(wellX, wellY, wellX + wellW, wellY + 1, edge);
        g.fill(wellX, wellY + wellH - 1, wellX + wellW, wellY + wellH, edge);
        g.fill(wellX, wellY, wellX + 1, wellY + wellH, edge);
        g.fill(wellX + wellW - 1, wellY, wellX + wellW, wellY + wellH, edge);
        // 内侧上缘压一道暗线 → 凹进去的错觉
        g.fill(wellX + 1, wellY + 1, wellX + wellW - 1, wellY + 2, 0xFFE2BECA);
    }

    /** 命中区扩到整条凹槽（控件本体只有 8px 高，凹槽上下各 3px 会点不到）。 */
    @Override
    protected boolean clicked(double mouseX, double mouseY) {
        return this.visible && this.active
                && mouseX >= wellX && mouseY >= wellY
                && mouseX < wellX + wellW && mouseY < wellY + wellH;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return this.visible && this.active
                && mouseX >= wellX && mouseY >= wellY
                && mouseX < wellX + wellW && mouseY < wellY + wellH;
    }
}
