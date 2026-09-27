package com.bitsson.gensokyou.ritual;

import com.bitsson.gensokyou.support.MinecraftTestBootstrap;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RitualSmeltRuleLoaderTest {

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.ensureStarted();
    }

    private static JsonObject json(String primary, int auxiliaryCount, String result) {
        JsonObject rule = new JsonObject();
        rule.addProperty("id", "gensokyou:test_rule");
        rule.addProperty("primary", primary);
        rule.addProperty("auxiliary", "minecraft:coal");
        rule.addProperty("auxiliary_count", auxiliaryCount);
        JsonObject resultJson = new JsonObject();
        resultJson.addProperty("item", result);
        resultJson.addProperty("count", 1);
        rule.add("result", resultJson);
        JsonObject root = new JsonObject();
        root.addProperty("pattern", "gensokyou:kanayamahiko_circle");
        root.add("rules", new com.google.gson.JsonArray());
        root.getAsJsonArray("rules").add(rule);
        return root;
    }

    @Test
    void parsesPrimaryAuxiliaryAndResult() {
        List<RitualSmeltRule> rules = RitualSmeltRuleLoader.parseFile(
                ResourceLocation.parse("gensokyou:test.json"),
                json("minecraft:iron_ore", 2, "minecraft:iron_ingot"));
        assertEquals(1, rules.size());
        RitualSmeltRule rule = rules.getFirst();
        assertEquals(Items.IRON_ORE, rule.primary());
        assertEquals(Items.COAL, rule.auxiliary());
        assertEquals(2, rule.auxiliaryCount());
        assertEquals(Items.IRON_INGOT, rule.result());
        assertEquals(1, rule.resultCount());
    }

    @Test
    void rejectsUnknownItemAndNonPositiveCounts() {
        assertThrows(IllegalArgumentException.class, () -> RitualSmeltRuleLoader.parseFile(
                ResourceLocation.parse("gensokyou:test.json"),
                json("gensokyou:not_registered", 1, "minecraft:iron_ingot")));
        assertThrows(IllegalArgumentException.class, () -> RitualSmeltRuleLoader.parseFile(
                ResourceLocation.parse("gensokyou:test.json"),
                json("minecraft:iron_ore", 0, "minecraft:iron_ingot")));
    }

    @Test
    void rejectsDuplicatePrimaryInOneFile() {
        JsonObject root = json("minecraft:iron_ore", 1, "minecraft:iron_ingot");
        root.getAsJsonArray("rules").add(root.getAsJsonArray("rules").get(0).getAsJsonObject());
        assertThrows(IllegalArgumentException.class, () -> RitualSmeltRuleLoader.parseFile(
                ResourceLocation.parse("gensokyou:test.json"), root));
    }
}
