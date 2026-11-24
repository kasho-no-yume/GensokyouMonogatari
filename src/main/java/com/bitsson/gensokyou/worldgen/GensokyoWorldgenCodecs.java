package com.bitsson.gensokyou.worldgen;

import com.bitsson.gensokyou.Gensokyou;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = Gensokyou.MODID)
public final class GensokyoWorldgenCodecs {
    private GensokyoWorldgenCodecs() {
    }

    @SubscribeEvent
    public static void registerWorldgenCodecs(RegisterEvent event) {
        event.register(Registries.PLACEMENT_MODIFIER_TYPE, Gensokyou.id("landmark_clearance"),
                () -> LandmarkClearanceFilter.TYPE);
        event.register(Registries.PLACEMENT_MODIFIER_TYPE, Gensokyou.id("height_band"), () -> HeightBandFilter.TYPE);
        event.register(Registries.BIOME_SOURCE, Gensokyou.id("layout"), () -> GensokyoBiomeSource.CODEC);
        event.register(Registries.DENSITY_FUNCTION_TYPE, Gensokyou.id("island_terrain"),
                () -> GensokyoIslandDensity.CODEC.codec());
        event.register(Registries.DENSITY_FUNCTION_TYPE, Gensokyou.id("island_layout"),
                () -> GensokyoLayoutDensity.CODEC.codec());
        event.register(Registries.FEATURE, Gensokyou.id("hakurei_approach_road"),
                () -> HakureiApproachRoadFeature.INSTANCE);
        event.register(Registries.FEATURE, Gensokyou.id("hakurei_mossy_boulder"),
                () -> HakureiMossyBoulderFeature.INSTANCE);
        event.register(Registries.FEATURE, Gensokyou.id("hakurei_spirit_spring"),
                () -> HakureiSpiritSpringFeature.INSTANCE);
        event.register(Registries.FEATURE, Gensokyou.id("hakurei_ancient_sacred_tree"),
                () -> HakureiAncientSacredTreeFeature.INSTANCE);
        event.register(Registries.FEATURE, Gensokyou.id("hakurei_wilderness_poi"),
                () -> HakureiWildernessPoiFeature.INSTANCE);
    }
}
