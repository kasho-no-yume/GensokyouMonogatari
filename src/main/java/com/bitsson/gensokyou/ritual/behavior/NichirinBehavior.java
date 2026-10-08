package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.ritual.SpiritPowerAccess;

import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import com.bitsson.gensokyou.config.GensokyouConfig;

/**
 * 日轮天台（{@code gensokyou:nichirin_circle}）：白天段线性产能发电机。
 * 日出(dayTime 0) 0 → 正午(6000) 峰值 → 日落(12000) 0，其余时段 0。
 */
public final class NichirinBehavior extends DayCycleGeneratorBehavior {

    @Override
    public long capacity(int level, SpiritPowerAccess core) {
        return com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity.daycycleCapacity(level,
                com.bitsson.gensokyou.config.GensokyouConfig.NICHIRIN_BASE_CAPACITY.get());
    }

    @Override
    protected boolean solar() {
        return true;
    }

    @Override
    protected double baseRatePerSecond() {
        return GensokyouConfig.NICHIRIN_BASE_RATE_PER_SECOND.get();
    }

    @Override
    protected long outRatePerSecond() {
        return GensokyouConfig.NICHIRIN_OUT_RATE_PER_SECOND.get().longValue();
    }

    @Override
    protected String langPrefix() {
        return "nichirin";
    }

    @Override
    protected int accentColor() {
        return 0xFFFFB300;
    }
}
