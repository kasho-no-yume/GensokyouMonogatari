package com.bitsson.gensokyou.ritual.editor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Map;

/**
 * v5 pattern JSON 的合并与序列化（仪式保存链路核心，design D5）：
 * 草稿（某阶 adds 全量替换 + palette 增量并入）套到基文件上得到合成产物，
 * GsonPretty 输出直接覆写文件——字段序不做承诺（loader 不关心排版）。
 */
public final class RitualPatternSerializer {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private RitualPatternSerializer() {
    }

    /** 基 raw 深拷贝后套草稿（原地修改返回新对象；levelNumber 无对应层则追加）。 */
    public static JsonObject mergeDraft(JsonObject base, int levelNumber, RitualDraft draft) {
        JsonObject merged = base.deepCopy().getAsJsonObject(); // Gson.fromJson(JsonObject) 会原样返回，必须 deepCopy
        JsonArray levels = merged.getAsJsonArray("levels");
        JsonObject target = null;
        for (JsonElement element : levels) {
            JsonObject level = element.getAsJsonObject();
            if (level.get("level").getAsInt() == levelNumber) {
                target = level;
                break;
            }
        }
        if (target == null) {
            target = new JsonObject();
            target.addProperty("level", levelNumber);
            levels.add(target);
        }
        target.add("adds", addsToJson(draft.adds()));
        JsonObject palette = merged.getAsJsonObject("palette");
        for (Map.Entry<Character, String> e : draft.paletteAdditions().entrySet()) {
            String key = String.valueOf(e.getKey());
            if (!palette.has(key)) {
                palette.addProperty(key, e.getValue());
            }
        }
        return merged;
    }

    public static JsonArray addsToJson(Iterable<RitualDiffCapture.AddsEntry> adds) {
        JsonArray array = new JsonArray();
        for (RitualDiffCapture.AddsEntry entry : adds) {
            JsonArray token = new JsonArray();
            token.add(String.valueOf(entry.key()));
            token.add(entry.x());
            token.add(entry.y());
            token.add(entry.z());
            if (entry.o() != null) {
                token.add((int) entry.o());
            }
            array.add(token);
        }
        return array;
    }

    /** 仓库排版：pretty、2 空格缩进。 */
    public static String serialize(JsonObject pattern) {
        return GSON.toJson(pattern) + "\n";
    }
}
