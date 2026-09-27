package com.bitsson.gensokyou.ritual;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 指导书页型守卫：拼错的页型会让 Patchouli 抛 "Template ... does not exist"，
 * 并使整本书退化为空内容，故必须在构建期拦住。
 */
class PatchouliBookPageTypeTest {

    private static final Path BOOK = Path.of(
            "src/main/resources/assets/gensokyou/patchouli_books/gensokyou_book/en_us");
    private static final Path ENTRIES = BOOK.resolve("entries");
    private static final Path TEMPLATES = BOOK.resolve("templates");

    private static final Set<String> PATCHOULI_PAGE_TYPES = Set.of(
            "patchouli:text", "patchouli:crafting", "patchouli:smelting", "patchouli:blasting",
            "patchouli:smoking", "patchouli:campfire", "patchouli:smithing", "patchouli:stonecutting",
            "patchouli:image", "patchouli:spotlight", "patchouli:empty", "patchouli:multiblock",
            "patchouli:link", "patchouli:relations", "patchouli:entity", "patchouli:quest");

    private static final Set<String> PATCHOULI_COMPONENT_TYPES = Set.of(
            "patchouli:text", "patchouli:item", "patchouli:image", "patchouli:header",
            "patchouli:separator", "patchouli:frame", "patchouli:entity", "patchouli:tooltip",
            "patchouli:custom");

    @Test
    void everyEntryPageTypeResolves() throws Exception {
        Set<String> templateIds = templateIds();
        Set<String> valid = new HashSet<>(PATCHOULI_PAGE_TYPES);
        valid.addAll(templateIds);

        int entries = 0;
        int pages = 0;
        try (Stream<Path> stream = Files.list(ENTRIES)) {
            for (Path path : stream.filter(p -> p.getFileName().toString().endsWith(".json")).toList()) {
                JsonObject entry = readJson(path);
                String name = path.getFileName().toString();
                assertTrue(entry.has("name"), name + " 缺少 name");
                assertTrue(entry.has("category"), name + " 缺少 category");
                assertTrue(entry.has("icon"), name + " 缺少 icon");
                JsonArray entryPages = entry.getAsJsonArray("pages");
                assertFalse(entryPages.isEmpty(), name + " pages 为空");
                for (int i = 0; i < entryPages.size(); i++) {
                    JsonElement element = entryPages.get(i);
                    if (element.isJsonPrimitive()) {
                        continue;
                    }
                    JsonObject page = element.getAsJsonObject();
                    assertTrue(page.has("type"), name + " 第 " + i + " 页缺少 type");
                    assertTrue(valid.contains(page.get("type").getAsString()),
                            name + " 第 " + i + " 页页型无法解析: " + page.get("type").getAsString());
                    pages++;
                }
                entries++;
            }
        }
        assertTrue(entries > 0, "没有任何词条");
        assertTrue(pages > 0, "没有任何页面");
        assertTrue(valid.contains("patchouli:crafting"), "内置工作台配方页型缺失");
    }

    @Test
    void everyTemplateComponentResolves() throws Exception {
        Set<String> validPages = new HashSet<>(PATCHOULI_PAGE_TYPES);
        validPages.addAll(templateIds());
        int templates = 0;
        try (Stream<Path> stream = Files.list(TEMPLATES)) {
            for (Path path : stream.filter(p -> p.getFileName().toString().endsWith(".json")).toList()) {
                JsonObject template = readJson(path);
                String name = path.getFileName().toString();
                if (template.has("include")) {
                    for (JsonElement element : template.getAsJsonArray("include")) {
                        String target = element.isJsonPrimitive()
                                ? element.getAsString()
                                : element.getAsJsonObject().get("template").getAsString();
                        assertTrue(validPages.contains(target), name + " include 了不存在的模板: " + target);
                    }
                }
                JsonArray components = template.getAsJsonArray("components");
                assertFalse(components.isEmpty(), name + " 没有任何 components");
                for (int i = 0; i < components.size(); i++) {
                    JsonObject component = components.get(i).getAsJsonObject();
                    String type = component.get("type").getAsString();
                    assertTrue(PATCHOULI_COMPONENT_TYPES.contains(type),
                            name + " 第 " + i + " 个组件类型无法解析: " + type);
                }
                templates++;
            }
        }
        assertTrue(templates > 0, "没有任何模板");
    }

