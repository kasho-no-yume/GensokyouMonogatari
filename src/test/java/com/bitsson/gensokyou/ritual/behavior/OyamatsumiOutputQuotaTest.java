package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.ritual.RitualLootTable;
import com.bitsson.gensokyou.ritual.RitualLootLoader;
import com.bitsson.gensokyou.support.MinecraftTestBootstrap;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OyamatsumiOutputQuotaTest {

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.ensureStarted();
    }

    private static List<RitualLootTable.Weighted> weights(double... values) {
        java.util.ArrayList<RitualLootTable.Weighted> out = new java.util.ArrayList<>();
        for (double value : values) {
            out.add(new RitualLootTable.Weighted(null, value));
        }
        return List.copyOf(out);
    }

    @Test
    void stoneAndMineralQuotasScaleIndependently() {
        assertEquals(30, ToolSacrificeBehavior.scaledCount(30, 4, 0));
        assertEquals(120, ToolSacrificeBehavior.scaledCount(30, 4, 1));
        assertEquals(480, ToolSacrificeBehavior.scaledCount(30, 4, 2));
        assertEquals(10, ToolSacrificeBehavior.scaledCount(10, 2, 0));
        assertEquals(20, ToolSacrificeBehavior.scaledCount(10, 2, 1));
        assertEquals(40, ToolSacrificeBehavior.scaledCount(10, 2, 2));
        assertEquals(140, 120 + 20);
        assertEquals(520, 480 + 40);
    }

    @Test
    void eachChannelHasItsOwnBaseAndConditionalPools() {
        RitualLootTable.TierTable stoneTier = new RitualLootTable.TierTable(
                "wood", List.of(), weights(4), weights(2));
        RitualLootTable.OutputChannel stone = new RitualLootTable.OutputChannel(
                "stone", 100, weights(1), List.of(), List.of(), List.of(stoneTier));
        RitualLootTable.TierTable mineralTier = new RitualLootTable.TierTable(
                "wood", weights(1), weights(3), List.of());
        RitualLootTable.OutputChannel mineral = new RitualLootTable.OutputChannel(
                "mineral", 0, List.of(), weights(5), List.of(), List.of(mineralTier));

        List<RitualLootTable.Weighted> stoneBase = RitualLootTable.buildPool(
                stone, "wood", false, false, false, false);
        List<RitualLootTable.Weighted> mineralBase = RitualLootTable.buildPool(
                mineral, "wood", false, false, false, false);
        assertEquals(1, stoneBase.size());
        assertEquals(100.0, stoneBase.getFirst().weight());
        assertEquals(List.of(1.0), mineralBase.stream().map(RitualLootTable.Weighted::weight).toList());

        List<RitualLootTable.Weighted> stoneWithNether = RitualLootTable.buildPool(
                stone, "wood", true, false, false, false);
        List<RitualLootTable.Weighted> mineralWithNether = RitualLootTable.buildPool(
                mineral, "wood", true, false, false, false);
        assertEquals(List.of(100.0, 4.0), stoneWithNether.stream()
                .map(RitualLootTable.Weighted::weight).toList());
        assertEquals(List.of(1.0, 3.0), mineralWithNether.stream()
                .map(RitualLootTable.Weighted::weight).toList());

        List<RitualLootTable.Weighted> mineralWithToken = RitualLootTable.buildPool(
                mineral, "wood", false, false, true, false);
        assertEquals(List.of(1.0, 5.0), mineralWithToken.stream()
                .map(RitualLootTable.Weighted::weight).toList());
        assertTrue(stoneBase.stream().allMatch(entry -> entry.weight() == 100.0));
    }

    @Test
    void saturationPreventsIntegerOverflow() {
        assertEquals(Integer.MAX_VALUE,
                ToolSacrificeBehavior.scaledCount(30, 1000, 3));
    }

    @Test
    void loaderAcceptsIndependentOutputChannels() {
        var json = JsonParser.parseString("""
                {
                  "pattern": "gensokyou:test",
                  "toolTag": "#minecraft:pickaxes",
                  "tables": [{"tier":"wood"}],
                  "output_channels": [
                    {"id":"stone", "commonsTotal":100,
                     "commons":[["minecraft:stone",1]],
                     "tables":[{"tier":"wood"}]},
                    {"id":"mineral", "commonsTotal":0, "commons":[],
                     "tables":[{"tier":"wood", "special":[["minecraft:iron_ore",1]]}]}
                  ]
                }
                """).getAsJsonObject();
        RitualLootTable table = RitualLootLoader.parseFileForClient(json);

        assertEquals(ResourceLocation.parse("gensokyou:test"), table.patternId());
        assertEquals(List.of("stone", "mineral"), table.outputChannels().stream()
                .map(RitualLootTable.OutputChannel::id).toList());
        assertEquals(100.0, RitualLootTable.sumWeight(RitualLootTable.buildPool(
                table.outputChannels().get(0), "wood", false, false, false, false)));
        assertEquals(1.0, RitualLootTable.sumWeight(RitualLootTable.buildPool(
                table.outputChannels().get(1), "wood", false, false, false, false)));
    }
}
