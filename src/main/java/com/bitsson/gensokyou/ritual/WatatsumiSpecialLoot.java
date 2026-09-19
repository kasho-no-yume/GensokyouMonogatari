package com.bitsson.gensokyou.ritual;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.Map;

/**
 * 绵津见神之藏：海洋特产池（独立于材质驱动权重表的一仪式一池）。
 *
 * <p>条目为相对权重；掷骰复用 {@link RitualLootTable} 的世界无关纯内核。数据来源见
 * {@link WatatsumiSpecialLootLoader}。
 */
public record WatatsumiSpecialLoot(List<RitualLootTable.Weighted> entries) {

    public static final WatatsumiSpecialLoot EMPTY = new WatatsumiSpecialLoot(List.of());

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** 池总权重（供约 % 折算与调试）。 */
    public double totalWeight() {
        return RitualLootTable.sumWeight(entries);
    }

    /** 按权重掷 {@code count} 次并聚合（保持首次出现序）。 */
    public Map<Item, Integer> rollMany(int count, RandomSource random) {
        return RitualLootTable.rollMany(entries, count, random);
    }
}
