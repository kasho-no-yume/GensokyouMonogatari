package com.bitsson.gensokyou.testkit.scenario;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.testkit.probe.Probe;
import com.bitsson.gensokyou.testkit.probe.ProbeArgs;
import com.bitsson.gensokyou.testkit.probe.ProbeContext;
import com.bitsson.gensokyou.testkit.probe.ProbeRegistry;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 场景执行器：tick 驱动的状态机。
 *
 * <p>关键纪律：
 * <ul>
 *   <li><b>等待消费服务端 tick</b>，MUST NOT 用编排侧墙钟——墙钟有进程启动与调度抖动，
 *       亚秒级时序断言（「1 秒内爆散」）在墙钟上根本判不准。</li>
 *   <li><b>MUST NOT 阻塞服务端线程</b>：本类没有 sleep，等待靠 tick 回调推进。</li>
 *   <li>任一断言 FAIL 即终止后续步骤——它们的前置已不成立。</li>
 *   <li>路径取不到即判 FAIL，MUST NOT 当成 0 或空值蒙混过关。</li>
 * </ul>
 */
public final class ScenarioRunner {

    public static final String RUNNING = "RUNNING";
    public static final String PASS = "PASS";
    public static final String FAIL = "FAIL";
    public static final String TIMEOUT = "TIMEOUT";
    public static final String ERROR = "ERROR";

    private final ScenarioSpec spec;
    private final CommandSourceStack source;
    private final ServerLevel level;
    private final Map<String, JsonObject> snapshots = new LinkedHashMap<>();
    private final List<JsonObject> stepReports = new ArrayList<>();

    private int cursor;
    private boolean waiting;
    private int waitRemaining;
    private long startTick;
    private String result = RUNNING;
    private String detail = "";
    /** 执行过的断言步数（assert / assert_delta）。 */
    private int assertions;
    /** 通过的断言步数。 */
    private int assertPassed;
    /** 判为 FAIL 的步数（任何 op）。 */
    private int failedSteps;
    private String lastSnapshot;

    public ScenarioRunner(ScenarioSpec spec, CommandSourceStack source, ServerLevel level) {
        this.spec = spec;
        this.source = source;
        this.level = level;
        this.startTick = level.getGameTime();
    }

    public String name() {
        return spec.name();
    }

    public boolean isFinished() {
        return !RUNNING.equals(result);
    }

    public String result() {
        return result;
    }

    public int cursor() {
        return cursor;
    }

    // ------------------------------------------------------------------ tick

    /** 由服务端 tick 回调驱动，推进场景。 */
    public void tick() {
        if (!RUNNING.equals(result)) {
            return;
        }
        long now = level.getGameTime();
        if (now - startTick > spec.timeoutTicks()) {
            finish(TIMEOUT, "timeout after " + spec.timeoutTicks() + " ticks");
            return;
        }
        while (cursor < spec.steps().size()) {
            JsonObject step = spec.steps().get(cursor);
            String op = op(step);

            if ("wait".equals(op)) {
                int ticks = intOf(step, "ticks", 1);
                if (!waiting) {
                    waiting = true;
                    waitRemaining = Math.max(0, ticks);
                }
                if (waitRemaining > 0) {
                    waitRemaining--;
                    return; // 消费本 tick，下个 tick 再来
                }
                waiting = false;
                cursor++;
                continue;
            }

            JsonObject report = execute(step, op);
            stepReports.add(report);
            cursor++;
            if (!RUNNING.equals(result)) {
                // 失败/出错：立刻落盘报告（否则报告会永远不写——因为下一 tick 的
                // tick() 开头就会因 result != RUNNING 直接返回）。
                finish(result, detail);
                return;
            }
        }
        finish(PASS, "");
    }

    // --------------------------------------------------------------- execute

