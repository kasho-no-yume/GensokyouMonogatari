package com.bitsson.gensokyou.ritual;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 献祭仪式的加权产出表（数据驱动，一仪式一文件）。
 *
 * <p>总权重模型（非写死概率）：某材质的实际抽取池 = commons 桶 + 该材质 special + 条件池。
 * commons 桶权重 = {@code max(0, commonsTotal − Σspecial)}，桶内按 commons 相对权重缩放分配；
 * nether/end 为额外加权（不下沉 commons）。每次抽取按累计权重归一化取 1 件。
 *
 * <p>本类只承载数据与世界无关纯内核；世界交互（扫描祭品台/扣费/投递）在行为侧。
 */
public record RitualLootTable(ResourceLocation patternId, TagKey<Item> toolTag, int commonsTotal,
                              int skullsRequired, int dragonHeadsRequired,
                              List<Weighted> commons, List<TierTable> tables) {

    /** 工具材质键（与 pattern/JSON 约定一致）。 */
    public static final List<String> TIER_KEYS =
            List.of("wood", "stone", "gold", "iron", "diamond", "netherite");

    /** 单条加权产物（权重为相对总权重）。 */
    public record Weighted(Item item, double weight) {
    }

    /** 单材质的权重表（nether/end 解析期已按顶层缺省合并完毕）。 */
    public record TierTable(String tier, List<Weighted> special,
                            List<Weighted> nether, List<Weighted> end) {
    }

    /** 工具类别标签（如 {@code #minecraft:pickaxes}）。 */
    public static TagKey<Item> parseToolTag(String raw) {
        String id = raw.startsWith("#") ? raw.substring(1) : raw;
        return TagKey.create(net.minecraft.core.registries.Registries.ITEM,
                ResourceLocation.parse(id));
    }

    /** 物品标签代表（供调试/展示；无成员返回空）。 */
    public static Optional<Item> representative(TagKey<Item> tag) {
        return BuiltInRegistries.ITEM.getTag(tag)
                .flatMap(holders -> holders.stream().findFirst())
                .map(holder -> holder.value());
    }

    /** 标签内在指定工具材质的代表物品（JEI 每材质卡用；mod 工具经 {@code TieredItem} 一并匹配）。 */
    public static Optional<Item> representativeForTier(TagKey<Item> tag, String tier) {
        var holders = BuiltInRegistries.ITEM.getTag(tag);
        if (holders.isEmpty()) {
            return Optional.empty();
        }
        Item fallback = null;
        for (var holder : holders.get()) {
            Item item = holder.value();
            if (fallback == null) {
                fallback = item;
            }
            if (item instanceof net.minecraft.world.item.TieredItem tieredItem
                    && tierKeyOf(tieredItem.getTier()).equals(tier)) {
                return Optional.of(item);
            }
        }
        return Optional.ofNullable(fallback);
    }

    public Optional<TierTable> table(String tier) {
        return tables.stream().filter(t -> t.tier().equals(tier)).findFirst();
    }

    /** {@code TieredItem} 的 Tier → 材质键；无法判定（自定义 Tier）回退最低档 wood。 */
    public static String tierKeyOf(@Nullable Tier tier) {
        if (tier == Tiers.STONE) {
            return "stone";
        }
        if (tier == Tiers.GOLD) {
            return "gold";
        }
        if (tier == Tiers.IRON) {
            return "iron";
        }
        if (tier == Tiers.DIAMOND) {
            return "diamond";
        }
        if (tier == Tiers.NETHERITE) {
            return "netherite";
        }
        return "wood";
    }

    // ---- 世界无关纯内核（单测 / 调试命令可直调）----

    public static double sumWeight(List<Weighted> list) {
        double sum = 0.0D;
        for (Weighted w : list) {
            if (w.weight() > 0.0D) {
                sum += w.weight();
            }
        }
        return sum;
    }

    /** commons 桶总权重 = max(0, commonsTotal − Σspecial)。 */
    public static double bucketWeight(double specialSum, int commonsTotal) {
        return Math.max(0.0D, commonsTotal - specialSum);
    }

    /**
     * 组装某材质的实际抽取池：commons 按桶权重缩放 + special（含条件池）。
     * 权重 ≤ 0 的条目不参与；空池返回空列表。
     */
    public static List<Weighted> buildPool(List<Weighted> commons, int commonsTotal,
                                           TierTable table, boolean nether, boolean end) {
        List<Weighted> pool = new ArrayList<>();
        double specialSum = sumWeight(table.special());
        double bucket = bucketWeight(specialSum, commonsTotal);
        double commonsRel = sumWeight(commons);
        if (bucket > 0.0D && commonsRel > 0.0D) {
            double scale = bucket / commonsRel;
            for (Weighted w : commons) {
                if (w.weight() > 0.0D) {
                    pool.add(new Weighted(w.item(), w.weight() * scale));
                }
            }
        }
        appendPositive(pool, table.special());
        if (nether) {
            appendPositive(pool, table.nether());
        }
        if (end) {
            appendPositive(pool, table.end());
        }
        return pool;
    }

    private static void appendPositive(List<Weighted> pool, List<Weighted> source) {
        for (Weighted w : source) {
            if (w.weight() > 0.0D) {
                pool.add(w);
            }
        }
    }

    /** 累计权重法单次抽取；空池/零权重返回 null。 */
    @Nullable
    public static Item roll(List<Weighted> pool, RandomSource random) {
        double total = sumWeight(pool);
        if (total <= 0.0D) {
            return null;
        }
        double pick = random.nextDouble() * total;
        double acc = 0.0D;
        for (Weighted w : pool) {
            if (w.weight() <= 0.0D) {
                continue;
            }
            acc += w.weight();
            if (pick < acc) {
                return w.item();
            }
        }
        // 浮点残差兜底：取最后一个正权重条目
        Item last = null;
        for (Weighted w : pool) {
            if (w.weight() > 0.0D) {
                last = w.item();
            }
        }
        return last;
    }

    /** 掷 {@code count} 次并聚合（保持首次出现序）。 */
    public static Map<Item, Integer> rollMany(List<Weighted> pool, int count, RandomSource random) {
        Map<Item, Integer> agg = new LinkedHashMap<>();
        Item last = null;
        for (int i = 0; i < count; i++) {
            Item item = roll(pool, random);
            if (item == null) {
                continue;
            }
            last = item;
            agg.merge(item, 1, Integer::sum);
        }
        if (agg.isEmpty() && last != null) {
            agg.put(last, 1);
        }
        return agg;
    }
}
