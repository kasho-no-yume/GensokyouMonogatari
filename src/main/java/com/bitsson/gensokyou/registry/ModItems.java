package com.bitsson.gensokyou.registry;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.entity.KnifeDanmaku;
import com.bitsson.gensokyou.entity.SphereDanmaku;
import com.bitsson.gensokyou.entity.TalismanDanmaku;
import com.bitsson.gensokyou.item.GuideBookItem;
import com.bitsson.gensokyou.item.LaevateinTier;
import com.bitsson.gensokyou.item.RitualBuilderItem;
import com.bitsson.gensokyou.item.RitualWandItem;
import com.bitsson.gensokyou.item.SummonCatalystItem;
import com.bitsson.gensokyou.item.TieredBlockItem;
import com.bitsson.gensokyou.item.spellcard.IcicleFallCardItem;
import com.bitsson.gensokyou.item.spellcard.LightReflectCardItem;
import com.bitsson.gensokyou.item.spellcard.MusouFuuinCardItem;
import com.bitsson.gensokyou.item.weapon.AmpCoreItem;
import com.bitsson.gensokyou.item.weapon.BulletCoreItem;
import com.bitsson.gensokyou.item.weapon.CoreStats;
import com.bitsson.gensokyou.item.weapon.DanmakuWeaponItem;
import com.bitsson.gensokyou.item.weapon.FirePattern;
import com.bitsson.gensokyou.item.weapon.WeaponLevelCoreItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SwordItem;
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

    public static final DeferredItem<BlockItem> RITUAL_CORE_ITEM =
            ITEMS.registerSimpleBlockItem("ritual_core", ModBlocks.RITUAL_CORE);
    /** 仪式石物品（品阶 0-5，名字染品阶色）。 */
    public static final List<DeferredItem<TieredBlockItem>> RITUAL_STONE_ITEMS = new ArrayList<>();
    /** 祭品台物品：单一默认 0 阶外观，名字不染品阶色（变色是放置后台子的视觉）。 */
    public static final DeferredItem<BlockItem> RITUAL_PEDESTAL_ITEM =
            ITEMS.registerSimpleBlockItem("ritual_pedestal", ModBlocks.RITUAL_PEDESTAL);
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
                            (level, owner, damage) -> new SphereDanmaku(level, owner, damage, 0, 0.4F, Set.of()),
                            () -> 1,
                            () -> 0D, () -> 0.9D, () -> 0D),
                    stats(GensokyouConfig.CORE_SPHERE_MULT::get, GensokyouConfig.CORE_SPHERE_SP_COST::get,
                            GensokyouConfig.CORE_SPHERE_RATE::get, GensokyouConfig.CORE_SPHERE_REQ_TIER::get)));

    public static final DeferredItem<BulletCoreItem> CORE_SPHERE_SHOTGUN =
            ITEMS.register("core_sphere_shotgun", () -> new BulletCoreItem(
                    new Item.Properties().stacksTo(1),
                    FirePattern.ofBullet(
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
                            (level, owner, damage) -> new KnifeDanmaku(level, owner, damage, Set.of()),
                            () -> 1, () -> 0D, GensokyouConfig.CORE_KNIFE_SPEED::get, () -> 0D),
                    stats(GensokyouConfig.CORE_KNIFE_MULT::get, GensokyouConfig.CORE_KNIFE_SP_COST::get,
                            GensokyouConfig.CORE_KNIFE_RATE::get, GensokyouConfig.CORE_KNIFE_REQ_TIER::get)));

    public static final DeferredItem<BulletCoreItem> CORE_TALISMAN =
            ITEMS.register("core_talisman", () -> new BulletCoreItem(
                    new Item.Properties().stacksTo(1),
                    FirePattern.ofTalisman(
                            (level, owner, damage) -> new TalismanDanmaku(level, owner, damage, 0, null,
                                    0D, Set.of()),
                            GensokyouConfig.CORE_TALISMAN_SPEED::get,
                            GensokyouConfig.CORE_TALISMAN_SENSITIVITY::get),
                    stats(GensokyouConfig.CORE_TALISMAN_MULT::get, GensokyouConfig.CORE_TALISMAN_SP_COST::get,
                            GensokyouConfig.CORE_TALISMAN_RATE::get, GensokyouConfig.CORE_TALISMAN_REQ_TIER::get)));

    public static final DeferredItem<BulletCoreItem> CORE_LASER_GUN =
            ITEMS.register("core_laser_gun", () -> new BulletCoreItem(
                    new Item.Properties().stacksTo(1),
                    FirePattern.ofLaser(UNSUPPORTED_FACTORY, GensokyouConfig.CORE_LASER_GUN_LENGTH::get,
                            GensokyouConfig.CORE_LASER_GUN_RADIUS::get, GensokyouConfig.CORE_LASER_GUN_DELAY::get,
                            GensokyouConfig.CORE_LASER_GUN_DURATION::get),
                    stats(GensokyouConfig.CORE_LASER_GUN_MULT::get, GensokyouConfig.CORE_LASER_GUN_SP_COST::get,
                            GensokyouConfig.CORE_LASER_GUN_RATE::get, GensokyouConfig.CORE_LASER_GUN_REQ_TIER::get)));

    public static final DeferredItem<BulletCoreItem> CORE_LASER_CANNON =
            ITEMS.register("core_laser_cannon", () -> new BulletCoreItem(
                    new Item.Properties().stacksTo(1),
                    FirePattern.ofLaser(UNSUPPORTED_FACTORY, GensokyouConfig.CORE_LASER_CANNON_LENGTH::get,
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
