package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.ritual.RitualLootTable;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 幻想乡信物带抽取池组装（世界无关纯内核）：无信物 / 仅低阶带 / 仅中阶带 / 双带四种情形。
 * 对应 add-gensokyo-material-ladder 的「幻想乡信物条件池」要求。
 *
 * <p>只校验权重与条数（内核不触达 {@link net.minecraft.world.item.Item} 方法，故物品置 null）。
 */
class SacrificeGensokyouPoolTest {

    private static final double COMMONS_WEIGHT = 100.0;
    private static final double LOW_WEIGHT = 7.0;
    private static final double HIGH_WEIGHT = 11.0;

    private static List<RitualLootTable.Weighted> weights(double... values) {
        List<RitualLootTable.Weighted> list = new ArrayList<>();
        for (double v : values) {
            list.add(new RitualLootTable.Weighted(null, v));
        }
        return list;
    }

    private static RitualLootTable table() {
        RitualLootTable.TierTable tier = new RitualLootTable.TierTable(
                "wood", List.of(), List.of(), List.of());
        return new RitualLootTable(ResourceLocation.parse("gensokyou:test"), null, 100, 0, 0,
                weights(1.0), weights(LOW_WEIGHT), weights(HIGH_WEIGHT), List.of(tier));
    }

    private static List<Double> poolWeights(boolean low, boolean high) {
        List<Double> out = new ArrayList<>();
        for (RitualLootTable.Weighted w
                : ToolSacrificeBehavior.poolFor(table(), "wood", false, false, low, high)) {
            out.add(w.weight());
        }
        out.sort(Double::compareTo);
        return out;
    }

    @Test
    void noTokenYieldsBaseOnly() {
        assertEquals(List.of(COMMONS_WEIGHT), poolWeights(false, false));
    }

    @Test
    void guideBookUnlocksLowBand() {
        assertEquals(List.of(LOW_WEIGHT, COMMONS_WEIGHT), poolWeights(true, false));
    }

    @Test
    void sukimaFragmentUnlocksHighBand() {
        assertEquals(List.of(HIGH_WEIGHT, COMMONS_WEIGHT), poolWeights(false, true));
    }

    @Test
    void bothTokensUnlockBothBands() {
        assertEquals(List.of(LOW_WEIGHT, HIGH_WEIGHT, COMMONS_WEIGHT), poolWeights(true, true));
    }
}
