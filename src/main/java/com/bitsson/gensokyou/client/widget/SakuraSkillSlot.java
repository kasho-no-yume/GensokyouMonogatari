package com.bitsson.gensokyou.client.widget;

import com.bitsson.gensokyou.client.SakuraPalette;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * 樱符 HUD 技能槽（24×24 符印格）。
 *
 * <pre>
 * ┌────────────┐
 * │    图标    │ ← 16×16 物品图标居中（偏移 4,4）
 * │            │
 * └────────────┘  ← 1px 樱粉外框
 *      G+1        ← 键位标签画在<b>槽下方</b>，绝不压图标
 * </pre>
 *
 * <p><b>为什么键位标签在下方而不是左上角</b>：旧版把 {@code "G+1"} 直接画在图标左上角，
 * 又把冷却秒数画在图标正中 —— 图标被两层信息盖住，认不出是哪张符卡。
 * 现在：图标区只留图标，秒数只在冷却时出现（压暗整格后米白高亮），键位在槽外。
 *
 * <p><b>冷却态</b>：整格压暗（底色变近黑 + 一层深色遮罩盖住图标），
 * 剩余秒数用米白画在正中 —— 压暗是为了让秒数跳出来。
 */
public final class SakuraSkillSlot {

    /** 槽边长。 */
    public static final int SIZE = 24;
    /** 图标偏移（(24 − 16) / 2）。 */
    private static final int ICON_OFF = 4;
    /** 键位标签行高（画在槽下方）。 */
    public static final int LABEL_H = 9;

    /**
     * 原版 {@code GuiGraphics#renderItem} 画物品时的 z（{@code pose.translate(x+8, y+8, 150)}）。
     *
     * <p>GUI 里 <b>z 越大越靠前</b>，故任何要盖在物品上的东西 MUST 用比它大的 z。
     */
    public static final int ITEM_Z = 150;

    /**
     * 盖在物品之上的元素用的 z（压暗遮罩 / 冷却秒数）。
     *
     * <p>⚠️ <b>HUD 层渲染时深度测试是开着的</b>：
     * {@code Gui#render} → {@code enableDepthTest()} → 渲染全部 GUI 层 → {@code disableDepthTest()}。
     * 故 z=0 的遮罩与文字会被 z=150 的物品<b>挡在后面</b> ——
     * 表现为「图标完全压不住、冷却秒数看不见」（实机反馈：符卡图标压在冷却数字上面）。
     * 先例：{@code CrystalStorageScreen} 的格内计数用 z=200 抬到物品之上。
     */
    public static final int OVERLAY_Z = 200;

    private SakuraSkillSlot() {
    }

    /**
     * 画一个技能槽。
     *
     * @param icon           已配装符卡的图标（未学/未配装传 {@link ItemStack#EMPTY}）
     * @param learned        该槽是否已学
     * @param cooldownSecond 剩余冷却秒数（0 = 不在冷却）
     * @param keyLabel       键位标签（如 {@code "G+1"}；过长时调用方自行传 null）
     */
    public static void render(GuiGraphics g, Font font, int x, int y,
                              ItemStack icon, boolean learned, int cooldownSecond,
                              @Nullable String keyLabel) {
        boolean cooling = cooldownSecond > 0;

        // 底：冷却时整格压暗
        g.fill(x, y, x + SIZE, y + SIZE, cooling ? 0xFF0E0A0D : 0xFF1A1218);

        if (learned && !icon.isEmpty()) {
            g.renderItem(icon, x + ICON_OFF, y + ICON_OFF);
            if (cooling) {
                // 压暗遮罩 MUST 抬到物品之上（z=150），否则被物品挡掉 → 图标根本不会变暗
                g.pose().pushPose();
                g.pose().translate(0.0F, 0.0F, OVERLAY_Z);
                g.fill(x + 1, y + 1, x + SIZE - 1, y + SIZE - 1, 0xB0100810);
                g.pose().popPose();
            }
        } else {
            // 空槽：一条樱粉暗调的短横，读作「此处空着」而不是「坏掉的图标」
            g.fill(x + 8, y + 11, x + SIZE - 8, y + 12, SakuraPalette.PINK_DARK);
        }

        // 樱粉外框
        g.fill(x, y, x + SIZE, y + 1, SakuraPalette.PINK);
        g.fill(x, y + SIZE - 1, x + SIZE, y + SIZE, SakuraPalette.PINK);
        g.fill(x, y, x + 1, y + SIZE, SakuraPalette.PINK);
        g.fill(x + SIZE - 1, y, x + SIZE, y + SIZE, SakuraPalette.PINK);

        if (cooling) {
            // 同上：秒数 MUST 抬到物品之上，否则被图标盖住
            g.pose().pushPose();
            g.pose().translate(0.0F, 0.0F, OVERLAY_Z);
            g.drawCenteredString(font, String.valueOf(cooldownSecond),
                    x + SIZE / 2, y + (SIZE - 8) / 2, SakuraPalette.CREAM);
            g.pose().popPose();
        }
        if (keyLabel != null && !keyLabel.isEmpty()) {
            g.drawCenteredString(font, keyLabel, x + SIZE / 2, y + SIZE + 1, SakuraPalette.CREAM);
        }
    }
}
