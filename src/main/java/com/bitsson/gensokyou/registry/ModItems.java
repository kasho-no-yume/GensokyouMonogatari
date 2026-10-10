package com.bitsson.gensokyou.registry;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.entity.KnifeDanmaku;
import com.bitsson.gensokyou.entity.SphereDanmaku;
import com.bitsson.gensokyou.entity.TalismanDanmaku;
import com.bitsson.gensokyou.item.GuideBookItem;
import com.bitsson.gensokyou.item.CodexOfBeingsItem;
import com.bitsson.gensokyou.item.GensokyouTools;
import com.bitsson.gensokyou.item.equipment.GensokyouAxeItem;
import com.bitsson.gensokyou.item.equipment.GensokyouHoeItem;
import com.bitsson.gensokyou.item.equipment.GensokyouPickaxeItem;
import com.bitsson.gensokyou.item.equipment.GensokyouShovelItem;
import com.bitsson.gensokyou.item.equipment.GensokyouSwordItem;
import com.bitsson.gensokyou.item.LandscapingToolItem;
import com.bitsson.gensokyou.item.SpiritBombItem;
import com.bitsson.gensokyou.item.LaevateinTier;
import com.bitsson.gensokyou.item.MemoryFragmentItem;
import com.bitsson.gensokyou.item.RitualBuilderItem;
import com.bitsson.gensokyou.item.RitualWandItem;
import com.bitsson.gensokyou.item.TieredBlockItem;
import com.bitsson.gensokyou.item.spellcard.IcicleFallCardItem;
import com.bitsson.gensokyou.item.spellcard.LightReflectCardItem;
import com.bitsson.gensokyou.item.spellcard.MusouFuuinCardItem;
import com.bitsson.gensokyou.item.weapon.AmpCoreItem;
import com.bitsson.gensokyou.item.weapon.BulletCoreItem;
import com.bitsson.gensokyou.item.weapon.CoreStats;
import com.bitsson.gensokyou.item.weapon.DanmakuKind;
import com.bitsson.gensokyou.item.weapon.DanmakuWeaponItem;
import com.bitsson.gensokyou.item.weapon.FirePattern;
import com.bitsson.gensokyou.item.weapon.WeaponLevelCoreItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;

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
    public static final DeferredItem<Item> MEMORY_FRAGMENT =
            ITEMS.register("memory_fragment", () -> new MemoryFragmentItem(new Item.Properties().stacksTo(64)));

    // ---------------- 幻想乡素材（add-gensokyo-material-ladder，T1~T2）----------------
    // 矿产/土产/木材共 11 种为可放置方块；成品金属与粗矿仍为物品。
    public static final DeferredItem<BlockItem> CINNABAR =
            ITEMS.registerSimpleBlockItem("cinnabar", ModBlocks.CINNABAR);
    public static final DeferredItem<Item> ROUGH_CINNABAR_ORE = simpleMaterial("rough_cinnabar_ore");
    public static final DeferredItem<Item> REFINED_CINNABAR = simpleMaterial("refined_cinnabar");
    // 原矿（大山津见产出）；成品金属 spirit_iron / star_silver 由金山彦命之仪炼出（后续）
    public static final DeferredItem<BlockItem> SPIRIT_IRON_ORE =
            ITEMS.registerSimpleBlockItem("spirit_iron_ore", ModBlocks.SPIRIT_IRON_ORE);
    public static final DeferredItem<Item> ROUGH_SPIRIT_IRON_ORE = simpleMaterial("rough_spirit_iron_ore");
    public static final DeferredItem<BlockItem> STAR_SILVER_ORE =
            ITEMS.registerSimpleBlockItem("star_silver_ore", ModBlocks.STAR_SILVER_ORE);
    public static final DeferredItem<Item> ROUGH_STAR_SILVER_ORE = simpleMaterial("rough_star_silver_ore");
    public static final DeferredItem<Item> SPIRIT_IRON = simpleMaterial("spirit_iron");
    public static final DeferredItem<Item> STAR_SILVER = simpleMaterial("star_silver");
    public static final DeferredItem<BlockItem> ONI_STONE =
            ITEMS.registerSimpleBlockItem("oni_stone", ModBlocks.ONI_STONE);
    public static final DeferredItem<BlockItem> SPIRIT_SOIL =
            ITEMS.registerSimpleBlockItem("spirit_soil", ModBlocks.SPIRIT_SOIL);
    public static final DeferredItem<BlockItem> PORCELAIN_CLAY =
            ITEMS.registerSimpleBlockItem("porcelain_clay", ModBlocks.PORCELAIN_CLAY);
    /** 瓷器：瓷土经金山彦命煅炉精炼的产物，mod 药水强效档的封装耗材。 */
    public static final DeferredItem<Item> PORCELAIN = simpleMaterial("porcelain");
    public static final DeferredItem<BlockItem> HIGAN_SOIL =
            ITEMS.registerSimpleBlockItem("higan_soil", ModBlocks.HIGAN_SOIL);
    public static final DeferredItem<BlockItem> MOON_SAND =
            ITEMS.registerSimpleBlockItem("moon_sand", ModBlocks.MOON_SAND);
    public static final DeferredItem<BlockItem> SACRED_WOOD =
            ITEMS.registerSimpleBlockItem("sacred_wood", ModBlocks.SACRED_WOOD);
    public static final DeferredItem<BlockItem> MAGIC_WOOD =
            ITEMS.registerSimpleBlockItem("magic_wood", ModBlocks.MAGIC_WOOD);
    public static final DeferredItem<BlockItem> ETERNAL_WOOD =
            ITEMS.registerSimpleBlockItem("eternal_wood", ModBlocks.ETERNAL_WOOD);
    public static final DeferredItem<BlockItem> SACRED_LEAVES =
            ITEMS.registerSimpleBlockItem("sacred_leaves", ModBlocks.SACRED_LEAVES);
    public static final DeferredItem<BlockItem> MAGIC_LEAVES =
            ITEMS.registerSimpleBlockItem("magic_leaves", ModBlocks.MAGIC_LEAVES);
    public static final DeferredItem<BlockItem> ETERNAL_LEAVES =
            ITEMS.registerSimpleBlockItem("eternal_leaves", ModBlocks.ETERNAL_LEAVES);
    public static final DeferredItem<BlockItem> SACRED_SAPLING =
            ITEMS.registerSimpleBlockItem("sacred_sapling", ModBlocks.SACRED_SAPLING);
    public static final DeferredItem<BlockItem> MAGIC_SAPLING =
            ITEMS.registerSimpleBlockItem("magic_sapling", ModBlocks.MAGIC_SAPLING);
    public static final DeferredItem<BlockItem> ETERNAL_SAPLING =
            ITEMS.registerSimpleBlockItem("eternal_sapling", ModBlocks.ETERNAL_SAPLING);
    public static final DeferredItem<Item> SANZU_FLASK = ITEMS.registerSimpleItem("sanzu_flask", new Item.Properties().stacksTo(16));
    public static final DeferredItem<Item> SPIRIT_FISH = simpleMaterial("spirit_fish");
    public static final DeferredItem<Item> MERMAID_SCALE = simpleMaterial("mermaid_scale");
    public static final DeferredItem<Item> TIDE_CRYSTAL = simpleMaterial("tide_crystal");
    public static final DeferredItem<Item> DRAGON_SCALE = simpleMaterial("dragon_scale");
    /** 植物（由农业产出）：灵草 / 龙胆 / 彼岸花 / 魔法菇。 */
    public static final DeferredItem<BlockItem> SPIRIT_HERB =
            ITEMS.registerSimpleBlockItem("spirit_herb", ModBlocks.SPIRIT_HERB);
    public static final DeferredItem<BlockItem> GENTIAN =
            ITEMS.registerSimpleBlockItem("gentian", ModBlocks.GENTIAN);
    public static final DeferredItem<BlockItem> HIGANBANA =
            ITEMS.registerSimpleBlockItem("higanbana", ModBlocks.HIGANBANA);
    public static final DeferredItem<BlockItem> MAGIC_MUSHROOM =
            ITEMS.registerSimpleBlockItem("magic_mushroom", ModBlocks.MAGIC_MUSHROOM);

    /**
     * 植物种子（茅野姬神花亭产出）：四种作物线的启动物。
     *
     * <p><b>与原版种子物品刻意区分</b>：{@code ItemNameBlockItem} 会带「种子」后缀本地化键，
     * 且右击逻辑与原版不同（不需要忠实转译方块描述）。此处用普通 {@link BlockItem}，
     * 描述名走自己的物品键，避免出现「龙胆的种子」这类由方块名拼接出来的怪名。
     */
    public static final DeferredItem<BlockItem> SPIRIT_HERB_SEEDS =
            ITEMS.registerSimpleBlockItem("spirit_herb_seeds", ModBlocks.SPIRIT_HERB_CROP);
    public static final DeferredItem<BlockItem> GENTIAN_SEEDS =
            ITEMS.registerSimpleBlockItem("gentian_seeds", ModBlocks.GENTIAN_CROP);
    public static final DeferredItem<BlockItem> HIGANBANA_SEEDS =
            ITEMS.registerSimpleBlockItem("higanbana_seeds", ModBlocks.HIGANBANA_CROP);
    public static final DeferredItem<BlockItem> MAGIC_MUSHROOM_SPORES =
            ITEMS.registerSimpleBlockItem("magic_mushroom_spores", ModBlocks.MAGIC_MUSHROOM_CROP);

    /**
     * 灵土耕地刻意<b>不注册物品形态</b>：只能由锄头锄 {@link ModBlocks#SPIRIT_SOIL} 得到，
     * 破坏后退回灵土（与原版耕地退泥土同构）。创造栏不给，JEI 不可见。
     */
    public static final DeferredItem<Item> SPIRIT_CHARCOAL = simpleMaterial("spirit_charcoal");
    public static final DeferredItem<Item> TALISMAN_PAPER = simpleMaterial("talisman_paper");
    public static final DeferredItem<Item> SUKIMA_FRAGMENT = simpleMaterial("sukima_fragment");

    private static DeferredItem<Item> simpleMaterial(String id) {
        return ITEMS.registerSimpleItem(id, new Item.Properties().stacksTo(64));
    }

    public static final DeferredItem<GuideBookItem> GUIDE_BOOK =
            ITEMS.register("guide_book", () -> new GuideBookItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<CodexOfBeingsItem> CODEX_OF_BEINGS =
            ITEMS.register("codex_of_beings", () -> new CodexOfBeingsItem(new Item.Properties().stacksTo(1)));
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

    public static final DeferredItem<BlockItem> RITUAL_CORE_ITEM =
            ITEMS.registerSimpleBlockItem("ritual_core", ModBlocks.RITUAL_CORE);
    /** 仪式石物品（品阶 0-5，名字染品阶色）。 */
    public static final List<DeferredItem<TieredBlockItem>> RITUAL_STONE_ITEMS = new ArrayList<>();
    /** 祭品台物品：单一默认 0 阶外观，名字不染品阶色（变色是放置后台子的视觉）。 */
    public static final DeferredItem<BlockItem> RITUAL_PEDESTAL_ITEM =
            ITEMS.registerSimpleBlockItem("ritual_pedestal", ModBlocks.RITUAL_PEDESTAL);
    /** 虹彩水晶：独立装饰方块物品。 */
    public static final DeferredItem<BlockItem> CRYSTAL_ITEM =
            ITEMS.registerSimpleBlockItem("crystal", ModBlocks.CRYSTAL);
    /** 弹幕方术装配台：主武器模块化修改方块。 */
    public static final DeferredItem<BlockItem> DANMAKU_ASSEMBLY_BENCH_ITEM =
            ITEMS.registerSimpleBlockItem("danmaku_assembly_bench", ModBlocks.DANMAKU_ASSEMBLY_BENCH);
    /** 仪式石装饰变种物品（台阶/楼梯/墙 × 品阶 0-5，名字染品阶色）。 */
    public static final List<DeferredItem<TieredBlockItem>> RITUAL_STONE_SLAB_ITEMS = new ArrayList<>();
    public static final List<DeferredItem<TieredBlockItem>> RITUAL_STONE_STAIRS_ITEMS = new ArrayList<>();
    public static final List<DeferredItem<TieredBlockItem>> RITUAL_STONE_WALL_ITEMS = new ArrayList<>();

    static {
        for (int i = 0; i < ModBlocks.TIER_COUNT; i++) {
            final int tier = i;
            RITUAL_STONE_ITEMS.add(ITEMS.register("ritual_stone_" + i,
                    () -> new TieredBlockItem(ModBlocks.RITUAL_STONES.get(tier).get(),
                            new Item.Properties(), tier)));
            RITUAL_STONE_SLAB_ITEMS.add(ITEMS.register("ritual_stone_slab_" + tier,
                    () -> new TieredBlockItem(ModBlocks.RITUAL_STONE_SLABS.get(tier).get(),
                            new Item.Properties(), tier)));
            RITUAL_STONE_STAIRS_ITEMS.add(ITEMS.register("ritual_stone_stairs_" + tier,
                    () -> new TieredBlockItem(ModBlocks.RITUAL_STONE_STAIRS.get(tier).get(),
                            new Item.Properties(), tier)));
            RITUAL_STONE_WALL_ITEMS.add(ITEMS.register("ritual_stone_wall_" + tier,
                    () -> new TieredBlockItem(ModBlocks.RITUAL_STONE_WALLS.get(tier).get(),
                            new Item.Properties(), tier)));
        }
    }
    public static final DeferredItem<RitualWandItem> RITUAL_WAND =
            ITEMS.register("ritual_wand", () -> new RitualWandItem(
                    new Item.Properties().stacksTo(1)));
    public static final DeferredItem<RitualBuilderItem> RITUAL_BUILDER =
            ITEMS.register("ritual_builder", () -> new RitualBuilderItem(
                    new Item.Properties().stacksTo(1)));

    /** 六阶灵力核心（电池）：容量 50000×12ⁿ / 速率 1000×8ⁿ，品阶=新物品新定值。 */
    public static final List<DeferredItem<com.bitsson.gensokyou.spirit.SpiritCoreItem>> SPIRIT_CORES =
            registerSpiritCores();

    // ---------------- 灵铁 / 星银装备阶梯（add-gensokyou-material-uses）----------------
    /**
     * 工具属性：复刻原版 {@code createAttributes}，让攻击伤害/攻击速度既真正生效、又出现在
     * tooltip 上。耐久由 {@code TieredItem} 从 {@link Tier#getUses()} 自动套用，此处不重复设。
     */
    private static Item.Properties pickaxeProps(Tier tier) {
        return new Item.Properties().stacksTo(1)
                .attributes(PickaxeItem.createAttributes(tier, 1.0F, -2.8F));
    }

    private static Item.Properties axeProps(Tier tier) {
        return new Item.Properties().stacksTo(1)
                .attributes(AxeItem.createAttributes(tier, 6.0F, -3.2F));
    }

    private static Item.Properties shovelProps(Tier tier) {
        return new Item.Properties().stacksTo(1)
                .attributes(ShovelItem.createAttributes(tier, 1.5F, -3.0F));
    }

    private static Item.Properties hoeProps(Tier tier) {
        return new Item.Properties().stacksTo(1)
                .attributes(HoeItem.createAttributes(tier, 0.0F, -3.0F));
    }

    private static Item.Properties swordProps(Tier tier) {
        return new Item.Properties().stacksTo(1)
                .attributes(SwordItem.createAttributes(tier, 3, -2.4F));
    }

    /** 盔甲属性：{@code ArmorItem} 只自动套用防御，耐久须由注册侧显式给。 */
    private static Item.Properties armorProps(ArmorItem.Type type, int durabilityMultiplier) {
        return new Item.Properties().stacksTo(1).durability(type.getDurability(durabilityMultiplier));
    }

    /** 灵铁 5 件工具。 */
    public static final DeferredItem<GensokyouPickaxeItem> SPIRIT_IRON_PICKAXE =
            ITEMS.register("spirit_iron_pickaxe",
                    () -> new GensokyouPickaxeItem(GensokyouTools.SPIRIT_IRON,
                            pickaxeProps(GensokyouTools.SPIRIT_IRON), true, false));
    public static final DeferredItem<GensokyouAxeItem> SPIRIT_IRON_AXE =
            ITEMS.register("spirit_iron_axe",
                    () -> new GensokyouAxeItem(GensokyouTools.SPIRIT_IRON,
                            axeProps(GensokyouTools.SPIRIT_IRON), true));
    public static final DeferredItem<GensokyouShovelItem> SPIRIT_IRON_SHOVEL =
            ITEMS.register("spirit_iron_shovel",
                    () -> new GensokyouShovelItem(GensokyouTools.SPIRIT_IRON,
                            shovelProps(GensokyouTools.SPIRIT_IRON), true));
    public static final DeferredItem<GensokyouHoeItem> SPIRIT_IRON_HOE =
            ITEMS.register("spirit_iron_hoe",
                    () -> new GensokyouHoeItem(GensokyouTools.SPIRIT_IRON,
                            hoeProps(GensokyouTools.SPIRIT_IRON), true));
    public static final DeferredItem<GensokyouSwordItem> SPIRIT_IRON_SWORD =
            ITEMS.register("spirit_iron_sword",
                    () -> new GensokyouSwordItem(GensokyouTools.SPIRIT_IRON,
                            swordProps(GensokyouTools.SPIRIT_IRON)));

    /** 星银 5 件工具。 */
    /**
     * 星银镐自带精准采集。这里直接写 {@code true} 而不是读 config：
     * 注册期 config 可能尚未就绪，且"是否自带"是<b>物品固有属性</b>而非可调数值——
     * 想关掉精准采集的整合包应改掉 {@code GensokyouTools.STAR_SILVER} 本身，
     * 而不是让同一件物品时有时无（那会让 JEI / 附魔提示自相矛盾）。
     * {@code STAR_SILVER_SILK_TOUCH} 配置保留给工具提示与调试查询使用。
     */
    public static final DeferredItem<GensokyouPickaxeItem> STAR_SILVER_PICKAXE =
            ITEMS.register("star_silver_pickaxe",
                    () -> new GensokyouPickaxeItem(GensokyouTools.STAR_SILVER,
                            pickaxeProps(GensokyouTools.STAR_SILVER), false, true));
    public static final DeferredItem<GensokyouAxeItem> STAR_SILVER_AXE =
            ITEMS.register("star_silver_axe",
                    () -> new GensokyouAxeItem(GensokyouTools.STAR_SILVER,
                            axeProps(GensokyouTools.STAR_SILVER), false));
    public static final DeferredItem<GensokyouShovelItem> STAR_SILVER_SHOVEL =
            ITEMS.register("star_silver_shovel",
                    () -> new GensokyouShovelItem(GensokyouTools.STAR_SILVER,
                            shovelProps(GensokyouTools.STAR_SILVER), false));
    public static final DeferredItem<GensokyouHoeItem> STAR_SILVER_HOE =
            ITEMS.register("star_silver_hoe",
                    () -> new GensokyouHoeItem(GensokyouTools.STAR_SILVER,
                            hoeProps(GensokyouTools.STAR_SILVER), false));
    public static final DeferredItem<GensokyouSwordItem> STAR_SILVER_SWORD =
            ITEMS.register("star_silver_sword",
                    () -> new GensokyouSwordItem(GensokyouTools.STAR_SILVER,
                            swordProps(GensokyouTools.STAR_SILVER)));

    /** 灵铁 4 件盔甲（耐久倍率 33，对齐钻石）。 */
    public static final DeferredItem<ArmorItem> SPIRIT_IRON_HELMET =
            ITEMS.register("spirit_iron_helmet",
                    () -> new ArmorItem(ModEquipmentMaterials.SPIRIT_IRON, ArmorItem.Type.HELMET,
                            armorProps(ArmorItem.Type.HELMET, 33)));
    public static final DeferredItem<ArmorItem> SPIRIT_IRON_CHESTPLATE =
            ITEMS.register("spirit_iron_chestplate",
                    () -> new ArmorItem(ModEquipmentMaterials.SPIRIT_IRON, ArmorItem.Type.CHESTPLATE,
                            armorProps(ArmorItem.Type.CHESTPLATE, 33)));
    public static final DeferredItem<ArmorItem> SPIRIT_IRON_LEGGINGS =
            ITEMS.register("spirit_iron_leggings",
                    () -> new ArmorItem(ModEquipmentMaterials.SPIRIT_IRON, ArmorItem.Type.LEGGINGS,
                            armorProps(ArmorItem.Type.LEGGINGS, 33)));
    public static final DeferredItem<ArmorItem> SPIRIT_IRON_BOOTS =
            ITEMS.register("spirit_iron_boots",
                    () -> new ArmorItem(ModEquipmentMaterials.SPIRIT_IRON, ArmorItem.Type.BOOTS,
                            armorProps(ArmorItem.Type.BOOTS, 33)));

    /** 星银 4 件盔甲（耐久倍率 40，略高于下界合金的 37）。 */
    public static final DeferredItem<ArmorItem> STAR_SILVER_HELMET =
            ITEMS.register("star_silver_helmet",
                    () -> new ArmorItem(ModEquipmentMaterials.STAR_SILVER, ArmorItem.Type.HELMET,
                            armorProps(ArmorItem.Type.HELMET, 40)));
    public static final DeferredItem<ArmorItem> STAR_SILVER_CHESTPLATE =
            ITEMS.register("star_silver_chestplate",
                    () -> new ArmorItem(ModEquipmentMaterials.STAR_SILVER, ArmorItem.Type.CHESTPLATE,
                            armorProps(ArmorItem.Type.CHESTPLATE, 40)));
    public static final DeferredItem<ArmorItem> STAR_SILVER_LEGGINGS =
            ITEMS.register("star_silver_leggings",
                    () -> new ArmorItem(ModEquipmentMaterials.STAR_SILVER, ArmorItem.Type.LEGGINGS,
                            armorProps(ArmorItem.Type.LEGGINGS, 40)));
    public static final DeferredItem<ArmorItem> STAR_SILVER_BOOTS =
            ITEMS.register("star_silver_boots",
                    () -> new ArmorItem(ModEquipmentMaterials.STAR_SILVER, ArmorItem.Type.BOOTS,
                            armorProps(ArmorItem.Type.BOOTS, 40)));

    // ---------------- 妙妙工具（add-gensokyou-material-uses）----------------

    /** 整地器：可放置方块物品，右键开界面配置长/宽/高盒体并启动整地。 */
    public static final DeferredItem<com.bitsson.gensokyou.item.LandscapingToolItem> LANDSCAPING_TOOL =
            ITEMS.register("landscaping_tool", () -> new LandscapingToolItem(
                    ModBlocks.LANDSCAPING.get(), new Item.Properties()));
    /** 灵力引爆器：可放置方块物品、可配置、可重复使用的引爆物。 */
    public static final DeferredItem<com.bitsson.gensokyou.item.SpiritBombItem> SPIRIT_BOMB =
            ITEMS.register("spirit_bomb", () -> new SpiritBombItem(
                    ModBlocks.SPIRIT_BOMB.get(), new Item.Properties()));

    private static List<DeferredItem<com.bitsson.gensokyou.spirit.SpiritCoreItem>> registerSpiritCores() {
        List<DeferredItem<com.bitsson.gensokyou.spirit.SpiritCoreItem>> cores = new ArrayList<>();
        for (int tier = 0; tier <= 5; tier++) {
            final int t = tier;
            final long capacity = 50000L * pow(12L, t);
            final int rate = 1000 * (int) pow(8L, t);
            cores.add(ITEMS.register("spirit_core_" + t,
                    () -> new com.bitsson.gensokyou.spirit.SpiritCoreItem(t, capacity, rate)));
        }
        return List.copyOf(cores);
    }

    private static long pow(long base, int exp) {
        long result = 1L;
        for (int i = 0; i < exp; i++) {
            result *= base;
        }
        return result;
    }

    // ---------------- 弹幕主武器与三槽核 ----------------
    private static ItemAttributeModifiers noMeleeAttributes() {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(
                        ResourceLocation.fromNamespaceAndPath(Gensokyou.MODID, "weapon_no_melee"), -1.0D,
                        AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .build();
    }

    private static CoreStats stats(DoubleSupplier mult, IntSupplier cost, IntSupplier rate, IntSupplier tier) {
        return new CoreStats(() -> (float) mult.getAsDouble(), cost, rate, tier);
    }

    /** 激光 pattern 的占位工厂：激光在 WeaponFiring 内按眼位+视线直接构造，不会走到这里。 */
    private static final FirePattern.DanmakuFactory UNSUPPORTED_FACTORY =
            (level, owner, damage) -> {
                throw new UnsupportedOperationException("laser pattern is constructed directly by WeaponFiring");
            };

    public static final DeferredItem<DanmakuWeaponItem> DANMAKU_WEAPON =
            ITEMS.register("danmaku_weapon", () -> new DanmakuWeaponItem(
                    new Item.Properties().stacksTo(1).attributes(noMeleeAttributes())));

    public static final DeferredItem<BulletCoreItem> CORE_SPHERE_SINGLE =
            ITEMS.register("core_sphere_single", () -> new BulletCoreItem(
                    new Item.Properties().stacksTo(1),
                    FirePattern.ofBullet(
                             DanmakuKind.SPHERE,
                            (level, owner, damage) -> new SphereDanmaku(level, owner, damage, 0, 0.4F, Set.of()),
                            () -> 1,
                            () -> 0D, () -> 0.9D, () -> 0D),
                    stats(GensokyouConfig.CORE_SPHERE_MULT::get, GensokyouConfig.CORE_SPHERE_SP_COST::get,
                            GensokyouConfig.CORE_SPHERE_RATE::get, GensokyouConfig.CORE_SPHERE_REQ_TIER::get)));

    public static final DeferredItem<BulletCoreItem> CORE_SPHERE_SHOTGUN =
            ITEMS.register("core_sphere_shotgun", () -> new BulletCoreItem(
                    new Item.Properties().stacksTo(1),
                    FirePattern.ofBullet(
                             DanmakuKind.SPHERE,
                            (level, owner, damage) -> new SphereDanmaku(level, owner, damage, 0, 0.3F, Set.of()),
                            GensokyouConfig.CORE_SHOTGUN_COUNT::get,
                            GensokyouConfig.CORE_SHOTGUN_SPREAD::get,
                            GensokyouConfig.CORE_SHOTGUN_SPEED::get,
                            GensokyouConfig.CORE_SHOTGUN_LIFETIME::get),
                    stats(GensokyouConfig.CORE_SHOTGUN_MULT::get, GensokyouConfig.CORE_SHOTGUN_SP_COST::get,
                            GensokyouConfig.CORE_SHOTGUN_RATE::get, GensokyouConfig.CORE_SHOTGUN_REQ_TIER::get)));

    public static final DeferredItem<BulletCoreItem> CORE_KNIFE =
            ITEMS.register("core_knife", () -> new BulletCoreItem(
                    new Item.Properties().stacksTo(1),
                    FirePattern.ofBullet(
                             DanmakuKind.KNIFE,
                            (level, owner, damage) -> new KnifeDanmaku(level, owner, damage, Set.of()),
                            () -> 1, () -> 0D, GensokyouConfig.CORE_KNIFE_SPEED::get, () -> 0D),
                    stats(GensokyouConfig.CORE_KNIFE_MULT::get, GensokyouConfig.CORE_KNIFE_SP_COST::get,
                            GensokyouConfig.CORE_KNIFE_RATE::get, GensokyouConfig.CORE_KNIFE_REQ_TIER::get)));

    public static final DeferredItem<BulletCoreItem> CORE_TALISMAN =
            ITEMS.register("core_talisman", () -> new BulletCoreItem(
                    new Item.Properties().stacksTo(1),
                    FirePattern.ofTalisman(
                             DanmakuKind.TALISMAN,
                            (level, owner, damage) -> new TalismanDanmaku(level, owner, damage, 0, null,
                                    0D, Set.of()),
                            GensokyouConfig.CORE_TALISMAN_SPEED::get,
                            GensokyouConfig.CORE_TALISMAN_SENSITIVITY::get),
                    stats(GensokyouConfig.CORE_TALISMAN_MULT::get, GensokyouConfig.CORE_TALISMAN_SP_COST::get,
                            GensokyouConfig.CORE_TALISMAN_RATE::get, GensokyouConfig.CORE_TALISMAN_REQ_TIER::get)));

    public static final DeferredItem<BulletCoreItem> CORE_LASER_GUN =
            ITEMS.register("core_laser_gun", () -> new BulletCoreItem(
                    new Item.Properties().stacksTo(1),
                    FirePattern.ofLaser(DanmakuKind.LASER, UNSUPPORTED_FACTORY, GensokyouConfig.CORE_LASER_GUN_LENGTH::get,
                            GensokyouConfig.CORE_LASER_GUN_RADIUS::get, GensokyouConfig.CORE_LASER_GUN_DELAY::get,
                            GensokyouConfig.CORE_LASER_GUN_DURATION::get),
                    stats(GensokyouConfig.CORE_LASER_GUN_MULT::get, GensokyouConfig.CORE_LASER_GUN_SP_COST::get,
                            GensokyouConfig.CORE_LASER_GUN_RATE::get, GensokyouConfig.CORE_LASER_GUN_REQ_TIER::get)));

    public static final DeferredItem<BulletCoreItem> CORE_LASER_CANNON =
            ITEMS.register("core_laser_cannon", () -> new BulletCoreItem(
                    new Item.Properties().stacksTo(1),
                    FirePattern.ofLaser(DanmakuKind.LASER, UNSUPPORTED_FACTORY, GensokyouConfig.CORE_LASER_CANNON_LENGTH::get,
                            GensokyouConfig.CORE_LASER_CANNON_RADIUS::get, GensokyouConfig.CORE_LASER_CANNON_DELAY::get,
                            GensokyouConfig.CORE_LASER_CANNON_DURATION::get),
                    stats(GensokyouConfig.CORE_LASER_CANNON_MULT::get, GensokyouConfig.CORE_LASER_CANNON_SP_COST::get,
                            GensokyouConfig.CORE_LASER_CANNON_RATE::get, GensokyouConfig.CORE_LASER_CANNON_REQ_TIER::get)));

    public static final DeferredItem<WeaponLevelCoreItem> WEAPON_CORE_LV1 =
            ITEMS.register("weapon_core_lv1", () -> new WeaponLevelCoreItem(
                    new Item.Properties().stacksTo(1), () -> 1));
    public static final DeferredItem<WeaponLevelCoreItem> WEAPON_CORE_LV2 =
            ITEMS.register("weapon_core_lv2", () -> new WeaponLevelCoreItem(
                    new Item.Properties().stacksTo(1), () -> 2));
    public static final DeferredItem<WeaponLevelCoreItem> WEAPON_CORE_LV3 =
            ITEMS.register("weapon_core_lv3", () -> new WeaponLevelCoreItem(
                    new Item.Properties().stacksTo(1), () -> 3));

    public static final DeferredItem<AmpCoreItem> AMP_CORE_T1 =
            ITEMS.register("amp_core_t1", () -> new AmpCoreItem(new Item.Properties().stacksTo(1), () -> 1));
    public static final DeferredItem<AmpCoreItem> AMP_CORE_T2 =
            ITEMS.register("amp_core_t2", () -> new AmpCoreItem(new Item.Properties().stacksTo(1), () -> 2));
    public static final DeferredItem<AmpCoreItem> AMP_CORE_T3 =
            ITEMS.register("amp_core_t3", () -> new AmpCoreItem(new Item.Properties().stacksTo(1), () -> 3));
}
