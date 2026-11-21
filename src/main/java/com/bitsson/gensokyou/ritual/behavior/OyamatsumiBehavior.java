package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.balance.BalanceNumbers;
import com.bitsson.gensokyou.config.GensokyouServerConfig;
import com.bitsson.gensokyou.ritual.RitualMatch;
import com.bitsson.gensokyou.ritual.RitualLootTable;
import net.minecraft.world.item.Item;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 大山祇神之座（镐）：工具献祭行为。
 * 数据表 {@code data/gensokyou/ritual_loot/oyamatsumi_circle.json}。
 */
public class OyamatsumiBehavior extends ToolSacrificeBehavior {

    private static final String CHANNEL_STONE = "stone";
    private static final String CHANNEL_MINERAL = "mineral";

    public static int stoneCount(int level) {
        return scaledCount(BalanceNumbers.OYAMATSUMI_STONE_BASE_COUNT,
                GensokyouServerConfig.OYAMATSUMI_STONE_COUNT_MULT.get(), level);
    }

    public static int mineralCount(int level) {
        return scaledCount(BalanceNumbers.OYAMATSUMI_MINERAL_BASE_COUNT,
                GensokyouServerConfig.OYAMATSUMI_MINERAL_COUNT_MULT.get(), level);
    }

    public static int totalCount(int level) {
        return (int) Math.min(Integer.MAX_VALUE, (long) stoneCount(level) + mineralCount(level));
    }

    @Override
    protected Map<Item, Integer> rollDrops(RitualLootTable table, String tier,
                                           boolean nether, boolean end,
                                           boolean gensokyouLow, boolean gensokyouHigh,
                                           int level, net.minecraft.util.RandomSource random) {
        RitualLootTable.OutputChannel stone = channel(table, CHANNEL_STONE);
        RitualLootTable.OutputChannel mineral = channel(table, CHANNEL_MINERAL);
        if (stone == null || mineral == null) {
            return super.rollDrops(table, tier, nether, end,
                    gensokyouLow, gensokyouHigh, level, random);
        }

        Map<Item, Integer> aggregate = new LinkedHashMap<>();
        merge(aggregate, RitualLootTable.rollMany(
                RitualLootTable.buildPool(stone, tier, nether, end, gensokyouLow, gensokyouHigh),
                stoneCount(level), random));
        merge(aggregate, RitualLootTable.rollMany(
                RitualLootTable.buildPool(mineral, tier, nether, end, gensokyouLow, gensokyouHigh),
                mineralCount(level), random));
        return aggregate;
    }

    private static RitualLootTable.OutputChannel channel(RitualLootTable table, String id) {
        return table.outputChannels().stream().filter(c -> id.equals(c.id())).findFirst().orElse(null);
    }

    private static void merge(Map<Item, Integer> target, Map<Item, Integer> source) {
        source.forEach((item, count) -> target.merge(item, count, Integer::sum));
    }

    @Override
    protected String langPrefix() {
        return "oyamatsumi";
    }

    @Override
    protected int accentColor() {
        return 0xFF9E9E9E;
    }
}
