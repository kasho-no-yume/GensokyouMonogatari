package com.bitsson.gensokyou.jei;

import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.ritual.brew.RitualBrewRule;
import com.bitsson.gensokyou.ritual.potion.PotionTierTransform;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * JEI 炼药卡的数据载体（一「试剂 → 药水」一条）。
 *
 * <p>三个阶的产物图标在<b>客户端现算</b>——{@link PotionTierTransform} 是世界无关纯函数，
 * 只需注册表与 config（客户端都拿得到），故卡片只需携带规则本身，不必由服务端展开。
 * 这样服务端 → 客户端的快照里多出来的只有一个 brew_rules 字段。
 */
public record BrewRecipeCardWrapper(ResourceLocation patternId, RitualBrewRule rule,
                                    List<ItemStack> tierResults) {

    public static final int TIER_COUNT = 3;

    public BrewRecipeCardWrapper {
        tierResults = List.copyOf(tierResults);
    }

    /** 由规则现算三个阶的产物；任何一阶算不出来就返回空卡（宁可少一条也不显示错图标）。 */
    public static BrewRecipeCardWrapper of(RitualBrewRule rule) {
        List<ItemStack> results = new ArrayList<>(TIER_COUNT);
        for (int tier = 1; tier <= TIER_COUNT; tier++) {
            final int currentTier = tier;
            ItemStack stack = PotionTierTransform.sourceOf(rule.potion(), rule.longPotion(),
                            rule.strongPotion())
                    .map(source -> PotionTierTransform.build(rule.potion(), source, currentTier,
                            options(rule)))
                    .orElse(ItemStack.EMPTY);
            if (stack.isEmpty()) {
                return new BrewRecipeCardWrapper(rule.patternId(), rule, List.of());
            }
            results.add(stack);
        }
        return new BrewRecipeCardWrapper(rule.patternId(), rule, results);
    }

    /** 该条的产物图标（1/2/3 阶），空卡返回空列表。 */
    public List<ItemStack> results() {
        return tierResults;
    }

    public ItemStack reagentStack() {
        return new ItemStack(rule.reagent());
    }

    /** 每 uid 恰一条（覆盖型规则表已由 loader 折叠，故按试剂 + 目标药水去重）。 */
    public ResourceLocation id() {
        return ResourceLocation.fromNamespaceAndPath(patternId.getNamespace(),
                "brew_" + BuiltInRegistries.ITEM.getKey(rule.reagent()).getPath()
                        + "_to_" + rule.potion().unwrapKey()
                        .map(key -> key.location().getPath()).orElse("unnamed"));
    }

    private static PotionTierTransform.Options options(RitualBrewRule rule) {
        return new PotionTierTransform.Options(
                GensokyouConfig.SUNAKO_LONG_DURATION_MULTIPLIER.get(),
                GensokyouConfig.SUNAKO_STRONG_DURATION_MULTIPLIER.get(),
                rule.extendWithoutLong(),
                rule.amplifyWithoutStrong(),
                rule.excludedEffects());
    }
}