package com.bitsson.gensokyou.testkit.scenario;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 场景定义（从一份 JSON 解析）。
 *
 * <p>场景是**数据**，不是代码：新增一条游戏内验收项只需新增一份 JSON，
 * MUST NOT 需要改 mod 代码或重编译。
 */
public record ScenarioSpec(String name, int timeoutTicks, List<JsonObject> steps) {

    /** 默认超时：1200 tick = 60 秒。 */
    public static final int DEFAULT_TIMEOUT_TICKS = 1200;

    /**
     * 解析场景定义。解析期 MUST NOT 抛异常——非法 op 之类的问题留到执行期逐步报错，
     * 这样一份坏场景也能跑出「哪一步坏了」的报告，而不是在加载期就炸掉。
     */
    public static ScenarioSpec parse(JsonObject root, String fallbackName) {
        String name = fallbackName;
        if (root != null && root.has("name") && root.get("name").isJsonPrimitive()) {
            String declared = root.get("name").getAsString();
            if (declared != null && !declared.isBlank()) {
                name = declared;
            }
        }
        int timeout = DEFAULT_TIMEOUT_TICKS;
        if (root != null && root.has("timeout_ticks") && root.get("timeout_ticks").isJsonPrimitive()) {
            try {
                timeout = root.get("timeout_ticks").getAsInt();
            } catch (RuntimeException ignored) {
                timeout = DEFAULT_TIMEOUT_TICKS;
            }
        }
        List<JsonObject> steps = new ArrayList<>();
        if (root != null && root.has("steps") && root.get("steps").isJsonArray()) {
            JsonArray arr = root.getAsJsonArray("steps");
            for (JsonElement e : arr) {
                if (e.isJsonObject()) {
                    steps.add(e.getAsJsonObject());
                }
            }
        }
        return new ScenarioSpec(name, timeout, List.copyOf(steps));
    }
}
