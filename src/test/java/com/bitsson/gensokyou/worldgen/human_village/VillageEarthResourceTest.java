package com.bitsson.gensokyou.worldgen.human_village;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class VillageEarthResourceTest {
    private static final Path ROOT=Path.of("src/main/resources");
    @Test void finishedEarthUsesUntintedModelAndWorldgenSurfaceRule() throws Exception {
        var model=JsonParser.parseString(Files.readString(ROOT.resolve("assets/gensokyou/models/block/landmark_packed_earth.json"))).getAsJsonObject();
        assertEquals("minecraft:block/cube_bottom_top",model.get("parent").getAsString());
        assertFalse(model.toString().contains("tintindex"));
        for(var texture:model.getAsJsonObject("textures").entrySet()) {
            String name=texture.getValue().getAsString().replace("gensokyou:","");
            assertTrue(Files.exists(ROOT.resolve("assets/gensokyou/textures/"+name+".png")));
        }
        var settings=JsonParser.parseString(Files.readString(ROOT.resolve("data/gensokyou/worldgen/noise_settings/gensokyo.json")));
        assertTrue(hasVillageEarth(settings));
    }
    private static boolean hasVillageEarth(JsonElement value) {
        if(value.isJsonObject()) {
            var object=value.getAsJsonObject();
            if(object.has("if_true") && object.get("if_true").isJsonObject()) {
                var condition=object.getAsJsonObject("if_true");
                if(condition.has("biome_is") && condition.get("biome_is").toString().equals("[\"gensokyou:human_village\"]"))
                    return object.get("then_run").toString().contains("gensokyou:landmark_packed_earth");
            }
            for(var e:object.entrySet()) if(hasVillageEarth(e.getValue())) return true;
        } else if(value.isJsonArray()) for(var e:value.getAsJsonArray()) if(hasVillageEarth(e)) return true;
        return false;
    }
}
