package com.bitsson.gensokyou.client.widget;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 技能槽（符印格）的绘制契约回归。
 *
 * <p>最重要的一条是 <b>z-order</b>：HUD 层渲染时<b>深度测试是开着的</b>
 * （{@code Gui#render}：{@code enableDepthTest()} → 渲染全部 GUI 层 → {@code disableDepthTest()}），
 * 而 GUI 里 <b>z 越大越靠前</b>，物品固定画在 z=150。
 * 故任何要盖在物品上的东西（压暗遮罩、冷却秒数）MUST 用更大的 z，
 * 否则会被物品<b>挡在后面</b> —— 实机表现是「符卡图标压在冷却数字上面」。
 */
class SakuraSkillSlotTest {

    @Test
    void overlayRendersInFrontOfItems() {
        assertTrue(SakuraSkillSlot.OVERLAY_Z > SakuraSkillSlot.ITEM_Z,
                "压暗遮罩与冷却秒数的 z（" + SakuraSkillSlot.OVERLAY_Z
                        + "）MUST 大于物品的 z（" + SakuraSkillSlot.ITEM_Z + "），"
                        + "否则会被物品挡在后面（深度测试在 HUD 层是开着的）");
    }

    @Test
    void iconIsCenteredInTheSlot() {
        int icon = 16;
        int left = (SakuraSkillSlot.SIZE - icon) / 2;
        int top = (SakuraSkillSlot.SIZE - icon) / 2;
        assertEquals(left, 4);
        assertEquals(top, 4);
        // 图标 MUST 完全落在槽内
        assertTrue(left + icon <= SakuraSkillSlot.SIZE);
    }

    @Test
    void keyLabelRowSitsBelowTheSlot() {
        // 键位标签画在槽**下方**，MUST NOT 压住图标（旧版把 "G+N" 画在图标左上角）
        assertTrue(SakuraSkillSlot.LABEL_H >= 8,
                "键位标签行 MUST 装得下一个 8px 高的字形");
        assertEquals(24, SakuraSkillSlot.SIZE);
    }
}
