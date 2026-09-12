package com.bitsson.gensokyou.ritual.editor;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 草稿 JSON 往返 + 合成（基文件其余层/字段原样，目标层 adds 替换、palette 增量并入）。 */
class RitualDraftAndMergeTest {

    @Test
    void draftJsonRoundTrip() {
        RitualDraft draft = new RitualDraft(
                List.of(new RitualDiffCapture.AddsEntry('S', 0, 1, 2, null),
                        new RitualDiffCapture.AddsEntry('L', 1, 0, 1, 5)),
                Map.of('L', "minecraft:soul_lantern"));
        RitualDraft back = RitualDraft.fromJson(draft.toJson());
        assertEquals(draft.adds(), back.adds());
        assertEquals(draft.paletteAdditions(), back.paletteAdditions());
    }

    @Test
    void mergeReplacesOnlyTargetLevel() {
        JsonObject base = JsonParser.parseString("""
                {
                  "id": "gensokyou:t",
                  "anchorKey": "C",
                  "palette": { "C": "gensokyou:ritual_core" },
                  "levels": [
                    { "level": 0, "adds": [["C",0,0,0]] },
                    { "level": 1, "adds": [["C",0,1,0]] }
                  ]
                }""").getAsJsonObject();
        RitualDraft draft = new RitualDraft(
                List.of(new RitualDiffCapture.AddsEntry('S', 0, 0, 2, null)),
                Map.of('S', "#gensokyou:ritual_stones"));
        JsonObject merged = RitualPatternSerializer.mergeDraft(base, 0, draft);

        // level 0 的 adds 被替换；level 1 原样
        assertEquals("[[\"S\",0,0,2]]", merged.getAsJsonArray("levels").get(0)
                .getAsJsonObject().getAsJsonArray("adds").toString().replace(" ", ""));
        assertEquals(1, merged.getAsJsonArray("levels").get(1).getAsJsonObject()
                .getAsJsonArray("adds").size());
        // palette 并入且不动既有键
        assertEquals("#gensokyou:ritual_stones",
                merged.getAsJsonObject("palette").get("S").getAsString());
        assertEquals("gensokyou:ritual_core",
                merged.getAsJsonObject("palette").get("C").getAsString());
        // 基对象未被原地修改
        assertFalse(base.getAsJsonObject("palette").has("S"));
    }

    @Test
    void mergeAppendsUnknownLevel() {
        JsonObject base = JsonParser.parseString("""
                {"id":"gensokyou:t","anchorKey":"C","palette":{"C":"gensokyou:ritual_core"},
                 "levels":[{"level":0,"adds":[["C",0,0,0]]}]}""").getAsJsonObject();
        RitualDraft draft = new RitualDraft(
                List.of(new RitualDiffCapture.AddsEntry('S', 0, 0, 2, null)),
                Map.of('S', "#gensokyou:ritual_stones"));
        JsonObject merged = RitualPatternSerializer.mergeDraft(base, 1, draft);
        assertEquals(2, merged.getAsJsonArray("levels").size());
        assertEquals(1, merged.getAsJsonArray("levels").get(1).getAsJsonObject().get("level").getAsInt());
        assertTrue(RitualPatternSerializer.serialize(merged).endsWith("}\n"));
    }
}
