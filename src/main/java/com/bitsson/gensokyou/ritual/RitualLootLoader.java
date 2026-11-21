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
 * 可选 {@code output_channels[]} 为互相独立的产量池，每个池有自己的 commons、条件池与材质表。
 *
 * <p>非法（未知物品 id / 负权重 / 空表 / 未知材质名）即拒载该文件并报因，不影响其他文件。
 */
public class RitualLootLoader extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new Gson();
    private static final Map<ResourceLocation, RitualLootTable> TABLES = new LinkedHashMap<>();
    /** 仅收录**通过校验**的文件的原始 JSON（客户端同步用），使客户端无须复现去重语义。 */
    private static final Map<ResourceLocation, JsonObject> RAWS = new LinkedHashMap<>();

    public RitualLootLoader() {
        super(GSON, "ritual_loot");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager,
                         net.minecraft.util.profiling.ProfilerFiller profiler) {
        Map<ResourceLocation, RitualLootTable> parsed = new LinkedHashMap<>();
        Map<ResourceLocation, JsonObject> raws = new LinkedHashMap<>();
        for (var file : files.entrySet()) {
            try {
                JsonObject json = GsonHelper.convertToJsonObject(file.getValue(), "loot table");
                RitualLootTable table = parse(json);
                RitualLootTable previous = parsed.putIfAbsent(table.patternId(), table);
                if (previous != null) {
                    throw new IllegalArgumentException("duplicate loot table for pattern "
                            + table.patternId() + " (already defined by another file)");
                }
                raws.put(file.getKey(), json);
            } catch (Exception exception) {
                Gensokyou.LOGGER.warn("Rejected ritual loot file {}: {}", file.getKey(),
                        exception.getMessage());
            }
        }
        synchronized (TABLES) {
            TABLES.clear();
            TABLES.putAll(parsed);
        }
        synchronized (RAWS) {
            RAWS.clear();
            RAWS.putAll(raws);
        }
        Gensokyou.LOGGER.info("Loaded {} ritual loot tables", parsed.size());
    }

    private static RitualLootTable parse(JsonObject json) {
        ResourceLocation pattern = ResourceLocation.parse(GsonHelper.getAsString(json, "pattern"));
        String toolTagRaw = GsonHelper.getAsString(json, "toolTag");
        int commonsTotal = GsonHelper.getAsInt(json, "commonsTotal", 100);
        int skulls = Math.max(0, GsonHelper.getAsInt(json, "skullsRequired", 3));
        int heads = Math.max(0, GsonHelper.getAsInt(json, "dragonHeadsRequired", 1));
        List<RitualLootTable.Weighted> commons = json.has("commons")
                ? parseWeighted(GsonHelper.getAsJsonArray(json, "commons"), "commons") : List.of();
        // 顶层条件池缺省：各材质未显式给 nether/end 时沿用
        List<RitualLootTable.Weighted> topNether = json.has("nether")
                ? parseWeighted(GsonHelper.getAsJsonArray(json, "nether"), "nether") : List.of();
        List<RitualLootTable.Weighted> topEnd = json.has("end")
                ? parseWeighted(GsonHelper.getAsJsonArray(json, "end"), "end") : List.of();
        // 幻想乡素材带：由信物解锁的额外池（顶层声明，不按工具材质）
        List<RitualLootTable.Weighted> gensokyouLow = json.has("gensokyou_low")
                ? parseWeighted(GsonHelper.getAsJsonArray(json, "gensokyou_low"), "gensokyou_low") : List.of();
        List<RitualLootTable.Weighted> gensokyouHigh = json.has("gensokyou_high")
                ? parseWeighted(GsonHelper.getAsJsonArray(json, "gensokyou_high"), "gensokyou_high") : List.of();

        List<RitualLootTable.TierTable> tables = json.has("tables")
                ? parseTierTables(GsonHelper.getAsJsonArray(json, "tables"), topNether, topEnd, "tables")
                : List.of();
        List<RitualLootTable.OutputChannel> channels = json.has("output_channels")
                ? parseOutputChannels(GsonHelper.getAsJsonArray(json, "output_channels")) : List.of();
        if (commons.isEmpty() && channels.isEmpty()) {
            throw new IllegalArgumentException("commons must not be empty unless output_channels are declared");
        }
        if (tables.isEmpty() && channels.isEmpty()) {
            throw new IllegalArgumentException("tables must not be empty unless output_channels are declared");
        }
        boolean hasStoneChannel = channels.stream().anyMatch(channel -> channel.id().equals("stone"));
        boolean hasMineralChannel = channels.stream().anyMatch(channel -> channel.id().equals("mineral"));
        if (pattern.getPath().equals("oyamatsumi_circle") && !channels.isEmpty()
                && (!hasStoneChannel || !hasMineralChannel)) {
            throw new IllegalArgumentException("oyamatsumi_circle requires stone and mineral output_channels");
        }
        return new RitualLootTable(pattern, RitualLootTable.parseToolTag(toolTagRaw), commonsTotal,
                skulls, heads, List.copyOf(commons), List.copyOf(gensokyouLow),
                List.copyOf(gensokyouHigh), List.copyOf(tables), List.copyOf(channels));
    }

    private static List<RitualLootTable.OutputChannel> parseOutputChannels(JsonArray channelsJson) {
        List<RitualLootTable.OutputChannel> channels = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        for (JsonElement element : channelsJson) {
            JsonObject channel = GsonHelper.convertToJsonObject(element, "output channel");
            String id = GsonHelper.getAsString(channel, "id");
            if (id.isBlank() || !seenIds.add(id)) {
                throw new IllegalArgumentException("output channel id must be non-empty and unique: " + id);
            }
            int channelCommonsTotal = GsonHelper.getAsInt(channel, "commonsTotal", 100);
            List<RitualLootTable.Weighted> channelCommons = channel.has("commons")
                    ? parseWeighted(GsonHelper.getAsJsonArray(channel, "commons"), id + ".commons")
                    : List.of();
            List<RitualLootTable.Weighted> topNether = channel.has("nether")
                    ? parseWeighted(GsonHelper.getAsJsonArray(channel, "nether"), id + ".nether")
                    : List.of();
            List<RitualLootTable.Weighted> topEnd = channel.has("end")
                    ? parseWeighted(GsonHelper.getAsJsonArray(channel, "end"), id + ".end")
                    : List.of();
            List<RitualLootTable.Weighted> low = channel.has("gensokyou_low")
                    ? parseWeighted(GsonHelper.getAsJsonArray(channel, "gensokyou_low"), id + ".gensokyou_low")
                    : List.of();
            List<RitualLootTable.Weighted> high = channel.has("gensokyou_high")
                    ? parseWeighted(GsonHelper.getAsJsonArray(channel, "gensokyou_high"), id + ".gensokyou_high")
                    : List.of();
            List<RitualLootTable.TierTable> channelTables = parseTierTables(
                    GsonHelper.getAsJsonArray(channel, "tables"), topNether, topEnd, id + ".tables");
            RitualLootTable.OutputChannel parsed = new RitualLootTable.OutputChannel(id, channelCommonsTotal,
                    List.copyOf(channelCommons), List.copyOf(low), List.copyOf(high), channelTables);
            for (RitualLootTable.TierTable tier : channelTables) {
                if (RitualLootTable.sumWeight(RitualLootTable.buildPool(parsed, tier.tier(),
                        false, false, false, false)) <= 0.0D) {
                    throw new IllegalArgumentException(id + "." + tier.tier()
                            + " must have a positive base pool to guarantee its quota");
                }
            }
            channels.add(parsed);
        }
        if (channels.isEmpty()) {
            throw new IllegalArgumentException("output_channels must not be empty");
        }
        return channels;
    }

    private static List<RitualLootTable.TierTable> parseTierTables(JsonArray tablesJson,
                                                                   List<RitualLootTable.Weighted> topNether,
                                                                   List<RitualLootTable.Weighted> topEnd,
                                                                   String where) {
        List<RitualLootTable.TierTable> tables = new ArrayList<>();
        Set<String> seenTiers = new HashSet<>();
        for (JsonElement element : tablesJson) {
            JsonObject entry = GsonHelper.convertToJsonObject(element, "tier table");
            String tier = GsonHelper.getAsString(entry, "tier");
            if (!RitualLootTable.TIER_KEYS.contains(tier)) {
                throw new IllegalArgumentException(where + ": unknown tool tier: " + tier);
            }
            if (!seenTiers.add(tier)) {
                throw new IllegalArgumentException(where + ": duplicate tool tier: " + tier);
            }
            List<RitualLootTable.Weighted> special = entry.has("special")
                    ? parseWeighted(GsonHelper.getAsJsonArray(entry, "special"), where + "." + tier + ".special")
                    : List.of();
            List<RitualLootTable.Weighted> nether = entry.has("nether")
                    ? parseWeighted(GsonHelper.getAsJsonArray(entry, "nether"), where + "." + tier + ".nether")
                    : topNether;
            List<RitualLootTable.Weighted> end = entry.has("end")
                    ? parseWeighted(GsonHelper.getAsJsonArray(entry, "end"), where + "." + tier + ".end")
                    : topEnd;
            tables.add(new RitualLootTable.TierTable(tier, List.copyOf(special),
                    List.copyOf(nether), List.copyOf(end)));
        }
        if (tables.isEmpty()) {
            throw new IllegalArgumentException(where + " must not be empty");
        }
        return List.copyOf(tables);
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

    /** 已被接受的文件之原始 JSON 副本（客户端同步用）；重复 pattern 的落败文件不在其中。 */
    public static Map<ResourceLocation, JsonObject> rawAll() {
        synchronized (RAWS) {
            Map<ResourceLocation, JsonObject> copy = new LinkedHashMap<>();
            RAWS.forEach((id, json) -> copy.put(id, json.deepCopy().getAsJsonObject()));
            return copy;
        }
    }

    /**
     * 客户端重建用：以 loader 同规则解析一个献祭权重文件（非法即抛 IllegalArgumentException）。
     * 不接收 fileId——本 loader 的归属由 JSON 内 {@code pattern} 字段决定，与文件名无关。
     */
    public static RitualLootTable parseFileForClient(JsonObject json) {
        return parse(json);
    }
}
