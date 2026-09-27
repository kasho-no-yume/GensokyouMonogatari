package com.bitsson.gensokyou.ritual;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「幻想素材」章节守卫（fantasy-materials-book）。
 *
 * <p>三条不变量，任一被破坏都会让玩家拿到错的制作信息：
 * <ol>
 *   <li>五座资源仪式的特产带（低/高）每一件都必须在章节里有对应书页——
 *       新加素材却忘了重跑生成器时由本测试拦下；</li>
 *   <li>分带与世界进度门槛严格对应：低阶带常驻、下界池挂下界门槛、
 *       末地池与高阶带挂末地门槛。越档提前剧透等于 spoilers；</li>
 *   <li>三种矿物的煅炉配方页必须指向 ritual_smelt_recipes 里真实存在的规则，
 *       且引火物就是灵炭——书里写的配比与煅炉真正吃的是不是一回事，只看这条。</li>
 * </ol>
 */
class FantasyMaterialsBookTest {

    private static final Path BOOK = Path.of(
            "src/main/resources/assets/gensokyou/patchouli_books/gensokyou_book/en_us");
    private static final Path ENTRIES = BOOK.resolve("entries");
    private static final Path CATEGORIES = BOOK.resolve("categories");
    private static final Path DATA = Path.of("src/main/resources/data/gensokyou");

    private static final String CATEGORY = "gensokyou:fantasy_materials";
    private static final String NETHER_GATE = "gensokyou:guide/nether_unlock";
    private static final String END_GATE = "gensokyou:guide/end_unlock";

    /** 段号（ritual_loot 的带）→ 该带书页必须挂的门槛；空串 = 常驻可见（Map.of 不接受 null）。 */
    private static final Map<Integer, String> GATE_BY_SECTION = Map.of(
            1, NETHER_GATE,
            2, END_GATE,
            3, "",
            4, END_GATE);

    /** 五座资源仪式 → 其特产带所在的数据文件。 */
    private static final Map<String, String> BAND_SOURCES = Map.of(
            "gensokyou:oyamatsumi_circle", "ritual_loot/oyamatsumi_circle.json",
            "gensokyou:haniyasu_circle", "ritual_loot/haniyasu_circle.json",
            "gensokyou:kukunochi_circle", "ritual_loot/kukunochi_circle.json",
            "gensokyou:watatsumi_circle", "ritual_special/watatsumi_special.json",
            "gensokyou:kaya_no_hime_circle", "ritual_loot/kaya_no_hime_circle.json");

    private static final String SMELT_PATTERN = "gensokyou:kanayamahiko_circle";
    private static final String SMELT_FUEL = "gensokyou:spirit_charcoal";
    private static final List<String> MINERALS = List.of(
            "gensokyou:refined_cinnabar", "gensokyou:spirit_iron", "gensokyou:star_silver");

    private record Page(String type, String advancement, String entries, String ritual, String recipe,
                       String result, String toolItem) {
    }

    private record BandItem(String item, int section) {
    }

    @Test
    void categoryDeclaresNameDescriptionAndIcon() throws Exception {
        JsonObject category = readJson(CATEGORIES.resolve("fantasy_materials.json"));
        assertEquals("gensokyou.book.category.fantasy_materials", category.get("name").getAsString());
        assertTrue(category.has("description"), "分类缺少 description");
        assertTrue(category.has("icon"), "分类缺少 icon");
    }

