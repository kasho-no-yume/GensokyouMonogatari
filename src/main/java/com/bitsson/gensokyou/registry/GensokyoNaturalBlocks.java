package com.bitsson.gensokyou.registry;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.block.SpiritPlantBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.PlaceOnWaterBlockItem;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.neoforged.neoforge.registries.DeferredBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** Natural materials and decorative blocks added with the island's biome pass. */
public final class GensokyoNaturalBlocks {
    private static final List<String> BLOCK_ITEMS = new ArrayList<>();

    private GensokyoNaturalBlocks() {}

    public static void register() {
        plants(SpiritPlantBlock::new, "bell_tuft", "cloud_azalea", "wind_plume", "bluecurl_fern",
                "dappled_mondo", "mist_sedge", "purple_sage", "ashen_spikemoss",
                "willow_scrub", "field_daisy", "golden_lovegrass",
                "maple_litter", "bamboo_litter", "scarlet_dew_rose");
        plants(NaturalRockPlantBlock::new, "moon_pink", "ashcap", "shore_iris", "salt_whisker");
        plants(GlowLichenBlock::new, "rock_fan_moss", "ink_moss", "ember_lichen", "ear_bracket",
                "wine_creeper");
        plants(HangingRootsBlock::new, "bead_vine", "snowfall_vine", "black_roots");
        plants(SeagrassBlock::new, "silver_ribbon_grass", "smoke_fan_algae", "sunk_thread_algae", "ferry_lantern_algae");
        DeferredBlock<Block> lily = ModBlocks.BLOCKS.registerBlock("mist_lily", WaterlilyBlock::new, plantProperties());
        ModItems.ITEMS.register("mist_lily", () -> new PlaceOnWaterBlockItem(lily.get(), new Item.Properties()));
        BLOCK_ITEMS.add("mist_lily");
        woodFamily("starcrown", "starcrown_tree");
        woodFamily("scarlet_maple", "scarlet_maple_tree");
        add("starcrown_planks", Block::new, Properties.ofFullCopy(Blocks.OAK_PLANKS));
        for (String id : List.of("wind_mica", "inkglow_stone", "ringed_jade", "snowmarrow_crystal",
                "echo_crystal_ore", "rose_garnet", "mistband_crystal")) {
            add(id, Block::new, Properties.ofFullCopy(Blocks.IRON_ORE));
        }
        add("nether_black_sand", Block::new, Properties.ofFullCopy(Blocks.SAND).requiresCorrectToolForDrops());
        plants(SpiritPlantBlock::new, "moonjoint_shoot");
        ModItems.ITEMS.register("moonjoint_tips", () -> new Item(new Item.Properties().food(
                new net.minecraft.world.food.FoodProperties.Builder().nutrition(2).saturationModifier(0.3F).build())));
        for (String id : List.of("echo_crystal_shard", "black_sand_iron", "mist_reed_fiber")) {
            ModItems.ITEMS.registerSimpleItem(id);
        }
    }

    public static void addCreativeItems(net.minecraft.world.item.CreativeModeTab.Output output) {
        for (String name : BLOCK_ITEMS) output.accept(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(Gensokyou.id(name)));
        for (String name : List.of("moonjoint_tips", "echo_crystal_shard", "black_sand_iron", "mist_reed_fiber")) {
            output.accept(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(Gensokyou.id(name)));
        }
    }

    public static TreeGrower treeGrower(String name, String feature, boolean giant) {
        var key = Optional.of(ResourceKey.create(Registries.CONFIGURED_FEATURE, Gensokyou.id(feature)));
        return new TreeGrower(name, giant ? key : Optional.empty(), giant ? Optional.empty() : key, Optional.empty());
    }

    private static void woodFamily(String name, String feature) {
        add(name + "_log", RotatedPillarBlock::new, Properties.ofFullCopy(Blocks.OAK_LOG));
        add(name + "_leaves", LeavesBlock::new, Properties.ofFullCopy(Blocks.OAK_LEAVES));
        TreeGrower grower = treeGrower(name, feature, name.equals("starcrown"));
        add(name + "_sapling", props -> new SaplingBlock(grower, props), plantProperties().randomTicks());
    }

    private static void plants(Function<Properties, Block> type, String... names) {
        for (String name : names) add(name, type, plantProperties());
    }

    private static Properties plantProperties() {
        return Properties.of().noCollission().noOcclusion().instabreak().sound(SoundType.GRASS);
    }

    private static void add(String name, Function<Properties, Block> type, Properties properties) {
        var block = ModBlocks.BLOCKS.registerBlock(name, type, properties);
        ModItems.ITEMS.registerSimpleBlockItem(name, block);
        BLOCK_ITEMS.add(name);
    }
}
