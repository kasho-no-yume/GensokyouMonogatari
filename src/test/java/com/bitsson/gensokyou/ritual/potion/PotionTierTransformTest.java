package com.bitsson.gensokyou.ritual.potion;

import com.bitsson.gensokyou.support.MinecraftTestBootstrap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 以design.md D3 真值表为回归基线，逐条锁定原版 1.21.1 全部19 条可产出药水的三阶结果。
 *
 * <p>这些数字直接取自 {@code Potions.java}，任何配方表改动或变换链改动导致偏移都会在此炸掉。
 */
class PotionTierTransformTest {

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.ensureStarted();
    }

    private static PotionTierTransform.Options options() {
        return PotionTierTransform.Options.vanilla();
    }

    private static PotionTierTransform.Source source(Holder<Potion> potion) {
        return PotionTierTransform.sourceOf(potion)
                .orElseThrow(() -> new AssertionError("no source for " + potion.unwrapKey()
                        .map(key -> key.location().toString()).orElse("?")));
    }

    private record State(int amplifier, int duration) {
    }

    /** 单效果药水：断言 1/2/3 阶的品质与时长。 */
    private static void assertTiers(Holder<Potion> potion,
                                    State tier1, State tier2, State tier3) {
        PotionTierTransform.Source src = source(potion);
        List<MobEffectInstance> base = PotionTierTransform.apply(src, 1, options());
        assertEquals(1, base.size(), potion + " 应恰有 1 条效果");
        assertEquals(tier1, state(base.get(0)), potion + " 1阶");

        List<MobEffectInstance> two = PotionTierTransform.apply(src, 2, options());
        assertEquals(tier2, state(two.get(0)), potion + " 2阶");

        List<MobEffectInstance> three = PotionTierTransform.apply(src, 3, options());
        assertEquals(tier3, state(three.get(0)), potion + " 3阶");
    }

    private static State state(MobEffectInstance instance) {
        return new State(instance.getAmplifier(), instance.getDuration());
    }

    private static void assertEffect(Holder<Potion> potion, int tier, Holder<MobEffect> effect,
                                     int amplifier, int duration) {
        List<MobEffectInstance> applied =
                PotionTierTransform.apply(source(potion), tier, options());
        MobEffectInstance found = applied.stream()
                .filter(instance -> instance.getEffect().is(effect))
                .findFirst()
                .orElseThrow(() -> new AssertionError(potion + " " + tier + "阶缺少效果 " + effect));
        assertEquals(amplifier, found.getAmplifier(), potion + " " + tier + "阶 " + effect + " 品质");
        assertEquals(duration, found.getDuration(), potion + " " + tier + "阶 " + effect + " 时长");
    }

    // -------------------------------------------------------------- 长+强双兄弟

    @Test
    void strengthFollowsD3TruthTable() {
        // 用户指定的核心样本：1 阶 I/3:00 → 2 阶 II/8:00 → 3 阶 III/8:00。
        // base a0/3600，LONG a0/9600 → 2 阶 amp+1 且取 LONG 时长；
        // 3 阶 amp+1=2 已超原版天花板（strong_strength=1），故地板不生效。
        assertTiers(Potions.STRENGTH, new State(0, 3600), new State(1, 9600), new State(2, 9600));
    }

    @Test
    void slowFallingHasLongButNoStrong() {
        assertTiers(Potions.SLOW_FALLING, new State(0, 1800), new State(1, 4800),
                new State(2, 4800));
    }

    @Test
    void leapingAndSwiftnessUseLongDuration() {
        assertTiers(Potions.LEAPING, new State(0, 3600), new State(1, 9600), new State(2, 9600));
        assertTiers(Potions.SWIFTNESS, new State(0, 3600), new State(1, 9600), new State(2, 9600));
    }

    @Test
    void poisonUsesLongWithRatioTwo() {
        assertTiers(Potions.POISON, new State(0, 900), new State(1, 1800), new State(2, 1800));
    }

    @Test
    void regenerationUsesLongWithRatioTwo() {
        assertTiers(Potions.REGENERATION, new State(0, 900), new State(1, 1800),
                new State(2, 1800));
    }

    // -------------------------------------------------------------- 仅长时效兄弟

    @Test
    void longOnlyPotionsStillGetAmplifier() {
        // 关键：2 阶的 amp+1 是**无条件**的。制敌者当年"夜视 2 阶仍是夜视 I"就是因为
        // 把品质提升绑在了"必须有强效兄弟"上——原版没有 strong_night_vision，于是永不触发。
        assertTiers(Potions.NIGHT_VISION, new State(0, 3600), new State(1, 9600),
                new State(2, 9600));
        assertTiers(Potions.INVISIBILITY, new State(0, 3600), new State(1, 9600),
                new State(2, 9600));
        assertTiers(Potions.FIRE_RESISTANCE, new State(0, 3600), new State(1, 9600),
                new State(2, 9600));
        assertTiers(Potions.WATER_BREATHING, new State(0, 3600), new State(1, 9600),
                new State(2, 9600));
        assertTiers(Potions.WEAKNESS, new State(0, 1800), new State(1, 4800), new State(2, 4800));
    }

    // -------------------------------------------------------------- 仅强效兄弟（瞬发）

    @Test
    void instantaneousPotionsOnlyHaveStrongSibling() {
        // 瞬发效果 MUST 保住 1 tick 时长：2 阶无 LONG 兄弟时取 STRONG 的时长（也是 1），
        // 而非按倍率把 1 撑成 3 —— 后者虽无害但读作脏数据。
        assertTiers(Potions.HEALING, new State(0, 1), new State(1, 1), new State(2, 1));
        assertTiers(Potions.HARMING, new State(0, 1), new State(1, 1), new State(2, 1));
    }

    // -------------------------------------------------------------- 双兄弟皆无

    @Test
    void potionsWithoutAnySiblingOnlyGainAmplifierByDefault() {
        // 原版从未为这些效果造过长/强变体，无从抄时长；默认（退化链关闭）只涨品质。
        // 时长要跟着涨 MUST 由 extend_without_long 显式打开，见下一个测试。
        assertTiers(Potions.LUCK, new State(0, 6000), new State(1, 6000), new State(2, 6000));
        assertTiers(Potions.WIND_CHARGED, new State(0, 3600), new State(1, 3600),
                new State(2, 3600));
        assertTiers(Potions.WEAVING, new State(0, 3600), new State(1, 3600), new State(2, 3600));
        assertTiers(Potions.OOZING, new State(0, 3600), new State(1, 3600), new State(2, 3600));
        assertTiers(Potions.INFESTED, new State(0, 3600), new State(1, 3600), new State(2, 3600));
    }

    // -------------------------------------------------------------- 多效果药水

    @Test
    void turtleMasterTransformsEachEffectIndependently() {
        // 基座本身即缓 a3 + 抗 a2（0:20）；LONG 只把两者延到 0:40，品质不动；
        // 2 阶在此基础上统一 +1（缓 a4 / 抗 a3），3 阶再 +1 并被 STRONG 的
        // 缓 a5 / 抗 a3 地板顶起 → 缓 a5 / 抗 a4。
        assertEffect(Potions.TURTLE_MASTER, 1, MobEffects.MOVEMENT_SLOWDOWN, 3, 400);
        assertEffect(Potions.TURTLE_MASTER, 1, MobEffects.DAMAGE_RESISTANCE, 2, 400);
        assertEffect(Potions.TURTLE_MASTER, 2, MobEffects.MOVEMENT_SLOWDOWN, 4, 800);
        assertEffect(Potions.TURTLE_MASTER, 2, MobEffects.DAMAGE_RESISTANCE, 3, 800);
        assertEffect(Potions.TURTLE_MASTER, 3, MobEffects.MOVEMENT_SLOWDOWN, 5, 800);
        assertEffect(Potions.TURTLE_MASTER, 3, MobEffects.DAMAGE_RESISTANCE, 4, 800);
    }

    // -------------------------------------------------------------- 品质地板防倒挂

    @Test
    void qualityFloorPreventsRegressionAgainstVanillaStrong() {
        // 原版 strong_slowness 是 amp 3；3 阶本该是 amp 2，被地板顶到 3，
        // 免得最高阶产物反被原版中阶（缓慢 IV）压制。
        assertEffect(Potions.SLOWNESS, 1, MobEffects.MOVEMENT_SLOWDOWN, 0, 1800);
        assertEffect(Potions.SLOWNESS, 2, MobEffects.MOVEMENT_SLOWDOWN, 1, 4800);
        assertEffect(Potions.SLOWNESS, 3, MobEffects.MOVEMENT_SLOWDOWN, 3, 4800);
    }

    // -------------------------------------------------------------- 黑名单隔离

    @Test
    void excludedEffectSkipsQualityBoost() {
        ResourceLocation slowness = MobEffects.MOVEMENT_SLOWDOWN.unwrapKey()
                .orElseThrow().location();
        PotionTierTransform.Options blacklisted = options().withExcluded(slowness);
        List<MobEffectInstance> three =
                PotionTierTransform.apply(source(Potions.SLOWNESS), 3, blacklisted);
        MobEffectInstance found = three.get(0);
        assertEquals(0, found.getAmplifier(), "黑名单内效果不应升品质");
        assertEquals(1800, found.getDuration(), "黑名单内效果应保持基础时长");

        // 同一条目未黑名单时仍照常升品质 —— 证明黑名单是逐条目隔离的
        assertEffect(Potions.SLOWNESS, 3, MobEffects.MOVEMENT_SLOWDOWN, 3, 4800);
    }

    @Test
    void blacklistedEffectIsUntouchedAtEveryTier() {
        ResourceLocation heal = MobEffects.HEAL.unwrapKey().orElseThrow().location();
        PotionTierTransform.Options blacklisted = options().withExcluded(heal);
        for (int tier = 1; tier <= 3; tier++) {
            List<MobEffectInstance> applied =
                    PotionTierTransform.apply(source(Potions.HEALING), tier, blacklisted);
            assertEquals(0, applied.get(0).getAmplifier(), "黑名单内 heal 不应升品质");
            assertEquals(1, applied.get(0).getDuration(), "黑名单内 heal 应保持基础时长");
        }
    }

    // -------------------------------------------------------------- 品质无意义效果

    /**
     * 实现 {@code NoAmplifierEffect} 的效果（如灵视 / 彼岸花毒）：品质不承载机制差异，
     * 变换时只延长时长、绝不拔品质——既不同于普通效果的 +1，也不同于黑名单的"完全不动"。
     */
    @Test
    void noAmplifierEffectExtendsDurationWithoutRaisingAmplifier() {
        Holder<MobEffect> effect = Holder.direct(new NonAmplifiableTestEffect());
        MobEffectInstance base = new MobEffectInstance(effect, 100, 0);
        MobEffectInstance longVariant = new MobEffectInstance(effect, 400, 0);
        PotionTierTransform.Source src =
                new PotionTierTransform.Source(List.of(base), List.of(longVariant), null);

        MobEffectInstance two = PotionTierTransform.apply(src, 2, options()).get(0);
        assertEquals(0, two.getAmplifier(), "品质无意义的效果不应升品质");
        assertEquals(400, two.getDuration(), "时长仍应抄 LONG 兄弟");

        MobEffectInstance three = PotionTierTransform.apply(src, 3, options()).get(0);
        assertEquals(0, three.getAmplifier(), "3 阶仍不应升品质");
        assertEquals(400, three.getDuration(), "3 阶时长不变");
    }

    private static final class NonAmplifiableTestEffect extends MobEffect
            implements com.bitsson.gensokyou.effect.NoAmplifierEffect {
        private NonAmplifiableTestEffect() {
            super(MobEffectCategory.BENEFICIAL, 0xFFFFFF);
        }
    }

    // -------------------------------------------------------------- 退化开关

    @Test
    void fallbackSwitchesAreOffByDefault() {
        PotionTierTransform.Options defaults = options();
        assertFalse(defaults.allowDurationFallback());
        assertFalse(defaults.allowAmplifierFallback());
    }

    @Test
    void amplifierFallbackAppliesWithoutStrongSibling() {
        PotionTierTransform.Options fallback = new PotionTierTransform.Options(
                8.0D / 3.0D, 1.0D, false, true, Set.of());
        List<MobEffectInstance> two =
                PotionTierTransform.apply(source(Potions.NIGHT_VISION), 2, fallback);
        // 有 LONG 兄弟 → 时长直接抄 long_night_vision（9600），倍率与退化开关都不参与
        assertEquals(1, two.get(0).getAmplifier());
        assertEquals(9600, two.get(0).getDuration());
    }

    @Test
    void durationFallbackMultipliesWhenNoSiblingAtAll() {
        PotionTierTransform.Options fallback = new PotionTierTransform.Options(
                8.0D / 3.0D, 1.0D, true, false, Set.of());
        List<MobEffectInstance> two =
                PotionTierTransform.apply(source(Potions.LUCK), 2, fallback);
        assertEquals(1, two.get(0).getAmplifier(), "2 阶无条件 +1 品质");
        assertEquals(16000, two.get(0).getDuration(), "6000 × 8/3 = 16000");

        List<MobEffectInstance> three =
                PotionTierTransform.apply(source(Potions.LUCK), 3, fallback);
        assertEquals(2, three.get(0).getAmplifier(), "3 阶再 +1，时长沿用 2 阶");
        assertEquals(16000, three.get(0).getDuration(), "3 阶不改时长");
    }

    // -------------------------------------------------------------- 兄弟查找

    @Test
    void siblingLookupFollowsNamingConvention() {
        assertEquals(Potions.LONG_STRENGTH,
                PotionTierTransform.findSibling(Potions.STRENGTH, "long_"));
        assertEquals(Potions.STRONG_STRENGTH,
                PotionTierTransform.findSibling(Potions.STRENGTH, "strong_"));
    }

    @Test
    void siblingLookupReturnsNullWhenAbsent() {
        assertNull(PotionTierTransform.findSibling(Potions.LUCK, "long_"));
        assertNull(PotionTierTransform.findSibling(Potions.LUCK, "strong_"));
        assertNull(PotionTierTransform.findSibling(Potions.NIGHT_VISION, "strong_"));
    }

    @Test
    void barePotionsAreNotProducible() {
        assertTrue(PotionTierTransform.isBare(Potions.WATER));
        assertTrue(PotionTierTransform.isBare(Potions.MUNDANE));
        assertTrue(PotionTierTransform.isBare(Potions.THICK));
        assertTrue(PotionTierTransform.isBare(Potions.AWKWARD));
        assertFalse(PotionTierTransform.isBare(Potions.STRENGTH));
        assertTrue(PotionTierTransform.sourceOf(Potions.MUNDANE).isEmpty());
        assertTrue(PotionTierTransform.sourceOf(Potions.AWKWARD).isEmpty());
    }

    @Test
    void explicitSiblingOverridesNamingLookup() {
        // 显式 long_potion 指向不含同一效果的条目 → 长时效取值不命中，
        // 又因夜视无强效兄弟且退化链关闭 → 时长保持基础值（只有 amp 照涨）
        PotionTierTransform.Source overridden = PotionTierTransform
                .sourceOf(Potions.NIGHT_VISION, Potions.LONG_SWIFTNESS, null)
                .orElseThrow();
        assertTrue(overridden.hasLong());
        assertFalse(overridden.hasStrong(), "显式 strong=null 表示回落命名查找；夜视本无强效兄弟");
        assertEquals(new State(1, 3600),
                state(PotionTierTransform.apply(overridden, 2, options()).get(0)),
                "显式兄弟不含同一效果时不得套用其时长");

        // 命名查找会给夜视找回 long_night_vision → 9600，两者结果不同即证明覆盖生效
        assertEquals(new State(1, 9600),
                state(PotionTierTransform.apply(source(Potions.NIGHT_VISION), 2, options()).get(0)));
    }

    // -------------------------------------------------------------- 产物构造

    @Test
    void tierOneKeepsHolderAndSkipsCustomName() {
        ItemStack stack = PotionTierTransform.build(Potions.STRENGTH,
                source(Potions.STRENGTH), 1, options());
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        assertTrue(contents.potion().isPresent(), "1阶应保留 holder 以白嫖原版名");
        assertTrue(contents.customEffects().isEmpty());
        assertFalse(stack.has(DataComponents.CUSTOM_NAME), "1阶不应设自定义名");
    }

    @Test
    void higherTiersDropHolderAndAddCustomName() {
        for (int tier : new int[]{2, 3}) {
            ItemStack stack = PotionTierTransform.build(Potions.STRENGTH,
                    source(Potions.STRENGTH), tier, options());
            PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
            assertTrue(contents.potion().isEmpty(), tier + "阶必须置空 holder，否则效果翻倍");
            assertEquals(1, contents.customEffects().size(), tier + "阶应有恰 1 条自定义效果");
            assertTrue(stack.has(DataComponents.CUSTOM_NAME),
                    tier + "阶缺自定义名会退化为 Uncraftable Potion");
            String customName = stack.get(DataComponents.CUSTOM_NAME).getString();
            assertTrue(customName.contains(tier >= 3 ? "III" : "II"),
                    tier + "阶名称应带阶级后缀，实际: " + customName);
        }
    }

    @Test
    void higherTierEffectCountIsNotDoubled() {
        ItemStack stack = PotionTierTransform.build(Potions.STRENGTH,
                source(Potions.STRENGTH), 2, options());
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        List<MobEffectInstance> all = new ArrayList<>();
        contents.getAllEffects().forEach(all::add);
        assertEquals(1, all.size(), "getAllEffects 应恰有 1 条，不得翻倍");
        assertEquals(9600, all.get(0).getDuration());
    }

    @Test
    void previewFallsBackToEmptyForBarePotions() {
        assertTrue(PotionTierTransform.preview(Potions.MUNDANE, 2, options()).isEmpty());
        assertFalse(PotionTierTransform.preview(Potions.STRENGTH, 2, options()).isEmpty());
    }

    // -------------------------------------------------------------- 缩放工具

    @Test
    void infiniteDurationIsNeverScaled() {
        assertEquals(MobEffectInstance.INFINITE_DURATION,
                PotionTierTransform.scaleDuration(MobEffectInstance.INFINITE_DURATION, 8.0D / 3.0D));
    }

    @Test
    void durationScalingRoundsAndFloorsAtOneTick() {
        assertEquals(2400, PotionTierTransform.scaleDuration(900, 8.0D / 3.0D));
        assertEquals(1, PotionTierTransform.scaleDuration(1, 0.1D));
        assertEquals(0, PotionTierTransform.scaleDuration(0, 8.0D));
    }

    @Test
    void sourceOfAcceptsNullSafeInputs() {
        Optional<PotionTierTransform.Source> empty = PotionTierTransform.sourceOf(null);
        assertTrue(empty.isEmpty());
    }
}