package com.bitsson.gensokyou.client.renderer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 核心渲染的<b>配色纪律</b>断言。
 *
 * <p>这组断言防的是一条具体的回归：上一版核心"整体发白"。成因不是审美，是算术：
 * 4 个加法混合层叠同一个饱和品阶色 + 亮度调制走 RGB 乘法并逐通道截顶
 * ⇒ 主通道触顶 ⇒ 色相漂移 + 饱和度塌陷（阶 1 绿实测 (160,255,168) 发白薄荷绿，
 * 阶 3 金实测 (255,255,12) 黄白）。
 *
 * <p>观感本身只能靠实机眼睛，但"**会不会发白**"是可以被算出来的——所以它必须被算出来。
 */
class CoreOrbPaletteTest {

    private static final double EPS = 1.0E-6D;

    // ------------------------------------------------------------------
    // R1｜加法预算：结构上不可能截顶
    // ------------------------------------------------------------------

    /**
     * 任意同像素上可能同时出现的加法层贡献之和 MUST ≤ 预算上限。
     *
     * <p>"可能同时出现"由色相槽的互斥关系推出：同像素上每槽至多一层。
     */
    @Test
    void additiveLayersStayWithinBudget() {
        assertTrue(CoreOrbPalette.worstCaseAdditive() <= CoreOrbPalette.ADDITIVE_BUDGET_TOTAL,
                "加法层贡献之和 MUST ≤ " + CoreOrbPalette.ADDITIVE_BUDGET_TOTAL
                        + "，实际 " + CoreOrbPalette.worstCaseAdditive());
    }

    /**
     * 本体峰值 + 加法总和 MUST &lt; 255。
     *
     * <p>这是"不发生通道截顶"的<b>充分条件</b>——只要这条成立，任何像素的任一通道都到不了 255，
     * 于是主通道不会触顶、色相不会漂、饱和度不会塌。
     */
    @Test
    void bodyPeakPlusAdditiveBudgetNeverReachesTwoFiftyFive() {
        int bodyPeak = (int) (CoreOrbPalette.BODY_PEAK_BRI * 255.0F);
        int total = bodyPeak + CoreOrbPalette.worstCaseAdditive();
        assertTrue(total <= 255,
                "本体峰值 " + bodyPeak + " + 加法 " + CoreOrbPalette.worstCaseAdditive()
                        + " = " + total + "，MUST ≤ 255");
    }

    /** 每个<b>加法</b>层在<b>最亮</b>的颜色下，其单通道贡献 MUST ≤ 自己的预算。 */
    @Test
    void everyAdditiveLayerRespectsItsOwnBudget() {
        // 最坏情况：一个峰值 255 的高饱和颜色 + 满 alpha
        int brightest = CoreOrbPalette.compose(0.10F, 1.0F, 1.0F);
        for (CoreOrbPalette.Layer layer : CoreOrbPalette.Layer.values()) {
            if (!layer.isAdditive()) {
                continue;
            }
            int alpha = CoreOrbPalette.alphaWithin(brightest, layer.budget(), 255);
            int contribution = CoreOrbPalette.contribution(brightest, alpha);
            assertTrue(contribution <= layer.budget(),
                    layer + " 的单通道贡献 " + contribution + " MUST ≤ 预算 " + layer.budget());
        }
    }

    /** 加法层 MUST 各自占用互不相同的色相槽——"同一色相不被两层持有"的实现形式。 */
    @Test
    void additiveLayersUseDistinctHueSlots() {
        boolean[] used = new boolean[8];
        for (CoreOrbPalette.Layer layer : CoreOrbPalette.Layer.values()) {
            int slot = layer.hueSlot();
            assertTrue(slot >= 0 && slot < used.length, "色相槽下标越界：" + slot);
            assertTrue(!used[slot], "色相槽 " + slot + " 被多个层占用（" + layer + "）");
            used[slot] = true;
        }
    }

    // ------------------------------------------------------------------
    // R2｜HSV 空间合成：亮度调制 MUST NOT 改色相
    // ------------------------------------------------------------------

