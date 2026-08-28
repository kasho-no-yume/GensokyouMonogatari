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

    public RitualRecipeLoader() {
        super(GSON, "ritual_recipes");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager,
                         net.minecraft.util.profiling.ProfilerFiller profiler) {
        List<RitualRecipe> parsed = new ArrayList<>();
        for (var file : files.entrySet()) {
            try {
                parsed.add(parse(file.getKey(), GsonHelper.convertToJsonObject(file.getValue(), "recipe")));
            } catch (Exception exception) {
                Gensokyou.LOGGER.warn("Rejected ritual recipe {}: {}", file.getKey(), exception.getMessage());
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
        synchronized (RECIPES) {
            RECIPES.clear();
            RECIPES.addAll(accepted);
        }
        Gensokyou.LOGGER.info("Loaded {} ritual recipes", accepted.size());
    }

    /** 归一化原料签名：身份（物品 id / #标签）聚合计数后按序拼接。 */
    public static String ingredientSignature(List<RitualRecipe.Ingredient> ingredients) {
        Map<String, Integer> agg = new TreeMap<>();
        for (RitualRecipe.Ingredient ingredient : ingredients) {
            String identity = ingredient.tag() != null
                    ? "#" + ingredient.tag().location()
                    : BuiltInRegistries.ITEM.getKey(ingredient.item()).toString();
            agg.merge(identity, ingredient.count(), Integer::sum);
        }
        StringBuilder signature = new StringBuilder();
        for (Map.Entry<String, Integer> entry : agg.entrySet()) {
            if (!signature.isEmpty()) {
                signature.append(';');
            }
            signature.append(entry.getKey()).append('x').append(entry.getValue());
        }
        return signature.toString();
    }

    private static RitualRecipe parse(ResourceLocation id, JsonObject json) {
        ResourceLocation patternId = ResourceLocation.parse(GsonHelper.getAsString(json, "pattern"));
        RitualRecipe.Mode mode = switch (GsonHelper.getAsString(json, "mode", "activation")) {
            case "activation" -> RitualRecipe.Mode.ACTIVATION;
            case "passive" -> RitualRecipe.Mode.PASSIVE;
            default -> throw new IllegalArgumentException("unknown mode: "
                    + GsonHelper.getAsString(json, "mode", "activation"));
        };
        int minTier = Math.max(1, GsonHelper.getAsInt(json, "minTier", 1));
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
        if (mode == RitualRecipe.Mode.PASSIVE && result == null) {
            throw new IllegalArgumentException("passive recipes require a result");
        }
        return new RitualRecipe(id, patternId, mode, minTier,
                List.copyOf(ingredients), spCost, result, effect);
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
}
