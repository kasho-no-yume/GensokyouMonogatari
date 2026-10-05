package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.support.MinecraftTestBootstrap;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 少名的分阶数值口径回归。
 *
 * <p>这些数字与 {@code sunako_circle} 的祭品台数（4/4/8，四重展开后）是一组配套平衡：
 * 三档的"满批耗灵"都低于缓存，故祭品台数才是每批产量的真实上限，灵力只在缓存见底时成为瓶颈。
 *
 * <p>公式走"显式传参"重载断言（ModConfig 在纯 JUnit 下未加载，{@code .get()} 会抛
 * {@code Cannot get config value before config is loaded}）；默认值另用 {@code getDefault()} 锁定。
 */
class SunakoScalingTest {

    private static final long CAP_BASE = 200_000L;
    private static final long CAP_MULT = 5L;
    private static final long CAP_L3 = 8_000_000L;
    private static final long IN_BASE = 10_000L;
    private static final long IN_MULT = 5L;
    private static final long IN_L3 = 200_000L;
    private static final long COST1 = 40_000L;
    private static final long COST2 = 150_000L;
    private static final long COST3 = 500_000L;

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.ensureStarted();
    }

    @Test
    void capacityIsHandTunedNotGeometric() {
        assertEquals(200_000L, SunakoScaling.capacityOf(1, CAP_BASE, CAP_MULT, CAP_L3));
        assertEquals(1_000_000L, SunakoScaling.capacityOf(2, CAP_BASE, CAP_MULT, CAP_L3));
        assertEquals(8_000_000L, SunakoScaling.capacityOf(3, CAP_BASE, CAP_MULT, CAP_L3),
                "3 阶必须走显式覆盖，不得由基值 × 倍率外推（否则只剩 2.5e6）");
        assertEquals(8_000_000L, SunakoScaling.capacityOf(5, CAP_BASE, CAP_MULT, CAP_L3),
                "超阶时钳到最高阶");
    }

    @Test
    void capacityNeverFallsBelowOne() {
        assertEquals(1L, SunakoScaling.capacityOf(3, CAP_BASE, CAP_MULT, 0L));
    }

    @Test
    void inRateIsGeometricUpToTwoThenHandTuned() {
        assertEquals(10_000L, SunakoScaling.inRateOf(1, IN_BASE, IN_MULT, IN_L3));
        assertEquals(50_000L, SunakoScaling.inRateOf(2, IN_BASE, IN_MULT, IN_L3));
        assertEquals(200_000L, SunakoScaling.inRateOf(3, IN_BASE, IN_MULT, IN_L3),
                "3 阶是 2 阶的 x4 而非 x5，必须走显式值（几何外推会得到 250000）");
    }

    @Test
    void unitCostIsExplicitPerTier() {
        assertEquals(COST1, SunakoScaling.unitCostOf(1, COST1, COST2, COST3));
        assertEquals(COST2, SunakoScaling.unitCostOf(2, COST1, COST2, COST3));
        assertEquals(COST3, SunakoScaling.unitCostOf(3, COST1, COST2, COST3));
    }

    @Test
    void unitCostClampsOutOfRangeLevels() {
        assertEquals(COST1, SunakoScaling.unitCostOf(0, COST1, COST2, COST3));
        assertEquals(COST3, SunakoScaling.unitCostOf(9, COST1, COST2, COST3));
    }

    @Test
    void pedestalsAreThePerBatchCeiling() {
        // 1 阶 4 台满产 4×4万=16万 < 容量 20万 → 台位先见底
        assertEquals(4, SunakoScaling.affordableBottles(4, 200_000L, COST1));
        // 3 阶 8 台满产 8×50万=400万 < 容量 800万 → 台位先见底
        assertEquals(8, SunakoScaling.affordableBottles(8, 8_000_000L, COST3));
    }

    @Test
    void partialBatchWhenSpiritRunsShort() {
        // 需求原文的例子：1 阶 4 瓶但只剩 5 万 → 只够 1 瓶
        assertEquals(1, SunakoScaling.affordableBottles(4, 50_000L, COST1));
        assertEquals(1, SunakoScaling.affordableBottles(4, 79_999L, COST1));
        assertEquals(2, SunakoScaling.affordableBottles(4, 80_000L, COST1));
    }

    @Test
    void noSpiritOrNoPedestalYieldsNothing() {
        assertEquals(0, SunakoScaling.affordableBottles(4, 39_999L, COST1));
        assertEquals(0, SunakoScaling.affordableBottles(0, 200_000L, COST1));
        assertEquals(0, SunakoScaling.affordableBottles(4, 0L, COST1));
        assertEquals(0, SunakoScaling.affordableBottles(-1, 200_000L, COST1));
    }

    @Test
    void batchCostIsSaturating() {
        assertEquals(160_000L, SunakoScaling.batchCost(4, COST1));
        assertEquals(600_000L, SunakoScaling.batchCost(4, COST2));
        assertEquals(4_000_000L, SunakoScaling.batchCost(8, COST3));
        assertEquals(0L, SunakoScaling.batchCost(0, COST1));
        assertEquals(1_073_741_823_500_000L, SunakoScaling.batchCost(Integer.MAX_VALUE, COST3),
                "int 台数 × int 单价仍在 long 内，不得误判为饱和");
        assertEquals(Long.MAX_VALUE, SunakoScaling.batchCost(3, Long.MAX_VALUE / 2L),
                "真的溢出时必须饱和，MUST NOT 回绕成负数");
    }

    @Test
    void defaultsMatchDesignDoc() {
        assertEquals(200_000, GensokyouConfig.SUNAKO_BASE_CAPACITY.getDefault());
        assertEquals(5, GensokyouConfig.SUNAKO_CAPACITY_MULTIPLIER.getDefault());
        assertEquals(8_000_000, GensokyouConfig.SUNAKO_CAPACITY_OVERRIDE_LEVEL3.getDefault());
        assertEquals(10_000, GensokyouConfig.SUNAKO_BASE_IN_RATE_PER_SECOND.getDefault());
        assertEquals(5, GensokyouConfig.SUNAKO_IN_RATE_MULTIPLIER.getDefault());
        assertEquals(200_000, GensokyouConfig.SUNAKO_IN_RATE_OVERRIDE_LEVEL3.getDefault());
        assertEquals(40_000, GensokyouConfig.SUNAKO_UNIT_COST_LEVEL1.getDefault());
        assertEquals(150_000, GensokyouConfig.SUNAKO_UNIT_COST_LEVEL2.getDefault());
        assertEquals(500_000, GensokyouConfig.SUNAKO_UNIT_COST_LEVEL3.getDefault());
    }

    @Test
    void durationMultipliersHaveNoZeroDivisor() {
        assertEquals(8.0D / 3.0D, GensokyouConfig.SUNAKO_LONG_DURATION_MULTIPLIER.getDefault(), 1.0E-9D);
        assertEquals(1.0D, GensokyouConfig.SUNAKO_STRONG_DURATION_MULTIPLIER.getDefault(), 1.0E-9D);
    }

    @Test
    void configFieldsAreDeclaredAsTypedValues() throws Exception {
        // 容量/单价为 IntValue、时长倍率为 DoubleValue —— 类型错配会让 defineInRange 静默退化
        assertEquals(ModConfigSpec.IntValue.class,
                GensokyouConfig.class.getField("SUNAKO_BASE_CAPACITY").getType());
        assertEquals(ModConfigSpec.IntValue.class,
                GensokyouConfig.class.getField("SUNAKO_CAPACITY_OVERRIDE_LEVEL3").getType());
        assertEquals(ModConfigSpec.DoubleValue.class,
                GensokyouConfig.class.getField("SUNAKO_LONG_DURATION_MULTIPLIER").getType());
    }
}