package com.bitsson.gensokyou.ritual;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 灵浴 GUI 语言键的<b>占位符契约</b>守卫。
 *
 * <p>实机踩过的坑：{@code tier_denied} 的语言值带两个 {@code %s}，而可见行传的是
 * {@code new String[0]} —— 实机直接显示出未替换的 {@code "%s > %s"}。同时 tooltip 的实参
 * 顺序也错了（玩家名顶替了「你的层级」）。
 *
 * <p>这类缺陷 {@code lang_audit.py} 查不出来：它只验证<b>键是否存在</b>，而这里键是在的，
 * 坏的是<b>占位符个数与实参个数不匹配</b>。本类把契约钉死。
 *
 * <p>刻意只覆盖灵浴：全量扫描需要解析 Java 调用点（行为、tooltip、颜色参数交织），
 * 性价比不抵维护成本。灵浴是本变更的责任范围，故逐键钉死。
 */
class ReiyokuLangContractTest {

    /** {@code %s} / {@code %d} / {@code %.Nf} 一类占位符。 */
    private static final Pattern PLACEHOLDER = Pattern.compile("%(?:\\d+\\$)?[-#+ 0,(]*\\d*(?:\\.\\d+)?[a-zA-Z%]");

    private static final String PREFIX = "gui.gensokyou.ritual.reiyoku.";

    private static JsonObject zh() throws IOException {
        Path p = Path.of("src", "main", "resources", "assets", "gensokyou", "lang", "zh_cn.json");
        try (Reader reader = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    /** 数一个语言值里的实参占位符个数（{@code %%} 是转义的字面百分号，不计）。 */
    private static int argCount(String value) {
        Matcher m = PLACEHOLDER.matcher(value);
        int n = 0;
        while (m.find()) {
            if (!"%%".equals(m.group())) {
                n++;
            }
        }
        return n;
    }

    /**
     * 越阶提示：**可见行 MUST NOT 带数字**，比较关系 MUST 只在 tooltip 里。
     *
     * <p>本条修过两次错，都记在这里：
     * <ol>
     *   <li>lang 值写成 {@code "泉等第不足（%s > %s）"} 而实参传 {@code new String[0]}
     *       —— 实机直接显示未替换的 {@code "%s > %s"}。</li>
     *   <li>补上实参后按 {@code [浴所阶, 玩家阶]} 传 —— 仍错。本行的成立<b>条件</b>是
     *       {@code 玩家阶 > 浴所阶}，写成不等式「浴所 &gt; 玩家」字面上恒假，实机读作
     *       「泉等第不足（2 &gt; 3）」，即<b>小 &gt; 大</b>。无标签的不等式必有一半是反的，
     *       而且超可见行 11 汉字预算。</li>
     * </ol>
     * 故可见行 MUST 是纯短标签（无占位符），tooltip 用「你的层级 %s 高于浴所等第 %s」
     * —— 顺序与句子天然一致，数值关系也必然为真。
     */
    @Test
    void tierDeniedVisibleLineCarriesNoComparison() throws IOException {
        JsonObject lang = zh();
        String visible = lang.get(PREFIX + "tier_denied").getAsString();
        assertEquals(0, argCount(visible),
                "the visible line MUST NOT interpolate a comparison; got: " + visible);
        assertFalse(visible.contains(">"), "the visible line MUST NOT contain '>': " + visible);
        assertTrue(visible.length() <= 11,
                "the visible line MUST stay within the 11-column budget: " + visible);

        String tip = lang.get(PREFIX + "tier_denied.tip").getAsString();
        assertEquals(2, argCount(tip),
                "tier_denied.tip MUST take 2 args (player level, structure level)");
        // 句式 MUST 是「你的层级 <玩家阶> 高于 浴所等第 <浴所阶>」：
        // 第一个占位符夹在「你的层级」与「高于」之间，第二个在「高于」之后。
        // 顺序一换，玩家读到的就是"你的层级 2 高于浴所等第 3"这种恒假的话。
        int first = tip.indexOf("%s");
        int second = tip.indexOf("%s", first + 1);
        int yours = tip.indexOf("你的层级");
        int above = tip.indexOf("高于");
        int bath = tip.indexOf("浴所等第");
        assertTrue(yours >= 0 && above > yours && bath > above
                        && first > yours && first < above && second > bath,
                "the tip MUST read player-level-then-structure-level: " + tip);
    }

    /**
     * 无占位符的可见行 MUST NOT 带占位符。
     *
     * <p>反向的那一半守卫：语言值里留了 {@code %s} 而代码传 {@code new String[0]}，
     * 是「未替换占位符」最常见的成因。
     */
    @Test
    void arglessKeysCarryNoPlaceholders() throws IOException {
        for (String key : new String[]{"not_started", "stalled", "tier_denied"}) {
            String value = lang().get(PREFIX + key).getAsString();
            assertEquals(0, argCount(value),
                    key + " takes no args, so its value MUST NOT contain placeholders: " + value);
        }
    }

    /** 全部灵浴语言键 MUST 不含畸形占位符（如落单的 {@code %}）。 */
    @Test
    void noMalformedPlaceholders() throws IOException {
        JsonObject lang = zh();
        for (String key : lang.keySet()) {
            if (!key.startsWith(PREFIX)) {
                continue;
            }
            String value = lang.get(key).getAsString();
            int bare = 0;
            for (int i = 0; i < value.length(); i++) {
                if (value.charAt(i) != '%') {
                    continue;
                }
                if (i + 1 < value.length() && value.charAt(i + 1) == '%') {
                    i++;
                    continue;
                }
                if (i + 1 >= value.length()) {
                    bare++;
                    continue;
                }
                // 交给 PLACEHOLDER 判定是否构成一个合法占位符
                Matcher m = PLACEHOLDER.matcher(value.substring(i));
                if (!m.lookingAt()) {
                    bare++;
                }
            }
            assertEquals(0, bare, key + " has a malformed placeholder: " + value);
        }
    }

    /** 灵浴 GUI 键 MUST 全部存在（防止删键后回落原始 key 字符串）。 */
    @Test
    void allReiyokuGuiKeysPresent() {
        assertFalse(langKeys().isEmpty(), "no reiyoku gui keys found in zh_cn");
        assertTrue(langKeys().contains(PREFIX + "bathing"));
        assertTrue(langKeys().contains(PREFIX + "buffer"));
        assertTrue(langKeys().contains(PREFIX + "charging"));
        assertTrue(langKeys().contains(PREFIX + "tier_denied"));
    }

    private static JsonObject cached;

    private static JsonObject lang() throws IOException {
        if (cached == null) {
            cached = zh();
        }
        return cached;
    }

    private static java.util.Set<String> langKeys() {
        // lang() 会抛 IOException，这里包一层仅用于"存在性"断言
        try {
            return lang().keySet();
        } catch (IOException e) {
            throw new AssertionError("cannot read zh_cn.json", e);
        }
    }
}
