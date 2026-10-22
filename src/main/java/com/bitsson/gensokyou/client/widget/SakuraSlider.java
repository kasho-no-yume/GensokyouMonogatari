package com.bitsson.gensokyou.client.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 樱符主题滑块：金色轨槽 + 樱粉填充 + 深粉红滑钮。
 *
 * <p>原版 {@code AbstractSliderButton} 用 {@code widget/slider} + {@code widget/slider_handle}
 * 两张灰调贴图拼出来，在樱粉面板上完全脱节，故自绘。
 *
 * <p><b>滑钮位置 MUST 与原版一致</b>：{@code x + (int)(value * (width - 8))}、宽 8px ——
 * 否则拖动时手柄与实际值会错位。轨槽高度也沿用原版的 8px（贴在垂直中线）。
 */
public final class SakuraSlider {

    private static final int TRACK_H = 8;
    private static final int HANDLE_W = 8;

    private static final int TRACK_BG = 0xFF2E1E28;
    private static final int TRACK_EDGE = 0xFFD8BA84;
    private static final int TRACK_FILL = 0xFFC77A92;
    private static final int HANDLE_BG = 0xFFBA4A62;

    private SakuraSlider() {
    }

    /**
     * 画一个樱符滑块。
     *
     * @param value      0..1 的进度（与原版 {@code this.value} 同义）
     * @param label      标签文字（画在轨槽之上）
     * @param labelColor 标签颜色
     */
    public static void render(GuiGraphics g, Font font, int x, int y, int w, int h,
                               double value, Component label, int labelColor) {
        if (w <= 0 || h <= 0) {
            return;
        }
        float v = (float) Math.max(0.0D, Math.min(1.0D, value));
        int trackY = y + h / 2 - TRACK_H / 2;
        int knobX = x + (int) (v * (w - HANDLE_W));

        // 轨槽：暗底 + 金边
        g.fill(x, trackY, x + w, trackY + TRACK_H, TRACK_BG);
        g.fill(x, trackY, x + w, trackY + 1, TRACK_EDGE);
        g.fill(x, trackY + TRACK_H - 1, x + w, trackY + TRACK_H, TRACK_EDGE);
        // 已填充段（到滑钮左沿）
        if (knobX > x + 1) {
            g.fill(x + 1, trackY + 1, knobX, trackY + TRACK_H - 1, TRACK_FILL);
        }
        // 滑钮：深粉红 + 金边
        g.fill(knobX, y, knobX + HANDLE_W, y + h, HANDLE_BG);
        g.fill(knobX, y, knobX + HANDLE_W, y + 1, TRACK_EDGE);
        g.fill(knobX, y + h - 1, knobX + HANDLE_W, y + h, TRACK_EDGE);
        g.fill(knobX, y, knobX + 1, y + h, TRACK_EDGE);
        g.fill(knobX + HANDLE_W - 1, y, knobX + HANDLE_W, y + h, TRACK_EDGE);

        // 标签：画在滑钮最小位置之后，避免 value=0 时被滑钮压住
        g.drawString(font, label, x + HANDLE_W + 4, y + h / 2 - 4, labelColor, false);
    }
}
