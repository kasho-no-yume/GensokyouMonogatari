package com.bitsson.gensokyou.danmaku.program.content;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Repeatable export of the migrated program manifest (tasks 1.7). */
class ProgramManifestTest {
    @Test void exportsNineteenCardsPlusTwoBackgrounds() throws Exception {
        var rows = BossProgramManifest.entries();
        assertEquals(21, rows.size(), "19 张卡 + 大妖精/山女两条底噪；蓝/露米娅无底噪");
        assertEquals(19, BossProgramManifest.migratedCardCount());

        Set<String> ids = new HashSet<>();
        for (var row : rows) {
            assertTrue(ids.add(row.programId()), "program id unique: " + row.programId());
            if (row.background()) assertEquals(0, row.index());
            else assertTrue(row.index() >= 1 && row.index() <= 8);
        }
        long backgrounds = rows.stream().filter(BossProgramManifest.Entry::background).count();
        assertEquals(2, backgrounds);

        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        JsonArray cards = new JsonArray();
        for (var row : rows) {
            JsonObject json = new JsonObject();
            json.addProperty("owner", row.owner());
            json.addProperty("index", row.index());
            json.addProperty("nameKey", row.nameKey());
            json.addProperty("hpFraction", row.hpFraction());
            json.addProperty("cycleTicks", row.cycleTicks());
            json.addProperty("programId", row.programId());
            json.addProperty("background", row.background());
            json.add("differences", gson.toJsonTree(row.notes()));
            cards.add(json);
        }
        JsonObject report = new JsonObject();
        report.add("cards", cards);
        report.add("sheetConflicts", gson.toJsonTree(BossProgramManifest.sheetConflicts()));
        report.add("unexecutedVerifications", gson.toJsonTree(BossProgramManifest.unexecutedVerifications()));

        Path output = Path.of("build/reports/danmaku-program/content-manifest.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, gson.toJson(report) + "\n", StandardCharsets.UTF_8);
        assertEquals(21, JsonParser.parseString(Files.readString(output))
                .getAsJsonObject().getAsJsonArray("cards").size());
    }
}
