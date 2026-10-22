package com.bitsson.gensokyou.client.widget;

import com.bitsson.gensokyou.client.SakuraPalette;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 樱符主题滑块：金色轨槽 + 樱粉填充 + 深粉红滑钮。
 *
 * <p>原版 {@code AbstractSliderButton} 用 {@code widget/slider} + {@code widget/slider_handle}
 * 两张灰调贴图拼出来，在樱粉面板上完全脱节，故自绘。
 *
 * <h2>纵向布局：标签独占一行，轨槽独占一行</h2>
 * <pre>
 * ┌────────────────────────────┐ ← 控件顶
 * │ 长（X）：8 格               │ ← 文字带（{@link #TEXT_BAND_H}）
 * │ ▓▓▓▓▓▓▓▓▓▓●▓▓▓▓▓▓▓▓▓▓▓▓▓▓ │ ← 轨槽带（{@link #TRACK_H}），滑钮与轨槽同高
 * └────────────────────────────┘ ← 控件底
 * </pre>
 *
 * <p><b>为什么标签 MUST 放在轨槽之外</b>（旧版踩过的坑）：
 * <ol>
 *   <li><b>字形高度是 8 行</b>：MC 的 unifont 字形 {@code pixelHeight=16 / oversample=2}
 *       （见 {@code UnihexProvider.Glyph}），实占 {@link #GLYPH_H} = 8 行。旧版轨槽也正好 8px，
 *       于是文字被轨槽上下两条金边<b>齐平地夹住</b>，底边金线直接切在字脚上 → 读作「字显示不全」。
 *   <li><b>滑钮会横切文字</b>：滑钮是 8px 宽的实心块，旧版贯穿整个控件高度；默认值 25% 时
 *       滑钮正好压在标签中段，金色竖边把汉字劈开。
 * </ol>
 * 两者都只有「标签另起一行、滑钮收缩到轨槽高度」才能根治。
 *
 * <p><b>滑钮位置 MUST 与原版一致</b>：{@code x + (int)(value * (width - 8))}、宽 8px ——
 * 否则拖动时手柄与实际值会错位。原版取值映射是
 * {@code (mouseX - (getX() + 4)) / (width - 8)}，故 value=0 时滑钮左沿在 x、value=1 时右沿在 x+w。
 */
public final class SakuraSlider {

    /**
     * 字形实际高度（逻辑像素）。
     *
     * <p>MC 的 unifont 字形是 16px 高、{@code oversample=2}，实际渲染高度 = 16 / 2 = <b>8</b>；
     * 原版 bitmap 字体同为 8px。故字形一律占 8 行，自 {@code drawString} 的 y 起算。
     */
    public static final int GLYPH_H = 8;

    /** 文字带高度（标签独占的一行）。 */
    public static final int TEXT_BAND_H = 12;

    /**
     * 轨槽高度（逻辑像素）。
     *
     * <p>MUST &gt; {@link #GLYPH_H}：轨槽比字形高，标签才不会和轨槽的金边贴在一起。
     */
    public static final int TRACK_H = 12;

    /** 滑钮宽（与原版一致：8px）。 */
    public static final int HANDLE_W = 8;

    /** 标签左缩进（原版 {@code AbstractSliderButton.TEXT_MARGIN} = 2）。 */
    public static final int TEXT_MARGIN = 2;

    /** 控件最小高度 = 文字带 + 轨槽带。低于此值文字带会被压缩。 */
    public static final int MIN_HEIGHT = TEXT_BAND_H + TRACK_H;

    private static final int TRACK_BG = SakuraPalette.DARK;
    private static final int TRACK_EDGE = SakuraPalette.GOLD;
    private static final int TRACK_FILL = SakuraPalette.PINK;
    private static final int HANDLE_BG = SakuraPalette.ROSE;

    private SakuraSlider() {
    }

    /** 轨槽带顶沿（绝对 y）。控件高度不足时文字带被压缩，轨槽始终贴控件底。 */
    public static int trackTop(int y, int height) {
        return y + Math.max(0, height - TRACK_H);
    }

    /** 标签绘制 y（绝对 y），字形自此行起占 {@link #GLYPH_H} 行。 */
    public static int labelY(int y, int height) {
        int band = Math.max(0, Math.min(TEXT_BAND_H, height - TRACK_H));
        return y + Math.max(0, (band - GLYPH_H) / 2);
    }

    /**
     * 滑钮左沿 x。
     *
     * <p>MUST 与原版一致：{@code x + (int)(value * (width - 8))}。原版取值映射是
     * {@code (mouseX - (getX() + 4)) / (width - 8)}，故 value=0 时左沿在 x、value=1 时右沿在 x+w。
     */
    public static int handleX(int x, int width, double value) {
        double v = Math.max(0.0D, Math.min(1.0D, value));
        return x + (int) (v * (width - HANDLE_W));
    }

    /**
     * 画一个樱符滑块。
     *
     * @param value      0..1 的进度（与原版 {@code this.value} 同义）
     * @param label      标签文字（画在文字带，与轨槽/滑钮完全分离）
     * @param labelColor 标签颜色
     */
    public static void render(GuiGraphics g, Font font, int x, int y, int w, int h,
                               double value, Component label, int labelColor) {
        if (w <= 0 || h <= 0) {
            return;
        }
        float v = (float) Math.max(0.0D, Math.min(1.0D, value));
        int trackY = trackTop(y, h);
        int knobX = handleX(x, w, v);

        // 轨槽：暗底 + 金边
        g.fill(x, trackY, x + w, trackY + TRACK_H, TRACK_BG);
        g.fill(x, trackY, x + w, trackY + 1, TRACK_EDGE);
        g.fill(x, trackY + TRACK_H - 1, x + w, trackY + TRACK_H, TRACK_EDGE);
        // 已填充段（到滑钮左沿）
        if (knobX > x + 1) {
            g.fill(x + 1, trackY + 1, knobX, trackY + TRACK_H - 1, TRACK_FILL);
        }
        // 滑钮：与轨槽<b>同高</b>，不再贯穿整个控件（旧版贯穿会横切文字带）
        g.fill(knobX, trackY, knobX + HANDLE_W, trackY + TRACK_H, HANDLE_BG);
        g.fill(knobX, trackY, knobX + HANDLE_W, trackY + 1, TRACK_EDGE);
        g.fill(knobX, trackY + TRACK_H - 1, knobX + HANDLE_W, trackY + TRACK_H, TRACK_EDGE);
        g.fill(knobX, trackY, knobX + 1, trackY + TRACK_H, TRACK_EDGE);
        g.fill(knobX + HANDLE_W - 1, trackY, knobX + HANDLE_W, trackY + TRACK_H, TRACK_EDGE);

        // 标签：独占文字带，字形完整落在带内，与轨槽/滑钮零重叠
        g.drawString(font, label, x + TEXT_MARGIN, labelY(y, h), labelColor, false);
    }
}
