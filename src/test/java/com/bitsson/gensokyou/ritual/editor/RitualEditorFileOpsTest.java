package com.bitsson.gensokyou.ritual.editor;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 保存落盘（任务 5.5）：pack.mcmeta 生成、同 id 覆写自身、dev 探测、生产环境跳过回声。 */
class RitualEditorFileOpsTest {

    @TempDir
    Path tmp;

    @Test
    void writesDatapackWithMcmetaAndOverwritesSameId() throws Exception {
        Path datapacks = tmp.resolve("world").resolve("datapacks");
        ResourceLocation id = ResourceLocation.parse("gensokyou:my_circle");
        RitualEditorFileOps.Outcome first = RitualEditorFileOps.save(datapacks, null, id, "{\"v\":1}");
        assertTrue(first.datapackWritten());
        assertFalse(first.sourceEchoed());

        Path meta = datapacks.resolve("gs_dev").resolve("pack.mcmeta");
        Path ritual = datapacks.resolve("gs_dev").resolve("data").resolve("gensokyou")
                .resolve("rituals").resolve("my_circle.json");
        assertTrue(Files.exists(meta));
        assertTrue(Files.readString(meta).contains("pack_format"));
        assertEquals("{\"v\":1}", Files.readString(ritual));

        // 同 id 二次保存覆写自身产物，不产生新文件
        RitualEditorFileOps.save(datapacks, null, id, "{\"v\":2}");
        assertEquals("{\"v\":2}", Files.readString(ritual));
        try (var files = Files.list(ritual.getParent())) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void echoesToSourceTreeWhenPresent() throws Exception {
        Path datapacks = tmp.resolve("dp");
        Path srcRituals = tmp.resolve("src").resolve("main").resolve("resources")
                .resolve("data").resolve("gensokyou").resolve("rituals");
        Files.createDirectories(srcRituals);
        ResourceLocation id = ResourceLocation.parse("gensokyou:echo");
        RitualEditorFileOps.Outcome outcome =
                RitualEditorFileOps.save(datapacks, srcRituals, id, "{\"ok\":true}");
        assertTrue(outcome.sourceEchoed());
        assertEquals("{\"ok\":true}",
                Files.readString(srcRituals.resolve("echo.json")));
    }

    @Test
    void detectSourceReturnsNullWhenAbsentAndPathWhenPresent() throws Exception {
        Path gameDir = tmp.resolve("run");
        Files.createDirectories(gameDir);
        org.junit.jupiter.api.Assertions.assertNull(
                RitualEditorFileOps.detectDevSourceRituals(gameDir, "gensokyou"));
        // 造出 dev 结构后应命中
        Path rituals = gameDir.toAbsolutePath().getParent().resolve("src").resolve("main")
                .resolve("resources").resolve("data").resolve("gensokyou").resolve("rituals");
        Files.createDirectories(rituals);
        assertEquals(rituals.normalize(),
                RitualEditorFileOps.detectDevSourceRituals(gameDir, "gensokyou"));
    }
}
