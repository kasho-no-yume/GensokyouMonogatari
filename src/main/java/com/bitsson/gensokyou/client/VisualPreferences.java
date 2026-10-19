package com.bitsson.gensokyou.client;

import com.bitsson.gensokyou.config.GensokyouClientConfig;

import java.util.Locale;

/**
 * 客户端表现偏好门面（config-surface）：把 {@code fxQuality} / {@code fxDrawDistance}
 * 映射为特效密度与 LOD 距离。逐效果的几何/颜色是代码常量，只在这里整体收口。
 */
public final class VisualPreferences {

    /** 画质档。 */
    public enum Quality {
        OFF, LOW, MEDIUM, HIGH
    }

    private static String cachedRaw;
    private static Quality cachedQuality = Quality.HIGH;

    private VisualPreferences() {
    }

    public static Quality quality() {
        String raw = GensokyouClientConfig.FX_QUALITY.get();
        if (!java.util.Objects.equals(raw, cachedRaw)) {
            cachedRaw = raw;
            cachedQuality = parse(raw);
        }
        return cachedQuality;
    }

    private static Quality parse(String raw) {
        String v = raw == null ? "high" : raw.trim().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "off" -> Quality.OFF;
            case "low" -> Quality.LOW;
            case "medium" -> Quality.MEDIUM;
            default -> Quality.HIGH;
        };
    }

    /** 关闭时渲染器可直接短路。 */
    public static boolean enabled() {
        return quality() != Quality.OFF;
    }

    /** 密度倍率：off=0 / low=0.35 / medium=0.7 / high=1。 */
    public static double density() {
        return switch (quality()) {
            case OFF -> 0.0D;
            case LOW -> 0.35D;
            case MEDIUM -> 0.7D;
            case HIGH -> 1.0D;
        };
    }

    /** 按画质档缩放一个计数（可为 0）。 */
    public static int count(int base) {
        return (int) Math.max(0L, Math.round(base * density()));
    }

    /** 按画质档缩放一个计数，至少保留 1（用于不可为零的环/节点数）。 */
    public static int countMin1(int base) {
        return Math.max(1, (int) Math.round(base * density()));
    }

    /** 低 LOD 距离（格）。 */
    public static double lodDistance() {
        return GensokyouClientConfig.FX_DRAW_DISTANCE.get();
    }
}
