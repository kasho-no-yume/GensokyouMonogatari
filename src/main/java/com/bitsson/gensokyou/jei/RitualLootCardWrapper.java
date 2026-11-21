package com.bitsson.gensokyou.jei;

import com.bitsson.gensokyou.ritual.RitualLootTable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;

/**
 * 献祭权重卡的数据载体（一仪式 × 一工具材质一张）。
 * 普通仪式使用一组分段池；声明 output channel 的仪式为每个保底配额独立显示概率。
 */
public record RitualLootCardWrapper(ResourceLocation patternId, String tier, TagKey<Item> toolTag,
                                    List<RitualLootTable.Weighted> base,
                                    List<RitualLootTable.Weighted> nether,
                                    List<RitualLootTable.Weighted> end,
                                    List<RitualLootTable.Weighted> gensokyouLow,
                                    List<RitualLootTable.Weighted> gensokyouHigh,
                                    List<ChannelPools> outputChannels) {

    public record ChannelPools(String id, List<RitualLootTable.Weighted> base,
                               List<RitualLootTable.Weighted> nether,
                               List<RitualLootTable.Weighted> end,
                               List<RitualLootTable.Weighted> gensokyouLow,
                               List<RitualLootTable.Weighted> gensokyouHigh) {
        public List<RitualLootTable.Weighted> section(int index) {
            return switch (index) {
                case 1 -> nether;
                case 2 -> end;
                case 3 -> gensokyouLow;
                case 4 -> gensokyouHigh;
                default -> base;
            };
        }
    }

    public static RitualLootCardWrapper of(RitualLootTable table, String tier) {
        if (!table.outputChannels().isEmpty()) {
            List<ChannelPools> channels = new ArrayList<>();
            for (RitualLootTable.OutputChannel channel : table.outputChannels()) {
                RitualLootTable.TierTable tierTable = channel.table(tier)
                        .orElseGet(() -> channel.tables().get(0));
                List<RitualLootTable.Weighted> base = RitualLootTable.buildPool(
                        channel, tier, false, false, false, false);
                channels.add(new ChannelPools(channel.id(), List.copyOf(base),
                        positive(tierTable.nether()), positive(tierTable.end()),
                        positive(channel.gensokyouLow()), positive(channel.gensokyouHigh())));
            }
            return new RitualLootCardWrapper(table.patternId(), tier, table.toolTag(),
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.copyOf(channels));
        }

        RitualLootTable.TierTable tierTable = table.table(tier)
                .orElseGet(() -> table.tables().get(0));
        List<RitualLootTable.Weighted> base = RitualLootTable.buildPool(
                table.commons(), table.commonsTotal(), tierTable, false, false);
        return new RitualLootCardWrapper(table.patternId(), tier, table.toolTag(),
                List.copyOf(base), positive(tierTable.nether()), positive(tierTable.end()),
                positive(table.gensokyouLow()), positive(table.gensokyouHigh()), List.of());
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

    public boolean hasOutputChannels() {
        return !outputChannels.isEmpty();
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

    /** 全部产物条目；通道仪式按 channel 依次分段。 */
    public List<RitualLootTable.Weighted> allEntries() {
        List<RitualLootTable.Weighted> out = new ArrayList<>();
        if (hasOutputChannels()) {
            for (ChannelPools channel : outputChannels) {
                for (int section = 0; section <= 4; section++) {
                    out.addAll(channel.section(section));
                }
            }
            return out;
        }
        out.addAll(base);
        out.addAll(nether);
        out.addAll(end);
        out.addAll(gensokyouLow);
        out.addAll(gensokyouHigh);
        return out;
    }

    /** 条目索引 → 概率分母；每个输出通道以自身池权重归一。 */
    public double denominatorFor(int index) {
        int section = sectionFor(index);
        if (hasOutputChannels()) {
            int channelIndex = section / 5;
            int localSection = section % 5;
            ChannelPools channel = outputChannels.get(channelIndex);
            double baseTotal = RitualLootTable.sumWeight(channel.base());
            return switch (localSection) {
                case 1 -> baseTotal + RitualLootTable.sumWeight(channel.nether());
                case 2 -> baseTotal + RitualLootTable.sumWeight(channel.nether())
                        + RitualLootTable.sumWeight(channel.end());
                case 3 -> baseTotal + RitualLootTable.sumWeight(channel.gensokyouLow());
                case 4 -> baseTotal + RitualLootTable.sumWeight(channel.gensokyouHigh());
                default -> baseTotal;
            };
        }
        return switch (section) {
            case 1 -> netherFullTotal();
            case 2 -> endFullTotal();
            case 3 -> gensokyouLowTotal();
            case 4 -> gensokyouHighTotal();
            default -> baseTotal();
        };
    }

    /** 条目所属段：普通表为 0..4；通道表为 channelIndex × 5 + 0..4。 */
    public int sectionFor(int index) {
        int remaining = index;
        int channelIndex = 0;
        if (hasOutputChannels()) {
            for (ChannelPools channel : outputChannels) {
                for (int section = 0; section <= 4; section++) {
                    int size = channel.section(section).size();
                    if (remaining < size) {
                        return channelIndex * 5 + section;
                    }
                    remaining -= size;
                }
                channelIndex++;
            }
            return Math.max(0, (outputChannels.size() - 1) * 5);
        }
        if (remaining < base.size()) return 0;
        remaining -= base.size();
        if (remaining < nether.size()) return 1;
        remaining -= nether.size();
        if (remaining < end.size()) return 2;
        remaining -= end.size();
        if (remaining < gensokyouLow.size()) return 3;
        return 4;
    }

    public String channelForSection(int section) {
        if (!hasOutputChannels()) {
            return "";
        }
        int channelIndex = Math.max(0, Math.min(outputChannels.size() - 1, section / 5));
        return outputChannels.get(channelIndex).id();
    }

    public int channelIndexForEntry(int index) {
        int remaining = index;
        for (int channelIndex = 0; channelIndex < outputChannels.size(); channelIndex++) {
            int size = channelEntryCount(channelIndex);
            if (remaining < size) {
                return channelIndex;
            }
            remaining -= size;
        }
        return Math.max(0, outputChannels.size() - 1);
    }

    public int channelEntryIndex(int index) {
        int remaining = index;
        for (int channelIndex = 0; channelIndex < outputChannels.size(); channelIndex++) {
            int size = channelEntryCount(channelIndex);
            if (remaining < size) {
                return remaining;
            }
            remaining -= size;
        }
        return 0;
    }

    private int channelEntryCount(int channelIndex) {
        ChannelPools channel = outputChannels.get(channelIndex);
        int count = 0;
        for (int section = 0; section <= 4; section++) {
            count += channel.section(section).size();
        }
        return count;
    }
}
