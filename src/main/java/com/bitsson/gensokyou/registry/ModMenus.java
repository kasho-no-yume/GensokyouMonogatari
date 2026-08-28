package com.bitsson.gensokyou.registry;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.menu.RitualCoreMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.network.IContainerFactory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, Gensokyou.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<RitualCoreMenu>> RITUAL_CORE =
            MENUS.register("ritual_core", () -> new MenuType<>(
                    (IContainerFactory<RitualCoreMenu>) RitualCoreMenu::new,
                    FeatureFlags.VANILLA_SET));

    private ModMenus() {
    }
}
