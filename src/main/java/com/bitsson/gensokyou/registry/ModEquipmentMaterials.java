package com.bitsson.gensokyou.registry;

import com.bitsson.gensokyou.Gensokyou;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 灵铁 / 星银的盔甲材质注册表。
 *
 * <p><b>贴图层命名</b>：{@link ArmorMaterial.Layer} 的 assetName 决定客户端去
 * {@code textures/models/armor/<assetName>_layer_1.png} 取图，命名空间必须是
 * {@code gensokyou}——写成 {@code minecraft} 会覆盖原版贴图。
 *
 * <p><b>{@code BODY} 件</b>：由四件 defense 相加而来（原版同口径：铁 15、钻石 20、
 * 下界合金 20），是属性修饰汇总用的占位键，不是真实装备件。
 */
public final class ModEquipmentMaterials {

    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, Gensokyou.MODID);

    /** 灵铁铠：防御≈钻石（20 点），韧度 2.0（钻石同级）。 */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> SPIRIT_IRON =
            ARMOR_MATERIALS.register("spirit_iron", () -> armor(
                    "spirit_iron", 3, 6, 8, 3,
                    2.0F, 0.0F, 10,
                    SoundEvents.ARMOR_EQUIP_IRON,
                    () -> Ingredient.of(ModItems.SPIRIT_IRON.get())));

    /** 星银铠：防御 24（略高于下界合金的 20），韧性 3.5、击退抗性 0.1。 */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> STAR_SILVER =
            ARMOR_MATERIALS.register("star_silver", () -> armor(
                    "star_silver", 4, 7, 9, 4,
                    3.5F, 0.1F, 18,
                    SoundEvents.ARMOR_EQUIP_NETHERITE,
                    () -> Ingredient.of(ModItems.STAR_SILVER.get())));

    private ModEquipmentMaterials() {
    }

    private static ArmorMaterial armor(String name, int helmet, int leggings, int chestplate,
                                       int boots, float toughness, float knockbackResistance,
                                       int enchantmentValue,
                                       Holder<net.minecraft.sounds.SoundEvent> equipSound,
                                       Supplier<Ingredient> repair) {
        Map<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
        defense.put(ArmorItem.Type.BOOTS, boots);
        defense.put(ArmorItem.Type.LEGGINGS, leggings);
        defense.put(ArmorItem.Type.CHESTPLATE, chestplate);
        defense.put(ArmorItem.Type.HELMET, helmet);
        defense.put(ArmorItem.Type.BODY, helmet + chestplate + leggings + boots);
        List<ArmorMaterial.Layer> layers = List.of(new ArmorMaterial.Layer(
                ResourceLocation.fromNamespaceAndPath(Gensokyou.MODID, name), "", false));
        return new ArmorMaterial(defense, enchantmentValue, equipSound, repair, layers,
                toughness, knockbackResistance);
    }
}
