package com.bitsson.gensokyou.client.widget;

import com.bitsson.gensokyou.Gensokyou;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 樱符主题按钮：九宫格贴图 + 居中文字。
 *
 * <p><b>为什么按钮能用九宫格，而面板底图不能</b>
 * 九宫格的前提是「边条可拉伸且拉伸不失真」。
 * 按钮的边框是规整色带、且<b>宽度随文字长度变</b>（中文 2 字与英文 12 字符差 5 倍），
 * 九宫格正是这类控件的标准解法；
 * 而面板底图的内容是散落花瓣、不规律分布，一平铺就重复露缝。
 * 同一套方法论，两个相反结论 —— 见 {@code openspec/changes/sakura-gui-art-system/}。
 *
 * <p><b>贴图的三条硬约束</b>（否则九宫格会糊，详见 {@code tools/textures/gui_buttons.py}）：
 * <ol>
 *   <li>斜面只做上下：左右边条被纵向拉伸，竖向斜面会让它不均匀 -&gt; 拉伸出横向条纹。
 *       顶高光在上边条、底阴影在下边条（横向拉伸，每行水平均匀 -&gt; 仍是 1px 厚）。
 *   <li>左/右边条竖向均匀：每列从上到下同色。
 *   <li>中心是纯色：中心块被双向拉伸，非纯色会被抹花。
 * </ol>
 *
 * <p>cap=6、贴图 20×20 -&gt; 中心 8×8。本项目按钮高度有 20/16/14/12 四种，
 * 20−2×6=8 -&gt; 中心被压到 8/4/2/0，<b>全部整除</b>，纵向缩放不产生半像素。
 */
public class SakuraButton extends Button {

    /** 九宫格切分边长（贴图 20×20，中心 8×8）。 */
    private static final int CAP = 6;
    private static final int TEX_W = 20;
    private static final int TEX_H = 20;
    /** 中心区边长 = 贴图边长 − 2×切分边长。边条拉伸时以中心区为源区域。 */
    private static final int MID = TEX_W - 2 * CAP;

    private static final ResourceLocation NORMAL =
            Gensokyou.id("textures/gui/sakura/button.png");
    private static final ResourceLocation HOVER =
            Gensokyou.id("textures/gui/sakura/button_hover.png");
    private static final ResourceLocation DISABLED =
            Gensokyou.id("textures/gui/sakura/button_disabled.png");

    private static final int TEXT_NORMAL = 0xFFF0CEBE;
    private static final int TEXT_HOVER = 0xFFFCE4DA;
    private static final int TEXT_DISABLED = 0xFFBCB2B6;

    /**
     * 按钮体内的**成片斑驳纹理** —— <b>程序化绘制</b>，MUST NOT 烘焙进贴图。
     *
     * <p>原因：九宫格的<b>中心区是双向拉伸</b>的，把纹理烘焙进去，
     * 按钮一变宽（50 → 108 → 150）纹理就被拉成横条。
     * 程序化逐像素画则形状不变。
     *
     * <p><b>为什么是「成片」而不是散点</b>：散点用浅粉/白落在深粉底上，
     * 在屏幕上读作「一片白点」，和米白文字叠在一起非常晃眼（实机反馈）。
     * 斑块用<b>与主体同色系、只稍淡</b>的两档色（186,74,98 → 204,98,120 / 196,86,108），
     * 远看只是「底色有点不匀」，不抢眼。
     *
     * <p>位置由按钮坐标取种子 -&gt; 逐帧稳定，不闪烁。
     */
    private static final int TEXTURE_LIGHT = 0xFFCC6278;
    private static final int TEXTURE_DARK = 0xFFC4566C;

    /** 造一个樱符主题按钮。 */
    public static SakuraButton of(Component message, OnPress onPress,
                                  int x, int y, int width, int height) {
        return new SakuraButton(message, onPress, x, y, width, height);
    }

    private SakuraButton(Component message, OnPress onPress,
                         int x, int y, int width, int height) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean hover = this.isHoveredOrFocused();
        ResourceLocation tex = !this.active ? DISABLED : (hover ? HOVER : NORMAL);
        int color = !this.active ? TEXT_DISABLED : (hover ? TEXT_HOVER : TEXT_NORMAL);

        // 原版 renderWidget 会显式设色（带 alpha）；不设会继承上一个 widget 的着色状态。
        graphics.setColor(1.0F, 1.0F, 1.0F, this.alpha);
        nineSlice(graphics, tex, getX(), getY(), getWidth(), getHeight());
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        if (this.active) {
            texture(graphics, getX(), getY(), getWidth(), getHeight());
        }

