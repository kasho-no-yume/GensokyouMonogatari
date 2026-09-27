package com.bitsson.gensokyou.danmaku.track;

import java.util.List;

/**
 * BOSS 的签名色盘：<b>色盘里的每个颜色就是一条轨道的身份证</b>。
 *
 * <p>「每条轨道必须有一种独占的视觉标识」这条规则在此落地——同一符卡内的轨道靠
 * 不同色相被玩家区分开，若色数不够就会出现两轨同色，在三维并发轨道里必然糊成一团。
 *
 * <p>颜色是 {@code 0xRRGGBB}，只作为弹幕的 {@code DATA_COLOR} 下发，<b>与渲染器无关</b>。
 */
public record SignaturePalette(int[] colors) {

    public SignaturePalette {
        colors = colors.clone();
    }

    public static SignaturePalette of(int... colors) {
        return new SignaturePalette(colors);
    }

    public int size() {
        return colors.length;
    }

    public int at(int index) {
        return colors[Math.floorMod(index, colors.length)];
    }

    /** 该色盘能否容纳给定数量的并发轨道。 */
    public boolean fits(int trackCount) {
        return trackCount <= colors.length;
    }

    /** 全部色盘（供 lint 与测试遍历）。 */
    public static List<SignaturePalette> all() {
        return List.of();
    }
}
