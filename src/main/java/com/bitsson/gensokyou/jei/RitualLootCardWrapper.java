package com.bitsson.gensokyou.jei;

import com.bitsson.gensokyou.ritual.RitualLootTable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;

/**
 * JEI 献祭权重卡的数据载体（一仪式 × 一工具材质一张）。
 * 三段分离：base（commons+special）、nether 额外、end 额外；概率按各自适用池总权重折算。
 */
public record RitualLootCardWrapper(ResourceLocation patternId, String tier, TagKey<Item> toolTag,
                                    List<RitualLootTable.Weighted> base,
                                    List<RitualLootTable.Weighted> nether,
                                    List<RitualLootTable.Weighted> end) {

    public static RitualLootCardWrapper of(RitualLootTable table, String tier) {
        RitualLootTable.TierTable tierTable = table.table(tier)
                .orElseGet(() -> table.tables().get(0));
        List<RitualLootTable.Weighted> base = RitualLootTable.buildPool(
                table.commons(), table.commonsTotal(), tierTable, false, false);
        return new RitualLootCardWrapper(table.patternId(), tier, table.toolTag(),
                List.copyOf(base),
                positive(tierTable.nether()),
                positive(tierTable.end()));
    }

    private static List<RitualLootTable.Weighted> positive(List<RitualLootTable.Weighted> source) {
        List<RitualLootTable.Weighted> out = new ArrayList<>();
        for (RitualLootTable.Weighted w : source) {
            if (w.weight() > 0.0D) {
                out.add(w);
            }
        }
        return List.copyOf(out);
    }

    public double baseTotal() {
        return RitualLootTable.sumWeight(base);
    }

    public double netherFullTotal() {
        return baseTotal() + RitualLootTable.sumWeight(nether);
    }

    public double endFullTotal() {
        return netherFullTotal() + RitualLootTable.sumWeight(end);
    }

    /** 全部产物条目（base → nether → end），顺序即展示顺序。 */
    public List<RitualLootTable.Weighted> allEntries() {
        List<RitualLootTable.Weighted> out = new ArrayList<>(base);
        out.addAll(nether);
        out.addAll(end);
        return out;
    }

    /** 条目索引 → 概率分母（按所属段）。 */
    public double denominatorFor(int index) {
        if (index < base.size()) {
            return baseTotal();
        }
        if (index < base.size() + nether.size()) {
            return netherFullTotal();
        }
        return endFullTotal();
    }

    /** 条目索引所属段：0=base 1=nether 2=end。 */
    public int sectionFor(int index) {
        if (index < base.size()) {
            return 0;
        }
        if (index < base.size() + nether.size()) {
            return 1;
        }
        return 2;
    }
}
