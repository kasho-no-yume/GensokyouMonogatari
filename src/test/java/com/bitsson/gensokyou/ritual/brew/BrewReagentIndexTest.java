package com.bitsson.gensokyou.ritual.brew;

import com.bitsson.gensokyou.support.MinecraftTestBootstrap;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code brew_recipes} 解析与酿造台反查回落的行为锁定。
 *
 * <p>反查部分的全部断言都建立在**真实 bootstrap 的 {@link PotionBrewing}** 上——
 * 这正是它容易出错的地方：1.21.1 把"通用增幅器"删掉后，探针药水的选择直接决定
 * 能否查到原版试剂（用平凡当探针时全部落空）。
 */
class BrewReagentIndexTest {

    private static PotionBrewing brewing;

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.ensureStarted();
        brewing = PotionBrewing.bootstrap(FeatureFlags.VANILLA_SET);
    }

    @BeforeEach
    void clearIndex() {
        BrewReagentIndex.invalidate();
    }

    private static JsonObject entry(String reagent, String potion) {
        JsonObject entry = new JsonObject();
        entry.addProperty("reagent", reagent);
        entry.addProperty("potion", potion);
        return entry;
    }

    private static JsonObject file(JsonObject... entries) {
        JsonObject root = new JsonObject();
        root.addProperty("pattern", "gensokyou:sunako_circle");
        JsonArray array = new JsonArray();
        for (JsonObject entry : entries) {
            array.add(entry);
        }
        root.add("entries", array);
        return root;
    }

    private static ItemStack potionStack(Holder<Potion> potion) {
        return PotionContents.createItemStack(Items.POTION, potion);
    }

    private static Holder<Potion> probe(Item reagent, Holder<Potion> probePotion) {
        ItemStack probeStack = potionStack(probePotion);
        return BrewReagentIndex.probeOnce(brewing, reagent, probeStack, probePotion);
    }

    // -------------------------------------------------------------- 解析

    @Test
    void parsesReagentPotionAndSiblings() {
        JsonObject blaze = entry("minecraft:blaze_powder", "minecraft:strength");
        blaze.addProperty("long_potion", "minecraft:long_strength");
        blaze.addProperty("strong_potion", "minecraft:strong_strength");
        List<RitualBrewRule> rules = RitualBrewRuleLoader.parseFile(
                ResourceLocation.parse("gensokyou:test.json"), file(blaze));
        assertEquals(1, rules.size());
        RitualBrewRule rule = rules.getFirst();
        assertEquals(Items.BLAZE_POWDER, rule.reagent());
        assertEquals(Potions.STRENGTH, rule.potion());
        assertEquals(Potions.LONG_STRENGTH, rule.longPotion());
        assertEquals(Potions.STRONG_STRENGTH, rule.strongPotion());
        assertTrue(rule.excludedEffects().isEmpty());
        assertFalse(rule.extendWithoutLong());
        assertFalse(rule.amplifyWithoutStrong());
    }

    @Test
    void parsesExcludedEffectsAndFallbackFlags() {
        JsonObject turtle = entry("minecraft:turtle_helmet", "minecraft:turtle_master");
        JsonArray excluded = new JsonArray();
        excluded.add("minecraft:slowness");
        turtle.add("excluded_effects", excluded);
        turtle.addProperty("extend_without_long", true);
        RitualBrewRule rule = RitualBrewRuleLoader.parseFile(
                ResourceLocation.parse("gensokyou:test.json"), file(turtle)).getFirst();
        assertEquals(1, rule.excludedEffects().size());
        assertTrue(rule.isExcluded(ResourceLocation.withDefaultNamespace("slowness")));
        assertTrue(rule.extendWithoutLong());
        assertFalse(rule.amplifyWithoutStrong());
    }

    @Test
    void rejectsUnknownIdsAndMalformedFiles() {
        assertThrows(IllegalArgumentException.class, () -> RitualBrewRuleLoader.parseFile(
                ResourceLocation.parse("gensokyou:test.json"),
                file(entry("gensokyou:not_registered", "minecraft:strength"))));
        assertThrows(IllegalArgumentException.class, () -> RitualBrewRuleLoader.parseFile(
                ResourceLocation.parse("gensokyou:test.json"),
                file(entry("minecraft:blaze_powder", "minecraft:not_a_potion"))));
        assertThrows(IllegalArgumentException.class, () -> RitualBrewRuleLoader.parseFile(
                ResourceLocation.parse("gensokyou:test.json"), file()));
    }

    @Test
    void shippedDataFileParsesAndCoversEveryVanillaReagent() {
        // 打包资源必须能被同一解析逻辑读通，否则运行时整表为空
        JsonObject shipped = com.google.gson.JsonParser.parseString(
                new String(readResource("/data/gensokyou/brew_recipes/sunako_circle.json"),
                        java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        List<RitualBrewRule> rules = RitualBrewRuleLoader.parseFile(
                ResourceLocation.parse("gensokyou:sunako_circle.json"), shipped);
        assertEquals(16, rules.size());
        for (RitualBrewRule rule : rules) {
            assertTrue(
                    com.bitsson.gensokyou.ritual.potion.PotionTierTransform
                            .sourceOf(rule.potion(), rule.longPotion(), rule.strongPotion())
                            .isPresent(),
                    rule.reagent() + " 的目标药水不可产出（多半指向了无效果的废招条目）");
        }
    }

    /**
     * mod 药水试剂文件（{@code sunako_mod_potions.json}）的形状校验。
     *
     * <p><b>为什么不走 {@link RitualBrewRuleLoader#parseFile}</b>：该入口会把
     * {@code gensokyou:*} id 送进注册表解析，而纯 JUnit 环境没有 mod 加载器，
     * {@code ModItems} / {@code ModPotions} 的 DeferredRegister 不会跑，一律查不到。
     * 这里只做<b>数据形状</b>断言——四条试剂各自的档位符合设计，且凡出现的兄弟档
     * 都遵循 {@code <base> / long_<base> / strong_<base>} 约定（{@code
     * PotionTierTransform.findSibling} 靠这条约定找兄弟），真正的注册表解析由运行时的 loader 承担。
     *
     * <p><b>档位并非每个效果都有三档</b>：回灵汤是瞬发效果（无 long），灵视与彼岸花毒
     * 的品质不承载任何机制差异（无 strong）。这与原版「瞬间治疗只有 I/II、夜视只有 I/长效」一致。
     */
    @Test
    void modPotionReagentFileHasExpectedTiersPerEntry() {
        JsonObject shipped = com.google.gson.JsonParser.parseString(
                new String(readResource("/data/gensokyou/brew_recipes/sunako_mod_potions.json"),
                        java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("gensokyou:sunako_circle", shipped.get("pattern").getAsString());

        JsonArray entries = shipped.getAsJsonArray("entries");
        assertEquals(4, entries.size());
        java.util.Set<String> reagents = new java.util.HashSet<>();
        java.util.Map<String, String[]> tiers = new java.util.HashMap<>();
        for (com.google.gson.JsonElement element : entries) {
            JsonObject entry = element.getAsJsonObject();
            String reagent = entry.get("reagent").getAsString();
            assertTrue(reagent.startsWith("gensokyou:"), "试剂必须是 mod 物品: " + reagent);
            reagents.add(reagent);

            String base = entry.get("potion").getAsString();
            assertTrue(base.startsWith("gensokyou:"), "mod 药水必须是 mod 条目: " + base);
            int slash = base.indexOf(':');
            String namespace = base.substring(0, slash + 1);
            String path = base.substring(slash + 1);

            String longPotion = entry.has("long_potion") ? entry.get("long_potion").getAsString() : null;
            String strongPotion = entry.has("strong_potion") ? entry.get("strong_potion").getAsString() : null;
            // 出现的兄弟档必须遵循命名约定：findSibling 是「命名空间不变、path 前加 long_/strong_」，
            // 写成就把前缀顶到命名空间前面，运行时一个兄弟都找不到。
            if (longPotion != null) {
                assertEquals(namespace + "long_" + path, longPotion,
                        reagent + " 的长效档不遵循命名约定");
            }
            if (strongPotion != null) {
                assertEquals(namespace + "strong_" + path, strongPotion,
                        reagent + " 的强效档不遵循命名约定");
            }
            tiers.put(reagent, new String[]{longPotion, strongPotion});
        }
        // 四种植物各一条，缺一条 mod 药水线就断一环
        assertEquals(java.util.Set.of(
                        "gensokyou:spirit_herb", "gensokyou:magic_mushroom",
                        "gensokyou:gentian", "gensokyou:higanbana"),
                reagents);

        // 回灵汤：瞬发，无长效档；强效档保留（I=10% / II=20%）
        assertNull(tiers.get("gensokyou:spirit_herb")[0], "回灵汤是瞬发效果，不应有长效档");
        assertNotNull(tiers.get("gensokyou:spirit_herb")[1], "回灵汤应保留强效档（I/II 两档）");
        // 灵视药水：品质无意义，无强效档；保留长效档
        assertNotNull(tiers.get("gensokyou:magic_mushroom")[0], "灵视药水应有长效档");
        assertNull(tiers.get("gensokyou:magic_mushroom")[1], "灵视药水不应有强效档");
        // 灵触药水：三档齐全
        assertNotNull(tiers.get("gensokyou:gentian")[0], "灵触药水应有长效档");
        assertNotNull(tiers.get("gensokyou:gentian")[1], "灵触药水应有强效档");
        // 彼岸花毒：品质无意义，无强效档；保留长效档
        assertNotNull(tiers.get("gensokyou:higanbana")[0], "彼岸花毒应有长效档");
        assertNull(tiers.get("gensokyou:higanbana")[1], "彼岸花毒不应有强效档");
    }

    private static byte[] readResource(String path) {
        try (java.io.InputStream stream =
                     BrewReagentIndexTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, "缺少打包资源 " + path);
            return stream.readAllBytes();
        } catch (java.io.IOException exception) {
            throw new AssertionError("cannot read " + path, exception);
        }
    }

    // -------------------------------------------------------------- 反查回落

    @Test
    void reverseLookupFindsVanillaStartMixReagentsFromAwkward() {
        // 苦艾是 addStartMix 的真实原料边：苦艾 + 烈焰粉 = 力量
        assertEquals(Potions.STRENGTH, probe(Items.BLAZE_POWDER, Potions.AWKWARD));
        assertEquals(Potions.SLOW_FALLING, probe(Items.PHANTOM_MEMBRANE, Potions.AWKWARD));
        assertEquals(Potions.POISON, probe(Items.SPIDER_EYE, Potions.AWKWARD));
        assertEquals(Potions.NIGHT_VISION, probe(Items.GOLDEN_CARROT, Potions.AWKWARD));
        assertEquals(Potions.TURTLE_MASTER, probe(Items.TURTLE_HELMET, Potions.AWKWARD));
    }

    @Test
    void mundaneProbeFindsNothing() {
        // addStartMix 展开为「水 + 试剂 = 平凡」与「苦艾 + 试剂 = 产物」两条边，
        // 平凡既不是原料也不是产物 → 用它当探针对所有原版试剂一律落空
        assertNull(probe(Items.BLAZE_POWDER, Potions.MUNDANE));
        assertNull(probe(Items.PHANTOM_MEMBRANE, Potions.MUNDANE));
    }

    @Test
    void waterProbeIsRejectedAsDeadMove() {
        // 水 + 任意试剂都得到平凡 → 命中了但被"废招"过滤拒收
        assertNull(probe(Items.BLAZE_POWDER, Potions.WATER));
        assertNull(probe(Items.SUGAR, Potions.WATER));
    }

    @Test
    void containerMixesAreFilteredOut() {
        // 枪粉把 potion 容器换成 splash_potion（容器类 mix）→ 产物不是 Items.POTION
        assertNull(probe(Items.GUNPOWDER, Potions.STRENGTH));
        // 龙息同理
        assertNull(probe(Items.DRAGON_BREATH, Potions.STRENGTH));
    }

    @Test
    void upgradingMixesFromTheBasePotionAreFound() {
        // 非 startMix 的升级边同样应被反查覆盖：力量 + 红石 = 长时效力量
        assertEquals(Potions.LONG_STRENGTH, probe(Items.REDSTONE, Potions.STRENGTH));
        assertEquals(Potions.STRONG_STRENGTH, probe(Items.GLOWSTONE_DUST, Potions.STRENGTH));
    }

    @Test
    void unrelatedPairsYieldNothing() {
        assertNull(probe(Items.BLAZE_POWDER, Potions.NIGHT_VISION));
        assertNull(probe(Items.DIRT, Potions.AWKWARD));
    }

    @Test
    void candidateReagentSetCoversEveryVanillaStartMix() {
        // 少名的显式数据包若漏了某个原版试剂，反查是它的兜底 —— 故候选集必须完整
        assertTrue(BrewReagentIndex.vanillaCandidates().containsAll(List.of(
                Items.BREEZE_ROD, Items.SLIME_BLOCK, Items.STONE, Items.COBWEB,
                Items.MAGMA_CREAM, Items.RABBIT_FOOT, Items.SUGAR,
                Items.GLISTERING_MELON_SLICE, Items.SPIDER_EYE, Items.GHAST_TEAR,
                Items.BLAZE_POWDER, Items.PHANTOM_MEMBRANE, Items.GOLDEN_CARROT,
                Items.TURTLE_HELMET, Items.PUFFERFISH)));
        assertEquals(15, BrewReagentIndex.vanillaCandidates().size(),
                "1.21.1 的 addStartMix 恰为 15 条");
        // 发酵蜘蛛眼只有「水 + 它 = 虚弱」一条边，水是废招 → 刻意不在候选集里
        assertFalse(BrewReagentIndex.vanillaCandidates().contains(Items.FERMENTED_SPIDER_EYE));
    }

    @Test
    void registryKeyHelperIsUsable() {
        assertEquals(ResourceLocation.withDefaultNamespace("strength"),
                Potions.STRENGTH.unwrapKey().orElseThrow().location());
        assertEquals(Registries.POTION, Potions.STRENGTH.unwrapKey().orElseThrow().registryKey());
    }
}