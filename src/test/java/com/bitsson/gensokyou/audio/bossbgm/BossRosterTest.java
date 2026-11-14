package com.bitsson.gensokyou.audio.bossbgm;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BOSS 名册的<b>棘轮</b>：新出现的 {@code TouhouBoss} 实现必须入册或显式豁免。
 *
 * <p>为什么用源码扫描而不是枚举实体注册表：{@code DeferredRegister} 会触发
 * {@code BuiltInRegistries} 的静态初始化，纯 JUnit 下直接抛 {@code Not bootstrapped}。
 * 扫描源码是本仓库既有的做法（见 {@code AudioBoundaryTest}）。
 *
 * <p>两条断言互为棘轮：
 * <ul>
 *   <li>名册外的具体 BOSS 类 → 失败（不能悄悄没音乐）；</li>
 *   <li>豁免表里的类已不存在 → 失败（修好了必须删条目）。</li>
 * </ul>
 */
class BossRosterTest {

    private static final Path ENTITY_ROOT =
            Path.of("src", "main", "java", "com", "bitsson", "gensokyou", "entity");

    /** 认作「BOSS 血缘」的父类型片段。 */
    private static final List<String> BOSS_MARKERS = List.of(
            "TouhouBoss",
            "UnmigratedTouhouBoss",
            "FlandreEntity",
            "BloodBarProbeBoss");

    private static final Pattern CLASS_DECL =
            Pattern.compile("^\\s*((?:public|protected|private|final|static|abstract)\\s+)*class\\s+(\\w+)([^\\{]*)\\{");

    @Test
    void everyConcreteBossIsInTheRosterOrExplicitlyExcluded() throws IOException {
        Set<String> found = discoverBossFiles();
        List<String> problems = new ArrayList<>();
        for (String simpleName : found) {
            if (!BossBgms.knownClass(simpleName)) {
                problems.add(simpleName + " 实现/继承了 TouhouBoss，但不在 BossBgms.ROSTER 或 EXCLUDED 里");
            }
        }
        assertTrue(problems.isEmpty(),
                "BOSS 名册缺项（新增 BOSS 必须入册，否则它不会响 BGM）:\n  "
                        + String.join("\n  ", problems));
    }

    @Test
    void exclusionsAreStillNeeded() throws IOException {
        Set<String> found = discoverBossFiles();
        List<String> problems = new ArrayList<>();
        for (BossBgms.Excluded excluded : BossBgms.excludedEntries()) {
            if (!found.contains(excluded.className())) {
                problems.add("豁免条目已不再命中任何 BOSS 类，请删除: " + excluded.className());
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void everyRosterClassExists() throws IOException {
        Set<String> found = discoverBossFiles();
        List<String> problems = new ArrayList<>();
        for (BossBgms.BossEntry entry : BossBgms.roster()) {
            if (!found.contains(entry.className())) {
                problems.add("名册指向不存在的 BOSS 类: " + entry.className());
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    /** 扫描 entity 目录，返回「有具体实现」的 BOSS 文件名（不含 .java）。 */
    private static Set<String> discoverBossFiles() throws IOException {
        Set<String> result = new LinkedHashSet<>();
        if (!Files.isDirectory(ENTITY_ROOT)) {
            return result;
        }
        try (Stream<Path> files = Files.walk(ENTITY_ROOT)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                if (hasConcreteBossDeclaration(file)) {
                    String name = file.getFileName().toString();
                    result.add(name.substring(0, name.length() - ".java".length()));
                }
            }
        }
        return result;
    }

    private static boolean hasConcreteBossDeclaration(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        boolean bossBlood = false;
        for (String line : lines) {
            for (String marker : BOSS_MARKERS) {
                if (line.contains(marker)) {
                    bossBlood = true;
                }
            }
        }
        if (!bossBlood) {
            return false;
        }
        for (String line : lines) {
            Matcher matcher = CLASS_DECL.matcher(line);
            if (!matcher.find()) {
                continue;
            }
            String modifiers = line.substring(0, matcher.start(2));
            if (modifiers.contains("abstract")) {
                continue;
            }
            String superTypes = matcher.group(3);
            for (String marker : BOSS_MARKERS) {
                if (superTypes.contains(marker)) {
                    return true;
                }
            }
        }
        return false;
    }
}