    private JsonObject execute(JsonObject step, String op) {
        JsonObject report = new JsonObject();
        report.addProperty("index", cursor);
        report.addProperty("op", op);
        try {
            switch (op) {
                case "cmd" -> execCmd(step, report);
                case "probe" -> execProbe(step, report);
                case "assert" -> execAssert(step, report, false);
                case "assert_delta" -> execAssert(step, report, true);
                case "log" -> execLog(step, report);
                default -> {
                    report.addProperty("status", FAIL);
                    report.addProperty("detail", "unknown op: " + op);
                    fail("step " + cursor + ": unknown op " + op);
                }
            }
        } catch (Exception e) {
            report.addProperty("status", FAIL);
            report.addProperty("detail", e.toString());
            fail("step " + cursor + " (" + op + "): " + e);
        }
        if (!report.has("status")) {
            report.addProperty("status", PASS);
        }
        return report;
    }

    private void execCmd(JsonObject step, JsonObject report) {
        String cmd = step.has("cmd") ? step.get("cmd").getAsString() : "";
        String bare = cmd.startsWith("/") ? cmd.substring(1).trim() : cmd.trim();
        report.addProperty("cmd", cmd);
        int code;
        try {
            // 直接走 Brigadier dispatcher（而非 Commands.performPrefixedCommand）：
            // 后者在 1.21.1 返回 void 且把结果异步投递，拿不到返回码；
            // dispatcher.execute 同步返回命令的返回值，命令写错时抛 CommandSyntaxException
            // ——这正是我们要的：命令没跑成 MUST 让该步判 FAIL，
            // 否则「拼错命令」会伪装成「前置条件天然满足」。
            code = source.getServer().getCommands().getDispatcher().execute(bare, source);
        } catch (CommandSyntaxException e) {
            report.addProperty("status", FAIL);
            report.addProperty("code", 0);
            report.addProperty("detail", "command error: " + e.getMessage());
            fail("step " + cursor + ": command '" + cmd + "' failed: " + e.getMessage());
            return;
        } catch (RuntimeException e) {
            report.addProperty("status", FAIL);
            report.addProperty("code", -1);
            report.addProperty("detail", "command threw: " + e);
            fail("step " + cursor + ": command '" + cmd + "' threw: " + e);
            return;
        }
        report.addProperty("code", code);
        if (step.has("expect_code")) {
            int expect = step.get("expect_code").getAsInt();
            if (code != expect) {
                report.addProperty("status", FAIL);
                report.addProperty("detail", "command returned " + code + ", expected " + expect);
                fail("step " + cursor + ": command '" + cmd + "' returned " + code + ", expected " + expect);
            }
        }
    }

    private void execProbe(JsonObject step, JsonObject report) throws Exception {
        String id = step.has("id") ? step.get("id").getAsString() : "";
        Optional<Probe> probe = ProbeRegistry.find(id);
        if (probe.isEmpty()) {
            report.addProperty("status", FAIL);
            report.addProperty("detail", "unknown probe: " + id);
            fail("step " + cursor + ": unknown probe " + id);
            return;
        }
        Map<String, String> args = step.has("args") && step.get("args").isJsonObject()
                ? ProbeArgs.fromJson(step.getAsJsonObject("args"))
                : Map.of();
        JsonObject value = probe.get().run(new ProbeContext(source, level, args));
        String as = step.has("as") && step.get("as").isJsonPrimitive()
                ? step.get("as").getAsString()
                : id;
        snapshots.put(as, value);
        lastSnapshot = as;
        report.addProperty("as", as);
        report.add("value", value);
    }

    private void execAssert(JsonObject step, JsonObject report, boolean delta) {
        assertions++;
        if (doAssert(step, report, delta)) {
            assertPassed++;
        }
    }