    /**
     * 在 HSV 侧调亮度 MUST NOT 改变色相。
     *
     * <p>这是与旧实现（RGB 等比乘法 + 逐通道截顶）的分水岭：后者在亮处会把主通道截掉，
     * 色相随之漂移（绿 → 青绿），饱和度同步塌掉。
     */
    @Test
    void brightnessModulationDoesNotShiftHue() {
        float hue = 0.33F;
        float sat = 0.80F;
        int dim = CoreOrbPalette.compose(hue, sat, 0.30F);
        int bright = CoreOrbPalette.compose(hue, sat, 0.90F);
        assertEquals(CoreOrbPalette.hueOf(dim), CoreOrbPalette.hueOf(bright), 0.01D,
                "亮度调制 MUST NOT 改变色相");
        assertTrue(CoreOrbPalette.saturationOf(bright) >= CoreOrbPalette.saturationOf(dim) - 0.02D,
                "提亮 MUST NOT 塌掉饱和度（否则读作发白）");
    }

    /** 合成 MUST 把亮度钳在 0..1——MUST NOT 在 RGB 侧做乘法后截顶。 */
    @Test
    void composeClampsBrightnessInsteadOfClippingChannels() {
        int over = CoreOrbPalette.compose(0.33F, 0.9F, 4.0F);
        int clamped = CoreOrbPalette.compose(0.33F, 0.9F, 1.0F);
        assertEquals(clamped, over, "超界亮度 MUST 被钳到 1.0，而不是逐通道截顶");
        int under = CoreOrbPalette.compose(0.33F, 0.9F, -3.0F);
        assertEquals(CoreOrbPalette.compose(0.33F, 0.9F, 0.0F), under, "负亮度 MUST 被钳到 0.0");
    }

    /** 色相平移 MUST 保持在 0..1（越界回绕而不是溢出）。 */
    @Test
    void hueShiftWrapsAround() {
        assertEquals(0.0D, CoreOrbPalette.shiftHue(0.0F, 360.0F), EPS);
        assertEquals(0.5D, CoreOrbPalette.shiftHue(0.0F, 180.0F), EPS);
        assertEquals(0.25D, CoreOrbPalette.shiftHue(0.75F, -180.0F), EPS);
        assertEquals(0.5D, CoreOrbPalette.complement(0.0F), EPS);
    }

    // ------------------------------------------------------------------
    // R3｜色相分工：品阶色只由本体层持有
    // ------------------------------------------------------------------

    /**
     * 加法层 MUST NOT 与本体层持有同一色相槽。
     *
     * <p>品阶色（装核态）由本体层独占；加法层走互补色 / 暖色 / 白金，
     * 这样"全彩"来自<b>层间色相分离</b>，而不是全色相循环——阶位因此仍然可辨。
     */
    @Test
    void bodyOwnsItsOwnHueSlot() {
        int bodySlot = CoreOrbPalette.Layer.BODY.hueSlot();
        for (CoreOrbPalette.Layer layer : CoreOrbPalette.Layer.values()) {
            if (layer == CoreOrbPalette.Layer.BODY) {
                continue;
            }
            assertTrue(layer.hueSlot() != bodySlot,
                    layer + " MUST NOT 与本体层同色相槽（品阶色只归本体层）");
        }
    }

    /** 装核态的色相摆动 MUST 保持在 ±30° 内（品阶仍可辨）。 */
    @Test
    void tierHueSwingStaysWithinNarrowBand() {
        assertTrue(CoreOrbPalette.TIER_HUE_SWING_DEG <= 30.0F,
                "品阶色相摆动 MUST ≤ ±30°，实际 " + CoreOrbPalette.TIER_HUE_SWING_DEG);
        assertTrue(CoreOrbPalette.HUE_FACE_SPREAD_DEG <= 10.0F,
                "逐面微偏 MUST 保持窄区间");
    }

    /** 行进光带的亮度系数 MUST 落在合理区间——MUST NOT 把本体推到 1.0 以上。 */
    @Test
    void flowBandStaysBounded() {
        for (int i = 0; i <= 64; i++) {
            float flow = i / 64.0F;
            float band = CoreOrbPalette.band(flow, 3.7F);
            assertTrue(band > 0.0F && band <= 1.0F + CoreOrbPalette.BAND_DEPTH + EPS,
                    "光带系数越界：" + band);
        }
    }
}
