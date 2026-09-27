package com.bitsson.gensokyou.ritual;

import com.bitsson.gensokyou.Gensokyou;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class RitualSmeltRuleLoader extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new Gson();
    private static volatile List<RitualSmeltRule> RULES = List.of();
    private static volatile Map<Item, RitualSmeltRule> BY_PRIMARY = Map.of();
    private static volatile Map<ResourceLocation, RitualSmeltRule> BY_ID = Map.of();
    /** 原始文件 JSON（文件 id → JSON 深拷贝），供服务端快照下发给客户端。 */
    private static final Map<ResourceLocation, JsonObject> RAWS = new LinkedHashMap<>();

    public RitualSmeltRuleLoader() {
        super(GSON, "ritual_smelt_recipes");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        List<RitualSmeltRule> accepted = new ArrayList<>();
        Map<Item, RitualSmeltRule> byPrimary = new LinkedHashMap<>();
        Map<ResourceLocation, RitualSmeltRule> byId = new LinkedHashMap<>();
        Map<ResourceLocation, JsonObject> raws = new LinkedHashMap<>();
        for (var entry : files.entrySet()) {
            try {
                JsonObject json = GsonHelper.convertToJsonObject(entry.getValue(), "ritual smelt rule");
                List<RitualSmeltRule> parsed = parseFile(entry.getKey(), json);
                raws.put(entry.getKey(), json);
                Map<Item, RitualSmeltRule> fileByPrimary = new LinkedHashMap<>();
                Map<ResourceLocation, RitualSmeltRule> fileById = new LinkedHashMap<>();
                for (RitualSmeltRule rule : parsed) {
                    RitualSmeltRule primaryOwner = byPrimary.get(rule.primary());
                    if (primaryOwner != null) {
                        throw new IllegalArgumentException("duplicate primary " + itemId(rule.primary())
                                + " already defined by " + primaryOwner.id());
                    }
                    RitualSmeltRule idOwner = byId.get(rule.id());
                    if (idOwner != null) {
                        throw new IllegalArgumentException("duplicate rule id " + rule.id()
                                + " already defined by " + idOwner.id());
                    }
                    fileByPrimary.put(rule.primary(), rule);
                    fileById.put(rule.id(), rule);
                }
                byPrimary.putAll(fileByPrimary);
                byId.putAll(fileById);
                accepted.addAll(parsed);
            } catch (Exception exception) {
                Gensokyou.LOGGER.warn("Rejected ritual smelt rule file {}: {}",
                        entry.getKey(), exception.getMessage());
            }
        }
        List<RitualSmeltRule> sorted = accepted.stream()
                .sorted(Comparator.comparing(rule -> rule.id().toString()))
                .toList();
        RULES = List.copyOf(sorted);
        BY_PRIMARY = Map.copyOf(byPrimary);
        BY_ID = Map.copyOf(byId);
        synchronized (RAWS) {
            RAWS.clear();
            RAWS.putAll(raws);
        }
        Gensokyou.LOGGER.info("Loaded {} ritual smelt rules", sorted.size());
    }

    static List<RitualSmeltRule> parseFile(ResourceLocation fileId, JsonObject json) {
        ResourceLocation patternId = location(requiredString(json, "pattern"), "pattern");
        JsonElement rulesElement = json.get("rules");
        if (rulesElement == null || !rulesElement.isJsonArray()) {
            throw new IllegalArgumentException("rules must be an array");
        }
        List<RitualSmeltRule> rules = new ArrayList<>();
        Map<Item, RitualSmeltRule> byPrimary = new LinkedHashMap<>();
        Map<ResourceLocation, RitualSmeltRule> byId = new LinkedHashMap<>();
        for (JsonElement element : rulesElement.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("rule must be an object");
            }
            JsonObject ruleJson = element.getAsJsonObject();
            ResourceLocation id = location(requiredString(ruleJson, "id"), "rule id");
            Item primary = item(requiredString(ruleJson, "primary"), "primary");
            Item auxiliary = item(requiredString(ruleJson, "auxiliary"), "auxiliary");
            int auxiliaryCount = positiveInt(ruleJson, "auxiliary_count");
            JsonElement resultElement = ruleJson.get("result");
            if (resultElement == null || !resultElement.isJsonObject()) {
                throw new IllegalArgumentException("result must be present for rule " + id);
            }
            JsonObject resultJson = resultElement.getAsJsonObject();
            Item result = item(requiredString(resultJson, "item"), "result.item");
            int resultCount = positiveInt(resultJson, "count");
            RitualSmeltRule rule = new RitualSmeltRule(id, patternId, primary, auxiliary,
                    auxiliaryCount, result, resultCount);
            RitualSmeltRule primaryOwner = byPrimary.putIfAbsent(primary, rule);
            if (primaryOwner != null) {
                throw new IllegalArgumentException("duplicate primary " + itemId(primary)
                        + " already defined by " + primaryOwner.id());
            }
            RitualSmeltRule idOwner = byId.putIfAbsent(id, rule);
            if (idOwner != null) {
                throw new IllegalArgumentException("duplicate rule id " + id
                        + " already defined by " + idOwner.id());
            }
            rules.add(rule);
        }
        if (rules.isEmpty()) {
            throw new IllegalArgumentException("rules must not be empty");
        }
        return List.copyOf(rules);
    }

    public static Optional<RitualSmeltRule> forPrimary(Item primary) {
        if (primary == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_PRIMARY.get(primary));
    }

    public static Optional<RitualSmeltRule> byPrimary(Item primary) {
        return forPrimary(primary);
    }

    public static Optional<RitualSmeltRule> byId(ResourceLocation id) {
        return Optional.ofNullable(BY_ID.get(id));
    }

    public static List<RitualSmeltRule> forPattern(ResourceLocation patternId) {
        return RULES.stream().filter(rule -> rule.patternId().equals(patternId)).toList();
    }

    public static List<RitualSmeltRule> all() {
        return RULES;
    }

    /**
     * 全部原始文件 JSON，供服务端组装 S2C 快照。
     *
     * <p>{@code AddReloadListenerEvent} 只在逻辑服务端触发，专用客户端拿不到这些数据，
     * 故必须随仪式快照一起下发，否则 JEI 煅炉页签与书内煅炉配方页在联机上会是空的。
     */
    public static Map<ResourceLocation, JsonObject> rawAll() {
        synchronized (RAWS) {
            Map<ResourceLocation, JsonObject> copy = new LinkedHashMap<>();
            RAWS.forEach((id, json) -> copy.put(id, json.deepCopy().getAsJsonObject()));
            return copy;
        }
    }

    /** 客户端重建用：与 loader 共用同一份解析逻辑，语义差异即抛 IllegalArgumentException。 */
    public static List<RitualSmeltRule> parseFileForClient(ResourceLocation fileId, JsonObject json) {
        return parseFile(fileId, json);
    }

    private static String requiredString(JsonObject json, String key) {
        JsonElement element = json.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(key + " must be a string");
        }
        return element.getAsString();
    }

    private static ResourceLocation location(String raw, String where) {
        try {
            return ResourceLocation.parse(raw);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(where + " has invalid id " + raw, exception);
        }
    }

    private static Item item(String raw, String where) {
        ResourceLocation id = location(raw, where);
        return BuiltInRegistries.ITEM.getOptional(id)
                .orElseThrow(() -> new IllegalArgumentException(where + " has unknown item " + raw));
    }

    private static int positiveInt(JsonObject json, String key) {
        JsonElement element = json.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            throw new IllegalArgumentException(key + " must be a positive integer");
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (!primitive.isNumber()) {
            throw new IllegalArgumentException(key + " must be a positive integer");
        }
        final BigDecimal value;
        try {
            value = new BigDecimal(primitive.getAsString());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " must be a positive integer", exception);
        }
        if (value.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException(key + " must be a positive integer");
        }
        final int result;
        try {
            result = value.intValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(key + " must be a positive integer", exception);
        }
        if (result <= 0) {
            throw new IllegalArgumentException(key + " must be a positive integer");
        }
        return result;
    }

    private static String itemId(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }
}
