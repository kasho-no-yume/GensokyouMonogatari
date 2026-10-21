package com.bitsson.gensokyou.testkit.probe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 探针参数解析：两种写法皆可——
 * <ul>
 *   <li>{@code k=v k2=v2}（空格分隔，值可含 {@code =} 之后的一切）</li>
 *   <li>一份 JSON 对象（以 {@code &#123;} 开头）</li>
 * </ul>
 *
 * <p>JSON 写法支持嵌套对象/数组——此时值以 JSON 文本形式落进 map，
 * 探针可自行再解析。
 */
public final class ProbeArgs {

    private ProbeArgs() {
    }

    /** 解析命令行参数串。解析失败时返回空 map（MUST NOT 抛异常——参数写错不该炸掉整个场景）。 */
    public static Map<String, String> parse(String raw) {
        Map<String, String> out = new LinkedHashMap<>();
        if (raw == null) {
            return out;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return out;
        }
        if (trimmed.startsWith("{")) {
            try {
                JsonElement parsed = JsonParser.parseString(trimmed);
                if (parsed.isJsonObject()) {
                    return fromJson(parsed.getAsJsonObject());
                }
            } catch (RuntimeException ignored) {
                // 落到 k=v 解析
            }
            return out;
        }
        for (String token : trimmed.split("\\s+")) {
            int eq = token.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            out.put(token.substring(0, eq).trim().toLowerCase(java.util.Locale.ROOT),
                    token.substring(eq + 1).trim());
        }
        return out;
    }

    /** JSON 对象 → 参数 map（值统一转字符串；嵌套结构保留 JSON 文本）。 */
    public static Map<String, String> fromJson(JsonObject obj) {
        Map<String, String> out = new LinkedHashMap<>();
        if (obj == null) {
            return out;
        }
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
            JsonElement v = e.getValue();
            String text;
            if (v == null || v.isJsonNull()) {
                text = "";
            } else if (v.isJsonPrimitive()) {
                text = v.getAsString();
            } else {
                text = v.toString();
            }
            out.put(e.getKey().toLowerCase(java.util.Locale.ROOT), text);
        }
        return out;
    }
}
