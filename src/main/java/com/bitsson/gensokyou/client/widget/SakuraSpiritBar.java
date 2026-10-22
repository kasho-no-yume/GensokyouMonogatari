package com.bitsson.gensokyou.client.widget;

import com.bitsson.gensokyou.client.SakuraPalette;
import com.bitsson.gensokyou.registry.TierPalette;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 樱符 HUD 灵力条（竖排符箓条）。
 *
 * <pre>
 * ┌────────┐
 * │ ❀ 印 ❀ │ ← 符头 13：樱深红底 + 金边 + 三朵樱花 + 米白印
 * ├────────┤
 * │▒│░░░░░░│ ← 框段：1px 金线 + 2px 樱粉带 + 1px 内侧暗线 + 10px 槽
 * │▒│░░░░░░│    槽底 = 深色；填充 = <b>品阶色</b>（TierPalette），从下往上
 * │▒│░░░░░░│
 * ├────────┤
 * │  ~~~   │ ← 符尾 13：樱粉暗调 + 金边 + 内收云纹
 * └────────┘
 * </pre>
 *
 * <h2>三条设计约束</h2>
 * <ol>
 *   <li><b>只显示比例，不显示具体数值。</b>5 阶上限 1000 万，写成
 *       {@code "10000000 / 10000000"} 要 116px —— 早就出了屏幕。
 *       比例靠「填充长度 + 25/50/75% 刻度」表达。
 *   <li><b>填充用品阶色</b>（{@link TierPalette}，全模组品阶色单一色源）。
 *       0 阶时灵力上限为 0、HUD 根本不画 → 实际只会出现 1~5 阶。
 *   <li><b>刻度画在框侧的樱粉带上</b>，不画在槽里：3 阶是金
 *       （0xFFC107），金色刻度落进金色填充会消失；樱粉带永远不变 → 任何阶色下都看得见。
 * </ol>
 *
 * <p>HUD 压在<b>世界</b>上而不是和纸上，故底色一律走深色（{@link SakuraPalette#DARK}），
 * 保证任何背景下都读得出。
 */
public final class SakuraSpiritBar {

    /** 整条宽（含外框）。 */
    public static final int WIDTH = 16;
    /** 符头高。 */
    public static final int HEAD_H = 13;
    /** 符尾高。 */
    public static final int TAIL_H = 13;
    /** 整条高（约屏幕高的 1/3；1080p/guiScale=4 下逻辑屏高 270 → 90）。 */
    public static final int HEIGHT = 90;
    /** 框段高（符头与符尾之间）。 */
    public static final int FRAME_H = HEIGHT - HEAD_H - TAIL_H;
    /** 槽上下内缩（框段与槽之间留白）。 */
    private static final int TRACK_INSET = 3;
    /** 槽高。 */
    public static final int TRACK_H = FRAME_H - TRACK_INSET * 2;
    /** 槽宽（外框内）。 */
    public static final int TRACK_W = WIDTH - 8;

    private SakuraSpiritBar() {
    }

    /**
     * 画一条竖排灵力条。
     *
     * @param ratio 0..1 的填充比例
     * @param tier  超人类阶级（0-5），决定填充色（{@link TierPalette}）
     */
    public static void render(GuiGraphics g, int x, int y, float ratio, int tier) {
        float r = Math.max(0.0F, Math.min(1.0F, ratio));
        int frameY = y + HEAD_H;

        drawHead(g, x, y);
        drawTail(g, x, frameY + FRAME_H);

        // 框段：金线 + 樱粉带 + 内侧暗线
        g.fill(x, frameY, x + WIDTH, frameY + FRAME_H, SakuraPalette.PINK);
        g.fill(x, frameY, x + 1, frameY + FRAME_H, SakuraPalette.GOLD);
        g.fill(x + WIDTH - 1, frameY, x + WIDTH, frameY + FRAME_H, SakuraPalette.GOLD);
        g.fill(x + 3, frameY, x + 4, frameY + FRAME_H, SakuraPalette.PINK_DARK);
        g.fill(x + WIDTH - 4, frameY, x + WIDTH - 3, frameY + FRAME_H, SakuraPalette.PINK_DARK);

        // 刻度：金色缺口，画在左侧樱粉带上（不碰填充色 → 任何阶色下都看得见）
        int tickX = x + 1;
        for (float q : new float[]{0.25F, 0.5F, 0.75F}) {
            int tickY = trackTop(y) + TRACK_H - Math.round(TRACK_H * q);
            g.fill(tickX, tickY, tickX + 2, tickY + 1, SakuraPalette.GOLD);
        }

        // 槽 + 品阶色填充（从下往上）
        int tx = x + 4;
        int ty = trackTop(y);
        g.fill(tx, ty, tx + TRACK_W, ty + TRACK_H, SakuraPalette.DARK);
        int fill = Math.round(TRACK_H * r);
        if (fill > 0) {
            g.fill(tx, ty + TRACK_H - fill, tx + TRACK_W, ty + TRACK_H, TierPalette.rgb(tier));
        }
    }

    /** 槽顶沿（相对整条顶沿）。 */
    public static int trackTop(int barY) {
        return barY + HEAD_H + TRACK_INSET;
    }

    /** 3×3 小樱花。 */
    private static void blossom(GuiGraphics g, int x, int y) {
        g.fill(x, y + 1, x + 3, y + 2, SakuraPalette.PINK);
        g.fill(x + 1, y, x + 2, y + 3, SakuraPalette.PINK);
        g.fill(x + 1, y + 1, x + 2, y + 2, SakuraPalette.CREAM);
    }

    /** 符头：樱深红底 + 金边 + 三朵樱花 + 米白印。 */
    private static void drawHead(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + WIDTH, y + HEAD_H, SakuraPalette.ROSE);
        g.fill(x, y, x + WIDTH, y + 1, SakuraPalette.GOLD);
        g.fill(x, y + HEAD_H - 1, x + WIDTH, y + HEAD_H, SakuraPalette.GOLD);
        g.fill(x, y, x + 1, y + HEAD_H, SakuraPalette.GOLD);
        g.fill(x + WIDTH - 1, y, x + WIDTH, y + HEAD_H, SakuraPalette.GOLD);
        blossom(g, x + 2, y + 3);
        blossom(g, x + WIDTH - 5, y + 3);
        blossom(g, x + 6, y + 8);
        g.fill(x + 7, y + 3, x + 9, y + 8, SakuraPalette.CREAM);
    }

    /** 符尾：樱粉暗调 + 金边 + 内收云纹。 */
    private static void drawTail(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + WIDTH, y + TAIL_H, SakuraPalette.PINK_DARK);
        g.fill(x, y, x + WIDTH, y + 1, SakuraPalette.GOLD);
        g.fill(x, y + TAIL_H - 1, x + WIDTH, y + TAIL_H, SakuraPalette.GOLD);
        g.fill(x, y, x + 1, y + TAIL_H, SakuraPalette.GOLD);
        g.fill(x + WIDTH - 1, y, x + WIDTH, y + TAIL_H, SakuraPalette.GOLD);
        for (int i = 0; i < 3; i++) {
            g.fill(x + 4 + i, y + 3 + i, x + WIDTH - 4 - i, y + 4 + i, SakuraPalette.PINK);
        }
    }
}
