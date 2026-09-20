package com.bitsson.gensokyou.entity;

import com.bitsson.gensokyou.config.GensokyouConfig;
import net.minecraft.util.RandomSource;

/**
 * 小妖精的攻击变体。每只在刷新时按权重固定一种（NBT 持久化、无外观暗示）。
 */
public enum FairyVariant {
    /** 单发：周期性发射一枚弹幕。 */
    SINGLE,
    /** 弹幕网：周期性发射 3×3 角度网格。 */
    NET,
    /** 激光：周期性发射一发带预警的激光。 */
    LASER;

    /**
     * 按配置权重随机 roll 一种变体（仅服务端调用）。
     */
    public static FairyVariant roll(RandomSource random) {
        int single = Math.max(0, GensokyouConfig.FAIRY_VARIANT_SINGLE_WEIGHT.get());
        int net = Math.max(0, GensokyouConfig.FAIRY_VARIANT_NET_WEIGHT.get());
        int laser = Math.max(0, GensokyouConfig.FAIRY_VARIANT_LASER_WEIGHT.get());
        int total = single + net + laser;
        if (total <= 0) {
            return SINGLE;
        }
        int r = random.nextInt(total);
        if (r < single) {
            return SINGLE;
        }
        r -= single;
        if (r < net) {
            return NET;
        }
        return LASER;
    }
}
