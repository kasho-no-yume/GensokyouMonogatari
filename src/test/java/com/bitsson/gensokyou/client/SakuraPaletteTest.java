package com.bitsson.gensokyou.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 樱符调色板的<b>跨语言一致性</b>回归。
 *
 * <p>调色板的「唯一色源」在 Python 侧（{@code tools/textures/gui_sakura.py} 的 {@code PAL}），
 * Java 侧必须<b>逐值一致</b>。历史上樱粉一度同时存在三个值
 * （0xC77A92 / 0xC87A92 / 0xCB7A92），跨面板对不上颜色时无从查起。
 *
 * <p>本测试直接读那份 Python 源文件比对 —— 改了 Python 忘了改 Java（或反之）会红。
 */
class SakuraPaletteTest {

    /** Python 侧 PAL 的一行：{@code "P": (253, 240, 242),} */
    private static final Pattern PAL_ENTRY =
            Pattern.compile("\"(\\w)\"\\s*:\\s*\\((\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\)");

    private static Path locatePythonPalette() {
        Path candidate = Path.of("tools", "textures", "gui_sakura.py");
        if (Files.exists(candidate)) {
            return candidate;
        }
        // 测试工作目录偶尔不是工程根，向上找一层
        Path up = Path.of("..", "tools", "textures", "gui_sakura.py");
        return Files.exists(up) ? up : candidate;
    }

    private static Map<String, int[]> parsePythonPalette() throws IOException {
        Path file = locatePythonPalette();
        assertTrue(Files.exists(file), "找不到 Python 调色板源文件：" + file.toAbsolutePath());
        String src = Files.readString(file, StandardCharsets.UTF_8);
        Map<String, int[]> pal = new HashMap<>();
        Matcher m = PAL_ENTRY.matcher(src);
        while (m.find()) {
            pal.put(m.group(1), new int[]{
                    Integer.parseInt(m.group(2)),
                    Integer.parseInt(m.group(3)),
                    Integer.parseInt(m.group(4))});
        }
        assertTrue(pal.size() >= 8, "解析到的调色板条目太少：" + pal.size());
        return pal;
    }

    private static int rgb(int argb) {
        return 0xFF000000 | argb;
    }

    private static void assertSame(int expectedRgb, Map<String, int[]> pal, String key, String what) {
        int[] py = pal.get(key);
        assertTrue(py != null, "Python PAL 里没有条目：" + key);
        int actual = rgb(expectedRgb);
        assertEquals(py[0], (actual >> 16) & 0xFF, what + " 的 R 分量与 Python 不一致");
        assertEquals(py[1], (actual >> 8) & 0xFF, what + " 的 G 分量与 Python 不一致");
        assertEquals(py[2], actual & 0xFF, what + " 的 B 分量与 Python 不一致");
    }

    @Test
    void javaPaletteMatchesPythonPalette() throws IOException {
        Map<String, int[]> pal = parsePythonPalette();
        assertSame(SakuraPalette.PAPER, pal, "P", "纸底");
        assertSame(SakuraPalette.CARD, pal, "p", "卡片面底");
        assertSame(SakuraPalette.RECESS, pal, "i", "槽内凹底");
        assertSame(SakuraPalette.PINK, pal, "C", "樱粉主线");
        assertSame(SakuraPalette.PINK_SOFT, pal, "g", "樱粉柔线");
        assertSame(SakuraPalette.PINK_DARK, pal, "c", "樱粉暗调");
        assertSame(SakuraPalette.ROSE, pal, "A", "樱深红");
        assertSame(SakuraPalette.GOLD, pal, "G", "金");
    }

    @Test
    void paletteEntriesAreOpaque() {
        int[] colors = {
                SakuraPalette.PAPER, SakuraPalette.CARD, SakuraPalette.RECESS,
                SakuraPalette.PINK, SakuraPalette.PINK_SOFT, SakuraPalette.PINK_DARK,
                SakuraPalette.ROSE, SakuraPalette.GOLD, SakuraPalette.CREAM,
                SakuraPalette.INK, SakuraPalette.DARK,
                SakuraPalette.SCROLL_TRACK, SakuraPalette.SCROLL_THUMB,
        };
        for (int c : colors) {
            assertEquals(0xFF, (c >>> 24) & 0xFF,
                    "调色板颜色必须不透明（alpha=0xFF），否则 fill() 会画成半透明");
        }
    }
}
