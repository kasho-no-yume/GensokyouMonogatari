package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.ritual.RitualScaling;

/**
 * 思兼神封（`gensokyou:omoikane_circle`）的数值口径。
 *
 * <p>三档刻意**不是** {@code base × 4^L} 几何序列：容量 1e6/1e7/1e8、受灵 1e5/1e6/5e6、
 * 单价 5e4/3e5/2e6（×10 外推不到 3 阶）。故 {@link com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity#getCapacity()}
 * MUST 显式分派到本类，漏分支会让三档全部静默回落 {@code DEFAULT_CORE_CAPACITY = 10000}——
 * 不报错，只是 1 阶连一条 2 阶词条都付不起（先例：SunakoScaling 的注释即为此而写）。
 *
 * <p>单批满耗上限（祭品台 4 台、理论最多 8 条有效词条）= 4e5 / 2.4e6 / 1.6e7，
 * 缓存 1e6 / 1e7 / 1e8：缓存刻意大于单批满耗，池子是给"连续打造 / 路由蓄能"准备的。
 *
 * <p>每个公式都有"显式传参"重载，供单测在**不加载 ModConfig** 的前提下断言（口径同
 * {@link SunakoScaling}）。
 *
 * <p><b>阶数偏移</b>：本仪式最低阶是 1（pattern {@code levels = 1/2/3}），而项目通用的
 * {@code RitualScaling.scale(base, mult, level)} 把 {@code base} 当作 0 阶值。故容量与受灵
 * 两处显式用 {@code level - 1}，使配置里的基值就是"1 阶值"。单价无此问题（显式三档表）。
 */
public final class OmoikaneScaling {

    /** 本仪式的阶数上限（pattern `levels` 为 1/2/3）。 */
    public static final int MAX_LEVEL = 3;

    private OmoikaneScaling() {
    }

    // ------------------------------------------------------------------ 缓存上限

    public static long capacityOf(int level) {
        return capacityOf(level,
                GensokyouConfig.OMOIKANE_BASE_CAPACITY.get(),
                GensokyouConfig.OMOIKANE_CAPACITY_MULTIPLIER.get(),
                GensokyouConfig.OMOIKANE_CAPACITY_OVERRIDE_LEVEL3.get());
    }

    /** 1 阶 = 基值，2 阶 = 基值 × 倍率，≥3 阶 = 显式覆盖值（非几何外推）。 */
    static long capacityOf(int level, long base, long multiplier, long overrideLevel3) {
        if (level >= MAX_LEVEL) {
            return Math.max(1L, overrideLevel3);
        }
        // 本仪式最低阶为 1（pattern levels = 1/2/3），故基值对应 1 阶而非 0 阶：
        // 与项目内 "scale(base, mult, level)" 的通用口径差一档，这里显式减 1。
        return RitualScaling.scale(base, multiplier, Math.max(0, level - 1));
    }

    // ------------------------------------------------------------------ 受灵汇

    public static long inRateOf(int level) {
        return inRateOf(level,
                GensokyouConfig.OMOIKANE_BASE_IN_RATE_PER_SECOND.get(),
                GensokyouConfig.OMOIKANE_IN_RATE_MULTIPLIER.get(),
                GensokyouConfig.OMOIKANE_IN_RATE_OVERRIDE_LEVEL3.get());
    }

    /** 1 阶 = 基值，2 阶 = 基值 × 倍率，≥3 阶 = 显式值（5e6 是 ×5 而非 ×10，非几何）。 */
    static long inRateOf(int level, long base, long multiplier, long overrideLevel3) {
        if (level >= MAX_LEVEL) {
            return Math.max(0L, overrideLevel3);
        }
        // 与 capacityOf 同口径：基值对应 1 阶
        return RitualScaling.scale(base, multiplier, Math.max(0, level - 1));
    }

    // ------------------------------------------------------------------ 单条词条耗灵

    public static long unitCostOf(int level) {
        return unitCostOf(level,
                GensokyouConfig.OMOIKANE_UNIT_COST_LEVEL1.get(),
                GensokyouConfig.OMOIKANE_UNIT_COST_LEVEL2.get(),
                GensokyouConfig.OMOIKANE_UNIT_COST_LEVEL3.get());
    }

    /** 三档显式表，阶数钳到 [1, 3]。 */
    static long unitCostOf(int level, long cost1, long cost2, long cost3) {
        return switch (clampLevel(level)) {
            case 1 -> cost1;
            case 2 -> cost2;
            default -> cost3;
        };
    }

    private static int clampLevel(int level) {
        return Math.max(1, Math.min(MAX_LEVEL, level));
    }

    // ------------------------------------------------------------------ 批次结算

    /** 本批总耗灵 = 单价 × 有效词条数（饱和乘）。 */
    public static long batchCost(int entries, int level) {
        return batchCost(entries, unitCostOf(level));
    }

    static long batchCost(int entries, long unitCost) {
        return RitualScaling.saturatingMultiply(unitCost, Math.max(0, entries));
    }

    /** 足额预检：缓存 ≥ 总价才可启动（刻意区别于少名的尽力产出）。 */
    public static boolean canAfford(long stored, int entries, int level) {
        return entries > 0 && stored >= batchCost(entries, level);
    }
}
