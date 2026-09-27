package com.bitsson.gensokyou.ritual.harvest;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class HarvestProviderRegistry {
    private static final Map<Item, HarvestProvider> CUSTOM_BY_ITEM = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, HarvestProvider> CUSTOM_BY_ID = new ConcurrentHashMap<>();
    private static final Map<Item, HarvestProvider> BUILT_IN_BY_ITEM = new ConcurrentHashMap<>();

    private HarvestProviderRegistry() {
    }

    public static void register(Item item, HarvestProvider provider) {
        CUSTOM_BY_ITEM.put(Objects.requireNonNull(item, "item"), Objects.requireNonNull(provider, "provider"));
    }

    public static void register(ResourceLocation itemId, HarvestProvider provider) {
        CUSTOM_BY_ID.put(Objects.requireNonNull(itemId, "itemId"), Objects.requireNonNull(provider, "provider"));
    }

    public static void unregister(Item item) {
        if (item != null) {
            CUSTOM_BY_ITEM.remove(item);
        }
    }

    public static void unregister(ResourceLocation itemId) {
        if (itemId != null) {
            CUSTOM_BY_ID.remove(itemId);
        }
    }

    public static void clearCustomRegistrations() {
        CUSTOM_BY_ITEM.clear();
        CUSTOM_BY_ID.clear();
    }

    public static Optional<HarvestProvider> find(ItemStack input) {
        if (input == null || input.isEmpty()) {
            return Optional.empty();
        }
        Item item = input.getItem();
        HarvestProvider provider = CUSTOM_BY_ITEM.get(item);
        if (provider == null) {
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
            provider = CUSTOM_BY_ID.get(itemId);
        }
        if (provider == null) {
            provider = BUILT_IN_BY_ITEM.get(item);
        }
        return Optional.ofNullable(provider);
    }

    static void registerBuiltIn(Item item, HarvestProvider provider) {
        BUILT_IN_BY_ITEM.put(Objects.requireNonNull(item, "item"), Objects.requireNonNull(provider, "provider"));
    }
}
