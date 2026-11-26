package com.bitsson.gensokyou.quest;

import com.bitsson.gensokyou.Gensokyou;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class QuestDefinitionLoader extends SimpleJsonResourceReloadListener {
    private static Map<ResourceLocation, QuestDefinition> definitions = Map.of();
    public QuestDefinitionLoader() { super(new Gson(), "quests"); }
    public static Map<ResourceLocation, QuestDefinition> all() { return definitions; }
    public record MainStage(QuestDefinition definition, int index) {}
    public static List<MainStage> mainline() {
        return definitions.values().stream().filter(d -> d.kind() == QuestDefinition.Kind.MAIN)
                .sorted(Comparator.comparingInt(QuestDefinition::mainlineOrder))
                .flatMap(d -> java.util.stream.IntStream.range(0, d.stages().size()).mapToObj(n -> new MainStage(d, n))).toList();
    }
    public static QuestDefinition decode(ResourceLocation file, JsonElement json) {
        var object = json.getAsJsonObject();
        if (object.has("children") || object.has("next_quest")) throw new IllegalArgumentException("Use ordered stages, not child quests");
        var def = QuestDefinition.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        if (!def.id().equals(file)) throw new IllegalArgumentException("Quest id does not match file " + file);
        for (var stage : def.stages()) for (var goal : stage.objectives()) QuestObjectives.handler(goal).validate(goal);
        for (var reward : def.rewards()) if (!BuiltInRegistries.ITEM.containsKey(reward.item())
                || reward.item().equals(ResourceLocation.withDefaultNamespace("air")))
            throw new IllegalArgumentException("Unknown reward item " + reward.item());
        return def;
    }
    @Override protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        var accepted = new LinkedHashMap<ResourceLocation, QuestDefinition>();
        var orders = new HashMap<Integer, ResourceLocation>();
        files.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            try {
                var def = decode(entry.getKey(), entry.getValue());
                if (def.kind() == QuestDefinition.Kind.MAIN && orders.putIfAbsent(def.mainlineOrder(), def.id()) != null)
                    throw new IllegalArgumentException("Duplicate mainline order " + def.mainlineOrder());
                accepted.put(def.id(), def);
            } catch (RuntimeException e) {
                Gensokyou.LOGGER.error("Rejected quest {}: {}", entry.getKey(), e.getMessage());
            }
        });
        definitions = Map.copyOf(accepted);
        Gensokyou.LOGGER.info("Loaded {} quest definitions", definitions.size());
    }
}
