package com.bitsson.gensokyou.ritual.enchant;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 思兼神封的附魔合并内核（世界无关纯静态）。
 *
 * <p><b>升序折叠</b>：同词条的所有等级（装备自带 + 各有效附魔书）先按等级升序排序，
 * 再依次折叠——累计值与下一等级相同 → 累计值 +1；不同 → 取较大者。顺序是语义的一部分：
 * 乱序折叠结果不同（[3,1,1,2] 乱序只得 3，升序得 4），调用方 MUST NOT 自行预排序后
 * 宣称等价——本函数内部统一排序。
 *
 * <p><b>上限口径</b>：1/2 阶结果截断到词条原版 {@code maxLevel}（数据驱动，mod 附魔同
 * 机制）；3 阶无视词条池内容，直接置 {@code maxLevel + 1}（超限）。
 *
 * <p>核验过的 1.21.1 事实：适用性用 {@link ItemStack#supportsEnchantment(Holder)}
 * （supportedItems 铁砧口径，Neo 扩展感知）；{@link ItemEnchantments.Mutable#set}
 * 只钳 255 不钳 maxLevel，故超限等级可直接写入。
 */
public final class OmoikaneMerge {

    private OmoikaneMerge() {
    }

    /** 一次装备模式批次的合并计划：每词条最终等级 + 每本书是否有效（决定 1 阶消耗）。 */
    public record MergePlan(Map<Holder<Enchantment>, Integer> levels,
                            List<Boolean> bookEffective) {

        public static final MergePlan EMPTY = new MergePlan(Map.of(), List.of());

        /** 有效词条数 = 计费单位数。 */
        public int entryCount() {
            return levels.size();
        }
    }

    /**
     * 升序折叠。
     *
     * @param levels 词条池（任意顺序；空列表未定义，调用方保证非空）
     * @param cap    截断上限（含）
     */
    public static int foldLevels(List<Integer> levels, int cap) {
        List<Integer> sorted = new ArrayList<>(levels);
        sorted.sort(Integer::compareTo);
        int acc = sorted.get(0);
        for (int i = 1; i < sorted.size(); i++) {
            int next = sorted.get(i);
            acc = acc == next ? acc + 1 : Math.max(acc, next);
        }
        return Math.min(acc, cap);
    }

    /** 3 阶超限等级。 */
    public static int overrideLevel(int maxLevel) {
        return maxLevel + 1;
    }

    /**
     * 汇总装备模式合并计划。
     *
     * <p>适用性过滤逐词条独立：书上对装备无效的词条不入池、不计费，但该书只要含
     * ≥1 条有效词条即标记为"有效"（1 阶消耗口径）。装备自带的词条仅当其也出现在
     * 某个有效书中时才入池（不被书参与的自带词条原样保留、不计费）。
     *
     * @param gear        槽内装备
     * @param books       祭品台上的附魔书（顺序与台位一一对应）
     * @param ritualTier  仪式阶（1/2/3；≥3 走超限）
     */
    public static MergePlan plan(ItemStack gear, List<ItemStack> books, int ritualTier) {
        Map<Holder<Enchantment>, List<Integer>> pools = new LinkedHashMap<>();
        List<Boolean> effective = new ArrayList<>(books.size());
        for (ItemStack book : books) {
            boolean any = false;
            ItemEnchantments stored = book.getOrDefault(DataComponents.STORED_ENCHANTMENTS,
                    ItemEnchantments.EMPTY);
            for (var entry : stored.entrySet()) {
                Holder<Enchantment> holder = entry.getKey();
                if (!gear.supportsEnchantment(holder)) {
                    continue;
                }
                pools.computeIfAbsent(holder, k -> new ArrayList<>()).add(entry.getIntValue());
                any = true;
            }
            effective.add(any);
        }
        if (pools.isEmpty()) {
            return new MergePlan(Map.of(), effective);
        }
        ItemEnchantments existing = gear.getOrDefault(DataComponents.ENCHANTMENTS,
                ItemEnchantments.EMPTY);
        for (var entry : existing.entrySet()) {
            List<Integer> pool = pools.get(entry.getKey());
            if (pool != null) {
                pool.add(entry.getIntValue());
            }
        }
        Map<Holder<Enchantment>, Integer> levels = new LinkedHashMap<>();
        for (var e : pools.entrySet()) {
            int maxLevel = e.getKey().value().getMaxLevel();
            levels.put(e.getKey(), ritualTier >= 3
                    ? overrideLevel(maxLevel)
                    : foldLevels(e.getValue(), maxLevel));
        }
        return new MergePlan(levels, effective);
    }

    /** 把合并计划写回装备：保留未被书参与的自带词条，覆写/新增计划内词条。 */
    public static void apply(ItemStack gear, MergePlan plan) {
        ItemEnchantments existing = gear.getOrDefault(DataComponents.ENCHANTMENTS,
                ItemEnchantments.EMPTY);
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(existing);
        for (var e : plan.levels().entrySet()) {
            mutable.set(e.getKey(), e.getValue());
        }
        gear.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
    }
}