        // 文字沿用原版按钮的居中 + 超长滚动（AbstractButton#renderString）
        this.renderString(graphics, Minecraft.getInstance().font, color);
    }

    /**
     * 成片斑驳纹理：按按钮位置取种子的确定性伪随机，逐像素、逐帧稳定。
     *
     * <p>斑块 2~4 × 1~2 px，密度约每 55px² 一块；只落在按钮体内
     * （让开顶高光行与底阴影行），不覆盖金边。
     */
    private static void texture(GuiGraphics g, int x, int y, int w, int h) {
        int bx = x + 1;
        int by = y + 2;              // 让开顶高光行
        int bw = w - 2;
        int bh = h - 4;              // 让开底阴影行
        if (bw <= 0 || bh <= 0) {
            return;
        }
        int count = (bw * bh) / 55;
        int seed = x * 73856093 ^ y * 19349663;
        for (int i = 0; i < count; i++) {
            int h1 = mix(seed + i * 0x9E3779B1);
            int h2 = mix(h1);
            int h3 = mix(h2);
            int px = bx + Math.floorMod(h1, bw);
            int py = by + Math.floorMod(h2, bh);
            int pw = 2 + Math.floorMod(h3, 3);                 // 2..4
            int ph = 1 + (Math.floorMod(h3, 4) < 2 ? 1 : 0);    // 1..2
            int x2 = Math.min(px + pw, x + w - 1);
            int y2 = Math.min(py + ph, y + h - 2);
            if (x2 > px && y2 > py) {
                g.fill(px, py, x2, y2, (h3 & 1) == 0 ? TEXTURE_LIGHT : TEXTURE_DARK);
            }
        }
    }

    /** 32 位整数混淆（xorshift-multiply 变体），只用于散点取样。 */
    private static int mix(int x) {
        x = (x ^ (x >>> 16)) * 0x45D9F3B;
        x = (x ^ (x >>> 16)) * 0x45D9F3B;
        return x ^ (x >>> 16);
    }

    /**
     * 九宫格绘制：四角 1:1、上下边条横向拉伸、左右边条纵向拉伸、中心双向拉伸。
     *
     * <p>⚠️ {@code GuiGraphics.blit} 有两个重载，<b>参数顺序不一样</b>，混用会静默画错：
     * <ul>
     *   <li>9 参 = 1:1 裁切：{@code (atlas, x, y, uOffset, vOffset, width, height, texW, texH)}
     *   <li>11 参 = 拉伸：{@code (atlas, x, y, width, height, uOffset, vOffset, uWidth, vHeight, texW, texH)}
     * </ul>
     * 前者把 {@code width/height} 放在 {@code uOffset/vOffset} 之后、后者放在之前 ——
     * 写反了会得到「0 尺寸的块」而不是报错，画面直接崩坏。
     *
     * <p>边条拉伸的源区域是<b>中心区</b>（{@code MID×MID} 的横条/竖条），
     * 角块才是 {@code CAP×CAP}。用 CAP 当源尺寸会采到角块区域。
     */
    private static void nineSlice(GuiGraphics g, ResourceLocation tex,
                                  int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) {
            return;
        }
        int cap = CAP;
        int midW = w - 2 * cap;
        int midH = h - 2 * cap;
        // 上/下边条：源 (cap,0,MID,cap) / (cap,H-cap,MID,cap)，横向拉伸
        if (midW > 0) {
            g.blit(tex, x + cap, y, midW, cap, cap, 0, MID, cap, TEX_W, TEX_H);
            g.blit(tex, x + cap, y + h - cap, midW, cap, cap, TEX_H - cap, MID, cap, TEX_W, TEX_H);
        }
        // 左/右边条：源 (0,cap,CAP,MID) / (W-cap,cap,CAP,MID)，纵向拉伸
        if (midH > 0) {
            g.blit(tex, x, y + cap, cap, midH, 0, cap, cap, MID, TEX_W, TEX_H);
            g.blit(tex, x + w - cap, y + cap, cap, midH, TEX_W - cap, cap, cap, MID, TEX_W, TEX_H);
        }
        // 四角：1:1 裁切（9 参重载：uOffset/vOffset 在前）
        g.blit(tex, x, y, 0, 0, cap, cap, TEX_W, TEX_H);
        g.blit(tex, x + w - cap, y, TEX_W - cap, 0, cap, cap, TEX_W, TEX_H);
        g.blit(tex, x, y + h - cap, 0, TEX_H - cap, cap, cap, TEX_W, TEX_H);
        g.blit(tex, x + w - cap, y + h - cap, TEX_W - cap, TEX_H - cap, cap, cap, TEX_W, TEX_H);
        // 中心：纯色，双向拉伸
        if (midW > 0 && midH > 0) {
            g.blit(tex, x + cap, y + cap, midW, midH, cap, cap, MID, MID, TEX_W, TEX_H);
        }
    }
}
