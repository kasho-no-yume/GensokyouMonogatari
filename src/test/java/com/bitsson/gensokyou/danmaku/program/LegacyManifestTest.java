package com.bitsson.gensokyou.danmaku.program;

import com.bitsson.gensokyou.danmaku.track.*;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Repeatable export of actual declaration values, not comments or intended replacements. */
class LegacyManifestTest {
    @Test void exportLegacyCards() throws Exception {
        var owners = new LinkedHashMap<String,List<SpellCard>>();
        owners.put("big_fairy",BossCards.bigFairy());
        owners.put("yamame",BossCards.yamame());
        owners.put("yakumo_ran",BossCards.yakumoRan());
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        JsonArray cards = new JsonArray();
        for (var owner : owners.entrySet()) {
            int index = 0;
            for (SpellCard card : owner.getValue()) {
                JsonObject row = new JsonObject();
                row.addProperty("owner",owner.getKey()); row.addProperty("index",++index);
                row.addProperty("nameKey","spellcard.gensokyou."+owner.getKey()+"."+index);
                row.addProperty("hpFraction",card.hpFraction()); row.addProperty("cycleTicks",card.cycleTicks());
                row.addProperty("densityWaiver",card.densityWaiver());
                row.add("tracks",gson.toJsonTree(card.tracks()));
                cards.add(row);
            }
        }
        assertEquals(15,cards.size());
        Path output = Path.of("build/reports/danmaku-core/legacy-cards.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output,gson.toJson(cards)+"\n",StandardCharsets.UTF_8);
        assertEquals(15,JsonParser.parseString(Files.readString(output)).getAsJsonArray().size());
    }
}
