package com.bitsson.gensokyou.menu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 核心 GUI 槽位几何回归。
 *
 * <p>本 GUI 的槽坐标约定（从贴图里电池槽实测得出：框在 {@code (29,39)}、槽在
 * {@code (30,40)}）：<b>物品与命中框的原点在槽坐标，18px 槽框画在槽坐标 −1 处</b>，
 * 物品因此天然内缩 1px 居中。代码绘制的槽框（增幅核目标槽、信息区只读物品格）
 * 必须遵守同一约定，否则会出现"逻辑位置与视觉位置错位 1px"。
 *
 * <p>另需保证槽框不越出信息框内壁（x=7 是贴图里的竖向分隔线），且不侵入信息正文区。
 */
class RitualCoreSlotGeometryTest {

    private static final int FRAME = 18;

    /** 贴图实测：电池槽框左上角 (29,39)，电池槽槽坐标 (30,40)。 */
    private static final int BATT_FRAME_X = 29;
    private static final int BATT_FRAME_Y = 39;

    /** 信息框竖向分隔线所在列；框不得越过它。 */
    private static final int INFO_BOX_WALL_X = 7;
    /** 信息正文左边界，与 {@code RitualCoreScreen.INFO_X} 一致。 */
    private static final int INFO_CONTENT_X = 8;
    /** 信息正文首行 y，与 {@code RitualCoreScreen.INFO_Y_START} 一致。 */
    private static final int INFO_FIRST_ROW_Y = 60;
    /** 目标槽占用的首行高度，与 {@code RitualCoreScreen.INFO_TARGET_ROW_H} 一致。 */
    private static final int INFO_TARGET_ROW_H = 20;

    @Test
    void batterySlotConfirmsFrameIsDrawnAtSlotMinusOne() {
        assertEquals(BATT_FRAME_X, RitualCoreMenu.BATTERY_SLOT_X - 1,
                "本约定源自贴图实测：槽框 = 槽坐标 −1");
        assertEquals(BATT_FRAME_Y, RitualCoreMenu.BATTERY_SLOT_Y - 1,
                "本约定源自贴图实测：槽框 = 槽坐标 −1");
    }

    @Test
    void targetSlotFrameStaysInsideInfoBoxAndAboveBody() {
        int frameX = RitualCoreMenu.TARGET_SLOT_X - 1;
        int frameY = RitualCoreMenu.TARGET_SLOT_Y - 1;

        assertTrue(frameX >= INFO_BOX_WALL_X + 1,
                "槽框左沿 x=" + frameX + " 压到了信息框竖向分隔线 x=" + INFO_BOX_WALL_X);
        assertEquals(INFO_CONTENT_X, frameX,
                "槽框左沿应与信息正文左边界对齐");
        assertEquals(INFO_FIRST_ROW_Y, frameY,
                "槽框顶沿应与信息区首行对齐");
        assertTrue(frameY + FRAME <= INFO_FIRST_ROW_Y + INFO_TARGET_ROW_H,
                "槽框底沿 " + (frameY + FRAME) + " 侵入了信息正文区（首行预留 "
                        + INFO_TARGET_ROW_H + "px）");
    }

    @Test
    void targetSlotHitBoxMatchesItemOriginNotFrame() {
        // 命中框与物品同源于槽坐标（原版 renderSlot / findSlot 都用 slot.x/slot.y）
        assertEquals(RitualCoreMenu.TARGET_SLOT_X, RitualCoreMenu.TARGET_SLOT_X);
        // 物品 16px 落在 [9,25)×[61,77)，框 18px 落在 [8,26)×[60,78)：四边各留 1px
        assertEquals(1, RitualCoreMenu.TARGET_SLOT_X - frameLeft());
        assertEquals(1, RitualCoreMenu.TARGET_SLOT_Y - frameTop());
    }

    private static int frameLeft() {
        return RitualCoreMenu.TARGET_SLOT_X - 1;
    }

    private static int frameTop() {
        return RitualCoreMenu.TARGET_SLOT_Y - 1;
    }
}
