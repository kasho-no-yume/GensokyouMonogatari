package com.bitsson.gensokyou.ritual;

import com.bitsson.gensokyou.client.book.RitualPageComponent;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetherWeaponCoreRecipeResourceTest {

    private static final Path ROOT = Path.of("src/main/resources");
    private static final Path RITUAL_RECIPES = ROOT.resolve("data/gensokyou/ritual_recipes/zaohua_circle.json");
    private static final Path NORMAL_RECIPES = ROOT.resolve("data/gensokyou/recipe");
    private static final Path ENTRIES = ROOT.resolve(
            "assets/gensokyou/patchouli_books/gensokyou_book/en_us/entries");

    @Test
    void tierFilteringAndRecipeSelectionStayStable() throws Exception {
        JsonObject root = readJson(RITUAL_RECIPES);
        JsonArray recipes = root.getAsJsonArray("recipes");
        // 计数哨兵：源初造化之仪 39 条（17 + 灵炭焖制 + spirit_core_3/4/5 + 灵铁/星银 9+9 装备）。
        // 有意增删配方时改这里。
        assertEquals(39, recipes.size());
        assertFalse(names(recipes).contains("zaohua_spirit_core_0"));

        List<JsonObject> jsonRecipes = new ArrayList<>();
        for (JsonElement element : recipes) {
            jsonRecipes.add(element.getAsJsonObject());
        }
        assertTrue(names(jsonRecipes).contains("zaohua_danmaku_weapon_frame"));
        assertFalse(availableAt(jsonRecipes, 0).contains("zaohua_danmaku_weapon_frame"));
        assertTrue(availableAt(jsonRecipes, 1).contains("zaohua_danmaku_weapon_frame"));
        assertFalse(availableAt(jsonRecipes, 1).contains("zaohua_spirit_core_2"));
        assertTrue(availableAt(jsonRecipes, 2).contains("zaohua_spirit_core_2"));

        JsonObject frame = byName(jsonRecipes, "zaohua_danmaku_weapon_frame");
        assertEquals(1, frame.get("minTier").getAsInt());
        assertEquals(20000, frame.get("spCost").getAsInt());
        assertEquals("gensokyou:danmaku_weapon", frame.getAsJsonObject("result").get("item").getAsString());

        RitualRecipe first = syntheticRecipe("gensokyou:first", 0);
        RitualRecipe exact = syntheticRecipe("gensokyou:exact", 1);
        List<RitualRecipe> synthetic = List.of(first, exact);
        assertSame(exact, RitualPageComponent.selectRecipe(synthetic, exact.id(), 0));
        assertSame(first, RitualPageComponent.selectRecipe(synthetic, null, 0));
        assertNull(RitualPageComponent.selectRecipe(synthetic, ResourceLocation.parse("gensokyou:missing"), 0));
        assertNull(RitualPageComponent.selectRecipe(synthetic, null, 2));
    }

    @Test
    void itemEntriesUseExactRecipeIds() throws Exception {
        List<String> expectedEntries = List.of(
                "item_danmaku_weapon.json",
                "item_core_sphere_single.json",
                "item_core_sphere_shotgun.json",
                "item_core_knife.json",
                "item_core_talisman.json",
                "item_core_laser_gun.json",
                "item_weapon_core_lv1.json",
                "item_weapon_core_lv2.json",
                "item_amp_core_t1.json",
                "item_amp_core_t2.json",
                "item_spirit_core_0.json",
                "item_spirit_core_1.json",
                "item_spirit_core_2.json");
        Set<String> recipeNames = names(readJson(RITUAL_RECIPES).getAsJsonArray("recipes"));
        for (String fileName : expectedEntries) {
            Path path = ENTRIES.resolve(fileName);
            assertTrue(Files.exists(path), "缺少物品词条 " + fileName);
            JsonObject entry = readJson(path);
            JsonObject recipePage = entry.getAsJsonArray("pages").get(1).getAsJsonObject();
            String type = recipePage.get("type").getAsString();
            String recipeId = recipePage.get("recipe").getAsString();
            assertTrue(recipeId.startsWith("gensokyou:"));
            if ("patchouli:crafting".equals(type)) {
                assertEquals("item_spirit_core_0.json", fileName);
                assertEquals("gensokyou:spirit_core_0", recipeId);
                assertTrue(Files.exists(NORMAL_RECIPES.resolve("spirit_core_0.json")));
            } else {
                assertEquals("gensokyou:ritual_page", type);
                assertTrue(recipeNames.contains(recipeId.substring("gensokyou:".length())));
            }
        }
        JsonObject zaohuaEntry = readJson(ENTRIES.resolve("ritual_zaohua_circle.json"));
        for (JsonElement element : zaohuaEntry.getAsJsonArray("pages")) {
            JsonObject page = element.getAsJsonObject();
            if ("gensokyou:ritual_tier_page".equals(page.get("type").getAsString())) {
                assertFalse(page.get("show_recipes").getAsBoolean());
            }
        }
    }

    /**
     * 弹幕系统入口（方术台 + 武器）不得卡在星类材料之后：
     * 否则「无方术台 → 无武器 → 打不过 BOSS → 无星」成环。
     */
    @Test
    void danmakuEntryItemsAreNotGatedBehindStarLoot() throws Exception {
        JsonObject bench = readJson(NORMAL_RECIPES.resolve("danmaku_assembly_bench.json"));
        assertFalse(bench.has("spCost"), "方术台配方不应消耗灵力");
        JsonObject benchKey = bench.getAsJsonObject("key");
        Set<String> benchItems = new HashSet<>();
        for (String symbol : benchKey.keySet()) {
            benchItems.add(benchKey.getAsJsonObject(symbol).get("item").getAsString());
        }
        assertFalse(benchItems.contains("gensokyou:spellcard_star"));
        assertFalse(benchItems.contains("gensokyou:broken_spell_card_star"));
        assertEquals("gensokyou:danmaku_assembly_bench",
                bench.getAsJsonObject("result").get("id").getAsString());

        List<JsonObject> recipes = objects(readJson(RITUAL_RECIPES).getAsJsonArray("recipes"));
        JsonObject frame = byName(recipes, "zaohua_danmaku_weapon_frame");
        for (JsonElement element : frame.getAsJsonArray("ingredients")) {
            String item = element.getAsJsonObject().get("item").getAsString();
            assertFalse(item.equals("gensokyou:spellcard_star"), "武器框不得消耗符卡星");
            assertFalse(item.equals("gensokyou:broken_spell_card_star"), "武器框不得消耗碎符卡星");
        }
    }

    /** 碎符卡星为 BOSS 专属稀缺物：任何配方都不得量产它（zaohua-crafting 需求）。 */
    @Test
    void brokenSpellCardStarStaysBossOnly() throws Exception {
        Path data = ROOT.resolve("data/gensokyou");
        try (var stream = Files.walk(data.resolve("recipe"))) {
            for (Path path : stream.filter(p -> p.getFileName().toString().endsWith(".json")).toList()) {
                assertFalse(readJson(path).getAsJsonObject("result").get("id").getAsString()
                                .equals("gensokyou:broken_spell_card_star"),
                        path.getFileName() + " 不得产出碎符卡星");
            }
        }
        for (String dir : List.of("ritual_recipes", "ritual_smelt_recipes")) {
            Path ritualDir = data.resolve(dir);
            if (!Files.exists(ritualDir)) {
                continue;
            }
            try (var stream = Files.walk(ritualDir)) {
                for (Path path : stream.filter(p -> p.getFileName().toString().endsWith(".json")).toList()) {
                    JsonObject root = readJson(path);
                    JsonArray entries = root.has("recipes")
                            ? root.getAsJsonArray("recipes")
                            : root.getAsJsonArray("rules");
                    for (JsonElement element : entries) {
                        JsonObject rule = element.getAsJsonObject();
                        if (rule.has("result")) {
                            assertFalse("gensokyou:broken_spell_card_star".equals(
                                    rule.getAsJsonObject("result").get("item").getAsString()),
                                    path.getFileName() + " 不得产出碎符卡星");
                        }
                    }
                }
            }
        }

        List<JsonObject> recipes = objects(readJson(RITUAL_RECIPES).getAsJsonArray("recipes"));
        assertFalse(names(recipes).contains("zaohua_broken_spell_card_star"));
        JsonObject star = byName(recipes, "zaohua_spellcard_star");
        assertEquals(0, star.get("minTier").getAsInt(), "符卡星重铸维持 0 阶");
        assertEquals(8, star.getAsJsonArray("ingredients").get(0).getAsJsonObject().get("count").getAsInt());
    }

    @Test
    void normalRecipesOnlyRestoreTheBootstrapSpiritCore() throws Exception {
        assertFalse(Files.exists(NORMAL_RECIPES.resolve("danmaku_weapon.json")));
        Set<String> gatedResults = Set.of(
                "gensokyou:danmaku_weapon",
                "gensokyou:core_sphere_single",
                "gensokyou:core_sphere_shotgun",
                "gensokyou:core_knife",
                "gensokyou:core_talisman",
                "gensokyou:core_laser_gun",
                "gensokyou:weapon_core_lv1",
                "gensokyou:weapon_core_lv2",
                "gensokyou:amp_core_t1",
                "gensokyou:amp_core_t2",
                "gensokyou:spirit_core_1",
                "gensokyou:spirit_core_2");
        Set<String> normalResults = new HashSet<>();
        try (var stream = Files.list(NORMAL_RECIPES)) {
            for (Path path : stream.filter(candidate -> candidate.getFileName().toString().endsWith(".json")).toList()) {
                normalResults.add(readJson(path).getAsJsonObject("result").get("id").getAsString());
            }
        }
        assertTrue(Collections.disjoint(normalResults, gatedResults));
        assertTrue(normalResults.contains("gensokyou:spirit_core_0"));

        JsonObject bootstrap = readJson(NORMAL_RECIPES.resolve("spirit_core_0.json"));
        assertEquals("minecraft:crafting_shapeless", bootstrap.get("type").getAsString());
        assertFalse(bootstrap.has("spCost"));
        assertEquals("gensokyou:spirit_core_0", bootstrap.getAsJsonObject("result").get("id").getAsString());
        assertEquals(9, bootstrap.getAsJsonArray("ingredients").size());
    }

    private static JsonObject readJson(Path path) throws Exception {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static List<JsonObject> objects(JsonArray array) {
        List<JsonObject> result = new ArrayList<>();
        for (JsonElement element : array) {
            result.add(element.getAsJsonObject());
        }
        return result;
    }

    private static Set<String> names(JsonArray array) {
        Set<String> names = new HashSet<>();
        for (JsonObject recipe : objects(array)) {
            names.add(recipe.get("name").getAsString());
        }
        return names;
    }

    private static Set<String> names(List<JsonObject> recipes) {
        Set<String> names = new HashSet<>();
        for (JsonObject recipe : recipes) {
            names.add(recipe.get("name").getAsString());
        }
        return names;
    }

    private static JsonObject byName(List<JsonObject> recipes, String name) {
        return recipes.stream().filter(recipe -> name.equals(recipe.get("name").getAsString()))
                .findFirst().orElseThrow();
    }

    private static Set<String> availableAt(List<JsonObject> recipes, int level) {
        Set<String> names = new HashSet<>();
        for (JsonObject recipe : recipes) {
            if (recipe.get("minTier").getAsInt() <= level) {
                names.add(recipe.get("name").getAsString());
            }
        }
        return names;
    }

    private static RitualRecipe syntheticRecipe(String id, int minTier) {
        return new RitualRecipe(ResourceLocation.parse(id), ResourceLocation.parse("gensokyou:zaohua_circle"),
                RitualRecipe.Mode.ACTIVATION, RitualRecipe.MatchMode.MAX, minTier, 0,
                List.of(), 0, null, null);
    }
}
