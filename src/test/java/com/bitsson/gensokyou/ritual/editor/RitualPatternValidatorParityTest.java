package com.bitsson.gensokyou.ritual.editor;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * D4 对表测试：Java 校验器与 {@code tools/validate_ritual_pattern.py} 对仓库全体 pattern
 * 的 ERROR/WARN 结论必须逐行一致（按排序集合比对）。python 不可用时跳过（CI 无 python 环境兜底）。
 */
class RitualPatternValidatorParityTest {

    private static final Path DATA = Path.of("src", "main", "resources", "data", "gensokyou");
    private static final Path RITUALS = DATA.resolve("rituals");
    private static final Path VALIDATOR_PY = Path.of("tools", "validate_ritual_pattern.py");

    private static List<JsonObject> raws;
    private static JsonTagIndex index;

    @BeforeAll
    static void load() throws IOException {
        raws = new ArrayList<>();
        try (var files = Files.list(RITUALS)) {
            for (Path path : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    JsonElement element = JsonParser.parseReader(reader);
                    raws.add(element.getAsJsonObject());
                }
            }
        }
        index = new JsonTagIndex(DATA);
    }

    @Test
    void javaValidatorAgreesWithPython() throws IOException, InterruptedException {
        assumeTrue(Files.exists(VALIDATOR_PY), "python validator not present");
        Process process = new ProcessBuilder("python", "-X", "utf8", VALIDATOR_PY.toString())
                .redirectErrorStream(true)
                .start();
        String output;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            output = reader.lines().collect(Collectors.joining("\n"));
        }
        int exitCode = process.waitFor();
        assumeTrue(exitCode == 0 || output.contains("ERROR"), "python validator failed to run:\n" + output);

        List<String> pythonLines = output.lines()
                .filter(line -> line.startsWith("ERROR: ") || line.startsWith("WARN: "))
                .sorted()
                .toList();
        List<String> javaLines = RitualPatternValidator.validateAll(raws, index).stream()
                .map(RitualPatternValidator.Issue::line)
                .sorted()
                .toList();
        assertEquals(pythonLines, javaLines);
    }

    @Test
    void repositoryPatternsAreClean() {
        List<String> problems = RitualPatternValidator.validateAll(raws, index).stream()
                .map(RitualPatternValidator.Issue::line)
                .toList();
        assertEquals(List.of(), problems, "仓库内置 pattern 应全部通过校验");
    }
}
