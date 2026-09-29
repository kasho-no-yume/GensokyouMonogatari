package com.bitsson.gensokyou.danmaku.visual;

import java.awt.Color;

/**
 * 弹幕的变色模式。
 *
 * <p>除 {@link #FIXED} 外的模式一律由<b>年龄</b>推导，<b>零同步</b>。
 * 依据是<b>纯视觉状态不需要双端一致</b>——只有服务端能造成伤害，客户端只负责画，
 * 故客户端自行推导颜色不会影响任何玩法判定。
 *
 * <p>但推导仍 MUST 用年龄而非 {@code tickCount}：客户端丢掉又重新拿到该实体时
 * tickCount 归零，变色相位会当场跳一下。调用方传 {@code entity.age()}。
 *
 * <p>饱和度与亮度沿用 {@code SphereDanmaku.randomColor()} 的 0.85 / 1.0，
 * 与既有弹幕保持同一套色彩语言。
 */
public enum DanmakuColorMode {

    /** 取弹幕自身的同步颜色。兼容既有色盘轨道——轨道视觉独占规则依赖色相维度。 */
    FIXED,

    /** 色相随时间循环（彩虹弹）。 */
    CYCLE_HUE,

    /** 亮度脉动（蓄力 / 预警）。 */
    PULSE;

    /** 弹幕随机取色所用的饱和度与亮度，全模式共用以保持色彩语言一致。 */
    private static final float SATURATION = 0.85F;
    private static final float VALUE_FULL = 1.0F;
    private static final float VALUE_LOW = 0.6F;

    /**
     * 按本模式解析某 tick 的显示颜色。
     *
     * @param baseColor 同步色（仅 {@link #FIXED} 使用）
     * @param tickCount 实体 tick 数
     * @param cycleTicks 变色周期
     * @return 0xRRGGBB
     */
    public int resolve(int baseColor, int tickCount, int cycleTicks) {
        if (this == FIXED || cycleTicks <= 0) {
            return baseColor;
        }
        double phase = (double) tickCount / cycleTicks;
        if (this == CYCLE_HUE) {
            return Color.HSBtoRGB((float) (phase - Math.floor(phase)), SATURATION, VALUE_FULL)
                    & 0xFFFFFF;
        }
        float value = (float) (VALUE_LOW
                + (VALUE_FULL - VALUE_LOW) * (0.5D + 0.5D * Math.sin(2.0D * Math.PI * phase)));
        float hue = ((baseColor >> 16) & 0xFF) / 255.0F;
        float sat = ((baseColor >> 8) & 0xFF) / 255.0F;
        return Color.HSBtoRGB(hue, sat, value) & 0xFFFFFF;
    }
}
