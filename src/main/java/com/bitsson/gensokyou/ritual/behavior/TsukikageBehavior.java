package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.ritual.SpiritPowerAccess;

import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import com.bitsson.gensokyou.config.GensokyouConfig;

/**
 * 月影水镜（{@code gensokyou:tsukikage_circle}）：夜晚段线性产能发电机，时段与日轮相反。
 * 日落(dayTime 12000) 0 → 午夜(18000) 峰值 → 日出(24000/0) 0，其余时段 0。
 */
public final class TsukikageBehavior extends DayCycleGeneratorBehavior {

    @Override
    public long capacity(int level, SpiritPowerAccess core) {
        return com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity.daycycleCapacity(level,
                com.bitsson.gensokyou.config.GensokyouConfig.TSUKIKAGE_BASE_CAPACITY.get());
    }

    @Override
    protected boolean solar() {
        return false;
    }

    @Override
    protected double baseRatePerSecond() {
        return GensokyouConfig.TSUKIKAGE_BASE_RATE_PER_SECOND.get();
    }

    @Override
    protected long outRatePerSecond() {
        return GensokyouConfig.TSUKIKAGE_OUT_RATE_PER_SECOND.get().longValue();
    }

    @Override
    protected String langPrefix() {
        return "tsukikage";
    }

    @Override
    protected int accentColor() {
        return 0xFF64B5F6;
    }
}
