package com.bitsson.gensokyou.ritual;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 绵津见神之藏：海洋特产池（独立于材质驱动权重表的一仪式一池）。
 *
 * <p>条目为相对权重；掷骰复用 {@link RitualLootTable} 的世界无关纯内核。数据来源见
 * {@link WatatsumiSpecialLootLoader}。
 *
 * <p>幻想乡海产带（{@code gensokyouLow} / {@code gensokyouHigh}）由信物解锁，并入基础条目后一起掷骰。
 */
public record WatatsumiSpecialLoot(List<RitualLootTable.Weighted> entries,
                                   List<RitualLootTable.Weighted> gensokyouLow,
                                   List<RitualLootTable.Weighted> gensokyouHigh) {

    public static final WatatsumiSpecialLoot EMPTY =
            new WatatsumiSpecialLoot(List.of(), List.of(), List.of());

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** 并入已解锁的幻想乡信物带后的实际条目。 */
    public List<RitualLootTable.Weighted> entriesWith(boolean gensokyouLow, boolean gensokyouHigh) {
        if (!gensokyouLow && !gensokyouHigh) {
            return entries;
        }
        List<RitualLootTable.Weighted> out = new ArrayList<>(entries);
        if (gensokyouLow) {
            out.addAll(this.gensokyouLow);
        }
        if (gensokyouHigh) {
            out.addAll(this.gensokyouHigh);
        }
        return out;
    }

    /** 池总权重（供约 % 折算与调试）。 */
    public double totalWeight() {
        return RitualLootTable.sumWeight(entries);
    }

    /** 按权重掷 {@code count} 次并聚合（保持首次出现序）。 */
    public Map<Item, Integer> rollMany(int count, RandomSource random) {
        return RitualLootTable.rollMany(entries, count, random);
    }

    /** 按信物带解锁后掷 {@code count} 次并聚合。 */
    public Map<Item, Integer> rollMany(int count, boolean gensokyouLow, boolean gensokyouHigh,
                                       RandomSource random) {
        return RitualLootTable.rollMany(entriesWith(gensokyouLow, gensokyouHigh), count, random);
    }
}
