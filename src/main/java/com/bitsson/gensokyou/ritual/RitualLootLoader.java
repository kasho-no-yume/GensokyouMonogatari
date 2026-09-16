package com.bitsson.gensokyou.ritual;

import com.bitsson.gensokyou.Gensokyou;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 献祭仪式加权产出表加载器（{@code data/gensokyou/ritual_loot/*.json}，一仪式一文件）。
 *
 * <p>文件顶层：{@code pattern}（归属仪式）、{@code toolTag}（工具类别标签）、
 * {@code commonsTotal}（桶总权重，缺省 100）、{@code skullsRequired}/{@code dragonHeadsRequired}、
 * {@code commons}（桶内相对权重）、可选顶层 {@code nether}/{@code end}（各材质缺省条件池）、
 * {@code tables[]}（每工具材质一条：{@code tier} + {@code special} + 可选 {@code nether}/{@code end} 覆盖）。
 *
 * <p>非法（未知物品 id / 负权重 / 空表 / 未知材质名）即拒载该文件并报因，不影响其他文件。
 */
public class RitualLootLoader extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new Gson();
    private static final Map<ResourceLocation, RitualLootTable> TABLES = new LinkedHashMap<>();

    public RitualLootLoader() {
        super(GSON, "ritual_loot");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager,
                         net.minecraft.util.profiling.ProfilerFiller profiler) {
        Map<ResourceLocation, RitualLootTable> parsed = new LinkedHashMap<>();
        for (var file : files.entrySet()) {
            try {
                RitualLootTable table = parse(GsonHelper.convertToJsonObject(file.getValue(), "loot table"));
                RitualLootTable previous = parsed.putIfAbsent(table.patternId(), table);
                if (previous != null) {
                    throw new IllegalArgumentException("duplicate loot table for pattern "
                            + table.patternId() + " (already defined by another file)");
                }
            } catch (Exception exception) {
                Gensokyou.LOGGER.warn("Rejected ritual loot file {}: {}", file.getKey(),
                        exception.getMessage());
            }
        }
        synchronized (TABLES) {
            TABLES.clear();
            TABLES.putAll(parsed);
        }
        Gensokyou.LOGGER.info("Loaded {} ritual loot tables", parsed.size());
    }

    private static RitualLootTable parse(JsonObject json) {
        ResourceLocation pattern = ResourceLocation.parse(GsonHelper.getAsString(json, "pattern"));
        String toolTagRaw = GsonHelper.getAsString(json, "toolTag");
        int commonsTotal = GsonHelper.getAsInt(json, "commonsTotal", 100);
        int skulls = Math.max(0, GsonHelper.getAsInt(json, "skullsRequired", 3));
        int heads = Math.max(0, GsonHelper.getAsInt(json, "dragonHeadsRequired", 1));
        List<RitualLootTable.Weighted> commons = parseWeighted(
                GsonHelper.getAsJsonArray(json, "commons"), "commons");
        if (commons.isEmpty()) {
            throw new IllegalArgumentException("commons must not be empty");
        }
        // 顶层条件池缺省：各材质未显式给 nether/end 时沿用
        List<RitualLootTable.Weighted> topNether = json.has("nether")
                ? parseWeighted(GsonHelper.getAsJsonArray(json, "nether"), "nether") : List.of();
        List<RitualLootTable.Weighted> topEnd = json.has("end")
                ? parseWeighted(GsonHelper.getAsJsonArray(json, "end"), "end") : List.of();

        List<RitualLootTable.TierTable> tables = new ArrayList<>();
        Set<String> seenTiers = new HashSet<>();
        JsonArray tablesJson = GsonHelper.getAsJsonArray(json, "tables");
        for (JsonElement element : tablesJson) {
            JsonObject entry = GsonHelper.convertToJsonObject(element, "tier table");
            String tier = GsonHelper.getAsString(entry, "tier");
            if (!RitualLootTable.TIER_KEYS.contains(tier)) {
                throw new IllegalArgumentException("unknown tool tier: " + tier);
            }
            if (!seenTiers.add(tier)) {
                throw new IllegalArgumentException("duplicate tool tier: " + tier);
            }
            List<RitualLootTable.Weighted> special = entry.has("special")
                    ? parseWeighted(GsonHelper.getAsJsonArray(entry, "special"), tier + ".special")
                    : List.of();
            List<RitualLootTable.Weighted> nether = entry.has("nether")
                    ? parseWeighted(GsonHelper.getAsJsonArray(entry, "nether"), tier + ".nether")
                    : topNether;
            List<RitualLootTable.Weighted> end = entry.has("end")
                    ? parseWeighted(GsonHelper.getAsJsonArray(entry, "end"), tier + ".end")
                    : topEnd;
            tables.add(new RitualLootTable.TierTable(tier, List.copyOf(special),
                    List.copyOf(nether), List.copyOf(end)));
        }
        if (tables.isEmpty()) {
            throw new IllegalArgumentException("tables must not be empty");
        }
        return new RitualLootTable(pattern, RitualLootTable.parseToolTag(toolTagRaw), commonsTotal,
                skulls, heads, List.copyOf(commons), List.copyOf(tables));
    }

    /** 解析 {@code [[itemId, weight], ...]}；未知物品 id / 负权重即抛。 */
    private static List<RitualLootTable.Weighted> parseWeighted(JsonArray array, String where) {
        List<RitualLootTable.Weighted> list = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonArray()) {
                throw new IllegalArgumentException(where + ": entry must be [itemId, weight]");
            }
            JsonArray pair = element.getAsJsonArray();
            if (pair.size() != 2) {
                throw new IllegalArgumentException(where + ": entry must be [itemId, weight]");
            }
            String id = pair.get(0).getAsString();
            Item item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id))
                    .orElseThrow(() -> new IllegalArgumentException(where + ": unknown item " + id));
            double weight = pair.get(1).getAsDouble();
            if (!(weight >= 0.0D) || Double.isNaN(weight) || Double.isInfinite(weight)) {
                throw new IllegalArgumentException(where + ": weight must be finite and >= 0 for " + id);
            }
            list.add(new RitualLootTable.Weighted(item, weight));
        }
        return list;
    }

    public static Optional<RitualLootTable> byPattern(ResourceLocation patternId) {
        synchronized (TABLES) {
            return Optional.ofNullable(TABLES.get(patternId));
        }
    }

    public static List<RitualLootTable> all() {
        synchronized (TABLES) {
            return List.copyOf(TABLES.values());
        }
    }
}
