package com.bitsson.gensokyou.registry;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.item.equipment.LazyEffectsPotion;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.alchemy.Potion;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * mod 药水线的 {@link Potion} 注册表。
 *
 * <p><b>命名约定</b>：除基液 {@code crude_sanzu_potion} 外，四个成品各注册三条——
 * {@code <name>} / {@code long_<name>} / {@code strong_<name>}。这条约定不是冗余：
 * {@code PotionTierTransform#findSibling} 就靠 {@code long_} / {@code strong_} 前缀找兄弟，
 * 少名渡汤之仪的 2/3 阶时长与品质提升全部以此为参考值；没有兄弟条目就会退化成
 * "只加品质不延时长"。炼药台的长/强档也由这几条承载（月砂 → long，瓷器 → strong）。
 *
 * <p><b>为什么每条都是 {@link LazyEffectsPotion}</b>：{@code MobEffectInstance} 构造器会当场
 * 解引用 {@code Holder<MobEffect>}，而 {@code Registry<POTION>} 的注册事件与
 * {@code Registry<MOB_EFFECT>} 的先后没有保证。若在 supplier 里 {@code new MobEffectInstance(...)}，
 * 一旦药水先注册就抛 "Trying to access unbound value"。惰性解析把这一步推到运行时。
 *
 * <p><b>语言键</b>：原版 {@code PotionItem.getDescriptionId} 走
 * {@code "item.minecraft.potion.effect." + potion.name}，而 {@code Potion} 构造器的
 * {@code name} 参数为 null 时回落到注册表路径，故本地化键是
 * {@code item.minecraft.potion.effect.<id>}（不是 {@code effect.gensokyou.*}）——
 * 后者是 {@link net.minecraft.world.effect.MobEffect} 的键。两者都必须在 lang 里补。
 */
public final class ModPotions {

    public static final DeferredRegister<Potion> POTIONS =
            DeferredRegister.create(Registries.POTION, Gensokyou.MODID);

    /** 粗制冥汤：mod 药水线的基液，无效果（只是 awkward 的 mod 对位物）。 */
    public static final DeferredHolder<Potion, Potion> CRUDE_SANZU =
            POTIONS.register("crude_sanzu_potion", () -> new Potion((String) null));
    /** 粗制冥汤的长/强档：纯占位条目，让 {@code PotionTierTransform} 的兄弟查找有参照。 */
    public static final DeferredHolder<Potion, Potion> LONG_CRUDE =
            POTIONS.register("long_crude_sanzu_potion", () -> new Potion((String) null));
    public static final DeferredHolder<Potion, Potion> STRONG_CRUDE =
            POTIONS.register("strong_crude_sanzu_potion", () -> new Potion((String) null));

    /** 成品药水的注册表：id → Holder。 */
    public static final Map<String, DeferredHolder<Potion, Potion>> BASE = new LinkedHashMap<>();
    /** 长效档：id → Holder（键与 {@link #BASE} 相同）。 */
    public static final Map<String, DeferredHolder<Potion, Potion>> LONG = new LinkedHashMap<>();
    /** 强效档：id → Holder（键与 {@link #BASE} 相同）。 */
    public static final Map<String, DeferredHolder<Potion, Potion>> STRONG = new LinkedHashMap<>();

    /** 供 lang / 调试遍历的成品顺序（也决定创造栏预览顺序）。 */
    public static final List<String> ORDER =
            List.of("reiki_recovery", "spiritual_sight", "spirit_touch", "higanbana_poison");

    /** effect id（{@code gensokyou:<path>} 的 path 部分）→ 各档时长（tick）与品质。 */
    private record Spec(String effectPath, int baseTicks, int longTicks, int strongTicks) {
    }

    private static final Map<String, Spec> SPECS = new LinkedHashMap<>();

    static {
        // 时长口径对齐原版红石/荧石：long ≈ base × 2~4，strong = base 时长 + 品质 +1。
        put("reiki_recovery", new Spec("reiki_recovery", 1200, 3600, 600));
        put("spiritual_sight", new Spec("spiritual_sight", 1200, 4800, 600));
        put("spirit_touch", new Spec("spirit_touch", 1200, 4800, 600));
        put("higanbana_poison", new Spec("higanbana_poison", 600, 2400, 300));
        for (String id : ORDER) {
            registerTiers(id, SPECS.get(id));
        }
    }

    private ModPotions() {
    }

    private static void put(String id, Spec spec) {
        SPECS.put(id, spec);
    }

    private static void registerTiers(String id, Spec spec) {
        BASE.put(id, POTIONS.register(id,
                () -> LazyEffectsPotion.of(spec.effectPath(), spec.baseTicks(), 0)));
        LONG.put(id, POTIONS.register("long_" + id,
                () -> LazyEffectsPotion.of(spec.effectPath(), spec.longTicks(), 0)));
        STRONG.put(id, POTIONS.register("strong_" + id,
                () -> LazyEffectsPotion.of(spec.effectPath(), spec.strongTicks(), 1)));
    }
}