    /**
     * 执行一条断言。
     *
     * @return true = 断言通过；false = 判为 FAIL（并已写入 report 与 detail）
     */
    private boolean doAssert(JsonObject step, JsonObject report, boolean delta) {
        String path = step.has("path") ? step.get("path").getAsString() : "";
        String cmp = step.has("cmp") && step.get("cmp").isJsonPrimitive()
                ? step.get("cmp").getAsString()
                : "eq";
        JsonElement expected = step.has("value") ? step.get("value") : JsonNull.INSTANCE;

        // 参照快照：base 指定；否则取最近一次 probe 的快照。
        String baseName = step.has("base") && step.get("base").isJsonPrimitive()
                ? step.get("base").getAsString()
                : lastSnapshot;
        // 路径自带快照前缀时（"before.current"）以路径为准。
        String scopedPath = path;
        int dot = path.indexOf('.');
        if (dot > 0) {
            String head = path.substring(0, dot);
            if (snapshots.containsKey(head)) {
                baseName = head;
                scopedPath = path.substring(dot + 1);
            }
        }

        JsonObject baseSnap = baseName == null ? null : snapshots.get(baseName);
        if (baseSnap == null) {
            report.addProperty("status", FAIL);
            report.addProperty("detail", "no snapshot '" + baseName + "' to read '" + path + "' from");
            fail("step " + cursor + ": no snapshot '" + baseName + "'");
            return false;
        }

        JsonElement actual = resolve(baseSnap, scopedPath);
        report.addProperty("base", baseName);
        report.addProperty("path", path);
        report.addProperty("cmp", cmp);
        report.add("actual", actual == null ? JsonNull.INSTANCE : actual);

        // exists / absent 问的是「路径能否解析」，与「值是否为 null」无关：
        //   exists  = 键存在（值可以是 null）
        //   absent  = 键不存在
        boolean pathOptional = "exists".equals(cmp) || "absent".equals(cmp);
        if (actual == null && pathOptional) {
            boolean ok = "absent".equals(cmp);
            if (!ok) {
                report.addProperty("status", FAIL);
                report.addProperty("detail", "path '" + path + "' not found in snapshot '" + baseName + "'");
                fail("step " + cursor + ": path '" + path + "' not found in snapshot '" + baseName + "'");
            }
            return ok;
        }

        if (delta) {
            String nowName = step.has("now") && step.get("now").isJsonPrimitive()
                    ? step.get("now").getAsString()
                    : lastSnapshot;
            JsonObject nowSnap = nowName == null ? null : snapshots.get(nowName);
            if (nowSnap == null) {
                report.addProperty("status", FAIL);
                report.addProperty("detail", "no snapshot '" + nowName + "' to diff against");
                fail("step " + cursor + ": no snapshot '" + nowName + "'");
                return false;
            }
            JsonElement nowValue = resolve(nowSnap, scopedPath);
            if (actual == null || nowValue == null) {
                report.addProperty("status", FAIL);
                report.addProperty("detail", "path '" + path + "' missing in one of the snapshots");
                fail("step " + cursor + ": path '" + path + "' missing in one of the snapshots");
                return false;
            }
            Double a = asDouble(actual);
            Double b = asDouble(nowValue);
            if (a == null || b == null) {
                report.addProperty("status", FAIL);
                report.addProperty("detail", "delta requires numeric values");
                fail("step " + cursor + ": delta requires numeric values");
                return false;
            }
            double deltaValue = b - a;
            report.addProperty("actual", deltaValue);
            report.addProperty("delta", deltaValue);
            report.add("now", nowValue);
            if (!compare(cmp, deltaValue, asDouble(expected), step)) {
                report.addProperty("status", FAIL);
                report.addProperty("detail", describe(cmp, deltaValue, expected));
                fail("step " + cursor + ": delta(" + path + ") = " + deltaValue + ", expected " + expected);
                return false;
            }
            return true;
        }

        if (actual == null) {
            report.addProperty("status", FAIL);
            report.addProperty("detail", "path '" + path + "' not found in snapshot '" + baseName + "'");
            fail("step " + cursor + ": path '" + path + "' not found in snapshot '" + baseName + "'");
            return false;
        }
        if (!compare(cmp, actual, expected, step)) {
            report.addProperty("status", FAIL);
            report.addProperty("detail", describe(cmp, actual, expected));
            fail("step " + cursor + ": " + path + " = " + actual + ", expected " + cmp + " " + expected);
            return false;
        }
        return true;
    }

