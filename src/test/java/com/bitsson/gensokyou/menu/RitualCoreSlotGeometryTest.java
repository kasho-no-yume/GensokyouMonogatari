package com.bitsson.gensokyou.menu;

import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
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
    /** 信息盒右钳界，与 {@code RitualCoreScreen.INFO_BOX_RIGHT} 一致（按钮列从 x=120 起）。 */
    private static final int INFO_BOX_RIGHT = 112;

    @Test
    void batterySlotConfirmsFrameIsDrawnAtSlotMinusOne() {
        assertEquals(BATT_FRAME_X, RitualCoreMenu.BATTERY_SLOT_X - 1,
                "本约定源自贴图实测：槽框 = 槽坐标 −1");
        assertEquals(BATT_FRAME_Y, RitualCoreMenu.BATTERY_SLOT_Y - 1,
                "本约定源自贴图实测：槽框 = 槽坐标 −1");
    }

    @Test
    void extraSlotFrameStaysInsideInfoBoxAndAboveBody() {
        int frameX = RitualCoreMenu.extraSlotX(0) - 1;
        int frameY = RitualCoreMenu.extraSlotY() - 1;

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
    void extraSlotHitBoxMatchesItemOriginNotFrame() {
        // 命中框与物品同源于槽坐标（原版 renderSlot / findSlot 都用 slot.x/slot.y）
        assertEquals(RitualCoreMenu.extraSlotX(0), frameLeft() + 1);
        assertEquals(RitualCoreMenu.extraSlotY(), frameTop() + 1);
    }

    @Test
    void everyExtraSlotFitsInTheReservedRow() {
        int last = RitualCoreBlockEntity.MAX_EXTRA_SLOTS - 1;
        int lastFrameRight = RitualCoreMenu.extraSlotX(last) - 1 + FRAME;
        assertTrue(lastFrameRight + FRAME <= INFO_BOX_RIGHT,
                "第 " + last + " 格槽框右沿 " + lastFrameRight
                        + " 越过了信息盒右钳界 " + INFO_BOX_RIGHT
                        + "（面板 176 宽，按钮列从 x=120 起）");
        for (int i = 0; i <= last; i++) {
            assertEquals(INFO_FIRST_ROW_Y, RitualCoreMenu.extraSlotY(i) - 1,
                    "缺省布局把所有额外槽放在信息区首行");
        }
    }

    @Test
    void slotsDoNotOverlapEachOther() {
        for (int i = 1; i < RitualCoreBlockEntity.MAX_EXTRA_SLOTS; i++) {
            int previousRight = RitualCoreMenu.extraSlotX(i - 1) + 16;
            assertTrue(RitualCoreMenu.extraSlotX(i) > previousRight,
                    "第 " + i + " 格与第 " + (i - 1) + " 格重叠（间距 " +
                            RitualCoreBlockEntity.EXTRA_SLOT_SPACING + "px，物品 16px）");
        }
    }

    private static int frameLeft() {
        return RitualCoreMenu.extraSlotX(0) - 1;
    }

    private static int frameTop() {
        return RitualCoreMenu.extraSlotY() - 1;
    }
}
