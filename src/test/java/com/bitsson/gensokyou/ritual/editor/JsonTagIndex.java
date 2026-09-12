package com.bitsson.gensokyou.ritual.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 测试侧 {@link BlockTagIndex}：直接读 {@code data/<ns>/tags/block/*.json}，
 * 语义与 python 校验器完全一致（含：忽略嵌套标签引用、品阶从方块 id 正则反推）。
 */
final class JsonTagIndex implements BlockTagIndex {

    private static final Pattern TIER =
            Pattern.compile("gensokyou:(ritual_stone)_([0-5])");

    private final Map<String, List<String>> tags = new HashMap<>();

    JsonTagIndex(Path dataDir) throws IOException {
        Path tagDir = dataDir.resolve("tags").resolve("block");
        try (var files = Files.list(tagDir)) {
            for (Path path : files.sorted().toList()) {
                String name = path.getFileName().toString();
                if (!name.endsWith(".json")) {
                    continue;
                }
                JsonObject json = readJson(path);
                List<String> members = new ArrayList<>();
                JsonArray values = json.getAsJsonArray("values");
                for (JsonElement value : values) {
                    String token = value.getAsString();
                    if (!token.startsWith("#")) {
                        members.add(token);
                    }
                }
                tags.put(dataDir.getFileName() + ":" + name.substring(0, name.length() - 5), members);
            }
        }
    }

    private static JsonObject readJson(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    @Override
    public List<String> members(String tagId) {
        return tags.getOrDefault(tagId, List.of());
    }

    @Override
    public int tierOf(String blockId) {
        Matcher matcher = TIER.matcher(blockId);
        return matcher.matches() ? Integer.parseInt(matcher.group(2)) : -1;
    }
}
