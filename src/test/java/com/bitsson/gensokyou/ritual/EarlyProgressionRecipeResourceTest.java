package com.bitsson.gensokyou.ritual;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 前期进度与材料用途的数据侧守卫（rebalance-early-progression + add-early-material-uses）。
 *
 * <p>锁四组不变量：
 * <ol>
 *   <li>1/2 阶仪式石配方不再经过精炼辰砂/星银/隙间碎片，且 Σcount 恰为对应阶台位数；</li>
 *   <li>首进阶 spCost = 200,000，二进阶（refine）仍为 1,000；</li>
 *   <li>9 张符卡配方各带 B 点，且 Σcount ≤ 1 阶祭品台数 16；</li>
 *   <li>人鱼鳞/龙鳞铠甲 5/8/7/4 主材 + 凡材；妖精实体战利品表非空。</li>
 * </ol>
 */
class EarlyProgressionRecipeResourceTest {

    private static final Path ROOT = Path.of("src/main/resources");
    private static final Path DATA = ROOT.resolve("data/gensokyou");
    private static final Path ZAOHUA = DATA.resolve("ritual_recipes/zaohua_circle.json");
    private static final Path GRACE = DATA.resolve("ritual_recipes/kami_no_megumi_circle.json");
    private static final Path ENTRIES = ROOT.resolve(
            "assets/gensokyou/patchouli_books/gensokyou_book/en_us/entries");

