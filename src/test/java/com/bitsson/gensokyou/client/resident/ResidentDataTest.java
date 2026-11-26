package com.bitsson.gensokyou.client.resident;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ResidentDataTest {
    @Test
    void appearancesAreStableAndCoverIndependentVisualChoices() {
        Set<ResidentAppearance.Outfit> clothes = new HashSet<>();
        Set<ResidentAppearance.HeadStyle> heads = new HashSet<>();
        Set<ResidentAppearance.Accessory> accessories = new HashSet<>();
        Set<Integer> colors = new HashSet<>();
        Set<ResidentAppearance> combinations = new HashSet<>();
        for (int i = 0; i < 512; i++) {
            String id = "gensokyou:test/neighbor_" + i;
            ResidentAppearance look = ResidentAppearance.of(id);
            assertEquals(look, ResidentAppearance.of(new String(id)));
            clothes.add(look.outfit()); heads.add(look.headStyle()); accessories.add(look.accessory());
            colors.add(look.cloth()); combinations.add(look);
            assertEquals(255, look.cloth() >>> 24);
            assertTrue(look.scale() >= 0.92 && look.scale() <= 1.021);
            assertFalse(look.outfit() == ResidentAppearance.Outfit.APRON
                    && look.accessory() == ResidentAppearance.Accessory.BUNDLE);
        }
        assertEquals(3, clothes.size()); assertEquals(4, heads.size());
        assertEquals(3, accessories.size()); assertEquals(6, colors.size());
        assertTrue(combinations.size() > 450);
    }

    @Test
    void resourceCoordinatesAndIdentitiesSurviveReload() {
        JsonObject json = json("""
                {"dimension":"gensokyou:gensokyo","origin":[-200,72,100],"residents":[
                  {"id":"reader","points":[[2,0,-3]],"facing":90},
                  {"id":"walker","points":[[0,0,0],[8,0,0],[8,0,4]]}
                ]}
                """);
        ResidentScene scene = ResidentScene.parse("gensokyou:square", json);
        ResidentScene reloaded = ResidentScene.parse("gensokyou:square", json);
        for (int i = 0; i < scene.residents().size(); i++) {
            assertEquals(scene.residents().get(i).route().points(), reloaded.residents().get(i).route().points());
            assertEquals(ResidentAppearance.of(scene.residents().get(i).identity()),
                    ResidentAppearance.of(reloaded.residents().get(i).identity()));
        }
        assertEquals(new ResidentRoute.Point(-198, 72, 97), scene.residents().getFirst().route().points().getFirst());
        assertTrue(scene.residents().stream().allMatch(ResidentScene.Slot::protectedGround));
    }

    @Test
    void invalidAndUnboundedResourcesAreRejected() {
        String header = "{\"dimension\":\"gensokyou:gensokyo\",\"origin\":[0,72,0],\"residents\":";
        String slot = "{\"id\":\"one\",\"points\":[[0,0,0]]}";
        assertThrows(IllegalArgumentException.class, () -> ResidentScene.parse("gensokyou:a", json(header + "[" + slot + "," + slot + "]}")));
        assertThrows(IllegalArgumentException.class, () -> ResidentScene.parse("gensokyou:a", json(header + "[]}")));
        assertThrows(IllegalArgumentException.class, () -> ResidentScene.parse("gensokyou:a", json(header + "[" + slot.replace("[[0,0,0]]", "[[0,0,0],[100,0,0]]") + "]}")));
        assertThrows(IllegalArgumentException.class, () -> new ResidentRoute.Point(Double.NaN, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ResidentRoute.Point(0, Double.POSITIVE_INFINITY, 0));
        assertThrows(IllegalArgumentException.class, () -> new ResidentRoute(List.of()));
        String many = java.util.stream.IntStream.range(0, 129)
                .mapToObj(i -> slot.replace("one", "resident_" + i)).collect(java.util.stream.Collectors.joining(","));
        assertThrows(IllegalArgumentException.class, () -> ResidentScene.parse("gensokyou:a", json(header + "[" + many + "]}")));
        List<ResidentRoute.Point> nodes = new ArrayList<>();
        for (int i = 0; i < 17; i++) nodes.add(new ResidentRoute.Point(i, 72, 0));
        assertThrows(IllegalArgumentException.class, () -> new ResidentRoute(nodes));
    }

    @Test
    void populationSelectionIsBoundedAndIncludesLongRoutesNearTheViewer() {
        List<ResidentScene.Slot> slots = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            slots.add(new ResidentScene.Slot("slot/" + i,
                    new ResidentRoute(List.of(new ResidentRoute.Point(i, 72, 0))), 0, true));
        }
        var near = ResidentScene.nearest(slots, new ResidentRoute.Point(0, 72, 0));
        assertEquals(96, near.size()); assertEquals("slot/0", near.getFirst().identity());
        assertTrue(ResidentScene.nearest(slots, new ResidentRoute.Point(1000, 72, 0)).isEmpty());
        ResidentRoute route = new ResidentRoute(List.of(new ResidentRoute.Point(0, 72, 0), new ResidentRoute.Point(64, 72, 0)));
        assertEquals(0, route.distanceSquared(new ResidentRoute.Point(63, 72, 0)));
    }

    @Test
    void packagedSceneIsValidAndBounded() throws Exception {
        String text = Files.readString(Path.of("src/main/resources/assets/gensokyou/village_residents/human_village.json"));
        ResidentScene scene = ResidentScene.parse("gensokyou:human_village", json(text));
        assertTrue(scene.residents().size() >= 8);
        assertTrue(scene.residents().stream().allMatch(ResidentScene.Slot::protectedGround));
    }

    private static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
}
