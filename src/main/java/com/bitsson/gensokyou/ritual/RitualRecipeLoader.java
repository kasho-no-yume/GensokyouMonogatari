package com.bitsson.gensokyou.ritual;

import com.bitsson.gensokyou.Gensokyou;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.Optional;

public class RitualRecipeLoader extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new Gson();
    private static final List<RitualRecipe> RECIPES = new ArrayList<>();
    /** 已警告过引用缺失 pattern 的配方 id，避免使用期刷屏。 */
    private static final List<ResourceLocation> WARNED_MISSING = new ArrayList<>();
    /** 原始文件 JSON（文件 id → JSON 副本），供客户端同步。 */
    private static final Map<ResourceLocation, JsonObject> RAWS = new LinkedHashMap<>();

    public RitualRecipeLoader() {
        super(GSON, "ritual_recipes");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager,
                         net.minecraft.util.profiling.ProfilerFiller profiler) {
        List<RitualRecipe> parsed = new ArrayList<>();
        Map<ResourceLocation, JsonObject> raws = new LinkedHashMap<>();
        for (var file : files.entrySet()) {
            try {
                JsonObject json = GsonHelper.convertToJsonObject(file.getValue(), "recipe file");
                parsed.addAll(parseFile(file.getKey(), json));
                raws.put(file.getKey(), json);
            } catch (Exception exception) {
                Gensokyou.LOGGER.warn("Rejected ritual recipe file {}: {}", file.getKey(),
                        exception.getMessage());
            }
        }
        // 歧义校验：同 pattern+mode 归一化原料表一致 → 后者拒载
        Map<String, RitualRecipe> seen = new HashMap<>();
        List<RitualRecipe> accepted = new ArrayList<>();
        for (RitualRecipe recipe : parsed) {
            String signature = recipe.patternId() + "|" + recipe.mode() + "|"
                    + ingredientSignature(recipe.ingredients());
            RitualRecipe previous = seen.get(signature);
            if (previous != null) {
                Gensokyou.LOGGER.warn("Rejected ritual recipe {}: ambiguous with {} (same pattern/mode/ingredients)",
                        recipe.id(), previous.id());
                continue;
            }
            seen.put(signature, recipe);
            accepted.add(recipe);
        }
        warnIfSuperset(accepted);
        synchronized (RECIPES) {
            RECIPES.clear();
            RECIPES.addAll(accepted);
        }
        synchronized (RAWS) {
            RAWS.clear();
            RAWS.putAll(raws);
        }
        Gensokyou.LOGGER.info("Loaded {} ritual recipes", accepted.size());
    }

    /** 归一化原料签名：身份（物品 id / #标签）聚合计数后按序拼接。 */
    public static String ingredientSignature(List<RitualRecipe.Ingredient> ingredients) {
        StringBuilder signature = new StringBuilder();
        for (Map.Entry<String, Integer> entry : aggregate(ingredients).entrySet()) {
            if (!signature.isEmpty()) {
                signature.append(';');
            }
            signature.append(entry.getKey()).append('x').append(entry.getValue());
        }
        return signature.toString();
    }

    /** 归一化原料表：身份 → 总需求数（TreeMap 保序）。 */
    static Map<String, Integer> aggregate(List<RitualRecipe.Ingredient> ingredients) {
        Map<String, Integer> agg = new TreeMap<>();
        for (RitualRecipe.Ingredient ingredient : ingredients) {
            String identity = ingredient.tag() != null
                    ? "#" + ingredient.tag().location()
                    : BuiltInRegistries.ITEM.getKey(ingredient.item()).toString();
            agg.merge(identity, ingredient.count(), Integer::sum);
        }
        return agg;
    }

    /**
     * 同 pattern 配方位集互含的软校验（仅提示，不拒载）：max 匹配下包含可判定，
     * 但设计上互含配方易混淆语义，报 WARN 列双方 id 与包含方向。
     */
    private static void warnIfSuperset(List<RitualRecipe> accepted) {
        Map<ResourceLocation, List<RitualRecipe>> byPattern = new HashMap<>();
        for (RitualRecipe recipe : accepted) {
            byPattern.computeIfAbsent(recipe.patternId(), k -> new ArrayList<>()).add(recipe);
        }
        for (List<RitualRecipe> group : byPattern.values()) {
            for (int i = 0; i < group.size(); i++) {
                for (int j = i + 1; j < group.size(); j++) {
                    RitualRecipe a = group.get(i);
                    RitualRecipe b = group.get(j);
                    Map<String, Integer> ma = aggregate(a.ingredients());
                    Map<String, Integer> mb = aggregate(b.ingredients());
                    if (isProperSuperset(ma, mb)) {
                        Gensokyou.LOGGER.warn("Ritual recipe {} ingredients properly contain recipe {} (same pattern)",
                                a.id(), b.id());
                    } else if (isProperSuperset(mb, ma)) {
                        Gensokyou.LOGGER.warn("Ritual recipe {} ingredients properly contain recipe {} (same pattern)",
                                b.id(), a.id());
                    }
                }
            }
        }
    }

    /** super ⊃ sub：逐身份覆盖且不落空集、总量严格更大（aggregate 非空恒成立）。 */
    static boolean isProperSuperset(Map<String, Integer> sup, Map<String, Integer> sub) {
        if (sup.size() < sub.size()) {
            return false;
        }
        int totalSup = 0;
        int totalSub = 0;
        for (Map.Entry<String, Integer> entry : sub.entrySet()) {
            Integer mine = sup.get(entry.getKey());
            if (mine == null || mine < entry.getValue()) {
                return false;
            }
            totalSub += entry.getValue();
        }
        for (int v : sup.values()) {
            totalSup += v;
        }
        return totalSup > totalSub;
    }

    /**
     * 解析一个配方文件（一仪式一文件）：顶层 {@code pattern} 声明归属仪式，
     * {@code recipes[]} 为其配方列表。每条配方 id = 文件命名空间 + ":" + 名称，
     * 名称取条目 {@code name}，缺省用 {@code <文件路径>_<下标>} 保证唯一稳定。
     * 兼容旧式单配方文件（顶层直接是配方字段 + 自带 pattern）以零破坏历史数据。
     */
    static List<RitualRecipe> parseFile(ResourceLocation fileId, JsonObject json) {
        if (!json.has("recipes")) {
            ResourceLocation legacyPattern =
                    ResourceLocation.parse(GsonHelper.getAsString(json, "pattern"));
            return List.of(parseRecipe(fileId, legacyPattern, json));
        }
        ResourceLocation patternId =
                ResourceLocation.parse(GsonHelper.getAsString(json, "pattern"));
        List<RitualRecipe> recipes = new ArrayList<>();
        int index = 0;
        for (JsonElement element : GsonHelper.getAsJsonArray(json, "recipes")) {
            JsonObject entry = GsonHelper.convertToJsonObject(element, "recipe");
            String name = GsonHelper.getAsString(entry, "name", fileId.getPath() + "_" + index);
            ResourceLocation recipeId = ResourceLocation.fromNamespaceAndPath(
                    fileId.getNamespace(), name);
            recipes.add(parseRecipe(recipeId, patternId, entry));
            index++;
        }
        if (recipes.isEmpty()) {
            throw new IllegalArgumentException("recipes array must not be empty");
        }
        return recipes;
    }

    private static RitualRecipe parseRecipe(ResourceLocation id, ResourceLocation patternId,
                                            JsonObject json) {
        RitualRecipe.Mode mode = parseMode(GsonHelper.getAsString(json, "mode", "activation"));
        RitualRecipe.MatchMode matchMode =
                parseMatch(GsonHelper.getAsString(json, "match", "exact"));
        int minTier = clampMinTier(GsonHelper.getAsInt(json, "minTier", 1));
        int minPlayerTier = Math.max(0, GsonHelper.getAsInt(json, "minPlayerTier", 0));
        List<RitualRecipe.Ingredient> ingredients = new ArrayList<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(json, "ingredients")) {
            JsonObject entry = GsonHelper.convertToJsonObject(element, "ingredient");
            int count = Math.max(1, GsonHelper.getAsInt(entry, "count", 1));
            String item = GsonHelper.getAsString(entry, "item");
            ingredients.add(item.startsWith("#")
                    ? new RitualRecipe.Ingredient(null, RitualRecipe.tagOf(item), count)
                    : new RitualRecipe.Ingredient(RitualRecipe.itemOrThrow(item), null, count));
        }
        if (ingredients.isEmpty()) {
            throw new IllegalArgumentException("ingredients must not be empty");
        }
        int spCost = Math.max(0, GsonHelper.getAsInt(json, "spCost", 0));
        RitualRecipe.ResultHolder result = null;
        if (json.has("result")) {
            JsonObject resultJson = GsonHelper.getAsJsonObject(json, "result");
            result = new RitualRecipe.ResultHolder(
                    RitualRecipe.itemOrThrow(GsonHelper.getAsString(resultJson, "item")),
                    Math.max(1, GsonHelper.getAsInt(resultJson, "count", 1)));
        }
        String effect = json.has("effect") ? GsonHelper.getAsString(json, "effect") : null;
        if (result == null && effect == null) {
            throw new IllegalArgumentException("result and effect must not both be absent");
        }
        validateModeMatch(mode, matchMode, result != null);
        return new RitualRecipe(id, patternId, mode, matchMode, minTier, minPlayerTier,
                List.copyOf(ingredients), spCost, result, effect);
    }

    /** 执行模式解析（缺省 activation）。 */
    static RitualRecipe.Mode parseMode(String raw) {
        return switch (raw) {
            case "activation" -> RitualRecipe.Mode.ACTIVATION;
            case "passive" -> RitualRecipe.Mode.PASSIVE;
            default -> throw new IllegalArgumentException("unknown mode: " + raw);
        };
    }

    /** 匹配模式解析（缺省 exact 严格等值）。 */
    static RitualRecipe.MatchMode parseMatch(String raw) {
        return switch (raw) {
            case "exact" -> RitualRecipe.MatchMode.EXACT;
            case "max" -> RitualRecipe.MatchMode.MAX;
            default -> throw new IllegalArgumentException("unknown match: " + raw);
        };
    }

    /** minTier 下限 0（0 阶可用配方；负值夹到 0）。默认 1 由调用方 GsonHelper 提供。 */
    static int clampMinTier(int raw) {
        return Math.max(0, raw);
    }

    /**
     * mode × match × result 组合合法性：passive 必带 result，且 passive MUST NOT 用 max。
     * 非法抛 IllegalArgumentException（外层 apply 捕获后拒载报因）。
     */
    static void validateModeMatch(RitualRecipe.Mode mode, RitualRecipe.MatchMode match,
                                  boolean hasResult) {
        if (mode == RitualRecipe.Mode.PASSIVE) {
            if (!hasResult) {
                throw new IllegalArgumentException("passive recipes require a result");
            }
            if (match == RitualRecipe.MatchMode.MAX) {
                throw new IllegalArgumentException("passive recipes must use exact matching");
            }
        }
    }

    /** 按挂靠仪式查询（minTier 升序 → id 稳定序）。 */
    public static List<RitualRecipe> forPattern(ResourceLocation patternId) {
        synchronized (RECIPES) {
            return RECIPES.stream()
                    .filter(r -> r.patternId().equals(patternId))
                    .sorted(Comparator.comparingInt(RitualRecipe::minTier)
                            .thenComparing(r -> r.id().toString()))
                    .toList();
        }
    }

    /**
     * 使用期缺失警告：pattern 引用不存在时其配方永远无法生效，每配方 id 仅警告一次。
     *
     * @param patternExists 调用方以 RitualPatternLoader.byId 确认的存在性
     */
    public static void warnIfPatternMissing(ResourceLocation patternId, boolean patternExists) {
        if (patternExists) {
            return;
        }
        synchronized (RECIPES) {
            for (RitualRecipe recipe : RECIPES) {
                if (recipe.patternId().equals(patternId) && !WARNED_MISSING.contains(recipe.id())) {
                    Gensokyou.LOGGER.warn("Ritual recipe {} references unknown pattern {}",
                            recipe.id(), patternId);
                    WARNED_MISSING.add(recipe.id());
                }
            }
        }
    }

    public static Optional<RitualRecipe> byId(ResourceLocation id) {
        synchronized (RECIPES) {
            return RECIPES.stream().filter(r -> r.id().equals(id)).findFirst();
        }
    }

    public static List<RitualRecipe> all() {
        synchronized (RECIPES) {
            return List.copyOf(RECIPES);
        }
    }

    /** 全体原始文件 JSON 副本（客户端同步用）。 */
    public static Map<ResourceLocation, JsonObject> rawAll() {
        synchronized (RAWS) {
            Map<ResourceLocation, JsonObject> copy = new LinkedHashMap<>();
            RAWS.forEach((id, json) -> copy.put(id, json.deepCopy().getAsJsonObject()));
            return copy;
        }
    }

    /** 客户端重建用：以 loader 同规则解析一个配方文件（非法即抛 IllegalArgumentException）。 */
    public static List<RitualRecipe> parseFileForClient(ResourceLocation fileId, JsonObject json) {
        return parseFile(fileId, json);
    }
}
