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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 付丧之冢燃料表加载器（{@code data/gensokyou/ritual_special/tsukumogami_fuel.json}）。
 *
 * <p>结构：顶层仅 {@code entries:[[itemId, points], ...]}，points 为 L0 单件总产物 SP。
 * 非法（未知物品 id / points 非正整数 / 空表）即拒载该文件并报因。
 */
public class TsukumogamiFuelLoader extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new Gson();
    private static volatile List<Entry> TABLE = List.of();
    private static volatile JsonObject raw = null;

    public TsukumogamiFuelLoader() {
        super(GSON, "ritual_special");
    }

    /** 单条燃料条目（物品 → L0 单件总量点位）。 */
    public record Entry(Item item, long points) {
        public ResourceLocation id() {
            return BuiltInRegistries.ITEM.getKey(item);
        }
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        List<Entry> parsed = List.of();
        JsonObject parsedRaw = null;
        for (var file : files.entrySet()) {
            if (!file.getKey().getPath().equals("tsukumogami_fuel")) {
                continue;
            }
            try {
                JsonObject json = GsonHelper.convertToJsonObject(file.getValue(), "tsukumogami fuel");
                parsed = parse(json);
                parsedRaw = json;
            } catch (Exception exception) {
                Gensokyou.LOGGER.warn("Rejected tsukumogami fuel file {}: {}", file.getKey(),
                        exception.getMessage());
            }
        }
        TABLE = parsed;
        raw = parsedRaw;
        Gensokyou.LOGGER.info("Loaded {} tsukumogami fuel entries", parsed.size());
    }

    private static List<Entry> parse(JsonObject json) {
        List<Entry> list = new ArrayList<>();
        JsonArray array = GsonHelper.getAsJsonArray(json, "entries");
        for (JsonElement element : array) {
            if (!element.isJsonArray() || element.getAsJsonArray().size() != 2) {
                Gensokyou.LOGGER.warn("Tsukumogami fuel: entry must be [itemId, points], got {}",
                        element);
                continue;
            }
            JsonArray pair = element.getAsJsonArray();
            String id = pair.get(0).getAsString();
            ResourceLocation key = ResourceLocation.tryParse(id);
            Item item = key == null ? null : BuiltInRegistries.ITEM.getOptional(key).orElse(null);
            if (item == null) {
                Gensokyou.LOGGER.warn("Tsukumogami fuel: unknown item {}, entry skipped", id);
                continue;
            }
            long points = pair.get(1).getAsLong();
            if (points <= 0) {
                Gensokyou.LOGGER.warn("Tsukumogami fuel: points must be > 0 for {}, entry skipped", id);
                continue;
            }
            list.add(new Entry(item, points));
        }
        if (list.isEmpty()) {
            throw new IllegalArgumentException("entries must not be empty (all skipped)");
        }
        return List.copyOf(list);
    }

    /** 当前的全量燃料条目（加载顺序即标准 JSON 顺序）。 */
    public static List<Entry> table() {
        return TABLE;
    }

    /** itemId → L0 点位映射（行为侧判燃料 + 取值共用）。 */
    public static Map<ResourceLocation, Long> pointsByItem() {
        LinkedHashMap<ResourceLocation, Long> out = new LinkedHashMap<>();
        for (Entry entry : TABLE) {
            out.put(entry.id(), entry.points());
        }
        return out;
    }

    /** 胜出文件的原始 JSON 深拷贝（客户端同步用）；无有效文件时为 null。 */
    public static JsonObject rawAll() {
        JsonObject snapshot = raw;
        return snapshot == null ? null : snapshot.deepCopy().getAsJsonObject();
    }

    /** 客户端重建用：以 loader 同规则解析燃料表文件（非法即抛 IllegalArgumentException）。 */
    public static List<Entry> parseFileForClient(JsonObject json) {
        return parse(json);
    }
}
