package com.bitsson.gensokyou.registry;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;

/**
 * 品阶色单一色源：品阶 0-5 → 主色 RGB。
 *
 * <p>色值出处：{@code openspec/project.md} §5.4 品阶配色环（主色列）。
 * 名字染色、物品 tint、tooltip 品阶标注与未来 UI 强调色一律从此取色，
 * 禁止在别处散落硬编码品阶色值。
 */
public final class TierPalette {

    private static final int[] RGB = {
            0x9E9E9E, // 0 灰
            0x4CAF50, // 1 绿
            0x2196F3, // 2 蓝
            0xFFC107, // 3 金
            0xF44336, // 4 红
            0x9C27B0, // 5 紫
    };

    /** 品阶主色（ARGB，alpha 强制 0xFF——物品 tint 会提取 alpha 乘入顶点色，缺省即全透明）。 */
    public static int rgb(int tier) {
        return (tier < 0 || tier >= RGB.length) ? 0xFF000000 | RGB[0] : 0xFF000000 | RGB[tier];
    }

    /** 品阶主色的 TextColor（供 Component 染色；fromRgb 只取低 24 位）。 */
    public static TextColor textColor(int tier) {
        return TextColor.fromRgb(rgb(tier) & 0xFFFFFF);
    }

    /** 物品名染色开关：0 级（灰）不染，保持默认白字。 */
    public static boolean shouldTintName(int tier) {
        return tier > 0;
    }

    /** 统一染名入口：0 级原样返回，其余以品阶色着色。 */
    public static Component tintName(Component name, int tier) {
        return shouldTintName(tier)
                ? name.copy().withStyle(style -> style.withColor(textColor(tier)))
                : name;
    }

    private TierPalette() {
    }
}
