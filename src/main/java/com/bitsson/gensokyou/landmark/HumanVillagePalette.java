package com.bitsson.gensokyou.landmark;

/**
 * Runtime palette themes for Human Village machiya houses (design.md §3.2).
 * Role 0 (FIXED) always keeps the original RGB baked into the GLC3 file.
 */
public final class HumanVillagePalette {
    public static final int ROLE_FIXED = 0;
    public static final int ROLE_ROOF_MAIN = 1;
    public static final int ROLE_ROOF_RIDGE = 2;
    public static final int ROLE_WALL_PLASTER = 3;
    public static final int ROLE_WOOD_FRAME = 4;
    public static final int ROLE_WOOD_DARK = 5;
    public static final int ROLE_ACCENT_CURTAIN = 6;
    public static final int ROLE_FOUNDATION_STONE = 7;

    public static final int NO_THEME = -1;
    public static final int THEME_COUNT = 8;

    private static final int[][] THEMES = {
            // 0: 经典黛瓦白壁
            {0, 0x4B5E6F, 0x687D8F, 0xECE6D9, 0x8A5D3B, 0x4E3320, 0x3A5F82, 0x8E8980},
            // 1: 老铺黑瓦茶木
            {0, 0x363A40, 0x525861, 0xE5DCCB, 0x6F482D, 0x3C2516, 0x9E3D2E, 0x7F7B74},
            // 2: 清雅青瓦桧木
            {0, 0x5A737A, 0x79939A, 0xF2EEE4, 0xB58A5E, 0x7A5635, 0x4B775B, 0x969188},
            // 3: 匠人赤瓦深杉
            {0, 0x6B433B, 0x885C52, 0xE3D8C5, 0x7C4E32, 0x442818, 0xC48A3A, 0x867F76},
            // 4: 茶屋朱帘暖木
            {0, 0x485561, 0x637280, 0xEFE6D5, 0x9B6A43, 0x5B3A22, 0xC14436, 0x8D877E},
            // 5: 染坊蓝染灰瓦
            {0, 0x555E66, 0x737E88, 0xE8E6DF, 0x7A5A42, 0x463224, 0x2F4E7A, 0x8A8A86},
            // 6: 商家紫帘白藏
            {0, 0x3F4652, 0x5C6573, 0xF4F1EA, 0x734C32, 0x3E2719, 0x6D4875, 0x84817B},
            // 7: 郊野茅褐素木
            {0, 0x7D694D, 0x988363, 0xDFD3BC, 0x9C754F, 0x62462C, 0x7A6A46, 0x92897B},
    };

    private HumanVillagePalette() {}

    public static int resolveRgb(int baseArgb, int roleId, int themeId) {
        if (themeId < 0 || themeId >= THEME_COUNT || roleId <= ROLE_FIXED || roleId >= THEMES[themeId].length) {
            return baseArgb;
        }
        return 0xFF000000 | THEMES[themeId][roleId];
    }
}
