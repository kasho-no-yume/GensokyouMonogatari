package com.bitsson.gensokyou.client.widget;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 樱符滑块布局回归。
 *
 * <p><b>旧版的两个真 bug（实机反馈「滑动条的高度比字的高度小了，导致字显示不全」）：</b>
 * <ol>
 *   <li>轨槽高 8px，而 MC 的 unifont 字形实占 <b>8 行</b>
 *       （{@code pixelHeight=16 / oversample=2}）——文字被轨槽上下两条金边齐平地夹住，
 *       底边金线直接切在字脚上，读作「字被切了」。
 *   <li>滑钮 8px 宽、贯穿整个控件高度；默认值 25% 时滑钮正好横切标签中段，
 *       金色竖边把汉字劈开。
 * </ol>
 *
 * <p>本测试把「标签独占一行、滑钮收缩到轨槽高度」这条契约钉死，防止回退。
 */
class SakuraSliderLayoutTest {

    /** 控件顶（相对值，测试里取 0 便于读）。 */
    private static final int Y = 0;

    @Test
    void trackIsTallerThanTheGlyph() {
        assertTrue(SakuraSlider.TRACK_H > SakuraSlider.GLYPH_H,
                "轨槽高 " + SakuraSlider.TRACK_H + " MUST 大于字形高 " + SakuraSlider.GLYPH_H
                        + "，否则标签会被轨槽的金边切掉上下两行");
    }

    @Test
    void minimumHeightCoversBothBands() {
        assertEquals(SakuraSlider.TEXT_BAND_H + SakuraSlider.TRACK_H, SakuraSlider.MIN_HEIGHT,
                "控件最小高度 = 文字带 + 轨槽带");
        assertTrue(SakuraSlider.MIN_HEIGHT >= SakuraSlider.GLYPH_H + 2,
                "文字带 MUST 装得下字形并留出余量");
    }

    @Test
    void labelBandDoesNotTouchTheTrack() {
        int labelTop = SakuraSlider.labelY(Y, SakuraSlider.MIN_HEIGHT);
        int labelBottom = labelTop + SakuraSlider.GLYPH_H;
        int trackTop = SakuraSlider.trackTop(Y, SakuraSlider.MIN_HEIGHT);

        assertTrue(labelBottom <= trackTop,
                "字形底沿 " + labelBottom + " 压到了轨槽顶沿 " + trackTop
                        + "（标签 MUST 独占一行，不得与轨槽重叠）");
        assertTrue(labelTop >= Y, "字形顶沿越过了控件顶");
    }

    @Test
    void trackStaysInsideTheWidget() {
        int trackTop = SakuraSlider.trackTop(Y, SakuraSlider.MIN_HEIGHT);
        assertTrue(trackTop >= Y + SakuraSlider.TEXT_BAND_H,
                "轨槽顶沿 " + trackTop + " 侵入了文字带（文字带 "
                        + SakuraSlider.TEXT_BAND_H + "px）");
        assertTrue(trackTop + SakuraSlider.TRACK_H <= Y + SakuraSlider.MIN_HEIGHT,
                "轨槽底沿越过了控件底");
    }

    @Test
    void knobNeverCrossesTheLabelBand() {
        // 滑钮与轨槽同高 → 滑钮的纵向范围恒等于轨槽带，绝无可能横切文字带
        int trackTop = SakuraSlider.trackTop(Y, SakuraSlider.MIN_HEIGHT);
        int knobTop = trackTop;
        int knobBottom = knobTop + SakuraSlider.TRACK_H;
        assertTrue(knobTop >= SakuraSlider.labelY(Y, SakuraSlider.MIN_HEIGHT)
                        + SakuraSlider.GLYPH_H,
                "滑钮顶沿侵入了字形行");
        assertTrue(knobBottom <= Y + SakuraSlider.MIN_HEIGHT, "滑钮底沿越过了控件底");
    }

    @Test
    void knobStaysHorizontallyInsideTheTrack() {
        int x = 16;
        int w = 140;
        assertEquals(x, SakuraSlider.handleX(x, w, 0.0D), "value=0 时滑钮左沿在轨槽左沿");
        assertEquals(x + w - SakuraSlider.HANDLE_W, SakuraSlider.handleX(x, w, 1.0D),
                "value=1 时滑钮右沿在轨槽右沿");
        assertTrue(SakuraSlider.handleX(x, w, 0.5D) > x
                && SakuraSlider.handleX(x, w, 0.5D) < x + w - SakuraSlider.HANDLE_W,
                "中间值时滑钮必须在轨槽内");
        // 越界值被钳住
        assertEquals(x, SakuraSlider.handleX(x, w, -1.0D));
        assertEquals(x + w - SakuraSlider.HANDLE_W, SakuraSlider.handleX(x, w, 2.0D));
    }

    @Test
    void undersizedWidgetDegradesWithoutBreaking() {
        // 控件被压矮时文字带被压缩，但轨槽 MUST 仍在控件内、标签 MUST 仍在控件顶之上
        int h = SakuraSlider.TRACK_H;
        int trackTop = SakuraSlider.trackTop(Y, h);
        assertTrue(trackTop + SakuraSlider.TRACK_H <= Y + h, "矮控件里轨槽越界");
        assertTrue(SakuraSlider.labelY(Y, h) >= Y, "矮控件里标签越界");
    }
}
