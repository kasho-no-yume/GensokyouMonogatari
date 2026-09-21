package com.bitsson.gensokyou.registry;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.menu.CrystalStorageMenu;
import com.bitsson.gensokyou.menu.DanmakuAssemblyBenchMenu;
import com.bitsson.gensokyou.menu.RitualBuilderMenu;
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

    public static final DeferredHolder<MenuType<?>, MenuType<DanmakuAssemblyBenchMenu>> DANMAKU_ASSEMBLY_BENCH =
            MENUS.register("danmaku_assembly_bench", () -> new MenuType<>(
                    (IContainerFactory<DanmakuAssemblyBenchMenu>) DanmakuAssemblyBenchMenu::new,
                    FeatureFlags.VANILLA_SET));

    public static final DeferredHolder<MenuType<?>, MenuType<RitualBuilderMenu>> RITUAL_BUILDER =
            MENUS.register("ritual_builder", () -> new MenuType<>(
                    (IContainerFactory<RitualBuilderMenu>) RitualBuilderMenu::new,
                    FeatureFlags.VANILLA_SET));

    public static final DeferredHolder<MenuType<?>, MenuType<com.bitsson.gensokyou.menu.RitualEditorMenu>> RITUAL_EDITOR =
            MENUS.register("ritual_editor", () -> new MenuType<>(
                    (IContainerFactory<com.bitsson.gensokyou.menu.RitualEditorMenu>)
                            com.bitsson.gensokyou.menu.RitualEditorMenu::new,
                    FeatureFlags.VANILLA_SET));

    public static final DeferredHolder<MenuType<?>, MenuType<CrystalStorageMenu>> CRYSTAL_STORAGE =
            MENUS.register("crystal_storage", () -> new MenuType<>(
                    (IContainerFactory<CrystalStorageMenu>) CrystalStorageMenu::new,
                    FeatureFlags.VANILLA_SET));

    public static final DeferredHolder<MenuType<?>, MenuType<com.bitsson.gensokyou.menu.WujinzangTerminalMenu>> WUJINZANG_TERMINAL =
            MENUS.register("wujinzang_terminal", () -> new MenuType<>(
                    (IContainerFactory<com.bitsson.gensokyou.menu.WujinzangTerminalMenu>)
                            com.bitsson.gensokyou.menu.WujinzangTerminalMenu::new,
                    FeatureFlags.VANILLA_SET));

    private ModMenus() {
    }
}