    @Test
    void craftingRecipePagesUseTheRegisteredPageType() throws Exception {
        Set<String> craftingEntries = new HashSet<>();
        try (Stream<Path> stream = Files.list(ENTRIES)) {
            for (Path path : stream.filter(p -> p.getFileName().toString().startsWith("item_")).toList()) {
                JsonArray pages = readJson(path).getAsJsonArray("pages");
                JsonObject recipePage = pages.get(pages.size() - 1).getAsJsonObject();
                if ("patchouli:crafting".equals(recipePage.get("type").getAsString())) {
                    craftingEntries.add(path.getFileName().toString());
                    String recipe = recipePage.get("recipe").getAsString();
                    assertTrue(recipe.startsWith("gensokyou:"));
                    assertTrue(Files.exists(Path.of("src/main/resources/data/gensokyou/recipe")
                            .resolve(recipe.substring("gensokyou:".length()) + ".json")),
                            path.getFileName() + " 指向不存在的工作台配方");
                }
            }
        }
        assertEquals(Set.of("item_spirit_core_0.json", "item_ritual_pedestal.json",
                "item_danmaku_assembly_bench.json"), craftingEntries);
    }

    /**
     * guide-book「新增书内文案仅维护 zh_cn」：书内正文键在 zh_cn.json 中必须齐备，
     * en_us.json 缺失不算缺陷，因此这里刻意不断言英文键存在。
     */
    @Test
    void bookTextKeysAreRequiredInChineseOnly() throws Exception {
        Path langDir = Path.of("src/main/resources/assets/gensokyou/lang");
        String zh = Files.readString(langDir.resolve("zh_cn.json"));
        Set<String> keys = referencedTextKeys();
        assertFalse(keys.isEmpty(), "书里没有引用任何正文文本键");
        for (String key : keys) {
            assertTrue(zh.contains("\"" + key + "\""), "zh_cn.json 缺少书中引用的文本键 " + key);
        }
    }

    private static Set<String> referencedTextKeys() throws Exception {
        Set<String> keys = new HashSet<>();
        try (Stream<Path> stream = Files.walk(BOOK)) {
            for (Path path : stream.filter(p -> p.getFileName().toString().endsWith(".json")).toList()) {
                collectTextKeys(JsonParser.parseString(Files.readString(path)), keys);
            }
        }
        return keys;
    }

    private static void collectTextKeys(JsonElement node, Set<String> keys) {
        if (node.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : node.getAsJsonObject().entrySet()) {
                String field = entry.getKey();
                JsonElement value = entry.getValue();
                if (("text".equals(field) || "title".equals(field))
                        && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                    String text = value.getAsString();
                    if (text.startsWith("gensokyou.")) {
                        keys.add(text);
                    }
                }
                collectTextKeys(value, keys);
            }
        } else if (node.isJsonArray()) {
            for (JsonElement element : node.getAsJsonArray()) {
                collectTextKeys(element, keys);
            }
        }
    }

    private static Set<String> templateIds() throws Exception {        Set<String> ids = new HashSet<>();
        try (Stream<Path> stream = Files.list(TEMPLATES)) {
            for (Path path : stream.filter(p -> p.getFileName().toString().endsWith(".json")).toList()) {
                String stem = path.getFileName().toString().replaceAll("\\.json$", "");
                ids.add("gensokyou:" + stem);
            }
        }
        assertFalse(ids.isEmpty(), "没有任何模板");
        return ids;
    }

    private static JsonObject readJson(Path path) throws Exception {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
