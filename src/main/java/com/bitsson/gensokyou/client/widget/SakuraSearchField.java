package com.bitsson.gensokyou.client.widget;

import com.bitsson.gensokyou.client.SakuraPalette;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;

/**
 * 樱符主题搜索框：<b>保留原版 {@link EditBox} 的编辑能力，脱掉它的壳</b>。
 *
 * <p>原版 {@code EditBox} 自带一张灰调贴图（{@code widget/field_background}），
 * 在樱符和纸面板上完全脱节。但它的<b>光标闪烁 / 选区 / 剪贴板 / 中文 IME</b>
 * 是原版打磨过的，自写一遍得不偿失 → 故<b>继承 EditBox</b>，
 * 只把「外观」接管：{@code setBordered(false)} 去掉灰盒，凹槽与描边自己画。
 *
 * <h2>为什么正文也自己画（不调 {@code super.renderWidget}）</h2>
 * 原版 {@code EditBox#renderWidget} 用的是
 * {@code GuiGraphics.drawString(font, text, x, y, color)} —— 这个 5 参重载
 * <b>默认带投影</b>（内部转调 6 参并传 {@code dropShadow = true}）。
 * 投影色是 {@code (color & 0xFCFCFC) >> 2}：墨色 {@code 0xFF4A1A2C} 的历史投影
 * 约 {@code #12060B}，在浅粉凹底（{@code RECESS}）上就是每个字右下角多一块近黑实心像素。
 * guiScale=4 时那是 4×4 物理像素的鬼影，中文笔画本来就密，叠上去读作「字发糊、双份」。
 *
 * <p>本项目的既有结论（见 {@code sakura-gui-art-system} 任务 2.7）是：<b>浅底上的深字
 * MUST 关投影</b>。但 {@code EditBox} 的投影是写死在 {@code renderWidget} 里的，
 * 没有可覆写的开关，故这里<b>整段接管绘制</b>（标题、正文、光标、选区都自己画），
 * 仍然复用原版的输入/光标/选区逻辑。
 *
 * <h2>为什么要镜像 {@code displayPos} / {@code highlightPos}</h2>
 * 绘制正文要的是「当前滚到哪一列」与「选区锚点」，而这两个字段在 {@code EditBox}
 * 里是 {@code private}、没有 getter。好在原版对它们的<b>每一次写入</b>都收敛到两个
 * <b>public</b> 方法上：
 * <pre>
 *   setCursorPosition(pos)   → cursorPos = clamp(pos); scrollTo(cursorPos);
 *   setHighlightPos(pos)     → highlightPos = clamp(pos); scrollTo(highlightPos);
 * </pre>
 * 于是子类覆写这两个方法、用同一份公式把 {@code scrollTo} 重放一遍，就能得到与父类
 * <b>逐帧一致</b>的两份镜像，不需要反射、不需要 mixin、也不碰其他 EditBox。
 *
 * <p>⚠️ 代价：这套镜像依赖 1.21.1 的 {@code EditBox} 实现。若升级 MC 后出现
 * 「光标位置与文字错位」，先核对这两处内部写入是否仍只经由那两个 public 方法。
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

    /** 原版选区高亮色（{@code 0xFF0000FF}，半透明蓝）。 */
    private static final int HIGHLIGHT_COLOR = 0xFF0000FF;

    private final Font font;
    /** 凹槽矩形（面板坐标，绝对值）。 */
    private final int wellX;
    private final int wellY;
    private final int wellW;
    private final int wellH;
    @Nullable
    private final Component hint;

    // —— 父类 private 状态的镜像（见类注释「为什么要镜像」）——
    /** 正文左端滚到的字符下标。 */
    private int displayPos;
    /** 选区锚点（与 {@link #getCursorPosition()} 相同表示无选区）。 */
    private int highlightPos;
    private int maxLength = 32;
    private boolean editable = true;
    private int textColor = SakuraPalette.INK;
    private int textColorUneditable = SakuraPalette.PINK_DARK;
    /** 获得焦点的时刻，用于光标 300ms 闪烁。 */
    private long focusedTime = Util.getMillis();

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

    // ---------------------------------------------------------------- 状态镜像

    @Override
    public void setCursorPosition(int pos) {
        super.setCursorPosition(pos);
        mirrorScrollTo(getCursorPosition());
    }

    @Override
    public void setHighlightPos(int pos) {
        super.setHighlightPos(pos);
        this.highlightPos = Mth.clamp(pos, 0, getValue().length());
        mirrorScrollTo(this.highlightPos);
    }

    @Override
    public void setMaxLength(int length) {
        this.maxLength = Math.max(0, length);
        super.setMaxLength(length);
    }

    @Override
    public void setEditable(boolean value) {
        this.editable = value;
        super.setEditable(value);
    }

    @Override
    public void setTextColor(int color) {
        this.textColor = color;
        super.setTextColor(color);
    }

    @Override
    public void setTextColorUneditable(int color) {
        this.textColorUneditable = color;
        super.setTextColorUneditable(color);
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (focused) {
            this.focusedTime = Util.getMillis();
        }
    }

    /**
     * 原版 {@code EditBox#scrollTo} 的同式重放（父类方法是 private）。
     *
     * <p>必须与父类<b>逐字对应</b>，否则镜像的 {@code displayPos} 会和父类算出的光标位置
     * 错开——表现为点选位置偏、或长文本滚动后文字与光标不重合。
     */
    private void mirrorScrollTo(int pos) {
        String value = getValue();
        int inner = getInnerWidth();
        this.displayPos = Math.min(this.displayPos, value.length());
        String window = font.plainSubstrByWidth(value.substring(this.displayPos), inner);
        int end = window.length() + this.displayPos;
        if (pos == this.displayPos) {
            this.displayPos -= font.plainSubstrByWidth(value, inner, true).length();
        } else if (pos > end) {
            this.displayPos += pos - end;
        } else if (pos <= this.displayPos) {
            this.displayPos = pos;
        }
        this.displayPos = Mth.clamp(this.displayPos, 0, value.length());
    }

    // ---------------------------------------------------------------- 绘制

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        drawWell(g);
        // 提示语自己画：原版的 hint 用的也是 textColor（不是 textColorUneditable），
        // 无法把「提示」与「正文」分成两色。
        if (hint != null && getValue().isEmpty()) {
            g.drawString(font, hint, getX(), getY(), SakuraPalette.PINK_DARK, false);
        }
        drawTextAndCaret(g);
    }

    /**
     * 正文 / 光标 / 选区。
     *
     * <p>几何与绘制顺序逐条对应原版 {@code EditBox#renderWidget}，
     * <b>唯一的差异是 {@code dropShadow = false}</b>，以及「追加态」光标改墨色
     * （原版的 {@code 0xFFD0D0D0} 浅灰在浅粉凹底上完全看不见）。
     */
    private void drawTextAndCaret(GuiGraphics g) {
        String value = getValue();
        int color = this.editable ? this.textColor : this.textColorUneditable;
        int caret = getCursorPosition();
        int localCaret = caret - this.displayPos;
        String window = font.plainSubstrByWidth(
                value.substring(Math.min(this.displayPos, value.length())), getInnerWidth());
        boolean caretVisible = localCaret >= 0 && localCaret <= window.length();
        // 原版语义：光标后面还有字，或已经顶到长度上限 → 光标画成竖线而不是下划线
        boolean appendMode = caret < value.length() || value.length() >= this.maxLength;
        int x = getX();
        int y = getY();
        int right = x;
        int highlightLocal = Mth.clamp(this.highlightPos - this.displayPos, 0, window.length());

        if (!window.isEmpty()) {
            String head = caretVisible ? window.substring(0, localCaret) : window;
            right = g.drawString(font, head, x, y, color, false);
        }

        int caretX = right;
        if (!caretVisible) {
            caretX = localCaret > 0 ? x + getWidth() : x;
        } else if (appendMode) {
            caretX = right - 1;
            right--;
        }

        if (!window.isEmpty() && caretVisible && localCaret < window.length()) {
            g.drawString(font, window.substring(localCaret), right, y, color, false);
        }

        if (isFocused() && (Util.getMillis() - this.focusedTime) / 300L % 2L == 0L && caretVisible) {
            if (appendMode) {
                g.fill(RenderType.guiOverlay(), caretX, y - 1, caretX + 1, y + 1 + 9, color);
            } else {
                g.drawString(font, "_", caretX, y, color, false);
            }
        }

        if (highlightLocal != localCaret) {
            int anchorX = x + font.width(window.substring(0, highlightLocal));
            drawHighlight(g, caretX, y - 1, anchorX - 1, y + 1 + 9);
        }
    }

    /** 选区高亮：与父类同款（半透明蓝、右/下界取大、右侧裁到控件宽度）。 */
    private void drawHighlight(GuiGraphics g, int x1, int y1, int x2, int y2) {
        if (x1 < x2) {
            int swap = x1;
            x1 = x2;
            x2 = swap;
        }
        if (y1 < y2) {
            int swap = y1;
            y1 = y2;
            y2 = swap;
        }
        x2 = Math.min(x2, getX() + getWidth());
        x1 = Math.min(x1, getX() + getWidth());
        g.fill(RenderType.guiTextHighlight(), x1, y1, x2, y2, HIGHLIGHT_COLOR);
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
