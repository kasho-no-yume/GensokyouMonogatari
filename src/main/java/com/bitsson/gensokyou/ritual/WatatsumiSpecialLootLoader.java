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
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 海洋特产池加载器（{@code data/gensokyou/ritual_special/watatsumi_special.json}）。
 *
 * <p>结构与材质驱动的 {@link RitualLootLoader} 不同：顶层仅 {@code entries:[[itemId, weight], ...]}，
 * 权重为相对值。非法（未知物品 id / 权重非有限或 ≤0 / 空表）即拒载该文件并报因。
 */
public class WatatsumiSpecialLootLoader extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new Gson();
    private static volatile WatatsumiSpecialLoot TABLE = WatatsumiSpecialLoot.EMPTY;
    /** 胜出文件的原始 JSON 副本（客户端同步用）；形态为单个对象而非 map——本 loader 只持一份表。 */
    private static volatile JsonObject raw = null;

    public WatatsumiSpecialLootLoader() {
        super(GSON, "ritual_special");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        WatatsumiSpecialLoot parsed = WatatsumiSpecialLoot.EMPTY;
        JsonObject parsedRaw = null;
        for (var file : files.entrySet()) {
            try {
                JsonObject json = GsonHelper.convertToJsonObject(file.getValue(), "watatsumi special loot");
                parsed = parse(json);
                parsedRaw = json;
            } catch (Exception exception) {
                Gensokyou.LOGGER.warn("Rejected watatsumi special loot file {}: {}", file.getKey(),
                        exception.getMessage());
            }
        }
        TABLE = parsed;
        raw = parsedRaw;
        Gensokyou.LOGGER.info("Loaded {} watatsumi ocean-special entries", parsed.entries().size());
    }

    private static WatatsumiSpecialLoot parse(JsonObject json) {
        List<RitualLootTable.Weighted> entries = parseList(
                GsonHelper.getAsJsonArray(json, "entries"), "entries");
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("entries must not be empty");
        }
        List<RitualLootTable.Weighted> low = json.has("gensokyou_low")
                ? parseList(GsonHelper.getAsJsonArray(json, "gensokyou_low"), "gensokyou_low") : List.of();
        List<RitualLootTable.Weighted> high = json.has("gensokyou_high")
                ? parseList(GsonHelper.getAsJsonArray(json, "gensokyou_high"), "gensokyou_high") : List.of();
        return new WatatsumiSpecialLoot(List.copyOf(entries), List.copyOf(low), List.copyOf(high));
    }

    /** 解析 {@code [[itemId, weight], ...]}；未知物品 id / 非法权重即抛。 */
    private static List<RitualLootTable.Weighted> parseList(JsonArray array, String where) {
        List<RitualLootTable.Weighted> list = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonArray() || element.getAsJsonArray().size() != 2) {
                throw new IllegalArgumentException(where + ": entry must be [itemId, weight]");
            }
            JsonArray pair = element.getAsJsonArray();
            String id = pair.get(0).getAsString();
            Item item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id))
                    .orElseThrow(() -> new IllegalArgumentException(where + ": unknown item " + id));
            double weight = pair.get(1).getAsDouble();
            if (!(weight > 0.0D) || Double.isNaN(weight) || Double.isInfinite(weight)) {
                throw new IllegalArgumentException(where + ": weight must be finite and > 0 for " + id);
            }
            list.add(new RitualLootTable.Weighted(item, weight));
        }
        return list;
    }

    public static WatatsumiSpecialLoot table() {
        return TABLE;
    }

    /** 胜出文件的原始 JSON 深拷贝（客户端同步用）；无有效文件时为 null。 */
    public static JsonObject rawAll() {
        JsonObject snapshot = raw;
        return snapshot == null ? null : snapshot.deepCopy().getAsJsonObject();
    }

    /** 客户端重建用：以 loader 同规则解析特产池文件（非法即抛 IllegalArgumentException）。 */
    public static WatatsumiSpecialLoot parseFileForClient(JsonObject json) {
        return parse(json);
    }
}
