package com.bitsson.gensokyou.jei;

import com.bitsson.gensokyou.ritual.RitualLootTable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;

/**
 * JEI 献祭权重卡的数据载体（一仪式 × 一工具材质一张）。
 * 分段：base（commons+special）、nether 额外、end 额外、幻想乡信物带（低/中）；
 * 概率按各自适用池总权重折算。
 */
public record RitualLootCardWrapper(ResourceLocation patternId, String tier, TagKey<Item> toolTag,
                                    List<RitualLootTable.Weighted> base,
                                    List<RitualLootTable.Weighted> nether,
                                    List<RitualLootTable.Weighted> end,
                                    List<RitualLootTable.Weighted> gensokyouLow,
                                    List<RitualLootTable.Weighted> gensokyouHigh) {

    public static RitualLootCardWrapper of(RitualLootTable table, String tier) {
        RitualLootTable.TierTable tierTable = table.table(tier)
                .orElseGet(() -> table.tables().get(0));
        List<RitualLootTable.Weighted> base = RitualLootTable.buildPool(
                table.commons(), table.commonsTotal(), tierTable, false, false);
        return new RitualLootCardWrapper(table.patternId(), tier, table.toolTag(),
                List.copyOf(base),
                positive(tierTable.nether()),
                positive(tierTable.end()),
                positive(table.gensokyouLow()),
                positive(table.gensokyouHigh()));
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

    public double gensokyouLowTotal() {
        return baseTotal() + RitualLootTable.sumWeight(gensokyouLow);
    }

    public double gensokyouHighTotal() {
        return baseTotal() + RitualLootTable.sumWeight(gensokyouHigh);
    }

    /** 全部产物条目（base → nether → end → 信物低带 → 信物中带），顺序即展示顺序。 */
    public List<RitualLootTable.Weighted> allEntries() {
        List<RitualLootTable.Weighted> out = new ArrayList<>(base);
        out.addAll(nether);
        out.addAll(end);
        out.addAll(gensokyouLow);
        out.addAll(gensokyouHigh);
        return out;
    }

    /** 条目索引 → 概率分母（按所属段）。信物带各自与 base 合并折算。 */
    public double denominatorFor(int index) {
        return switch (sectionFor(index)) {
            case 1 -> netherFullTotal();
            case 2 -> endFullTotal();
            case 3 -> gensokyouLowTotal();
            case 4 -> gensokyouHighTotal();
            default -> baseTotal();
        };
    }

    /** 条目索引所属段：0=base 1=nether 2=end 3=信物低带 4=信物中带。 */
    public int sectionFor(int index) {
        if (index < base.size()) {
            return 0;
        }
        if (index < base.size() + nether.size()) {
            return 1;
        }
        if (index < base.size() + nether.size() + end.size()) {
            return 2;
        }
        if (index < base.size() + nether.size() + end.size() + gensokyouLow.size()) {
            return 3;
        }
        return 4;
    }
}
