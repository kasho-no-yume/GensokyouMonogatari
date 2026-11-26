package com.bitsson.gensokyou.village;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.registry.ModItems;
import com.bitsson.gensokyou.ritual.brew.BrewReagentIndex;
import com.bitsson.gensokyou.ritual.brew.RitualBrewRuleLoader;
import com.bitsson.gensokyou.ritual.potion.PotionTierTransform;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Runtime registries supply compatibility; tags let packs exclude or assign tiers to unfamiliar entries. */
public final class VillageShopCatalog {
    public record Entry(String id, VillagePlayerData.Offer offer) {}
    public static List<Entry> candidates(ServerPlayer player, VillageRole role) {
        var entries = switch (role) {
            case RINNOSUKE -> curios(); case MIYOI -> potions(); case KOSUZU -> books(player); case AKYUU -> new ArrayList<Entry>();
        };
        var forbidden = DailyCommissions.turnInItems();
        return entries.stream().filter(e -> !forbidden.contains(BuiltInRegistries.ITEM.getKey(e.offer().result().getItem())))
                .filter(e -> !excluded(e.offer().result().getItemHolder(), Registries.ITEM))
                .sorted(Comparator.comparing(Entry::id)).toList();
    }
    private static ArrayList<Entry> curios() {
        var entries = new ArrayList<Entry>();
        add(entries, Items.NAME_TAG, 1, 0, 12, 2); add(entries, Items.SADDLE, 1, 0, 16, 1);
        add(entries, Items.SPONGE, 2, 0, 20, 2); add(entries, Items.NAUTILUS_SHELL, 1, 1, 12, 3);
        add(entries, Items.HEART_OF_THE_SEA, 1, 1, 48, 1); add(entries, Items.TOTEM_OF_UNDYING, 1, 1, 64, 1);
        add(entries, Items.ENCHANTED_GOLDEN_APPLE, 1, 2, 96, 1); add(entries, Items.ECHO_SHARD, 2, 2, 24, 2);
        add(entries, Items.NETHERITE_SCRAP, 1, 1, 48, 2); add(entries, Items.SHULKER_SHELL, 1, 2, 32, 2);
        add(entries, Items.EXPERIENCE_BOTTLE, 4, 0, 8, 4); add(entries, Items.DIAMOND, 1, 0, 16, 3);
        add(entries, Items.GHAST_TEAR, 2, 1, 12, 3); add(entries, Items.ENDER_PEARL, 2, 1, 8, 4);
        add(entries, Items.DRAGON_BREATH, 2, 2, 20, 3);
        add(entries, ModItems.SPIRIT_IRON.get(), 2, 1, 16, 3); add(entries, ModItems.STAR_SILVER.get(), 1, 2, 24, 2);
        add(entries, ModItems.MERMAID_SCALE.get(), 2, 1, 16, 3); add(entries, ModItems.TIDE_CRYSTAL.get(), 1, 2, 24, 2);
        add(entries, ModItems.DRAGON_SCALE.get(), 1, 2, 32, 2); add(entries, ModItems.REFINED_CINNABAR.get(), 3, 1, 12, 3);
        return entries;
    }
    private static void add(List<Entry> entries, Item item, int count, int tier, int price, int limit) {
        entries.add(new Entry(BuiltInRegistries.ITEM.getKey(item).toString(), new VillagePlayerData.Offer(
                new ItemStack(item, count), tier(item.builtInRegistryHolder(), Registries.ITEM, tier), price, limit, 0)));
    }
    private static ArrayList<Entry> potions() {
        var entries = new ArrayList<Entry>();
        var declared = new LinkedHashMap<ResourceLocation, com.bitsson.gensokyou.ritual.brew.RitualBrewRule>();
        for (var rule : RitualBrewRuleLoader.all()) {
            // Discard overridden reagent mappings, matching the ritual's actual authoritative lookup.
            if (RitualBrewRuleLoader.byReagent(rule.reagent()).orElse(null) == rule)
                rule.potion().unwrapKey().ifPresent(key -> declared.put(key.location(), rule));
        }
        for (ResourceLocation id : BuiltInRegistries.POTION.keySet()) {
            if (variant(id) || id.getPath().equals("luck")) continue;
            var potion = BuiltInRegistries.POTION.getHolder(ResourceKey.create(Registries.POTION, id)).orElse(null);
            if (potion == null || excluded(potion, Registries.POTION)) continue;
            var rule = declared.get(id);
            var source = rule == null ? PotionTierTransform.sourceOf(potion)
                    : PotionTierTransform.sourceOf(potion, rule.longPotion(), rule.strongPotion());
            if (source.isEmpty()) continue;
            var options = rule == null ? PotionTierTransform.Options.vanilla() : BrewReagentIndex.optionsOf(rule);
            var result = PotionTierTransform.build(potion, source.get(), 3, options);
            int minimum = id.getNamespace().equals("minecraft") ? 1 : id.getNamespace().equals("gensokyou") ? 3 : 5;
            int minimumTier = tier(potion, Registries.POTION, minimum);
            entries.add(new Entry(id.toString(), new VillagePlayerData.Offer(result, minimumTier, minimumTier >= 3 ? 24 : 16, 3, 0)));
        }
        return entries;
    }
    private static boolean variant(ResourceLocation id) {
        for (String prefix : List.of("long_", "strong_")) if (id.getPath().startsWith(prefix)) {
            var base = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), id.getPath().substring(prefix.length()));
            if (BuiltInRegistries.POTION.containsKey(base)) return true;
        }
        return false;
    }
    private static ArrayList<Entry> books(ServerPlayer player) {
        var entries = new ArrayList<Entry>();
        var registry = player.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        for (var enchantment : registry.holders().toList()) {
            if (enchantment.is(EnchantmentTags.CURSE) || !enchantment.is(EnchantmentTags.TRADEABLE)
                    || excluded(enchantment, Registries.ENCHANTMENT)) continue;
            ResourceLocation id = enchantment.key().location();
            int minimum = id.getNamespace().equals("minecraft") ? (enchantment.is(EnchantmentTags.TREASURE) ? 2 : 1) : 5;
            int minimumTier = tier(enchantment, Registries.ENCHANTMENT, minimum);
            int level = enchantment.value().getMaxLevel();
            ItemStack result = EnchantedBookItem.createForEnchantment(new EnchantmentInstance(enchantment, level));
            entries.add(new Entry(id.toString(), new VillagePlayerData.Offer(result, minimumTier,
                    Math.min(128, 12 + level * 6 + (enchantment.is(EnchantmentTags.TREASURE) ? 16 : 0)), 1, 0)));
        }
        return entries;
    }
    public static <T> boolean excluded(Holder<T> holder, ResourceKey<? extends Registry<T>> registry) {
        return holder.is(TagKey.create(registry, Gensokyou.id("village_shop/excluded")));
    }
    private static <T> int tier(Holder<T> holder, ResourceKey<? extends Registry<T>> registry, int fallback) {
        for (int k = 5; k >= 0; k--)
            if (holder.is(TagKey.create(registry, Gensokyou.id("village_shop/tier_" + k)))) return k;
        return fallback;
    }
    private VillageShopCatalog() {}
}
