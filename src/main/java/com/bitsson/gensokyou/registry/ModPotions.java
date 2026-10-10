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
 * <p><b>命名约定</b>：除基液 {@code crude_sanzu_potion} 外，四个成品按需注册
 * {@code <name>} / {@code long_<name>} / {@code strong_<name>}。这条约定不是冗余：
 * {@code PotionTierTransform#findSibling} 就靠 {@code long_} / {@code strong_} 前缀找兄弟，
 * 少名渡汤之仪的 2/3 阶时长与品质提升全部以此为参考值；没有兄弟条目就会退化成
 * "只加品质不延时长"。炼药台的长/强档也由这几条承载（月砂 → long，瓷器 → strong）。
 *
 * <p><b>并非每个效果都有三个档</b>（与原版口径对齐）：
 * <ul>
 *   <li>{@code reiki_recovery} 是<b>瞬发</b>效果（见 {@code ReikiRecoveryEffect}），时长毫无意义 →
 *       <b>无 long 档</b>（同原版瞬间治疗：只有 I / II）。</li>
 *   <li>{@code spiritual_sight} 与 {@code higanbana_poison} 的品质不承载任何机制差异
 *       （显形半径固定 / 免伤固定）→ <b>无 strong 档</b>（同原版夜视：只有 I / 长效）。</li>
 *   <li>{@code spirit_touch} 的品质决定触及加成 → 三档齐全。</li>
 * </ul>
 * 没有的档位在 {@link #LONG} / {@link #STRONG} 里根本没有条目，调用方用
 * {@link #longOf(String)} / {@link #strongOf(String)} 取（缺失返回 {@code null}）。
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
 * 后者是 {@link net.minecraft.world.effect.MobEffect} 的键。喷溅/滞留药水与药箭各自走
 * {@code item.minecraft.splash_potion.effect.<id>} / {@code ...lingering_potion...} /
 * {@code ...tipped_arrow...}，也必须在 lang 里补。
 */
public final class ModPotions {

    public static final DeferredRegister<Potion> POTIONS =
            DeferredRegister.create(Registries.POTION, Gensokyou.MODID);

    /** 粗制冥汤：mod 药水线的基液，无效果（只是 awkward 的 mod 对位物）。 */
    public static final DeferredHolder<Potion, Potion> CRUDE_SANZU =
            POTIONS.register("crude_sanzu_potion", () -> new Potion((String) null));

    /** 成品药水的注册表：id → Holder。基础档恒存在。 */
    public static final Map<String, DeferredHolder<Potion, Potion>> BASE = new LinkedHashMap<>();
    /** 长效档：id → Holder（键与 {@link #BASE} 相同，仅含实际拥有该档的效果）。 */
    public static final Map<String, DeferredHolder<Potion, Potion>> LONG = new LinkedHashMap<>();
    /** 强效档：id → Holder（键与 {@link #BASE} 相同，仅含实际拥有该档的效果）。 */
    public static final Map<String, DeferredHolder<Potion, Potion>> STRONG = new LinkedHashMap<>();

    /** 供 lang / 调试遍历的成品顺序（也决定创造栏预览顺序）。 */
    public static final List<String> ORDER =
            List.of("reiki_recovery", "spiritual_sight", "spirit_touch", "higanbana_poison");

    /**
     * effect id（{@code gensokyou:<path>} 的 path 部分）→ 各档时长（tick）与品质。
     *
     * @param baseTicks 基础档时长；瞬发效果填 1（时长不参与，只为构造合法条目）
     * @param longTicks 长效档时长；{@code hasLong} 为 false 时忽略
     * @param strongTicks 强效档时长；{@code hasStrong} 为 false 时忽略
     * @param hasLong 是否有长效档
     * @param hasStrong 是否有强效档（强效档品质恒为 1）
     */
    private record Spec(String effectPath, int baseTicks, int longTicks, int strongTicks,
                        boolean hasLong, boolean hasStrong) {
    }

    private static final Map<String, Spec> SPECS = new LinkedHashMap<>();

    static {
        // 瞬发：无 long 档（时长无意义），II 阶 = 20%（同原版瞬间治疗）。
        put("reiki_recovery", new Spec("reiki_recovery", 1, 1, 1, false, true));
        // 品质无意义：无 strong 档，仅基础 + 长效（同原版夜视）。
        put("spiritual_sight", new Spec("spiritual_sight", 1200, 4800, 600, true, false));
        // 品质决定触及加成：三档齐全。
        put("spirit_touch", new Spec("spirit_touch", 1200, 4800, 600, true, true));
        // 品质无意义：无 strong 档，仅基础 + 长效。
        put("higanbana_poison", new Spec("higanbana_poison", 600, 2400, 300, true, false));
        for (String id : ORDER) {
            registerTiers(id, SPECS.get(id));
        }
    }

    private ModPotions() {
    }

    private static void put(String id, Spec spec) {
        SPECS.put(id, spec);
    }

    /** 某成品是否有长效档。 */
    public static boolean hasLong(String id) {
        return LONG.containsKey(id);
    }

    /** 某成品是否有强效档。 */
    public static boolean hasStrong(String id) {
        return STRONG.containsKey(id);
    }

    /** 长效档条目；缺失返回 {@code null}（供炼药台注册时判空）。 */
    public static DeferredHolder<Potion, Potion> longOf(String id) {
        return LONG.get(id);
    }

    /** 强效档条目；缺失返回 {@code null}（供炼药台注册时判空）。 */
    public static DeferredHolder<Potion, Potion> strongOf(String id) {
        return STRONG.get(id);
    }

    private static void registerTiers(String id, Spec spec) {
        BASE.put(id, POTIONS.register(id,
                () -> LazyEffectsPotion.of(spec.effectPath(), spec.baseTicks(), 0)));
        if (spec.hasLong()) {
            LONG.put(id, POTIONS.register("long_" + id,
                    () -> LazyEffectsPotion.of(spec.effectPath(), spec.longTicks(), 0)));
        }
        if (spec.hasStrong()) {
            STRONG.put(id, POTIONS.register("strong_" + id,
                    () -> LazyEffectsPotion.of(spec.effectPath(), spec.strongTicks(), 1)));
        }
    }
}
