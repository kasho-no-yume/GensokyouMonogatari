package com.bitsson.gensokyou.jei;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.registry.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class RitualCatalysts {

    private static final Map<ResourceLocation, List<Supplier<? extends Item>>> MAP = new HashMap<>();

    static {
        MAP.put(Gensokyou.id("summon_circle"),
                List.<Supplier<? extends Item>>of(ModItems.SUMMON_CATALYST, ModItems.CIRNO_CATALYST));
    }

    private RitualCatalysts() {
    }

    public static List<ItemStack> forPattern(ResourceLocation id) {
        return MAP.getOrDefault(id, List.of()).stream()
                .map(supplier -> new ItemStack(supplier.get()))
                .toList();
    }
}