    private static JsonObject readJson(Path path) throws Exception {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static List<JsonObject> recipes(Path path) throws Exception {
        List<JsonObject> out = new ArrayList<>();
        for (JsonElement element : readJson(path).getAsJsonArray("recipes")) {
            out.add(element.getAsJsonObject());
        }
        return out;
    }

    private static JsonObject byName(List<JsonObject> recipes, String name) {
        return recipes.stream().filter(r -> name.equals(r.get("name").getAsString()))
                .findFirst().orElseThrow(() -> new AssertionError("缺少配方 " + name));
    }

    private static Map<String, Integer> ingredientCounts(JsonObject recipe) {
        Map<String, Integer> counts = new HashMap<>();
        for (JsonElement element : recipe.getAsJsonArray("ingredients")) {
            JsonObject ingredient = element.getAsJsonObject();
            counts.merge(ingredient.get("item").getAsString(),
                    ingredient.get("count").getAsInt(), Integer::sum);
        }
        return counts;
    }

    private static int total(JsonObject recipe) {
        return ingredientCounts(recipe).values().stream().mapToInt(Integer::intValue).sum();
    }

    // ---------------------------------------------------------------- 仪式石

    @Test
    void tierOneStoneFitsZeroTierAndSkipsRefinedCinnabar() throws Exception {
        JsonObject stone = byName(recipes(ZAOHUA), "zaohua_stone_t1");
        assertEquals(0, stone.get("minTier").getAsInt());
        assertEquals(2000, stone.get("spCost").getAsInt());
        assertEquals(8, total(stone), "Σcount 必须恰好填满 0 阶的 8 台");
        Map<String, Integer> counts = ingredientCounts(stone);
        assertEquals(1, counts.get("minecraft:diamond"));
        assertEquals(2, counts.get("gensokyou:ritual_stone_0"));
        assertEquals(1, counts.get("gensokyou:cinnabar"));
        assertEquals(4, counts.get("minecraft:blackstone"));
        assertFalse(counts.containsKey("gensokyou:refined_cinnabar"), "灵炭闭环必须解除");
        assertEquals(1, stone.getAsJsonObject("result").get("count").getAsInt());
    }

    @Test
    void tierTwoStoneFitsOneTierAndYieldsFour() throws Exception {
        JsonObject stone = byName(recipes(ZAOHUA), "zaohua_stone_t2");
        assertEquals(1, stone.get("minTier").getAsInt());
        assertEquals(20000, stone.get("spCost").getAsInt());
        assertEquals(16, total(stone), "Σcount 必须恰好填满 1 阶的 16 台");
        Map<String, Integer> counts = ingredientCounts(stone);
        assertEquals(2, counts.get("gensokyou:ritual_stone_1"));
        assertEquals(4, counts.get("gensokyou:spirit_iron"));
        assertEquals(1, counts.get("gensokyou:tide_crystal"));
        assertEquals(1, counts.get("gensokyou:spirit_herb"));
        assertEquals(1, counts.get("gensokyou:gentian"));
        assertEquals(1, counts.get("gensokyou:higanbana"));
        assertEquals(6, counts.get("minecraft:purpur_block"));
        for (String banned : List.of("gensokyou:refined_cinnabar", "gensokyou:star_silver",
                "gensokyou:sukima_fragment")) {
            assertFalse(counts.containsKey(banned), "1/2 阶石不得以 " + banned + " 为门");
        }
        assertEquals(4, stone.getAsJsonObject("result").get("count").getAsInt(),
                "2 阶石一次产 4 块");
    }

    @Test
    void firstAdvanceCostsTwoHundredThousandSpirit() throws Exception {
        List<JsonObject> grace = recipes(GRACE);
        assertEquals(200000, byName(grace, "grace_advance_1").get("spCost").getAsInt());
        assertEquals(1000, byName(grace, "grace_refine_1").get("spCost").getAsInt());
    }

    // ---------------------------------------------------------------- B 点

    @Test
    void bPointIsEightPInARing() throws Exception {
        JsonObject recipe = readJson(DATA.resolve("recipe/bpoint.json"));
        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
        JsonArray pattern = recipe.getAsJsonArray("pattern");
        int symbols = 0;
        for (JsonElement row : pattern) {
            for (char c : row.getAsString().toCharArray()) {
                if (c == 'P') {
                    symbols++;
                }
            }
        }
        assertEquals(8, symbols, "必须恰好用 8 个 P 点围成一环");
        assertEquals("gensokyou:ppoint",
                recipe.getAsJsonObject("key").getAsJsonObject("P").get("item").getAsString());
        assertEquals("gensokyou:bpoint", recipe.getAsJsonObject("result").get("id").getAsString());
        assertEquals(1, recipe.getAsJsonObject("result").get("count").getAsInt());
    }

    @Test
    void spellCardRecipesTakeBPointWithinCapacity() throws Exception {
        List<JsonObject> recipes = recipes(ZAOHUA);
        Map<String, Integer> expected = Map.ofEntries(
                Map.entry("zaohua_spellcard_spider_web", 1),
                Map.entry("zaohua_spellcard_plague_repay", 1),
                Map.entry("zaohua_spellcard_light_reflect", 1),
                Map.entry("zaohua_spellcard_healing_garden", 2),
                Map.entry("zaohua_spellcard_flower_armor", 2),
                Map.entry("zaohua_spellcard_demarcation", 2),
                Map.entry("zaohua_spellcard_icicle_fall", 2),
                Map.entry("zaohua_spellcard_fox_servant", 2),
                Map.entry("zaohua_spellcard_musou_fuuin", 2));
        for (Map.Entry<String, Integer> entry : expected.entrySet()) {
            JsonObject recipe = byName(recipes, entry.getKey());
            Map<String, Integer> counts = ingredientCounts(recipe);
            assertEquals(entry.getValue(), counts.get("gensokyou:bpoint"),
                    entry.getKey() + " 的 B 点数量不符");
            assertTrue(counts.containsKey("gensokyou:spellcard_star")
                            || counts.containsKey("gensokyou:broken_spell_card_star"),
                    entry.getKey() + " 仍须保留星类材料（B 点只是追加门）");
            assertTrue(total(recipe) <= 16,
                    entry.getKey() + " Σcount=" + total(recipe) + " 超过 1 阶 16 台");
        }
        assertEquals(16, total(byName(recipes, "zaohua_spellcard_musou_fuuin")),
                "无双封印加 2 个 B 点后恰为 16，不能再加");
    }

    // ---------------------------------------------------------------- 铠甲

    @Test
    void scaleArmorRecipesUseExpectedCountsAndVanillaMaterials() throws Exception {
        List<JsonObject> recipes = recipes(ZAOHUA);
        Map<String, Integer> mainCounts = Map.of(
                "helmet", 5, "chestplate", 8, "leggings", 7, "boots", 4);
        for (String kind : List.of("helmet", "chestplate", "leggings", "boots")) {
            int expected = mainCounts.get(kind);
            int minTier = 1;
            for (String[] material : new String[][]{
                    {"mermaid_scale", "1"}, {"dragon_scale", "2"}}) {
                String name = "zaohua_" + material[0] + "_" + kind;
                JsonObject recipe = byName(recipes, name);
                assertEquals(Integer.parseInt(material[1]), recipe.get("minTier").getAsInt(),
                        name + " 的 minTier 不符");
                Map<String, Integer> counts = ingredientCounts(recipe);
                assertEquals(expected, counts.get("gensokyou:" + material[0]),
                        name + " 主材数量应为 " + expected);
                boolean hasVanilla = counts.keySet().stream()
                        .anyMatch(id -> id.startsWith("minecraft:"));
                assertTrue(hasVanilla, name + " 必须配凡材（禁止纯 mod 材配方）");
                assertTrue(total(recipe) <= 24, name + " 原料总量超 2 阶台位");
            }
        }
    }

    @Test
    void scaleArmorItemsHaveModelsAndTranslations() throws Exception {
        for (String material : List.of("mermaid_scale", "dragon_scale")) {
            for (String kind : List.of("helmet", "chestplate", "leggings", "boots")) {
                String id = material + "_" + kind;
                assertTrue(Files.exists(ROOT.resolve(
                                "assets/gensokyou/models/item/" + id + ".json")),
                        "缺少物品模型 " + id);
                assertTrue(Files.exists(ROOT.resolve(
                                "assets/gensokyou/textures/item/" + id + ".png")),
                        "缺少物品贴图 " + id);
            }
        }
    }

    // ---------------------------------------------------------------- 妖精战利品表

    @Test
    void fairyEntityLootTableIsNonEmpty() throws Exception {
        Path table = DATA.resolve("loot_table/entities/fairy.json");
        assertTrue(Files.exists(table), "缺少妖精实体战利品表");
        JsonObject json = readJson(table);
        assertEquals("minecraft:entity", json.get("type").getAsString());
        Set<String> names = new HashSet<>();
        for (JsonElement pool : json.getAsJsonArray("pools")) {
            for (JsonElement entry : pool.getAsJsonObject().getAsJsonArray("entries")) {
                names.add(entry.getAsJsonObject().get("name").getAsString());
            }
        }
        assertEquals(Set.of("gensokyou:memory_fragment", "gensokyou:ppoint", "gensokyou:bpoint"),
                names, "妖精战利品表必须是记忆残页 + P 点 + B 点");
    }

    /**
     * 1.21.1 的实际击杀会先掷 {@code getDefaultLootTable()}、再走 {@code dropCustomDeathLoot}。
     * 有了妖精战利品表之后，若 {@code FairyEntity} 不压制前者就会「表 + 代码」双掉。
     * 本守卫钉住那处覆写（删除它不会有编译错误，只会在实机上悄悄多掉一份）。
     */
    @Test
    void fairySuppressesDefaultLootTableOnKill() {
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() ->
                com.bitsson.gensokyou.entity.FairyEntity.class.getDeclaredMethod(
                        "dropFromLootTable",
                        net.minecraft.world.damagesource.DamageSource.class, boolean.class));
    }

    // ---------------------------------------------------------------- 指导书

    @Test
    void ritualsBasicsShowsStoneRecipesByFullId() throws Exception {
        JsonObject entry = readJson(ENTRIES.resolve("rituals_basics.json"));
        Set<String> recipeIds = new HashSet<>();
        for (JsonElement element : entry.getAsJsonArray("pages")) {
            JsonObject page = element.getAsJsonObject();
            if ("gensokyou:ritual_page".equals(page.get("type").getAsString())) {
                recipeIds.add(page.get("recipe").getAsString());
            }
        }
        assertEquals(Set.of("gensokyou:zaohua_stone_t1", "gensokyou:zaohua_stone_t2"), recipeIds);
    }

    @Test
    void starSilverPageIsVisibleAfterNether() throws Exception {
        JsonObject entry = readJson(ENTRIES.resolve("material_star_silver.json"));
        assertEquals("gensokyou:guide/nether_unlock", entry.get("advancement").getAsString());
        assertTrue(entry.get("secret").getAsBoolean());
    }
}