    @Test
    void everySpecialtyMaterialIsDocumented() throws Exception {
        Set<BandItem> expected = new HashSet<>();
        for (Map.Entry<String, String> source : BAND_SOURCES.entrySet()) {
            JsonObject data = readJson(DATA.resolve(source.getValue()));
            // ritual_special/watatsumi_special.json 由 WatatsumiSpecialLootLoader 单独绑定仪式，
            // 文件内不写 pattern；其余战利品表必须自报家门。
            if (data.has("pattern")) {
                assertEquals(source.getKey(), data.get("pattern").getAsString(),
                        source.getValue() + " 的 pattern 与预期资源仪式不符");
            } else {
                assertEquals("ritual_special/watatsumi_special.json", source.getValue(),
                        "只有绵津见的特产池允许不写 pattern");
            }
            collectBand(data, "gensokyou_low", 3, expected);
            collectBand(data, "gensokyou_high", 4, expected);
        }
        assertFalse(expected.isEmpty(), "没有读到任何特产带");

        Set<BandItem> documented = new HashSet<>();
        for (JsonObject entry : chapterEntries()) {
            for (Page page : pages(entry)) {
                if (!"gensokyou:loot_page".equals(page.type())) {
                    continue;
                }
                for (String part : page.entries().split(";")) {
                    String[] fields = part.split(",");
                    assertEquals(3, fields.length, "loot_page 条目串格式应为 item,pct,section：" + part);
                    documented.add(new BandItem(fields[0], Integer.parseInt(fields[2])));
                }
            }
        }
        List<String> missing = new ArrayList<>();
        for (BandItem item : expected) {
            if (!documented.contains(item)) {
                missing.add(item.item() + "(段 " + item.section() + ")");
            }
        }
        assertTrue(missing.isEmpty(),
                "以下特产在幻想素材章节里没有书页（重跑 python tools/gen_material_book_entries.py）：" + missing);
    }

    @Test
    void bandGatesFollowWorldProgress() throws Exception {
        int gated = 0;
        for (JsonObject entry : chapterEntries()) {
            for (Page page : pages(entry)) {
                if (!"gensokyou:loot_page".equals(page.type())) {
                    continue;
                }
                for (String part : page.entries().split(";")) {
                    int section = Integer.parseInt(part.split(",")[2]);
                    assertTrue(GATE_BY_SECTION.containsKey(section), "未知段号 " + section);
                    String required = GATE_BY_SECTION.get(section);
                    assertEquals(required.isEmpty() ? null : required, page.advancement(),
                            entry.get("name").getAsString() + " 里段 " + section
                                    + " 的书页门槛与世界进度不符：" + part);
                    if (!required.isEmpty()) {
                        gated++;
                    }
                }
            }
        }
        assertTrue(gated > 0, "没有任何素材页挂世界进度门槛，分级形同虚设");
    }

    /** 章节内的造化仪配方页必须指向 ritual_recipes 里真实存在的配方（拼错 id = 空白配方页）。 */
    @Test
    void craftRecipePagesPointAtRealRecipes() throws Exception {
        Set<String> known = new HashSet<>();
        for (String file : List.of("ritual_recipes/zaohua_circle.json",
                "ritual_recipes/kami_no_megumi_circle.json")) {
            JsonObject recipes = readJson(DATA.resolve(file));
            for (JsonElement element : recipes.getAsJsonArray("recipes")) {
                known.add("gensokyou:" + element.getAsJsonObject().get("name").getAsString());
            }
        }
        int pages = 0;
        for (JsonObject entry : chapterEntries()) {
            for (Page page : pages(entry)) {
                if (!"gensokyou:ritual_page".equals(page.type())) {
                    continue;
                }
                assertTrue(known.contains(page.recipe()),
                        entry.get("name").getAsString() + " 指向不存在的仪式配方 " + page.recipe());
                pages++;
            }
        }
        assertTrue(pages > 0, "章节里没有任何造化仪配方页");
    }

    /**
     * 素材带与工具材质无关（同一块神木，木斧金斧等价）。书页若摆一把铁镐，
     * 玩家会读成「只有铁器才出产」——逐工具材质的确切产出在仪式条目里，
     * 本章节只管有哪些素材。
     */
    @Test
    void noPagePinsASpecificToolTier() throws Exception {
        List<String> tiered = new ArrayList<>();
        for (JsonObject entry : chapterEntries()) {
            for (Page page : pages(entry)) {
                if (page.type() != null && page.type().startsWith("patchouli:")) {
                    continue;
                }
                if (page.toolItem() != null) {
                    tiered.add(entry.get("name").getAsString() + " 摆出了 " + page.toolItem());
                }
            }
        }
        assertTrue(tiered.isEmpty(),
                "幻想素材章节不该按工具材质出图（会误导成只有该材质可产出）：" + tiered);
    }

