package com.bitsson.gensokyou.ritual;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RitualPedestalRecipeResourceTest {

    private static final Path ROOT = Path.of("src/main/resources");
    private static final Path RECIPE = ROOT.resolve("data/gensokyou/recipe/ritual_pedestal.json");
    private static final Path RITUAL_RECIPES = ROOT.resolve("data/gensokyou/ritual_recipes");
    private static final Path ENTRY = ROOT.resolve(
            "assets/gensokyou/patchouli_books/gensokyou_book/en_us/entries/item_ritual_pedestal.json");

    @Test
    void pedestalIsCraftableAtAWorkbenchWithoutSpirit() throws Exception {
        JsonObject recipe = readJson(RECIPE);
        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
        assertEquals("gensokyou:ritual_pedestal",
                recipe.getAsJsonObject("result").get("id").getAsString());
        assertEquals(1, recipe.getAsJsonObject("result").get("count").getAsInt());

        JsonArray pattern = recipe.getAsJsonArray("pattern");
        assertEquals(3, pattern.size());
        JsonObject key = recipe.getAsJsonObject("key");
        Map<String, String> symbolItems = new HashMap<>();
        for (String symbol : key.keySet()) {
            symbolItems.put(symbol, key.getAsJsonObject(symbol).get("item").getAsString());
        }
        assertEquals("minecraft:smooth_stone", symbolItems.get("S"));
        assertEquals("gensokyou:ritual_stone_0", symbolItems.get("R"));
        assertEquals("gensokyou:ppoint", symbolItems.get("P"));

        Map<String, Integer> counts = new HashMap<>();
        for (JsonElement element : pattern) {
            for (char symbol : element.getAsString().toCharArray()) {
                String item = symbolItems.get(String.valueOf(symbol));
                assertTrue(item != null, "未在 key 中声明的符号: " + symbol);
                counts.merge(item, 1, Integer::sum);
            }
        }
        assertEquals(Map.of(
                "minecraft:smooth_stone", 6,
                "gensokyou:ritual_stone_0", 2,
                "gensokyou:ppoint", 1), counts);

        for (String forbidden : new String[]{"spCost", "minTier", "mode", "match"}) {
            assertFalse(recipe.has(forbidden), "bootstrap 配方不得含 " + forbidden);
        }
    }

    @Test
    void noRitualRecipeProducesPedestals() throws Exception {
        try (var stream = Files.list(RITUAL_RECIPES)) {
            for (Path path : stream.filter(candidate -> candidate.getFileName().toString().endsWith(".json")).toList()) {
                JsonObject root = readJson(path);
                for (JsonElement element : root.getAsJsonArray("recipes")) {
                    JsonObject ritualRecipe = element.getAsJsonObject();
                    if (ritualRecipe.has("result")) {
                        assertFalse("gensokyou:ritual_pedestal".equals(
                                ritualRecipe.getAsJsonObject("result").get("item").getAsString()),
                                path.getFileName() + " 不应产出祭品台");
                    }
                    for (JsonElement ingredient : ritualRecipe.getAsJsonArray("ingredients")) {
                        assertFalse("gensokyou:ritual_pedestal".equals(
                                ingredient.getAsJsonObject().get("item").getAsString()),
                                path.getFileName() + " 不应消耗祭品台");
                    }
                }
            }
        }
    }

    @Test
    void guideEntryShowsTheWorkbenchRecipe() throws Exception {
        JsonObject entry = readJson(ENTRY);
        assertEquals("item.gensokyou.ritual_pedestal", entry.get("name").getAsString());
        assertEquals("gensokyou:items", entry.get("category").getAsString());
        JsonArray pages = entry.getAsJsonArray("pages");
        assertEquals(2, pages.size());
        assertEquals("patchouli:spotlight", pages.get(0).getAsJsonObject().get("type").getAsString());
        assertEquals("gensokyou.book.entry.item_recipe.pedestal.p1",
                pages.get(0).getAsJsonObject().get("text").getAsString());
        JsonObject recipePage = pages.get(1).getAsJsonObject();
        assertEquals("patchouli:crafting", recipePage.get("type").getAsString());
        assertEquals("gensokyou:ritual_pedestal", recipePage.get("recipe").getAsString());
        assertFalse(entry.has("advancement"));
        assertFalse(entry.has("secret"));
    }

    private static JsonObject readJson(Path path) throws Exception {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
