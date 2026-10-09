package com.bitsson.gensokyou;

import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.registry.ModAttributes;
import com.bitsson.gensokyou.registry.ModBlocks;
import com.bitsson.gensokyou.registry.ModBlockEntities;
import com.bitsson.gensokyou.registry.ModCreativeTabs;
import com.bitsson.gensokyou.registry.ModDataComponents;
import com.bitsson.gensokyou.registry.ModEntityTypes;
import com.bitsson.gensokyou.registry.ModEquipmentMaterials;
import com.bitsson.gensokyou.registry.ModItems;
import com.bitsson.gensokyou.registry.ModMenus;
import com.bitsson.gensokyou.registry.ModMobEffects;
import com.bitsson.gensokyou.registry.ModPotions;
import com.bitsson.gensokyou.spirit.ModAttachments;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(Gensokyou.MODID)
public class Gensokyou {
    public static final String MODID = "gensokyou";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    public Gensokyou(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModEntityTypes.ENTITY_TYPES.register(modEventBus);
        ModMobEffects.EFFECTS.register(modEventBus);
        ModPotions.POTIONS.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);
        ModAttachments.ATTACHMENTS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        ModDataComponents.DATA_COMPONENTS.register(modEventBus);
        ModAttributes.ATTRIBUTES.register(modEventBus);
        ModEquipmentMaterials.ARMOR_MATERIALS.register(modEventBus);
        modContainer.registerConfig(ModConfig.Type.COMMON, GensokyouConfig.SPEC);
    }
}
