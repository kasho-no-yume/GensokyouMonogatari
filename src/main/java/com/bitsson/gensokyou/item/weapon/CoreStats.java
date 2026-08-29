package com.bitsson.gensokyou.item.weapon;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** 弹幕核定值（注册期写死，数值使用时读配置）。 */
public record CoreStats(Supplier<Float> coreBaseMult,
                        IntSupplier spiritCost,
                        IntSupplier attackRateTicks,
                        IntSupplier requiredTier) {
}
