package com.bitsson.gensokyou.ritual.brew;

import com.bitsson.gensokyou.Gensokyou;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.alchemy.Potion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * {@code data/<ns>/brew_recipes/*.json} 的加载器 —— 「炼药试剂 → 基础药水」的显式声明来源。
 *
 * <p>结构照搬 {@link com.bitsson.gensokyou.ritual.RitualSmeltRuleLoader}（金屋彦专属规则表）：
 * 单文件声明归属 {@code pattern}，{@code entries} 逐条给出映射。
 *
 * <p><b>覆盖规则</b>：同一 {@code reagent} 被多条 entries / 多个文件命中时，
 * <b>后者覆盖前者</b>并记录日志——整合包据此追加或改写映射，无需改原文件。
 * 这与 {@code ritual_smelt_recipes} 的"重复即拒收"相反：本类型是**扩展点**而非封闭表。
 *
 * <p>未被显式声明的试剂**回落到向酿造台反查**（见 {@link BrewReagentIndex}）。
 */
public final class RitualBrewRuleLoader extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new Gson();
    private static volatile List<RitualBrewRule> RULES = List.of();
    /** reagent → 规则（后者覆盖前者）。 */
    private static volatile Map<Item, RitualBrewRule> BY_REAGENT = Map.of();
    /** 原始文件 JSON（文件 id → JSON），供服务端快照下发给客户端。 */
    private static final Map<ResourceLocation, JsonObject> RAWS = new LinkedHashMap<>();

    public RitualBrewRuleLoader() {
        super(GSON, "brew_recipes");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        List<RitualBrewRule> accepted = new ArrayList<>();
        Map<Item, RitualBrewRule> byReagent = new LinkedHashMap<>();
        Map<ResourceLocation, JsonObject> raws = new LinkedHashMap<>();
        for (var entry : files.entrySet()) {
            try {
                JsonObject json = GsonHelper.convertToJsonObject(entry.getValue(), "ritual brew rule");
                raws.put(entry.getKey(), json);
                for (RitualBrewRule rule : parseFile(entry.getKey(), json)) {
                    RitualBrewRule previous = byReagent.put(rule.reagent(), rule);
                    if (previous != null) {
                        Gensokyou.LOGGER.info("Brew rule {} overrides earlier mapping {} -> {}",
                                entry.getKey(),
                                itemId(previous.reagent()) + "=" + potionId(previous.potion()),
                                itemId(rule.reagent()) + "=" + potionId(rule.potion()));
                    }
                    accepted.add(rule);
                }
            } catch (Exception exception) {
                Gensokyou.LOGGER.warn("Rejected ritual brew rule file {}: {}",
                        entry.getKey(), exception.getMessage());
            }
        }
        RULES = List.copyOf(accepted);
        BY_REAGENT = Map.copyOf(byReagent);
        synchronized (RAWS) {
            RAWS.clear();
            RAWS.putAll(raws);
        }
        Gensokyou.LOGGER.info("Loaded {} ritual brew rules ({} distinct reagents)",
                accepted.size(), byReagent.size());
        BrewReagentIndex.invalidate();
    }

    // ------------------------------------------------------------------ 解析

    /** 单文件的纯解析（无注册表写副作用），供单测直接调用。 */
    public static List<RitualBrewRule> parseFile(ResourceLocation fileId, JsonObject json) {
        ResourceLocation patternId = location(requiredString(json, "pattern"), "pattern");
        JsonElement entriesElement = json.get("entries");
        if (entriesElement == null || !entriesElement.isJsonArray()) {
            throw new IllegalArgumentException("entries must be an array");
        }
        JsonArray entries = entriesElement.getAsJsonArray();
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("entries must not be empty");
        }
        List<RitualBrewRule> rules = new ArrayList<>(entries.size());
        for (JsonElement element : entries) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("entry must be an object");
            }
            JsonObject entry = element.getAsJsonObject();
            rules.add(parseEntry(patternId, entry));
        }
        return List.copyOf(rules);
    }

    private static RitualBrewRule parseEntry(ResourceLocation patternId, JsonObject entry) {
        Item reagent = item(requiredString(entry, "reagent"), "reagent");
        Holder<Potion> potion = potion(requiredString(entry, "potion"), "potion");
        Holder<Potion> longPotion = optionalPotion(entry, "long_potion");
        Holder<Potion> strongPotion = optionalPotion(entry, "strong_potion");
        Set<ResourceLocation> excluded = new LinkedHashSet<>();
        JsonElement excludedElement = entry.get("excluded_effects");
        if (excludedElement != null) {
            if (!excludedElement.isJsonArray()) {
                throw new IllegalArgumentException("excluded_effects must be an array");
            }
            for (JsonElement id : excludedElement.getAsJsonArray()) {
                excluded.add(location(id.getAsString(), "excluded_effects entry"));
            }
        }
        return new RitualBrewRule(patternId, reagent, potion, longPotion, strongPotion,
                excluded,
                bool(entry, "extend_without_long"),
                bool(entry, "amplify_without_strong"));
    }

    // ------------------------------------------------------------------ 查询

    public static Optional<RitualBrewRule> byReagent(Item reagent) {
        if (reagent == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_REAGENT.get(reagent));
    }

    public static List<RitualBrewRule> all() {
        return RULES;
    }

    public static List<RitualBrewRule> forPattern(ResourceLocation patternId) {
        return RULES.stream().filter(rule -> rule.patternId().equals(patternId)).toList();
    }

    public static Set<Item> declaredReagents() {
        return BY_REAGENT.keySet();
    }

    /**
     * 全部原始文件 JSON，供服务端组装 S2C 快照。
     *
     * <p>客户端 MUST NOT 直读本 loader（{@code AddReloadListenerEvent} 只在逻辑服务端触发，
     * 专用服务器客户端上恒为空），故必须随快照下发，否则 JEI 炼药页签与书内配方页会空白。
     */
    public static Map<ResourceLocation, JsonObject> rawAll() {
        synchronized (RAWS) {
            Map<ResourceLocation, JsonObject> copy = new LinkedHashMap<>();
            RAWS.forEach((id, json) -> copy.put(id, json.deepCopy().getAsJsonObject()));
            return copy;
        }
    }

    // ------------------------------------------------------------------ 工具

    static String itemId(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    static String potionId(Holder<Potion> potion) {
        return potion == null ? "?" : potion.unwrapKey()
                .map(key -> key.location().toString()).orElse("?");
    }

    private static String requiredString(JsonObject json, String key) {
        JsonElement element = json.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(key + " must be a string");
        }
        return element.getAsString();
    }

    private static boolean bool(JsonObject json, String key) {
        JsonElement element = json.get(key);
        if (element == null) {
            return false;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (!primitive.isBoolean()) {
            throw new IllegalArgumentException(key + " must be a boolean");
        }
        return primitive.getAsBoolean();
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

    private static Holder<Potion> potion(String raw, String where) {
        ResourceLocation id = location(raw, where);
        return BuiltInRegistries.POTION.getHolder(ResourceKey.create(Registries.POTION, id))
                .orElseThrow(() -> new IllegalArgumentException(where + " has unknown potion " + raw));
    }

    private static Holder<Potion> optionalPotion(JsonObject json, String key) {
        JsonElement element = json.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        return potion(element.getAsString(), key);
    }
}