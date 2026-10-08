package com.bitsson.gensokyou.ritual;

/**
 * 灵力缓存容量的阶级缩放公式（base × mult^level）。
 *
 * <p>此前 {@code kagutsuchiCapacity}/{@code yumewatariCapacity}/{@code daycycleCapacity}
 * 三份逐字重复的 for 循环已统一到本类；数值语义保持不变（无饱和检查的版本与历史一致，
 * 有饱和检查的版本单独提供）。
 */
public final class SpiritCapacityScaling {

    private SpiritCapacityScaling() {
    }

    /** base × mult^level，不做溢出保护（与历史 kagutsuchi/yumewatari/daycycle/shujou 一致）。*/
    public static long scaled(long base, long mult, int level) {
        long value = base;
        for (int i = 0; i < level; i++) {
            value *= mult;
        }
        return value;
    }

    /** base × mult^level，溢出时饱和到 {@link Long#MAX_VALUE}（与历史 scaledWujinzang 一致）。*/
    public static long scaledSaturating(long base, long mult, int level) {
        long value = base;
        for (int i = 0; i < level; i++) {
            if (value > Long.MAX_VALUE / Math.max(1L, mult)) {
                return Long.MAX_VALUE;
            }
            value *= mult;
        }
        return value;
    }
}
