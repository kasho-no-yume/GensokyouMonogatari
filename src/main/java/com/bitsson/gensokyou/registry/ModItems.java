package com.bitsson.gensokyou.registry;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.item.GuideBookItem;
import com.bitsson.gensokyou.item.LaevateinTier;
import com.bitsson.gensokyou.item.RitualWandItem;
import com.bitsson.gensokyou.item.SummonCatalystItem;
import com.bitsson.gensokyou.item.spellcard.IcicleFallCardItem;
import com.bitsson.gensokyou.item.spellcard.LightReflectCardItem;
import com.bitsson.gensokyou.item.spellcard.MusouFuuinCardItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SwordItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(Gensokyou.MODID);

    public static final DeferredItem<Item> PPOINT =
            ITEMS.registerSimpleItem("ppoint", new Item.Properties().stacksTo(64));
    public static final DeferredItem<Item> BPOINT =
            ITEMS.registerSimpleItem("bpoint", new Item.Properties().stacksTo(64));
    public static final DeferredItem<Item> SPELLCARD_STAR =
            ITEMS.registerSimpleItem("spellcard_star", new Item.Properties().stacksTo(16));
    public static final DeferredItem<Item> BROKEN_SPELL_CARD_STAR =
            ITEMS.registerSimpleItem("broken_spell_card_star", new Item.Properties().stacksTo(16));
    public static final DeferredItem<Item> YEN =
            ITEMS.registerSimpleItem("yen", new Item.Properties().stacksTo(64));

    public static final DeferredItem<GuideBookItem> GUIDE_BOOK =
            ITEMS.register("guide_book", () -> new GuideBookItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<SummonCatalystItem> SUMMON_CATALYST =
            ITEMS.register("summon_catalyst", () -> new SummonCatalystItem(
                    new Item.Properties().stacksTo(16), () -> ModEntityTypes.FLANDRE.get()));
    public static final DeferredItem<SummonCatalystItem> CIRNO_CATALYST =
            ITEMS.register("cirno_catalyst", () -> new SummonCatalystItem(
                    new Item.Properties().stacksTo(16), () -> ModEntityTypes.CIRNO.get()));
    public static final DeferredItem<SwordItem> LAEVATEIN =
            ITEMS.register("laevatein", () -> new SwordItem(LaevateinTier.INSTANCE,
                    new Item.Properties().attributes(SwordItem.createAttributes(
                            LaevateinTier.INSTANCE, 3, -2.4F))));

    public static final DeferredItem<MusouFuuinCardItem> MUSOU_FUUIN =
            ITEMS.register("musou_fuuin", () -> new MusouFuuinCardItem(new Item.Properties()));
    public static final DeferredItem<LightReflectCardItem> LIGHT_REFLECT =
            ITEMS.register("light_reflect", () -> new LightReflectCardItem(new Item.Properties()));
    public static final DeferredItem<IcicleFallCardItem> ICICLE_FALL =
            ITEMS.register("icicle_fall", () -> new IcicleFallCardItem(new Item.Properties()));

    public static final DeferredItem<BlockItem> RITUAL_STONE_ITEM =
            ITEMS.registerSimpleBlockItem("ritual_stone", ModBlocks.RITUAL_STONE);
    public static final DeferredItem<BlockItem> RITUAL_CORE_ITEM =
            ITEMS.registerSimpleBlockItem("ritual_core", ModBlocks.RITUAL_CORE);
    public static final DeferredItem<BlockItem> RITUAL_PEDESTAL_ITEM =
            ITEMS.registerSimpleBlockItem("ritual_pedestal", ModBlocks.RITUAL_PEDESTAL);
    public static final DeferredItem<RitualWandItem> RITUAL_WAND =
            ITEMS.register("ritual_wand", () -> new RitualWandItem(
                    new Item.Properties().stacksTo(1)));
}
