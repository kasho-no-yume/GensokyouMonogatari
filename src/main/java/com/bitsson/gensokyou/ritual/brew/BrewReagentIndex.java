package com.bitsson.gensokyou.ritual.brew;

import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.ritual.potion.PotionTierTransform;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 「炼药试剂 → 基础药水」的解析器：**显式声明优先，未声明则向酿造台反查**。
 *
 * <p><b>为什么必须扫全注册表而不是挑一个探针药水</b>：1.21.1 的
 * {@code PotionBrewing.addStartMix(reagent, result)} 展开后是两条边 ——
 * 「水 + 试剂 = 平凡」与「苦艾 + 试剂 = 产物」。故只有苦艾药水能命中原版试剂；
 * 用平凡当探针对所有原版试剂<b>一律查不到</b>。而模组可以注册任意 {@code from → to} 的边，
 * 只有遍历 {@code Registry<POTION>} 全量才能覆盖。
 *
 * <p>三条过滤（缺一不可）：
 * <ol>
 *   <li>{@code mix} 结果与输入相同 → 无变化，丢弃；</li>
 *   <li>结果物品不是 {@link Items#POTION} → 丢弃（滤掉容器类转化：枪粉→喷溅、龙息→滞留）；</li>
 *   <li>结果 potion 是 water / mundane / thick / awkward → 丢弃（滤除"任意试剂 + 水 = 平凡"的废招）。</li>
 * </ol>
 *
 * <p><b>缓存口径</b>：反查索引构建一次（首次访问 / {@code /reload} 后重建）并缓存，
 * 运行时 MUST NOT 重复全表扫描。但 {@link #resolve} 是<b>现查</b>的 —— 它读的是当前
 * 数据包 + 当前索引，不缓存 {@code Holder<Potion>} 强引用，避免 reload 后指向已删条目。
 */
public final class BrewReagentIndex {

    /**
     * 原版 {@code addStartMix} 的全部试剂（1.21.1 共 15 条）。
     *
     * <p><b>刻意不含 {@code FERMENTED_SPIDER_EYE}</b>：它只有「水 + 发酵蜘蛛眼 = 虚弱」
     * 一条边，而水是废招条目、按过滤规则 3 会被丢弃 —— 故反查查不到它，需要数据包显式声明。
     */
    private static final List<Item> VANILLA_START_MIX_REAGENTS = List.of(
            Items.BREEZE_ROD,          // wind_charged
            Items.SLIME_BLOCK,         // oozing
            Items.STONE,               // infested
            Items.COBWEB,              // weaving
            Items.MAGMA_CREAM,         // fire_resistance
            Items.RABBIT_FOOT,         // leaping
            Items.SUGAR,               // swiftness
            Items.GLISTERING_MELON_SLICE, // healing
            Items.SPIDER_EYE,          // poison
            Items.GHAST_TEAR,          // regeneration
            Items.BLAZE_POWDER,        // strength
            Items.PHANTOM_MEMBRANE,    // slow_falling
            Items.GOLDEN_CARROT,       // night_vision
            Items.TURTLE_HELMET,       // turtle_master
            Items.PUFFERFISH           // water_breathing
    );

    private static volatile Map<Item, Holder<Potion>> reverseIndex;

    private BrewReagentIndex() {
    }

    /**
     * 一次解析的结果。
     *
     * @param base 基础药水条目
     * @param source 含 LONG / STRONG 兄弟的效果源
     * @param options 变换选项（倍率来自 config，退化开关与黑名单来自声明）
     * @param explicit 是否来自 {@code brew_recipes} 显式声明（false = 反查回落）
     */
    public record Resolution(Holder<Potion> base,
                             PotionTierTransform.Source source,
                             PotionTierTransform.Options options,
                             boolean explicit) {
    }

    // ------------------------------------------------------------------ 对外入口

    /**
     * 解析试剂槽里的物品。**每次调用现查**：`/reload` 改写映射后立即生效，
     * 旧试剂变为"无法炼制"而非静默沿用旧结果。
     */
    public static Optional<Resolution> resolve(Level level, ItemStack reagent) {
        if (reagent == null || reagent.isEmpty()) {
            return Optional.empty();
        }
        Item item = reagent.getItem();
        RitualBrewRule declared = RitualBrewRuleLoader.byReagent(item).orElse(null);
        if (declared != null) {
            return fromRule(declared);
        }
        Holder<Potion> probed = reverseIndex(level).get(item);
        if (probed == null) {
            return Optional.empty();
        }
        return PotionTierTransform.sourceOf(probed)
                .map(source -> new Resolution(probed, source, fallbackOptions(), false));
    }

    /** 该物品当前能否作为炼药试剂（供 GUI 槽校验用，避免持有世界对象）。 */
    public static boolean isKnownReagent(Item item) {
        if (item == null) {
            return false;
        }
        if (RitualBrewRuleLoader.byReagent(item).isPresent()) {
            return true;
        }
        return vanillaCandidates().contains(item);
    }

    /** 试剂能炼出的基础药水（仅用于 GUI 预览；解析失败返回空）。 */
    public static Optional<Holder<Potion>> peekBasePotion(Level level, ItemStack reagent) {
        return resolve(level, reagent).map(Resolution::base);
    }

    // ------------------------------------------------------------------ 反查索引

    /** {@code /reload} 后作废，下次访问重建。 */
    public static void invalidate() {
        reverseIndex = null;
    }

    public static Map<Item, Holder<Potion>> reverseIndex(Level level) {
        Map<Item, Holder<Potion>> cached = reverseIndex;
        if (cached != null) {
            return cached;
        }
        synchronized (BrewReagentIndex.class) {
            if (reverseIndex == null) {
                reverseIndex = Map.copyOf(build(level.potionBrewing()));
            }
            return reverseIndex;
        }
    }

    /** 候选试剂集 = 原版 startMix 试剂 ∪ 数据包显式声明的试剂。 */
    public static Set<Item> vanillaCandidates() {
        return Set.copyOf(VANILLA_START_MIX_REAGENTS);
    }

    private static Set<Item> candidateReagents() {
        Set<Item> candidates = new LinkedHashSet<>(VANILLA_START_MIX_REAGENTS);
        candidates.addAll(RitualBrewRuleLoader.declaredReagents());
        return candidates;
    }

    private static Map<Item, Holder<Potion>> build(PotionBrewing brewing) {
        Map<Item, Holder<Potion>> found = new LinkedHashMap<>();
        if (brewing == null) {
            return found;
        }
        Set<Item> candidates = candidateReagents();
        List<Holder<Potion>> probes = new ArrayList<>();
        // Registry<Potion> 的迭代产出 Potion 值本身而非 Holder，故走 keySet + getHolder
        for (ResourceLocation location : BuiltInRegistries.POTION.keySet()) {
            BuiltInRegistries.POTION
                    .getHolder(ResourceKey.create(Registries.POTION, location))
                    .filter(holder -> !PotionTierTransform.isBare(holder))
                    .ifPresent(probes::add);
        }
        for (Holder<Potion> probe : probes) {
            ItemStack probeStack = PotionContents.createItemStack(Items.POTION, probe);
            for (Item reagent : candidates) {
                if (found.containsKey(reagent)) {
                    // 首个命中即定：注册表迭代序稳定，重建后结果一致
                    continue;
                }
                Holder<Potion> result = probeOnce(brewing, reagent, probeStack, probe);
                if (result != null) {
                    found.put(reagent, result);
                }
            }
        }
        return found;
    }

    /**
     * 单次探针查询 + 三条过滤。抽成包级可见方法以便单测用自建 {@link PotionBrewing} 直接断言。
     *
     * @return 接受的基础药水条目；被任一过滤拒收时返回 {@code null}
     */
    static Holder<Potion> probeOnce(PotionBrewing brewing, Item reagent, ItemStack probeStack,
                                    Holder<Potion> probe) {
        if (brewing == null) {
            return null;
        }
        ItemStack mixed = brewing.mix(new ItemStack(reagent), probeStack);
        // 过滤 1：无变化
        if (ItemStack.isSameItemSameComponents(mixed, probeStack)) {
            return null;
        }
        // 过滤 2：容器类转化（枪粉→喷溅药水、龙息→滞留药水）
        if (mixed.getItem() != Items.POTION) {
            return null;
        }
        PotionContents contents = mixed.get(DataComponents.POTION_CONTENTS);
        if (contents == null) {
            return null;
        }
        Optional<Holder<Potion>> result = contents.potion();
        if (result.isEmpty()) {
            return null;
        }
        Holder<Potion> potion = result.get();
        // 过滤 3：1.21 的废招（任意试剂 + 水都得到平凡）
        if (PotionTierTransform.isBare(potion)) {
            return null;
        }
        return potion;
    }

    // ------------------------------------------------------------------ 选项装配

    private static Optional<Resolution> fromRule(RitualBrewRule rule) {
        return PotionTierTransform
                .sourceOf(rule.potion(), rule.longPotion(), rule.strongPotion())
                .map(source -> new Resolution(rule.potion(), source,
                        new PotionTierTransform.Options(
                                GensokyouConfig.SUNAKO_LONG_DURATION_MULTIPLIER.get(),
                                GensokyouConfig.SUNAKO_STRONG_DURATION_MULTIPLIER.get(),
                                rule.extendWithoutLong(),
                                rule.amplifyWithoutStrong(),
                                rule.excludedEffects()),
                        true));
    }

    /** 反查回落条目没有数据包声明，故两个退化开关均关闭（保持 vanilla 原样口径）。 */
    private static PotionTierTransform.Options fallbackOptions() {
        return new PotionTierTransform.Options(
                GensokyouConfig.SUNAKO_LONG_DURATION_MULTIPLIER.get(),
                GensokyouConfig.SUNAKO_STRONG_DURATION_MULTIPLIER.get(),
                false, false, Set.of());
    }

    /** 无世界上下文的选项装配（纯倍率、黑名单由调用方给），供 GUI / JEI 预览复用。 */
    public static PotionTierTransform.Options optionsOf(RitualBrewRule rule) {
        return new PotionTierTransform.Options(
                GensokyouConfig.SUNAKO_LONG_DURATION_MULTIPLIER.get(),
                GensokyouConfig.SUNAKO_STRONG_DURATION_MULTIPLIER.get(),
                rule.extendWithoutLong(),
                rule.amplifyWithoutStrong(),
                rule.excludedEffects());
    }
}