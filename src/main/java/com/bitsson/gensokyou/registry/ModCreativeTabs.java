package com.bitsson.gensokyou.registry;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.item.weapon.AmpCoreItem;
import com.bitsson.gensokyou.item.weapon.RuneGenerator;
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
                        ModItems.RITUAL_STONE_ITEMS.forEach(item -> output.accept(item.get()));
                        output.accept(ModItems.RITUAL_CORE_ITEM.get());
                        ModItems.RITUAL_PEDESTAL_ITEMS.forEach(item -> output.accept(item.get()));
                        output.accept(ModItems.RITUAL_WAND.get());
                        output.accept(ModItems.DANMAKU_WEAPON.get());
                        output.accept(ModItems.CORE_SPHERE_SINGLE.get());
                        output.accept(ModItems.CORE_SPHERE_SHOTGUN.get());
                        output.accept(ModItems.CORE_KNIFE.get());
                        output.accept(ModItems.CORE_TALISMAN.get());
                        output.accept(ModItems.CORE_LASER_GUN.get());
                        output.accept(ModItems.CORE_LASER_CANNON.get());
                        output.accept(ModItems.WEAPON_CORE_LV1.get());
                        output.accept(ModItems.WEAPON_CORE_LV2.get());
                        output.accept(ModItems.WEAPON_CORE_LV3.get());
                        // 增幅核：预生成词条+晶石随机色，创造栏即所见即所得
                        for (AmpCoreItem amp : new AmpCoreItem[]{
                                ModItems.AMP_CORE_T1.get(), ModItems.AMP_CORE_T2.get(),
                                ModItems.AMP_CORE_T3.get()}) {
                            ItemStack core = new ItemStack(amp);
                            RuneGenerator.ensureGenerated(core, amp.tier());
                            output.accept(core);
                        }
                    })
                    .build());
}
