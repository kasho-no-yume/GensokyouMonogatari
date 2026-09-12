package com.bitsson.gensokyou.ritual.editor;

import com.bitsson.gensokyou.Gensokyou;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 编辑杖保存落盘（design D5）：世界数据包 gs_dev 覆写 + dev 源码树回声。
 * 根路径注入以便纯 JVM 测试；返回值为回执摘要（成功/跳过原因）。
 */
public final class RitualEditorFileOps {

    public record Outcome(boolean datapackWritten, boolean sourceEchoed) {
    }

    private static final String PACK_MCMETA = """
            {
              "pack": {
                "pack_format": 48,
                "description": "Gensokyou ritual editor live output (overwrite of mod built-ins)"
              }
            }
            """;

    private RitualEditorFileOps() {
    }

    /** 写 {@code <datapacksRoot>/gs_dev/data/<ns>/rituals/<path>.json}（pack.mcmeta 缺失时创建）。 */
    public static Outcome save(Path datapacksRoot, Path devSourceRitualsDirOrNull,
                               ResourceLocation id, String json) {
        boolean datapackWritten = false;
        try {
            Path pack = datapacksRoot.resolve("gs_dev");
            Path rituals = pack.resolve("data").resolve(id.getNamespace()).resolve("rituals");
            Files.createDirectories(rituals);
            Path meta = pack.resolve("pack.mcmeta");
            if (!Files.exists(meta)) {
                Files.writeString(meta, PACK_MCMETA);
            }
            Files.writeString(rituals.resolve(id.getPath() + ".json"), json);
            datapackWritten = true;
        } catch (IOException exception) {
            Gensokyou.LOGGER.warn("Editor save datapack write failed for {}: {}", id, exception.getMessage());
        }
        boolean sourceEchoed = false;
        if (devSourceRitualsDirOrNull != null && Files.isDirectory(devSourceRitualsDirOrNull)) {
            try {
                Files.writeString(devSourceRitualsDirOrNull.resolve(id.getPath() + ".json"), json);
                sourceEchoed = true;
            } catch (IOException exception) {
                Gensokyou.LOGGER.warn("Editor source-tree echo failed for {}: {}", id, exception.getMessage());
            }
        }
        return new Outcome(datapackWritten, sourceEchoed);
    }

    /** dev 源码树探测：GAMEDIR(run/) 向上找 src/main/resources/data/<ns>/rituals；找不到返回 null。 */
    public static Path detectDevSourceRituals(Path gameDir, String namespace) {
        try {
            Path candidate = gameDir.toAbsolutePath().getParent() == null ? null
                    : gameDir.toAbsolutePath().getParent()
                    .resolve("src").resolve("main").resolve("resources")
                    .resolve("data").resolve(namespace).resolve("rituals")
                    .normalize();
            return candidate != null && Files.isDirectory(candidate) ? candidate : null;
        } catch (Exception exception) {
            return null;
        }
    }
}
