package com.bitsson.gensokyou.audio;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 唯一出声出口的<b>静态边界</b>校验。
 *
 * <p>规则不是「全仓 baseline 全绿」——仓库里既有服务端音效广播（仪式/物品/方块）
 * 合法存在，硬要求全绿只会逼人写无意义豁免。规则是<b>棘轮</b>：
 *
 * <ol>
 *   <li>扫描范围内的生产代码，除 {@code audio} 包外 MUST NOT 出现直接播放调用；</li>
 *   <li>任何临时存量 MUST 登记在豁免清单里，且清单内的文件 MUST 仍然违规
 *       —— 修好一处却不删条目同样失败，于是清单只会变短；全部迁移后清单可为空。</li>
 * </ol>
 *
 * <p><b>禁止模式必须覆盖「不叫 playSound 的直放」</b>：存量直放曾走
 * {@code getSoundManager().play(SimpleSoundInstance...)}，只匹配 {@code playSound(}
 * 会让本测试对真实违规恒绿。
 *
 * <p>刻意<b>不</b>禁止裸 {@code SoundEvents.}：{@code registry/ModEquipmentMaterials}
 * 用它作材质装备音，是合法常量引用。
 */
class AudioBoundaryTest {

    private static final Path SOURCE_ROOT =
            Path.of("src", "main", "java", "com", "bitsson", "gensokyou");
    private static final Path ALLOWLIST =
            Path.of("src", "test", "resources", "audio-playback-allowlist.txt");

    /** 战斗表现层。仪式/物品/方块的服务端广播不在此列。 */
    private static final List<String> SCAN_ROOTS =
            List.of("client", "danmaku", "entity", "item/weapon", "network");

    private static final List<String> FORBIDDEN = List.of(
            "playSound(",
            "playLocalSound(",
            "getSoundManager(",
            "SimpleSoundInstance",
            "BuiltInRegistries.SOUND_EVENT");

    private record Violation(Path file, int line, String pattern, String text) {
        String describe() {
            return SOURCE_ROOT.relativize(file).toString().replace('\\', '/')
                    + ":" + line + "  [" + pattern + "]  " + text.trim();
        }
    }

    @Test
    void allowlistedFilesStillViolateSoTheListCanOnlyShrink() throws IOException {
        Map<String, String> allowed = readAllowlist();
        // 空清单是棘轮的合法终点：两处存量直放迁移到 audio 门面后不再需要任何豁免。

        List<String> problems = new ArrayList<>();
        Set<String> violations = violationIndex();
        for (String path : allowed.keySet()) {
            Path file = Path.of(path);
            if (!Files.isRegularFile(file)) {
                problems.add("清单条目指向不存在的文件: " + path);
            } else if (!isScanned(file)) {
                problems.add("清单条目不在扫描范围内: " + path);
            } else if (!violations.contains(normalize(file))) {
                problems.add("清单条目已不再违规，请删除该条目: " + path);
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void noDirectPlaybackOutsideTheAudioPackageOrAllowlist() throws IOException {
        Map<String, String> allowed = readAllowlist();
        List<Violation> unlisted = new ArrayList<>();
        for (Violation violation : scan()) {
            if (!allowed.containsKey(violation.file().toString().replace('\\', '/'))) {
                unlisted.add(violation);
            }
        }
        assertTrue(unlisted.isEmpty(),
                "扫描范围内出现 audio 包之外的直接播放调用（应改走 Audio 门面，或登记豁免并说明理由）:\n"
                        + join(unlisted));
    }

    private static Set<String> violationIndex() throws IOException {
        Set<String> index = new LinkedHashSet<>();
        for (Violation violation : scan()) {
            index.add(normalize(violation.file()));
        }
        return index;
    }

    private static String normalize(Path file) {
        return file.toString().replace('\\', '/');
    }

    private static List<Violation> scan() throws IOException {
        List<Violation> result = new ArrayList<>();
        for (String root : SCAN_ROOTS) {
            Path directory = SOURCE_ROOT.resolve(root);
            if (!Files.isDirectory(directory)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(directory)) {
                for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                    result.addAll(scanFile(file));
                }
            }
        }
        return result;
    }

    private static List<Violation> scanFile(Path file) throws IOException {
        List<Violation> result = new ArrayList<>();
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            for (String pattern : FORBIDDEN) {
                if (line.contains(pattern)) {
                    result.add(new Violation(file, i + 1, pattern, line));
                }
            }
        }
        return result;
    }

    private static boolean isScanned(Path file) {
        String path = normalize(file);
        for (String root : SCAN_ROOTS) {
            if (path.startsWith(normalize(SOURCE_ROOT.resolve(root)) + "/")) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, String> readAllowlist() throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        if (!Files.isRegularFile(ALLOWLIST)) {
            return entries;
        }
        for (String raw : Files.readAllLines(ALLOWLIST, StandardCharsets.UTF_8)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int separator = line.indexOf('|');
            String path = separator < 0 ? line : line.substring(0, separator);
            String reason = separator < 0 ? "" : line.substring(separator + 1);
            assertFalse(reason.isBlank(), "豁免条目必须写明理由: " + path);
            entries.put(path.trim(), reason.trim());
        }
        return entries;
    }

    private static String join(List<Violation> violations) {
        StringBuilder builder = new StringBuilder();
        for (Violation violation : violations) {
            builder.append("  ").append(violation.describe()).append('\n');
        }
        return builder.toString();
    }
}
