package com.bitsson.gensokyou.client.ritual;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.ritual.RitualPattern;
import com.bitsson.gensokyou.ritual.RitualPatternLoader;
import com.bitsson.gensokyou.ritual.RitualRecipe;
import com.bitsson.gensokyou.ritual.RitualRecipeLoader;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 客户端仪式数据缓存（guide-book 方案 B）：接收服务端下发的原始 JSON，复用现有 parser
 * 重建 RitualPattern / RitualRecipe，并落盘缓存（config/gensokyou/ritual_data.json）。
 * 断线/重连时先读缓存，保证离线可读；数据未到达时查询为空，页面显示占位。
 */
public final class ClientRitualData {

    private static final Path CACHE =
            FMLPaths.CONFIGDIR.get().resolve("gensokyou").resolve("ritual_data.json");

    private static volatile Map<ResourceLocation, RitualPattern> patterns = Map.of();
    private static volatile List<RitualRecipe> recipes = List.of();
    private static volatile boolean diskChecked = false;

    private ClientRitualData() {
    }

    public static Optional<RitualPattern> pattern(ResourceLocation id) {
        ensureLoaded();
        return Optional.ofNullable(patterns.get(id));
    }

    public static List<RitualRecipe> recipesFor(ResourceLocation patternId) {
        ensureLoaded();
        List<RitualRecipe> out = new ArrayList<>();
        for (RitualRecipe recipe : recipes) {
            if (recipe.patternId().equals(patternId)) {
                out.add(recipe);
            }
        }
        out.sort(java.util.Comparator.comparingInt(RitualRecipe::minTier)
                .thenComparing(r -> r.id().toString()));
        return out;
    }

    public static boolean hasData() {
        ensureLoaded();
        return !patterns.isEmpty();
    }

    /** 服务端快照到达：解析、替换并落盘。 */
    public static void applyJson(String snapshotJson) {
        Map<ResourceLocation, RitualPattern> parsedPatterns = new LinkedHashMap<>();
        List<RitualRecipe> parsedRecipes = new ArrayList<>();
        try {
            JsonObject root = JsonParser.parseString(snapshotJson).getAsJsonObject();
            if (root.has("patterns")) {
                for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("patterns").entrySet()) {
                    ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
                    if (id == null) {
                        continue;
                    }
                    try {
                        parsedPatterns.put(id, RitualPatternLoader.parseForEdit(id,
                                entry.getValue().getAsJsonObject()));
                    } catch (Exception ex) {
                        Gensokyou.LOGGER.warn("Client rejected ritual pattern {}: {}",
                                id, ex.getMessage());
                    }
                }
            }
            if (root.has("recipes")) {
                for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("recipes").entrySet()) {
                    ResourceLocation fileId = ResourceLocation.tryParse(entry.getKey());
                    if (fileId == null) {
                        continue;
                    }
                    try {
                        parsedRecipes.addAll(RitualRecipeLoader.parseFileForClient(fileId,
                                entry.getValue().getAsJsonObject()));
                    } catch (Exception ex) {
                        Gensokyou.LOGGER.warn("Client rejected ritual recipe file {}: {}",
                                fileId, ex.getMessage());
                    }
                }
            }
        } catch (Exception ex) {
            Gensokyou.LOGGER.warn("Client rejected ritual data sync: {}", ex.getMessage());
            return;
        }
        patterns = Map.copyOf(parsedPatterns);
        recipes = List.copyOf(parsedRecipes);
        diskChecked = true;
        writeCache(snapshotJson);
    }

    /** 首次访问时尝试从磁盘缓存载入（离线可用）。 */
    public static synchronized void ensureLoaded() {
        if (diskChecked) {
            return;
        }
        diskChecked = true;
        if (!Files.exists(CACHE)) {
            return;
        }
        try {
            applyJson(Files.readString(CACHE, StandardCharsets.UTF_8));
        } catch (Exception ex) {
            Gensokyou.LOGGER.warn("Failed to read ritual data cache: {}", ex.getMessage());
        }
    }

    private static void writeCache(String json) {
        try {
            Files.createDirectories(CACHE.getParent());
            Files.writeString(CACHE, json, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            Gensokyou.LOGGER.warn("Failed to write ritual data cache: {}", ex.getMessage());
        }
    }
}
