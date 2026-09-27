package com.bitsson.gensokyou.client.ritual;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.ritual.RitualLootLoader;
import com.bitsson.gensokyou.ritual.RitualLootTable;
import com.bitsson.gensokyou.ritual.RitualPattern;
import com.bitsson.gensokyou.ritual.RitualPatternLoader;
import com.bitsson.gensokyou.ritual.RitualRecipe;
import com.bitsson.gensokyou.ritual.RitualRecipeLoader;
import com.bitsson.gensokyou.ritual.RitualSmeltRule;
import com.bitsson.gensokyou.ritual.RitualSmeltRuleLoader;
import com.bitsson.gensokyou.ritual.WatatsumiSpecialLoot;
import com.bitsson.gensokyou.ritual.WatatsumiSpecialLootLoader;
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
 * 重建 RitualPattern / RitualRecipe / RitualLootTable / WatatsumiSpecialLoot，
 * 并落盘缓存（config/gensokyou/ritual_data.json）。断线/重连时先读缓存，保证离线可读；
 * 数据未到达时查询为空，页面显示占位。
 *
 * <p>本类是客户端侧仪式的**唯一事实来源**：指导书与 JEI 全部页签均读此处，刻意不直读
 * {@code AddReloadListenerEvent} 注册的数据加载器——后者只在逻辑服务端触发，在专用服务器
 * 客户端上恒为空。
 */
public final class ClientRitualData {

    private static final Path CACHE =
            FMLPaths.CONFIGDIR.get().resolve("gensokyou").resolve("ritual_data.json");

    private static volatile Map<ResourceLocation, RitualPattern> patterns = Map.of();
    private static volatile List<RitualRecipe> recipes = List.of();
    private static volatile List<RitualSmeltRule> smeltRules = List.of();
    private static volatile List<RitualLootTable> lootTables = List.of();
    private static volatile WatatsumiSpecialLoot watatsumi = WatatsumiSpecialLoot.EMPTY;
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

    /** 煅炉规则（书内 smelt_page 与 JEI 煅炉页签共用，避免两边配比走样）。 */
    public static List<RitualSmeltRule> smeltsFor(ResourceLocation patternId) {
        ensureLoaded();
        return smeltRules.stream().filter(rule -> rule.patternId().equals(patternId)).toList();
    }

    public static List<RitualSmeltRule> smeltsAll() {
        ensureLoaded();
        return smeltRules;
    }

    /** 全体仪式配方（JEI 配方页签用）。 */
    public static List<RitualRecipe> recipesAll() {
        ensureLoaded();
        return recipes;
    }

    /** 献祭权重表全体（JEI 献祭页签用）。 */
    public static List<RitualLootTable> lootsAll() {
        ensureLoaded();
        return lootTables;
    }

    /** 绵津见特产池（JEI 绵津见页签用）。 */
    public static WatatsumiSpecialLoot watatsumiTable() {
        ensureLoaded();
        return watatsumi;
    }

    public static boolean hasData() {
        ensureLoaded();
        return !patterns.isEmpty();
    }

    /**
     * 服务端快照到达：解析、替换并落盘。
     *
     * @return 是否成功解析并应用。失败时**保持既有数据不变**并返回 false，调用方据此决定
     *         是否需要刷新下游（JEI 页签）——用陈旧数据刷新优于用空数据刷新。
     */
    public static boolean applyJson(String snapshotJson) {
        Map<ResourceLocation, RitualPattern> parsedPatterns = new LinkedHashMap<>();
        List<RitualRecipe> parsedRecipes = new ArrayList<>();
        List<RitualSmeltRule> parsedSmelts = new ArrayList<>();
        List<RitualLootTable> parsedLoots = new ArrayList<>();
        WatatsumiSpecialLoot parsedWatatsumi = WatatsumiSpecialLoot.EMPTY;
        try {
            JsonObject root = JsonParser.parseString(snapshotJson).getAsJsonObject();
            if (root.has("patterns")) {
                for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("patterns").entrySet()) {
                    ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
                    if (id == null) {
                        continue;
                    }
                    try {
                        parsedPatterns.put(id, RitualPatternLoader.parseForEdit(
                                id, entry.getValue().getAsJsonObject()));
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
            if (root.has("smelt_rules")) {
                for (Map.Entry<String, JsonElement> entry
                        : root.getAsJsonObject("smelt_rules").entrySet()) {
                    ResourceLocation fileId = ResourceLocation.tryParse(entry.getKey());
                    if (fileId == null) {
                        continue;
                    }
                    try {
                        parsedSmelts.addAll(RitualSmeltRuleLoader.parseFileForClient(fileId,
                                entry.getValue().getAsJsonObject()));
                    } catch (Exception ex) {
                        Gensokyou.LOGGER.warn("Client rejected ritual smelt rule file {}: {}",
                                fileId, ex.getMessage());
                    }
                }
            }
            if (root.has("ritual_loot")) {
                for (Map.Entry<String, JsonElement> entry
                        : root.getAsJsonObject("ritual_loot").entrySet()) {
                    try {
                        // 快照仅含服务端已接受的文件（同 pattern 重复的落败文件不随包下发），
                        // 故此处无须复现去重规则。
                        parsedLoots.add(RitualLootLoader.parseFileForClient(
                                entry.getValue().getAsJsonObject()));
                    } catch (Exception ex) {
                        Gensokyou.LOGGER.warn("Client rejected ritual loot file {}: {}",
                                entry.getKey(), ex.getMessage());
                    }
                }
            }
            if (root.has("ritual_special")) {
                try {
                    parsedWatatsumi = WatatsumiSpecialLootLoader.parseFileForClient(
                            root.getAsJsonObject("ritual_special"));
                } catch (Exception ex) {
                    Gensokyou.LOGGER.warn("Client rejected watatsumi special loot: {}",
                            ex.getMessage());
                }
            }
        } catch (Exception ex) {
            Gensokyou.LOGGER.warn("Client rejected ritual data sync: {}", ex.getMessage());
            return false;
        }
        patterns = Map.copyOf(parsedPatterns);
        recipes = List.copyOf(parsedRecipes);
        smeltRules = List.copyOf(parsedSmelts);
        lootTables = List.copyOf(parsedLoots);
        watatsumi = parsedWatatsumi;
        diskChecked = true;
        writeCache(snapshotJson);
        return true;
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
        }    }

    private static void writeCache(String json) {
        try {
            Files.createDirectories(CACHE.getParent());
            Files.writeString(CACHE, json, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            Gensokyou.LOGGER.warn("Failed to write ritual data cache: {}", ex.getMessage());
        }
    }
}
