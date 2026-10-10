package com.bitsson.gensokyou.ritual.potion;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 药水阶级变换内核 —— 以 {@code Registry<POTION>} 为唯一事实源，按仪式阶级改写药水的
 * 品质（amplifier）与时效（duration），产出原版酿造台造不出的组合。
 *
 * <p><b>为什么不能用"红石/荧石"那套心智模型</b>：1.21.1（NeoForge 21.1）已删除通用增幅器，
 * 每个变体是独立注册条目（{@code minecraft:strength} / {@code long_strength} /
 * {@code strong_strength}），且 {@code long_strength} 的品质仍是 0——"红石+荧石同施"
 * （长时效 + 高品质）在原版确实无法达成，这正是本变换存在的理由。
 *
* <p><b>变换链</b>（{@code E} = 基础效果，{@code longE} / {@code strongE} = 兄弟条目中的同一效果）：
 * <pre>
 * 1 阶：E 原样
 * 2 阶：品质 := E.品质 + 1                              ← 无条件（"红石+荧石同施"）
 *       时长 := longE.时长 › strongE.时长 › E.时长 × 倍率   ← 抄注册表实在值优先
 * 3 阶：在 2 阶基础上品质 := max(品质 + 1, 原版可达最高品质)，时长不变
 * </pre>
 *
 * <p><b>2 阶为何要"同时"改两件事</b>：原版 1.21.1 已删除通用增幅器，长时效与高品质是两条
 * 互斥的注册条目，{@code long_strength} 的品质恒为 0——"8 分钟的力量 II"原版造不出来。
 * 用户要的正是这个越界组合（力量：1 阶 I/3:00 → 2 阶 II/8:00 → 3 阶 III/8:00）。
 *
 * <p><b>品质地板的必要性</b>：原版「原版天花板」不是统一的 amp 1——力量顶到 amp 1，
 * 但 {@code strong_slowness} 是 amp 3（缓慢 IV），{@code turtle_master} 基座本身就是 3/2。
 * 没有地板时本仪式 3 阶缓慢只有 amp 2，反而被原版中阶产物压制（数值倒挂）。
 *
 * <p><b>世界无关</b>：全部方法只吃 {@link Holder} / 列表 / 选项，不触碰 {@code ServerLevel}、
 * 方块实体或玩家，故可在纯 JUnit 中直接断言。注册表查找集中在 {@link #sourceOf}。
 */
public final class PotionTierTransform {

    /** 原版 1.21.1 无效果的基础条目：不可作为产出目标。 */
    private static final Set<String> BARE_POTIONS =
            Set.of("water", "mundane", "thick", "awkward");

    private PotionTierTransform() {
    }

    /**
     * 变换所需的全部输入。
     *
     * @param base基础药水的效果列表（1 阶原样输出）
     * @param longEffects {@code LONG_} 兄弟的效果列表；{@code null} 表示无该兄弟
     * @param strongEffects {@code STRONG_} 兄弟的效果列表；{@code null} 表示无该兄弟
     */
    public record Source(List<MobEffectInstance> base,
                         List<MobEffectInstance> longEffects,
                         List<MobEffectInstance> strongEffects) {

        public Source {
            base = List.copyOf(base);
            longEffects = longEffects == null ? null : List.copyOf(longEffects);
            strongEffects = strongEffects == null ? null : List.copyOf(strongEffects);
        }

        public static Source of(List<MobEffectInstance> base) {
            return new Source(base, null, null);
        }

        public boolean hasLong() {
            return longEffects != null;
        }

        public boolean hasStrong() {
            return strongEffects != null;
        }
    }

    /**
     * 退化策略。
     *
     * @param longDurationMultiplier 缺 {@code LONG_} 兄弟且允许退化时，时长的放大倍率
     * @param strongDurationMultiplier 缺 {@code STRONG_} 兄弟且允许退化时，时长的缩放倍率
     * @param allowDurationFallback 无任何兄弟时，是否仍按 {@code longDurationMultiplier} 延长时效
     * @param allowAmplifierFallback 无任何兄弟时，是否仍 +1 品质（时长按 {@code strongDurationMultiplier} 缩放）
     * @param excludedEffects 效果黑名单：其中的效果跳过全部品质提升，保留基础品质与时长
     */
    public record Options(double longDurationMultiplier,
                          double strongDurationMultiplier,
                          boolean allowDurationFallback,
                          boolean allowAmplifierFallback,
                          Set<ResourceLocation> excludedEffects) {

        /** 原版默认口径：两个退化开关都关闭，黑名单为空。 */
        public static Options vanilla() {
            return new Options(8.0D / 3.0D, 1.0D, false, false, Set.of());
        }

        public Options {
            excludedEffects = Set.copyOf(excludedEffects);
        }

        public Options withExcluded(ResourceLocation effectId) {
            Set<ResourceLocation> merged = new LinkedHashSet<>(excludedEffects);
            merged.add(effectId);
            return new Options(longDurationMultiplier, strongDurationMultiplier,
                    allowDurationFallback, allowAmplifierFallback, merged);
        }

        public boolean isExcluded(MobEffectInstance instance) {
            ResourceLocation id = effectId(instance);
            return id != null && excludedEffects.contains(id);
        }
    }

    // ------------------------------------------------------------------ 输入装配

    /**
     * 由注册表装配 {@link Source}：按 {@code long_} / {@code strong_} 前缀约定找兄弟，
     * 调用方可用显式兄弟覆盖（供无命名约定的模组条目使用）。
     *
     * <p>基础条目本身无效果时返回 {@link Optional#empty()}（不可产出）。
     */
    public static Optional<Source> sourceOf(Holder<Potion> base,
                                            Holder<Potion> explicitLong,
                                            Holder<Potion> explicitStrong) {
        if (base == null) {
            return Optional.empty();
        }
        List<MobEffectInstance> baseEffects = base.value().getEffects();
        if (baseEffects.isEmpty() || isBare(base)) {
            return Optional.empty();
        }
        Holder<Potion> longPotion = explicitLong != null ? explicitLong : findSibling(base, "long_");
        Holder<Potion> strongPotion = explicitStrong != null ? explicitStrong
                : findSibling(base, "strong_");
        List<MobEffectInstance> longEffects = longPotion == null ? null
                : longPotion.value().getEffects();
        List<MobEffectInstance> strongEffects = strongPotion == null ? null
                : strongPotion.value().getEffects();
        return Optional.of(new Source(baseEffects, longEffects, strongEffects));
    }

    public static Optional<Source> sourceOf(Holder<Potion> base) {
        return sourceOf(base, null, null);
    }

    /**
     * 按命名约定查兄弟：{@code minecraft:long_strength} ↔ {@code minecraft:strength}。
     * 非 {@code minecraft} 命名空间或无注册键时返回 {@code null}。
     */
    public static Holder<Potion> findSibling(Holder<Potion> base, String prefix) {
        Optional<ResourceKey<Potion>> key = base.unwrapKey();
        if (key.isEmpty()) {
            return null;
        }
        ResourceLocation id = key.get().location();
        String path = id.getPath();
        if (path.startsWith(prefix)) {
            // 自身已是变体条目（long_strength 之于 strength）→ 不再二次加前缀
            return null;
        }
        ResourceKey<Potion> siblingKey = ResourceKey.create(Registries.POTION,
                ResourceLocation.fromNamespaceAndPath(id.getNamespace(), prefix + path));
        return BuiltInRegistries.POTION.getHolder(siblingKey).orElse(null);
    }

    /** 无效果的1.21.1 基础条目（water / mundane / thick / awkward）。 */
    public static boolean isBare(Holder<Potion> potion) {
        return potion != null && potion.unwrapKey()
                .map(key -> BARE_POTIONS.contains(key.location().getPath()))
                .orElse(false);
    }

    // ------------------------------------------------------------------ 变换内核

    /**
     * 按阶级变换效果列表（纯函数，无注册表访问）。
     *
     * @param tier 1 = 原样；2 = 品质 +1 且延长时长；≥3 = 再叠加品质地板
     */
    public static List<MobEffectInstance> apply(Source source, int tier, Options options) {
        List<MobEffectInstance> base = source.base();
        if (tier <= 1 || base.isEmpty()) {
            return copyOf(base);
        }
        List<MobEffectInstance> tier2 = applyTier2(source, options);
        if (tier == 2) {
            return tier2;
        }
        return applyTier3(source, tier2, options);
    }

    private static List<MobEffectInstance> applyTier2(Source source, Options options) {
        List<MobEffectInstance> out = new ArrayList<>(source.base().size());
        for (MobEffectInstance instance : source.base()) {
            out.add(tier2Of(source, instance, options));
        }
        return List.copyOf(out);
    }

    private static MobEffectInstance tier2Of(Source source, MobEffectInstance instance,
                                              Options options) {
        if (options.isExcluded(instance)) {
            return copyOf(instance);
        }
        // 2阶 = 品质 +1 <b>且</b> 时长延长（"红石 + 荧石同施"，原版造不出这个组合）。
        // 品质无意义的效果（NoAmplifierEffect，如灵视/彼岸花毒）只延长时长、不拔品质。
        int newAmplifier = noAmplify(instance) ? instance.getAmplifier() : instance.getAmplifier() + 1;
        return with(instance, tier2Duration(source, instance, options), newAmplifier);
    }

    /** 该效果是否声明品质无意义（见 {@link com.bitsson.gensokyou.effect.NoAmplifierEffect}）。 */
    private static boolean noAmplify(MobEffectInstance instance) {
        Holder<MobEffect> effect = instance.getEffect();
        return effect != null
                && effect.value() instanceof com.bitsson.gensokyou.effect.NoAmplifierEffect;
    }

    /**
     * 2 阶时长：优先<b>抄注册表里真实存在的兄弟</b>，都没有才按倍率合成。
     *
     * <p>取值顺序 {@code LONG_} → {@code STRONG_} → 倍率：
     * <ul>
     *   <li>{@code LONG_} 命中即取其时长（原版"红石"那一档）。</li>
     *   <li>无长时效兄弟但有强效兄弟时取<b>强效</b>时长。这条不是冗余：它让
     *       {@code strong_healing} 这类<b>瞬发</b>效果保住 1 tick 的时长 ——
     *       直接按倍率缩放会把 1 tick 撑成 3 tick，虽无害但读作脏数据。</li>
     *   <li>两个兄弟都没有（原版从未造过长/强变体：幸运、潮涌、缠绕、浮肿、寄生）时，
     *       由 {@code allowDurationFallback} 决定是否按 {@code longDurationMultiplier} 合成；
     *       关闭则保持基础时长。</li>
     * </ul>
     */
    private static int tier2Duration(Source source, MobEffectInstance instance, Options options) {
        Optional<MobEffectInstance> longHit = findEffect(source.longEffects(), instance);
        if (longHit.isPresent()) {
            return longHit.get().getDuration();
        }
        Optional<MobEffectInstance> strongHit = findEffect(source.strongEffects(), instance);
        if (strongHit.isPresent()) {
            return strongHit.get().getDuration();
        }
        if (options.allowDurationFallback()) {
            return scaleDuration(instance.getDuration(), options.longDurationMultiplier());
        }
        return instance.getDuration();
    }

    private static List<MobEffectInstance> applyTier3(Source source,
                                                      List<MobEffectInstance> tier2,
                                                      Options options) {
        List<MobEffectInstance> out = new ArrayList<>(tier2.size());
        for (int i = 0; i < tier2.size(); i++) {
            MobEffectInstance original = source.base().get(i);
            MobEffectInstance current = tier2.get(i);
            if (options.isExcluded(current)) {
                out.add(copyOf(current));
                continue;
            }
            boolean touched = current.getAmplifier() != original.getAmplifier()
                    || current.getDuration() != original.getDuration();
            if (!touched) {
                // 兜底：2 阶没动过它（当前只可能来自"基础 amp 已达 Integer.MAX_VALUE"这类
                // 退化情形）→ 无从参照，3 阶亦不变。正常路径下 2 阶必改 amp，此分支不走。
                out.add(copyOf(current));
                continue;
            }
            int floor = vanillaCeiling(source, current);
            int amplifier = noAmplify(current) ? current.getAmplifier()
                    : Math.max(current.getAmplifier() + 1, floor);
            out.add(with(current, current.getDuration(), amplifier));
        }
        return List.copyOf(out);
    }

    /**
     * 原版可达最高品质 = {@code max(基础, LONG_, STRONG_)} 中该效果的品质。
     *
* <p>用于防止本仪式最高阶产物被原版中阶产物压制（缓慢是最典型的例子：
 * {@code strong_slowness} 是 amp 3，无地板时 3 阶缓慢只有 amp 2）。
     * 特别超标的条目可由 {@code brew_recipes} 的 {@code excluded_effects} 拉黑。
     */
    public static int vanillaCeiling(Source source, MobEffectInstance instance) {
        int ceiling = instance.getAmplifier();
        for (List<MobEffectInstance> side : List.of(
                source.longEffects() == null ? List.<MobEffectInstance>of() : source.longEffects(),
                source.strongEffects() == null ? List.<MobEffectInstance>of() : source.strongEffects())) {
            Optional<MobEffectInstance> hit = findEffect(side, instance);
            if (hit.isPresent()) {
                ceiling = Math.max(ceiling, hit.get().getAmplifier());
            }
        }
        return ceiling;
    }

    // ------------------------------------------------------------------ 产物构造

    /**
     * 构造产物药水栈。
     *
     * <p><b>1 阶</b>保留 {@code potion} holder 且不设自定义名——效果与原版条目完全一致，
     * 物品名直接白嫖原版 lang（{@code item.minecraft.potion.effect.strength}）。
     *
     * <p><b>2/3 阶</b>必须把 {@code potion} 置空：{@link PotionContents#getAllEffects()} 会把
     * holder 效果与 {@code customEffects} <b>拼接</b>，两者同留会让效果翻倍。置空后
     * {@code PotionItem.getDescriptionId} 会退化成 {@code item.minecraft.potion.effect.empty}
     * （英文 "Uncraftable Potion"），故 MUST 补 {@code CUSTOM_NAME}——本方法以
     * 基药水自身显示名 + 阶级后缀兜底，任何情况下都不会产出无名药水。
     */
    public static ItemStack build(Holder<Potion> base, Source source, int tier, Options options) {
        ItemStack stack = new ItemStack(Items.POTION);
        if (tier <= 1) {
            stack.set(DataComponents.POTION_CONTENTS, new PotionContents(base));
            return stack;
        }
        List<MobEffectInstance> effects = apply(source, tier, options);
        stack.set(DataComponents.POTION_CONTENTS,
                new PotionContents(Optional.empty(), Optional.empty(), effects));
        // 名称统一口径：基药水自身的显示名 + 阶级后缀（II / III）。直接用基药水栈的显示名，
        // modded 药水的语言文件必然有其名字，无需额外 lang 键。
        stack.set(DataComponents.CUSTOM_NAME,
                basePotionName(base).copy().append(Component.literal(tier >= 3 ? " III" : " II")));
        return stack;
    }

    private static Component basePotionName(Holder<Potion> base) {
        ItemStack ref = new ItemStack(Items.POTION);
        ref.set(DataComponents.POTION_CONTENTS, new PotionContents(base));
        return ref.getHoverName();
    }

    /** 给定药水的三阶产物图标，供 GUI / JEI 预览复用。 */
    public static ItemStack preview(Holder<Potion> base, int tier, Options options) {
        return sourceOf(base).map(source -> build(base, source, tier, options))
                .orElse(ItemStack.EMPTY);
    }

    // ------------------------------------------------------------------ 工具

    public static ResourceLocation effectId(MobEffectInstance instance) {
        Holder<MobEffect> effect = instance.getEffect();
        return effect == null ? null : effect.unwrapKey().map(key -> key.location()).orElse(null);
    }

    public static Optional<MobEffectInstance> findEffect(List<MobEffectInstance> effects,
                                                         MobEffectInstance probe) {
        if (effects == null || effects.isEmpty()) {
            return Optional.empty();
        }
        for (MobEffectInstance candidate : effects) {
            if (candidate.getEffect().is(probe.getEffect())) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static MobEffectInstance with(MobEffectInstance instance, int duration, int amplifier) {
        return new MobEffectInstance(instance.getEffect(), duration, amplifier,
                instance.isAmbient(), instance.isVisible(), instance.showIcon());
    }

    private static MobEffectInstance copyOf(MobEffectInstance instance) {
        return new MobEffectInstance(instance.getEffect(), instance.getDuration(),
                instance.getAmplifier(), instance.isAmbient(), instance.isVisible(),
                instance.showIcon());
    }

    private static List<MobEffectInstance> copyOf(List<MobEffectInstance> effects) {
        List<MobEffectInstance> out = new ArrayList<>(effects.size());
        for (MobEffectInstance instance : effects) {
            out.add(copyOf(instance));
        }
        return List.copyOf(out);
    }

    /** 无限时长（-1）不缩放；其余按倍率四舍五入，且至少 1 tick。 */
    public static int scaleDuration(int duration, double multiplier) {
        if (duration == MobEffectInstance.INFINITE_DURATION) {
            return duration;
        }
        if (duration <= 0) {
            return duration;
        }
        double factor = multiplier > 0.0D ? multiplier : 1.0D;
        long scaled = Math.round(duration * factor);
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, scaled));
    }
}