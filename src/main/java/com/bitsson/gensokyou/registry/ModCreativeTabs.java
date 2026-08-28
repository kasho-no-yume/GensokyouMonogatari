package com.bitsson.gensokyou.registry;

import com.bitsson.gensokyou.Gensokyou;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Gensokyou.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> GENSOKYOU_TAB =
            TABS.register("gensokyou", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.gensokyou"))
                    .icon(() -> new ItemStack(ModItems.GUIDE_BOOK.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.GUIDE_BOOK.get());
                        output.accept(ModItems.MUSOU_FUUIN.get());
                        output.accept(ModItems.LIGHT_REFLECT.get());
                        output.accept(ModItems.ICICLE_FALL.get());
                        output.accept(ModItems.PPOINT.get());
                        output.accept(ModItems.BPOINT.get());
                        output.accept(ModItems.SPELLCARD_STAR.get());
                        output.accept(ModItems.BROKEN_SPELL_CARD_STAR.get());
                        output.accept(ModItems.YEN.get());
                        output.accept(ModItems.LAEVATEIN.get());
                        output.accept(ModItems.SUMMON_CATALYST.get());
                        output.accept(ModItems.CIRNO_CATALYST.get());
                        output.accept(ModItems.RITUAL_STONE_ITEM.get());
                        output.accept(ModItems.RITUAL_CORE_ITEM.get());
                        output.accept(ModItems.RITUAL_PEDESTAL_ITEM.get());
                        output.accept(ModItems.RITUAL_WAND.get());
                    })
                    .build());
}
