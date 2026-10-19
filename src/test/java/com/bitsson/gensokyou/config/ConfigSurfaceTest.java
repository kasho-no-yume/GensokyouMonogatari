package com.bitsson.gensokyou.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
/**
 * config-surface：锁定配置面。
 *
 * <p>只允许 SERVER（{@link GensokyouServerConfig}）与 CLIENT（{@link GensokyouClientConfig}）；
 * 键集合 MUST 等于 {@code config-surface-allowlist.txt}（新增/删除键必须同步白名单）；
 * SERVER 配置 MUST NOT 含战力数值形态的键；原 COMMON 类 MUST 已删除。
 */
class ConfigSurfaceTest {

    /** 战力形态：出现在 SERVER 配置即视为违规（spirit cost 是经济，不在此列）。 */
    private static final List<String> FORBIDDEN_IN_SERVER = List.of(
            "DAMAGE", "ATTACK", "MAX_HEALTH", "_HP_", "BOSS_SECONDS",
            "TIER_SCALE", "DROP_CHANCE", "MONSTER_", "HITS", "FLANDRE");

    @Test
    void commonConfigClassIsGone() {
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("com.bitsson.gensokyou.config.GensokyouConfig"));
    }

    @Test
    void declaredKeysMatchAllowlist() throws Exception {
        Set<String> actual = new TreeSet<>();
        for (String f : keyNames(GensokyouServerConfig.class)) {
            actual.add("server:" + f);
        }
        for (String f : keyNames(GensokyouClientConfig.class)) {
            actual.add("client:" + f);
        }

        Set<String> expected = new HashSet<>();
        try (InputStream in = getClass().getResourceAsStream("/config-surface-allowlist.txt")) {
            assertTrue(in != null, "allowlist resource missing");
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                String s = line.trim();
                if (!s.isEmpty()) {
                    expected.add(s);
                }
            }
        }
        assertEquals(new TreeSet<>(expected), actual,
                "config surface changed: update config-surface-allowlist.txt and justify it in the change design.md");
    }

    @Test
    void serverHasNoCombatKeys() throws Exception {
        for (String f : keyNames(GensokyouServerConfig.class)) {
            for (String bad : FORBIDDEN_IN_SERVER) {
                assertTrue(!f.contains(bad),
                        "combat-shaped key in SERVER config: " + f + " (contains " + bad + ")");
            }
        }
    }

    @Test
    void clientOnlyTypesAreNotUsedServerSide() throws Exception {
        // 画质档 / 客户端设置若被服务端读取，会在 dedicated server 上抛未加载异常，且会改变服务端行为。
        Path root = Paths.get(System.getProperty("user.dir"), "src", "main", "java");
        assertTrue(Files.isDirectory(root), "source root missing: " + root);
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String rel = root.relativize(p).toString().replace('\\', '/');
                if (rel.contains("/client/") || rel.endsWith("Gensokyou.java")
                        || rel.endsWith("GensokyouClientConfig.java")
                        || rel.endsWith("GensokyouServerConfig.java")) {
                    continue;
                }
                String src = Files.readString(p, StandardCharsets.UTF_8);
                if (src.contains("GensokyouClientConfig") || src.contains("VisualPreferences")) {
                    offenders.add(rel);
                }
            }
        }
        assertEquals(List.of(), offenders,
                "client-only types referenced outside the client package");
    }

    private static Set<String> keyNames(Class<?> cfg) throws Exception {
        Set<String> out = new LinkedHashSet<>();
        for (Field f : cfg.getDeclaredFields()) {
            if (f.getName().equals("SPEC") || f.getName().equals("BUILDER")) {
                continue;
            }
            if (Modifier.isStatic(f.getModifiers())
                    && f.getType().getName().startsWith("net.neoforged.neoforge.common.ModConfigSpec")) {
                out.add(f.getName());
            }
        }
        return out;
    }
}
