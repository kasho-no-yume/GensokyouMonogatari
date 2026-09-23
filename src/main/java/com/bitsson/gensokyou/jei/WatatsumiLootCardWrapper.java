package com.bitsson.gensokyou.jei;

import com.bitsson.gensokyou.ritual.RitualLootTable;
import com.bitsson.gensokyou.ritual.WatatsumiSpecialLootLoader;
import com.bitsson.gensokyou.ritual.behavior.WatatsumiBehavior;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * 绵津见 JEI 卡的数据载体（一等级一张，共 0/1/2 三张）。
 *
 * <p>钓鱼池按原版根表类别权重（fish 85 / junk 10 / treasure 5，L≥1 才有 treasure）静态折算为
 * 等效权重；海洋特产池取当前数据文件。两池各自独立，概率按所属池总权重折算。钓鱼池为原版静态
 * 数据（dedicated server 不要求同步原版掉落表）。幻想乡信物带（低/中）来自特产池，需信物解锁。
 */
public record WatatsumiLootCardWrapper(int level, int fishingRolls, int specialRolls,
                                       List<RitualLootTable.Weighted> fishing,
                                       List<RitualLootTable.Weighted> treasure,
                                       List<RitualLootTable.Weighted> special,
                                       List<RitualLootTable.Weighted> gensokyouLow,
                                       List<RitualLootTable.Weighted> gensokyouHigh) {

    // ---- 原版 gameplay/fishing/* 静态权重（luck=0，quality 无效果）----
    private static final List<RitualLootTable.Weighted> FISH = List.of(
            new RitualLootTable.Weighted(Items.COD, 60),
            new RitualLootTable.Weighted(Items.SALMON, 25),
            new RitualLootTable.Weighted(Items.PUFFERFISH, 13),
            new RitualLootTable.Weighted(Items.TROPICAL_FISH, 2));
    private static final List<RitualLootTable.Weighted> JUNK = List.of(
            new RitualLootTable.Weighted(Items.LILY_PAD, 17),
            new RitualLootTable.Weighted(Items.LEATHER_BOOTS, 10),
            new RitualLootTable.Weighted(Items.LEATHER, 10),
            new RitualLootTable.Weighted(Items.BONE, 10),
            new RitualLootTable.Weighted(Items.POTION, 10),
            new RitualLootTable.Weighted(Items.STRING, 5),
            new RitualLootTable.Weighted(Items.FISHING_ROD, 2),
            new RitualLootTable.Weighted(Items.BOWL, 10),
            new RitualLootTable.Weighted(Items.STICK, 5),
            new RitualLootTable.Weighted(Items.INK_SAC, 1),
            new RitualLootTable.Weighted(Items.TRIPWIRE_HOOK, 10),
            new RitualLootTable.Weighted(Items.ROTTEN_FLESH, 10),
            new RitualLootTable.Weighted(Items.BAMBOO, 10));
    private static final List<RitualLootTable.Weighted> TREASURE = List.of(
            new RitualLootTable.Weighted(Items.NAME_TAG, 1),
            new RitualLootTable.Weighted(Items.SADDLE, 1),
            new RitualLootTable.Weighted(Items.BOW, 1),
            new RitualLootTable.Weighted(Items.FISHING_ROD, 1),
            new RitualLootTable.Weighted(Items.BOOK, 1),
            new RitualLootTable.Weighted(Items.NAUTILUS_SHELL, 1));

    public static WatatsumiLootCardWrapper of(int level) {
        List<RitualLootTable.Weighted> fishing = new ArrayList<>();
        scaleInto(fishing, FISH, 85.0D);
        scaleInto(fishing, JUNK, 10.0D);
        List<RitualLootTable.Weighted> treasure = new ArrayList<>();
        if (level >= 1) {
            scaleInto(treasure, TREASURE, 5.0D);
        }
        List<RitualLootTable.Weighted> special = level >= 1
                ? List.copyOf(WatatsumiSpecialLootLoader.table().entries())
                : List.of();
        List<RitualLootTable.Weighted> gkLow = level >= 1
                ? List.copyOf(WatatsumiSpecialLootLoader.table().gensokyouLow()) : List.of();
        List<RitualLootTable.Weighted> gkHigh = level >= 1
                ? List.copyOf(WatatsumiSpecialLootLoader.table().gensokyouHigh()) : List.of();
        return new WatatsumiLootCardWrapper(level,
                WatatsumiBehavior.fishingCount(level),
                WatatsumiBehavior.specialCount(level),
                List.copyOf(fishing), List.copyOf(treasure), special, gkLow, gkHigh);
    }

    private static void scaleInto(List<RitualLootTable.Weighted> out,
                                  List<RitualLootTable.Weighted> src, double categoryWeight) {
        double sum = RitualLootTable.sumWeight(src);
        if (sum <= 0.0D) {
            return;
        }
        double scale = categoryWeight / sum;
        for (RitualLootTable.Weighted w : src) {
            out.add(new RitualLootTable.Weighted(w.item(), w.weight() * scale));
        }
    }

    /** 全部产物条目：钓鱼（fish+junk）→ 宝藏 → 海洋特产 → 信物低带 → 信物中带。 */
    public List<RitualLootTable.Weighted> allEntries() {
        List<RitualLootTable.Weighted> out = new ArrayList<>(fishing);
        out.addAll(treasure);
        out.addAll(special);
        out.addAll(gensokyouLow);
        out.addAll(gensokyouHigh);
        return out;
    }

    /** 所属段：0=钓鱼 1=宝藏 2=海洋特产 3=信物低带 4=信物中带。 */
    public int sectionFor(int index) {
        if (index < fishing.size()) {
            return 0;
        }
        if (index < fishing.size() + treasure.size()) {
            return 1;
        }
        if (index < fishing.size() + treasure.size() + special.size()) {
            return 2;
        }
        if (index < fishing.size() + treasure.size() + special.size() + gensokyouLow.size()) {
            return 3;
        }
        return 4;
    }

    /** 条目概率分母：钓鱼段 = 钓鱼池总权重（含宝藏等效）；特产/信物段 = 对应池总权重。 */
    public double denominatorFor(int index) {
        int section = sectionFor(index);
        if (section >= 3) {
            List<RitualLootTable.Weighted> band = section == 3 ? gensokyouLow : gensokyouHigh;
            return RitualLootTable.sumWeight(special) + RitualLootTable.sumWeight(band);
        }
        if (section == 2) {
            return RitualLootTable.sumWeight(special);
        }
        return RitualLootTable.sumWeight(fishing) + RitualLootTable.sumWeight(treasure);
    }
}
