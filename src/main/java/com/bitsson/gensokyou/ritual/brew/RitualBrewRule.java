package com.bitsson.gensokyou.ritual.brew;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.alchemy.Potion;

import java.util.Set;

/**
 * 一条「炼药试剂 → 基础药水」的显式声明。
 *
 * <p>{@code longPotion} / {@code strongPotion} 是给**无命名约定**的模组药水准备的兄弟指针：
 * 原版条目按 {@code long_} / {@code strong_} 前缀自动查找（见
 * {@link com.bitsson.gensokyou.ritual.potion.PotionTierTransform#findSibling}），
 * 模组条目则需要在此显式指明。
 *
 * @param patternId 归属仪式图案
 * @param reagent 试剂物品
 * @param potion 基础药水
 * @param longPotion 显式 LONG 兄弟；{@code null} 表示"按命名约定找，找不到就没有"
 * @param strongPotion 显式 STRONG 兄弟；语义同 {@code longPotion}
 * @param excludedEffects 本条目生效的效果黑名单（逐条目隔离，MUST NOT 是全局开关）
 * @param extendWithoutLong 无 LONG 兄弟时是否仍按时效倍率延长
 * @param amplifyWithoutStrong 无 STRONG 兄弟时是否仍 +1 品质
 */
public record RitualBrewRule(ResourceLocation patternId,
                              Item reagent,
                              Holder<Potion> potion,
                              Holder<Potion> longPotion,
                              Holder<Potion> strongPotion,
                              Set<ResourceLocation> excludedEffects,
                              boolean extendWithoutLong,
                              boolean amplifyWithoutStrong) {

    public RitualBrewRule {
        if (patternId == null) {
            throw new IllegalArgumentException("patternId must not be null");
        }
        if (reagent == null) {
            throw new IllegalArgumentException("reagent must not be null");
        }
        if (potion == null) {
            throw new IllegalArgumentException("potion must not be null");
        }
        excludedEffects = excludedEffects == null ? Set.of() : Set.copyOf(excludedEffects);
    }

    /** 便捷构造：无兄弟覆盖、无黑名单、两个退化开关关闭。 */
    public RitualBrewRule(ResourceLocation patternId, Item reagent, Holder<Potion> potion) {
        this(patternId, reagent, potion, null, null, Set.of(), false, false);
    }

    /** 本条目是否把某效果列入黑名单。 */
    public boolean isExcluded(ResourceLocation effectId) {
        return effectId != null && excludedEffects.contains(effectId);
    }

    /** 便捷构造：把 {@link MobEffect} 列表收成黑名单 id 集合。 */
    public static Set<ResourceLocation> idsOf(Iterable<? extends Holder<MobEffect>> effects) {
        java.util.Set<ResourceLocation> ids = new java.util.LinkedHashSet<>();
        for (Holder<MobEffect> effect : effects) {
            effect.unwrapKey().ifPresent(key -> ids.add(key.location()));
        }
        return Set.copyOf(ids);
    }
}