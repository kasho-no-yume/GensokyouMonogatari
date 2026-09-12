package com.bitsson.gensokyou.ritual.editor;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 五条规则各自的触发/豁免用例（消息文案与 python 侧逐字对齐，任务 1.3）。 */
class RitualPatternValidatorRulesTest {

    private static JsonTagIndex index;

    @BeforeAll
    static void load() throws IOException {
        index = new JsonTagIndex(Path.of("src", "main", "resources", "data", "gensokyou"));
    }

    private static JsonObject raw(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private static List<String> errors(JsonObject... raws) {
        return RitualPatternValidator.validateAll(List.of(raws), index).stream()
                .filter(i -> !i.warn())
                .map(RitualPatternValidator.Issue::message)
                .toList();
    }

    private static List<String> warnings(JsonObject... raws) {
        return RitualPatternValidator.validateAll(List.of(raws), index).stream()
                .filter(RitualPatternValidator.Issue::warn)
                .map(RitualPatternValidator.Issue::message)
                .toList();
    }

    private static String pattern(String id, String palette, String levels) {
        return "{\"id\": \"" + id + "\", \"anchorKey\": \"C\", \"palette\": " + palette
                + ", \"levels\": " + levels + "}";
    }

    private static final String CORE_PALETTE =
            "{\"C\": \"gensokyou:ritual_core\", \"S\": \"gensokyou:ritual_stone_1\"}";

    @Test
    void minimalValidPatternPasses() {
        assertEquals(List.of(), errors(raw(pattern("t:ok", CORE_PALETTE,
                "[{\"level\": 0, \"adds\": [[\"C\",0,0,0]]}]"))));
    }

    @Test
    void additionIntersectsWithFoundationIsError() {
        assertEquals(
                List.of("t:x: level 1: 增量与 level 0 累积切片在 (0, 0, 2) 相交: 'S' vs 'S'"
                        + "（对低级结构的重复登记或改写）"),
                errors(raw(pattern("t:x", CORE_PALETTE,
                        "[{\"level\": 0, \"adds\": [[\"C\",0,0,0],[\"S\",0,0,2]]},"
                                + " {\"level\": 1, \"adds\": [[\"S\",0,0,2]]}]"))));
    }

    @Test
    void withinLevelConflictIsError() {
        assertEquals(
                List.of("t:w: level 0: 层内重复/冲突 (1, 0, -1): 'S' vs 'S'"),
                errors(raw(pattern("t:w", CORE_PALETTE,
                        "[{\"level\": 0, \"adds\": [[\"C\",0,0,0],[\"S\",1,0,1],[\"S\",1,0,-1]]}]"))));
    }

    @Test
    void duplicateLevelNumberIsError() {
        assertEquals(
                List.of("t:d: level 号 0 重复出现（levels[0] 与 levels[1]）"),
                errors(raw(pattern("t:d", CORE_PALETTE,
                        "[{\"level\": 0, \"adds\": [[\"C\",0,0,0]]},"
                                + " {\"level\": 0, \"adds\": [[\"S\",0,0,2]]}]"))));
    }

    @Test
    void anchorOutsideOriginIsError() {
        assertEquals(
                List.of("t:a: anchorKey 须位于原点 (0,0,0)，当前在 level 0 的 (0,1,0)"),
                errors(raw(pattern("t:a", CORE_PALETTE,
                        "[{\"level\": 0, \"adds\": [[\"C\",0,1,0]]}]"))));
    }

    @Test
    void anchorInNonLowestLevelIsError() {
        assertEquals(
                List.of("t:n: anchorKey 须只写在最低级增量（level 0），当前写在 level 1"),
                errors(raw(pattern("t:n", CORE_PALETTE,
                        "[{\"level\": 0, \"adds\": [[\"S\",0,0,2]]},"
                                + " {\"level\": 1, \"adds\": [[\"C\",0,0,0]]}]"))));
    }

    @Test
    void anchorCountZeroIsError() {
        assertEquals(
                List.of("t:c: anchorKey 全文件出现 0 次（须恰一次，且只写在最低级增量中）"),
                errors(raw(pattern("t:c", CORE_PALETTE,
                        "[{\"level\": 0, \"adds\": [[\"S\",0,0,2]]}]"))));
    }

    @Test
    void tierFloorBelowFirstLevelWarns() {
        String stonePalette =
                "{\"C\": \"gensokyou:ritual_core\", \"S\": \"#gensokyou:ritual_stones\"}";
        assertEquals(
                List.of("t:f: key S (#gensokyou:ritual_stones) 首次出现于 level 1，但品阶下限仅 0"),
                warnings(raw(pattern("t:f", stonePalette,
                        "[{\"level\": 0, \"adds\": [[\"C\",0,0,0]]},"
                                + " {\"level\": 1, \"adds\": [[\"S\",0,0,2]]}]"))));
        // 同级出现则豁免
        assertEquals(List.of(), warnings(raw(pattern("t:f2", stonePalette,
                "[{\"level\": 0, \"adds\": [[\"C\",0,0,0],[\"S\",0,0,2]]}]"))));
    }

    @Test
    void crossPatternHijackIsError() {
        JsonObject big = raw(pattern("t:big", CORE_PALETTE,
                "[{\"level\": 0, \"adds\": [[\"C\",0,0,0],[\"S\",0,0,1]]},"
                        + " {\"level\": 1, \"adds\": [[\"S\",0,2,0]]}]"));
        JsonObject small = raw(pattern("t:small", CORE_PALETTE,
                "[{\"level\": 0, \"adds\": [[\"C\",0,0,0]]},"
                        + " {\"level\": 1, \"adds\": [[\"S\",0,0,1]]}]"));
        assertEquals(
                List.of("劫持: t:small 的建筑会被先尝试的 t:big (level 0) 认领"),
                errors(big, small));
    }

    @Test
    void orientationTokenAccepted() {
        String palette = "{\"C\": \"gensokyou:ritual_core\", \"B\": \"minecraft:banner\"}";
        assertEquals(List.of(), errors(raw(pattern("t:o", palette,
                "[{\"level\": 0, \"adds\": [[\"C\",0,0,0],[\"B\",1,0,1,\"r3\"]]}]"))));
        assertEquals(List.of(), errors(raw(pattern("t:o2", palette,
                "[{\"level\": 0, \"adds\": [[\"C\",0,0,0],[\"B\",1,0,1,14]]}]"))));
    }

    @Test
    void badOrientationNameIsError() {
        String palette = "{\"C\": \"gensokyou:ritual_core\", \"B\": \"minecraft:banner\"}";
        assertTrue(errors(raw(pattern("t:bo", palette,
                "[{\"level\": 0, \"adds\": [[\"C\",0,0,0],[\"B\",1,0,1,\"upwards\"]]}]")))
                .get(0).contains("非法朝向名"));
    }

    @Test
    void issueLineFormatMatchesPythonConvention() {
        assertEquals("ERROR: t:x: boom", new RitualPatternValidator.Issue(false, "t:x: boom").line());
        assertEquals("WARN: t:x: hmm", new RitualPatternValidator.Issue(true, "t:x: hmm").line());
    }

    @Test
    void relevantToFiltersByPatternId() {
        List<RitualPatternValidator.Issue> all = RitualPatternValidator.validateAll(
                List.of(raw(pattern("t:mine", CORE_PALETTE, "[{\"level\": 3, \"adds\": [[\"S\",0,0,0]]}]"))),
                index);
        assertTrue(RitualPatternValidator.relevantTo(all, "t:mine").size() >= 1);
    }
}