    @Test
    void mineralEntriesShowRealForgeRecipes() throws Exception {        Map<String, JsonObject> rules = smeltRulesByResult();
        assertFalse(rules.isEmpty(), "ritual_smelt_recipes 里没有规则");

        List<String> documented = new ArrayList<>();
        for (JsonObject entry : chapterEntries()) {
            for (Page page : pages(entry)) {
                if (!"gensokyou:smelt_page".equals(page.type())) {
                    continue;
                }
                assertEquals(SMELT_PATTERN, page.ritual(),
                        "矿物词条的煅炉页应指向金山彦命煅炉");
                String result = page.result();
                assertNotNull(rules.get(result),
                        result + " 的煅炉配方页指向了不存在的产物");
                assertEquals(SMELT_FUEL, rules.get(result).get("auxiliary").getAsString(),
                        result + " 的引火物不是灵炭");
                assertTrue(rules.get(result).get("auxiliary_count").getAsInt() > 0,
                        result + " 的灵炭配比必须为正");
                documented.add(result);
            }
        }
        assertEquals(new HashSet<>(MINERALS), new HashSet<>(documented),
                "三种矿物都应在幻想素材章节里有煅炉配方页");
    }

    private static Map<String, JsonObject> smeltRulesByResult() throws Exception {
        Map<String, JsonObject> byResult = new HashMap<>();
        JsonObject file = readJson(DATA.resolve("ritual_smelt_recipes/kanayamahiko_circle.json"));
        assertEquals(SMELT_PATTERN, file.get("pattern").getAsString());
        for (JsonElement element : file.getAsJsonArray("rules")) {
            JsonObject rule = element.getAsJsonObject();
            byResult.put(rule.getAsJsonObject("result").get("item").getAsString(), rule);
        }
        return byResult;
    }

    private static void collectBand(JsonObject data, String field, int section, Set<BandItem> out) {
        JsonArray band = data.getAsJsonArray(field);
        if (band == null) {
            return;
        }
        for (JsonElement element : band) {
            JsonArray pair = element.getAsJsonArray();
            assertEquals(2, pair.size(), field + " 条目应为 [item, weight]");
            if (pair.get(1).getAsDouble() > 0.0D) {
                out.add(new BandItem(pair.get(0).getAsString(), section));
            }
        }
    }

    private static List<JsonObject> chapterEntries() throws Exception {
        List<JsonObject> out = new ArrayList<>();
        try (Stream<Path> stream = Files.list(ENTRIES)) {
            for (Path path : stream.filter(p -> p.getFileName().toString().endsWith(".json")).toList()) {
                JsonObject entry = readJson(path);
                if (CATEGORY.equals(entry.get("category").getAsString())) {
                    out.add(entry);
                }
            }
        }
        assertFalse(out.isEmpty(), "幻想素材分类下没有任何词条");
        return out;
    }

    private static List<Page> pages(JsonObject entry) {
        List<Page> out = new ArrayList<>();
        for (JsonElement element : entry.getAsJsonArray("pages")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject page = element.getAsJsonObject();
            out.add(new Page(
                    page.get("type").getAsString(),
                    page.has("advancement") ? page.get("advancement").getAsString() : null,
                    page.has("entries") ? page.get("entries").getAsString() : null,
                    page.has("ritual") ? page.get("ritual").getAsString() : null,
                    page.has("recipe") ? page.get("recipe").getAsString() : null,
                    page.has("result") ? page.get("result").getAsString() : null,
                    page.has("tool_item") ? page.get("tool_item").getAsString() : null));
        }
        return out;
    }

    private static JsonObject readJson(Path path) throws Exception {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
