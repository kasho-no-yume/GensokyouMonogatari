package com.bitsson.gensokyou.event;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.registry.ModItems;
import com.bitsson.gensokyou.registry.ModPotions;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;

/**
 * mod 药水线的炼药台配方注册。
 *
 * <p><b>两步而非三步</b>：{@code sanzu_flask} 是普通 {@link Item} 而不是 potion container，
 * 进不了 {@link PotionBrewing} 的水源位。因此第一步让原版 {@link Potions#AWKWARD} 老老实实
 * 当水源位，把冥水作为材料消耗掉，产出 {@code crude_sanzu_potion}；第二步再由粗制冥汤加
 * mod 植物出成品。
 *
 * <p>这两步把两句约束同时钉死：<b>"从地狱疴的粗制药水做出来"</b>（水源位就是
 * {@code awkward_potion}）与 <b>"瓶装三途川水是 mod 专属基液"</b>（没有它永远进不了
 * mod 线；原版水瓶与原版粗制药水都炼不出任何 mod 药水，绕不开）。
 *
 * <p><b>档位素材门槛</b>：长效档把 {@code moon_sand} 当"红石"用、强效档把
 * {@code porcelain} 当"荧石"用——两者都是普通物品，天然满足"恰好消耗 1 个"，
 * 不需要额外事件拦截。原版红石 / 荧石对 mod 药水<b>无效</b>（这里没有注册它们的 mix），
 * 因此 mod 线的档位进阶永远绕不开月砂与瓷器。
 */
@EventBusSubscriber(modid = Gensokyou.MODID)
public final class ModPotionBrewing {

    private ModPotionBrewing() {
    }

    @SubscribeEvent
    public static void onRegisterBrewingRecipes(RegisterBrewingRecipesEvent event) {
        PotionBrewing.Builder builder = event.getBuilder();
        Item flask = ModItems.SANZU_FLASK.get();

        // 第一步：粗制药水 + 瓶装三途川水（冥水）→ 粗制冥汤 / 长效 / 强效
        builder.addMix(Potions.AWKWARD, flask, ModPotions.CRUDE_SANZU);
        builder.addMix(Potions.AWKWARD, flask, ModPotions.LONG_CRUDE);
        builder.addMix(Potions.AWKWARD, flask, ModPotions.STRONG_CRUDE);

        // 第二步：粗制冥汤（三档）+ 四种 mod 植物 → 对应成品药水
        for (String id : ModPotions.ORDER) {
            Item reagent = reagentFor(id);
            builder.addMix(ModPotions.CRUDE_SANZU, reagent, ModPotions.BASE.get(id));
            builder.addMix(ModPotions.LONG_CRUDE, reagent, ModPotions.LONG.get(id));
            builder.addMix(ModPotions.STRONG_CRUDE, reagent, ModPotions.STRONG.get(id));
        }

        // 档位：mod 药水 + 月砂 → 长效；mod 药水 + 瓷器 → 强效（跨档互通）
        Item moonSand = ModItems.MOON_SAND.get();
        Item porcelain = ModItems.PORCELAIN.get();
        for (String id : ModPotions.ORDER) {
            builder.addMix(ModPotions.BASE.get(id), moonSand, ModPotions.LONG.get(id));
            builder.addMix(ModPotions.BASE.get(id), porcelain, ModPotions.STRONG.get(id));
            builder.addMix(ModPotions.LONG.get(id), porcelain, ModPotions.STRONG.get(id));
            builder.addMix(ModPotions.STRONG.get(id), moonSand, ModPotions.LONG.get(id));
        }
    }

    private static Item reagentFor(String id) {
        return switch (id) {
            case "reiki_recovery" -> ModItems.SPIRIT_HERB.get();
            case "spiritual_sight" -> ModItems.MAGIC_MUSHROOM.get();
            case "spirit_touch" -> ModItems.GENTIAN.get();
            default -> ModItems.HIGANBANA.get();
        };
    }
}