    private void execLog(JsonObject step, JsonObject report) {
        String tag = step.has("tag") ? step.get("tag").getAsString() : spec.name();
        String message = step.has("message") ? step.get("message").getAsString() : "";
        report.addProperty("tag", tag);
        report.addProperty("message", message);
        Gensokyou.LOGGER.info("[GS-SCENARIO:{}] {}", tag, message);
    }

    // ------------------------------------------------------------- comparing

    private boolean compare(String cmp, JsonElement actual, JsonElement expected, JsonObject step) {
        switch (cmp) {
            case "exists":
                return actual != null;
            case "absent":
                return actual == null;
            case "eq":
                return jsonEquals(actual, expected);
            case "ne":
                return !jsonEquals(actual, expected);
            case "lt":
            case "le":
            case "gt":
            case "ge": {
                Double a = asDouble(actual);
                Double b = asDouble(expected);
                if (a == null || b == null) {
                    return false;
                }
                return switch (cmp) {
                    case "lt" -> a < b;
                    case "le" -> a <= b;
                    case "gt" -> a > b;
                    default -> a >= b;
                };
            }
            case "approx": {
                Double a = asDouble(actual);
                Double b = asDouble(expected);
                if (a == null || b == null) {
                    return false;
                }
                double tol = step.has("tolerance") ? step.get("tolerance").getAsDouble() : 0.01;
                boolean absolute = step.has("abs") && step.get("abs").getAsBoolean();
                double diff = Math.abs(a - b);
                if (absolute) {
                    return diff <= tol;
                }
                double scale = Math.abs(b);
                return scale < 1e-9 ? diff <= tol : diff / scale <= tol;
            }
            case "contains": {
                if (actual == null || actual.isJsonNull() || expected == null) {
                    return false;
                }
                if (actual.isJsonArray()) {
                    for (JsonElement e : actual.getAsJsonArray()) {
                        if (jsonEquals(e, expected)) {
                            return true;
                        }
                    }
                    return false;
                }
                if (actual.isJsonObject()) {
                    return actual.getAsJsonObject().has(expected.getAsString());
                }
                if (actual.isJsonPrimitive() && expected.isJsonPrimitive()) {
                    return actual.getAsString().contains(expected.getAsString());
                }
                return false;
            }
            default:
                return false;
        }
    }

    private boolean compare(String cmp, double actual, Double expected, JsonObject step) {
        if (expected == null) {
            return false;
        }
        return compare(cmp, new JsonPrimitive(actual), new JsonPrimitive(expected), step);
    }

    private static boolean jsonEquals(JsonElement a, JsonElement b) {
        if (a == null || b == null) {
            return a == b;
        }
        if (a.isJsonPrimitive() && b.isJsonPrimitive()) {
            JsonPrimitive pa = a.getAsJsonPrimitive();
            JsonPrimitive pb = b.getAsJsonPrimitive();
            if (pa.isNumber() && pb.isNumber()) {
                return Double.compare(pa.getAsDouble(), pb.getAsDouble()) == 0;
            }
            if (pa.isBoolean() && pb.isBoolean()) {
                return pa.getAsBoolean() == pb.getAsBoolean();
            }
            return pa.getAsString().equals(pb.getAsString());
        }
        return a.equals(b);
    }

