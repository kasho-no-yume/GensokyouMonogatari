package com.bitsson.gensokyou.ritual.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一阶 adds 草稿（阶级保存的产物，仪式保存前只存于世界级 saved data）。
 * JSON 形态：{@code {"adds":[["S",1,0,1],...],"palette":{"F":"#gensokyou:..."}}}，
 * adds 条目为规范四分之一位置式数组（与 v5 文件同格式）。
 */
public record RitualDraft(List<RitualDiffCapture.AddsEntry> adds,
                          Map<Character, String> paletteAdditions) {

    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"adds\":[");
        for (int i = 0; i < adds.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            appendEntry(sb, adds.get(i));
        }
        sb.append("],\"palette\":{");
        boolean first = true;
        for (Map.Entry<Character, String> e : paletteAdditions.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(e.getKey()).append("\":\"").append(e.getValue()).append('"');
        }
        return sb.append("}}").toString();
    }

    public static RitualDraft fromJson(String json) {
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        List<RitualDiffCapture.AddsEntry> adds = new ArrayList<>();
        for (JsonElement element : obj.getAsJsonArray("adds")) {
            JsonArray entry = element.getAsJsonArray();
            adds.add(new RitualDiffCapture.AddsEntry(
                    entry.get(0).getAsString().charAt(0),
                    entry.get(1).getAsInt(), entry.get(2).getAsInt(), entry.get(3).getAsInt(),
                    entry.size() > 4 ? entry.get(4).getAsInt() : null));
        }
        Map<Character, String> palette = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : obj.getAsJsonObject("palette").entrySet()) {
            palette.put(e.getKey().charAt(0), e.getValue().getAsString());
        }
        return new RitualDraft(adds, palette);
    }

    private static void appendEntry(StringBuilder sb, RitualDiffCapture.AddsEntry entry) {
        sb.append("[\"").append(entry.key()).append("\",")
                .append(entry.x()).append(',').append(entry.y()).append(',').append(entry.z());
        if (entry.o() != null) {
            sb.append(',').append(entry.o());
        }
        sb.append(']');
    }
}
