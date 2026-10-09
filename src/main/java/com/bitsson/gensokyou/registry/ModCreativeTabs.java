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
                        output.accept(ModItems.CODEX_OF_BEINGS.get());
                        output.accept(ModItems.MUSOU_FUUIN.get());
                        output.accept(ModItems.LIGHT_REFLECT.get());
                        output.accept(ModItems.ICICLE_FALL.get());
                        output.accept(ModItems.PPOINT.get());
                        output.accept(ModItems.BPOINT.get());
                        output.accept(ModItems.SPELLCARD_STAR.get());
                        output.accept(ModItems.BROKEN_SPELL_CARD_STAR.get());
                        output.accept(ModItems.YEN.get());
                        output.accept(ModItems.MEMORY_FRAGMENT.get());
                        // 幻想乡素材（按 矿产/土产/木材/海产/植物 排列）
                        output.accept(ModItems.CINNABAR.get());
                        output.accept(ModItems.ROUGH_CINNABAR_ORE.get());
                        output.accept(ModItems.REFINED_CINNABAR.get());
                        output.accept(ModItems.SPIRIT_IRON_ORE.get());
                        output.accept(ModItems.ROUGH_SPIRIT_IRON_ORE.get());
                        output.accept(ModItems.STAR_SILVER_ORE.get());
                        output.accept(ModItems.ROUGH_STAR_SILVER_ORE.get());
                        output.accept(ModItems.SPIRIT_IRON.get());
                        output.accept(ModItems.STAR_SILVER.get());
                        output.accept(ModItems.ONI_STONE.get());
                        output.accept(ModItems.SPIRIT_SOIL.get());
                        output.accept(ModItems.PORCELAIN_CLAY.get());
                        output.accept(ModItems.PORCELAIN.get());
                        output.accept(ModItems.HIGAN_SOIL.get());
                        output.accept(ModItems.MOON_SAND.get());
                        output.accept(ModItems.SACRED_WOOD.get());
                        output.accept(ModItems.MAGIC_WOOD.get());
                        output.accept(ModItems.ETERNAL_WOOD.get());
                        output.accept(ModItems.SACRED_LEAVES.get());
                        output.accept(ModItems.MAGIC_LEAVES.get());
                        output.accept(ModItems.ETERNAL_LEAVES.get());
                        output.accept(ModItems.SACRED_SAPLING.get());
                        output.accept(ModItems.MAGIC_SAPLING.get());
                        output.accept(ModItems.ETERNAL_SAPLING.get());
                        output.accept(ModItems.SANZU_FLASK.get());
                        output.accept(ModItems.SPIRIT_FISH.get());
                        output.accept(ModItems.MERMAID_SCALE.get());
                        output.accept(ModItems.TIDE_CRYSTAL.get());
                        output.accept(ModItems.DRAGON_SCALE.get());
                        output.accept(ModItems.SPIRIT_HERB.get());
                        output.accept(ModItems.SPIRIT_HERB_SEEDS.get());
                        output.accept(ModItems.GENTIAN.get());
                        output.accept(ModItems.GENTIAN_SEEDS.get());
                        output.accept(ModItems.HIGANBANA.get());
                        output.accept(ModItems.HIGANBANA_SEEDS.get());
                        output.accept(ModItems.MAGIC_MUSHROOM.get());
                        output.accept(ModItems.MAGIC_MUSHROOM_SPORES.get());
                        output.accept(ModItems.SPIRIT_CHARCOAL.get());
                        output.accept(ModItems.TALISMAN_PAPER.get());
                        output.accept(ModItems.SUKIMA_FRAGMENT.get());
                        // 灵铁 / 星银装备阶梯（add-gensokyou-material-uses）
                        output.accept(ModItems.SPIRIT_IRON_PICKAXE.get());
                        output.accept(ModItems.SPIRIT_IRON_AXE.get());
                        output.accept(ModItems.SPIRIT_IRON_SHOVEL.get());
                        output.accept(ModItems.SPIRIT_IRON_HOE.get());
                        output.accept(ModItems.SPIRIT_IRON_SWORD.get());
                        output.accept(ModItems.SPIRIT_IRON_HELMET.get());
                        output.accept(ModItems.SPIRIT_IRON_CHESTPLATE.get());
                        output.accept(ModItems.SPIRIT_IRON_LEGGINGS.get());
                        output.accept(ModItems.SPIRIT_IRON_BOOTS.get());
                        output.accept(ModItems.STAR_SILVER_PICKAXE.get());
                        output.accept(ModItems.STAR_SILVER_AXE.get());
                        output.accept(ModItems.STAR_SILVER_SHOVEL.get());
                        output.accept(ModItems.STAR_SILVER_HOE.get());
                        output.accept(ModItems.STAR_SILVER_SWORD.get());
                        output.accept(ModItems.STAR_SILVER_HELMET.get());
                        output.accept(ModItems.STAR_SILVER_CHESTPLATE.get());
                        output.accept(ModItems.STAR_SILVER_LEGGINGS.get());
                        output.accept(ModItems.STAR_SILVER_BOOTS.get());
                        output.accept(ModItems.PORCELAIN.get());
                        output.accept(ModItems.LANDSCAPING_TOOL.get());
                        output.accept(ModItems.SPIRIT_BOMB.get());
                        output.accept(ModItems.LAEVATEIN.get());
                        ModItems.RITUAL_STONE_ITEMS.forEach(item -> output.accept(item.get()));
                        ModItems.RITUAL_STONE_SLAB_ITEMS.forEach(item -> output.accept(item.get()));
                        ModItems.RITUAL_STONE_STAIRS_ITEMS.forEach(item -> output.accept(item.get()));
                        ModItems.RITUAL_STONE_WALL_ITEMS.forEach(item -> output.accept(item.get()));
                        output.accept(ModItems.RITUAL_CORE_ITEM.get());
                        output.accept(ModItems.RITUAL_PEDESTAL_ITEM.get());
                        output.accept(ModItems.CRYSTAL_ITEM.get());
                        output.accept(ModItems.DANMAKU_ASSEMBLY_BENCH_ITEM.get());
                        output.accept(ModItems.RITUAL_WAND.get());
                        output.accept(ModItems.RITUAL_BUILDER.get());
                        ModItems.SPIRIT_CORES.forEach(core -> output.accept(core.get()));
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
                        // 增幅核：创造栏给<b>未 roll</b> 的裸核（无词条/无晶石色）。
                        // 取出后由 AmpCoreItem.inventoryTick 首次 roll —— 创造栏不该出现
                        // "每次进栏都变"的随机词条，玩家无法在创造栏里比大小。
                        for (AmpCoreItem amp : new AmpCoreItem[]{
                                ModItems.AMP_CORE_T1.get(), ModItems.AMP_CORE_T2.get(),
                                ModItems.AMP_CORE_T3.get()}) {
                            output.accept(new ItemStack(amp));
                        }
                    })
                    .build());
}