    private static Double asDouble(JsonElement e) {
        if (e == null || !e.isJsonPrimitive()) {
            return null;
        }
        JsonPrimitive p = e.getAsJsonPrimitive();
        if (p.isNumber()) {
            return p.getAsDouble();
        }
        if (p.isString()) {
            try {
                return Double.parseDouble(p.getAsString().trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private static String describe(String cmp, JsonElement actual, JsonElement expected) {
        return "actual=" + (actual == null ? "null" : actual.toString())
                + " expected " + cmp + " " + (expected == null ? "null" : expected.toString());
    }

    private static String describe(String cmp, double actual, JsonElement expected) {
        return "actual=" + actual + " expected " + cmp + " "
                + (expected == null ? "null" : expected.toString());
    }

    // ----------------------------------------------------------- path lookup

    /** 在快照里按路径取值；取不到返回 null（MUST NOT 返回 0 或空对象冒充）。 */
    static JsonElement resolve(JsonObject snapshot, String path) {
        JsonElement cur = snapshot;
        for (String seg : splitPath(path)) {
            if (cur == null || cur.isJsonNull()) {
                return null;
            }
            if (cur.isJsonObject()) {
                cur = cur.getAsJsonObject().get(seg);
            } else if (cur.isJsonArray()) {
                int idx;
                try {
                    idx = Integer.parseInt(seg);
                } catch (NumberFormatException e) {
                    return null;
                }
                JsonArray arr = cur.getAsJsonArray();
                if (idx < 0 || idx >= arr.size()) {
                    return null;
                }
                cur = arr.get(idx);
            } else {
                return null;
            }
        }
        return cur;
    }

    /** {@code a.b[0].c} → {@code ["a","b","0","c"]}。 */
    private static List<String> splitPath(String path) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '.') {
                flush(out, cur);
            } else if (c == '[') {
                flush(out, cur);
                int close = path.indexOf(']', i);
                if (close < 0) {
                    return out;
                }
                out.add(path.substring(i + 1, close).trim());
                i = close;
            } else {
                cur.append(c);
            }
        }
        flush(out, cur);
        return out;
    }

    private static void flush(List<String> out, StringBuilder cur) {
        if (cur.length() > 0) {
            out.add(cur.toString());
            cur.setLength(0);
        }
    }

    // ---------------------------------------------------------------- finish

    private void fail(String message) {
        failedSteps++;
        if (FAIL.equals(result)) {
            return;
        }
        result = FAIL;
        detail = message;
    }

    private void finish(String finalResult, String finalDetail) {
        this.result = finalResult;
        this.detail = finalDetail;

        JsonObject report = new JsonObject();
        report.addProperty("name", spec.name());
        report.addProperty("result", finalResult);
        report.addProperty("detail", finalDetail);
        report.addProperty("assertions", assertions);
        report.addProperty("passed", assertPassed);
        report.addProperty("failed_steps", failedSteps);
        report.addProperty("started_tick", startTick);
        report.addProperty("ended_tick", level.getGameTime());
        JsonArray steps = new JsonArray();
        for (JsonObject s : stepReports) {
            steps.add(s);
        }
        report.add("steps", steps);

        Path file = writeReport(report);
        String pathText = file == null ? "<none>" : file.toString();
        String line = "[GS-SCENARIO] " + spec.name() + " " + finalResult
                + " " + assertPassed + "/" + assertions + " report=" + pathText;
        Gensokyou.LOGGER.info(line);
        try {
            source.sendSuccess(() -> Component.literal(line), false);
        } catch (RuntimeException ignored) {
            // 命令来源可能已失效（RCON 连接早已断开）——不影响结论落盘
        }
    }

    private Path writeReport(JsonObject report) {
        try {
            Path dir = level.getServer().getServerDirectory().resolve("gs_report");
            Files.createDirectories(dir);
            Path file = dir.resolve(spec.name() + ".json");
            Files.writeString(file, report.toString(), StandardCharsets.UTF_8);
            return file;
        } catch (Exception e) {
            Gensokyou.LOGGER.error("[GS-SCENARIO] failed to write report for {}: {}", spec.name(), e.toString());
            return null;
        }
    }

    // ----------------------------------------------------------------- utils

    private static String op(JsonObject step) {
        if (step == null || !step.has("op") || !step.get("op").isJsonPrimitive()) {
            return "";
        }
        return step.get("op").getAsString().trim().toLowerCase(Locale.ROOT);
    }

    private static int intOf(JsonObject step, String key, int fallback) {
        if (step == null || !step.has(key) || !step.get(key).isJsonPrimitive()) {
            return fallback;
        }
        try {
            return step.get(key).getAsInt();
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
