package com.bitsson.gensokyou.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Server resource definition. Presentation uses translation keys; runtime state never lives here. */
public record QuestDefinition(ResourceLocation id, Kind kind, Scope scope, String title, String description,
                              ResourceLocation provider, ResourceLocation returnNpc, List<Stage> stages,
                              List<ItemAmount> rewards, int mainlineOrder, boolean test) {
    public enum Kind { MAIN, SIDE, COMMISSION }
    public enum Scope { WORLD, PLAYER }
    public static final Codec<Kind> KIND_CODEC = Codec.STRING.xmap(
            s -> Kind.valueOf(s.toUpperCase(Locale.ROOT)), k -> k.name().toLowerCase(Locale.ROOT));
    public static final Codec<Scope> SCOPE_CODEC = Codec.STRING.xmap(
            s -> Scope.valueOf(s.toUpperCase(Locale.ROOT)), k -> k.name().toLowerCase(Locale.ROOT));
    public static final Codec<QuestDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(QuestDefinition::id),
            KIND_CODEC.fieldOf("kind").forGetter(QuestDefinition::kind),
            SCOPE_CODEC.fieldOf("scope").forGetter(QuestDefinition::scope),
            Codec.STRING.fieldOf("title").forGetter(QuestDefinition::title),
            Codec.STRING.fieldOf("description").forGetter(QuestDefinition::description),
            ResourceLocation.CODEC.fieldOf("provider").forGetter(QuestDefinition::provider),
            ResourceLocation.CODEC.fieldOf("return_npc").forGetter(QuestDefinition::returnNpc),
            Stage.CODEC.listOf().fieldOf("stages").forGetter(QuestDefinition::stages),
            ItemAmount.CODEC.listOf().optionalFieldOf("rewards", List.of()).forGetter(QuestDefinition::rewards),
            Codec.INT.optionalFieldOf("mainline_order", -1).forGetter(QuestDefinition::mainlineOrder),
            Codec.BOOL.optionalFieldOf("test", false).forGetter(QuestDefinition::test)
    ).apply(i, QuestDefinition::new));

    public QuestDefinition {
        stages = List.copyOf(stages);
        rewards = List.copyOf(rewards);
        if ((kind == Kind.MAIN) != (scope == Scope.WORLD)) throw new IllegalArgumentException("Invalid kind/scope");
        if (stages.isEmpty() || stages.size() > 256) throw new IllegalArgumentException("Invalid stage count");
        if (kind == Kind.COMMISSION && stages.size() != 1) throw new IllegalArgumentException("Commission must have one stage");
        if (kind == Kind.MAIN && (mainlineOrder < 0 || !rewards.isEmpty()))
            throw new IllegalArgumentException("Mainline needs an order and cannot distribute personal rewards");
        var ids = new HashSet<String>();
        for (Stage stage : stages) {
            if (!ids.add(stage.id())) throw new IllegalArgumentException("Duplicate stage " + stage.id());
            for (var goal : stage.objectives()) if (goal.required() < 1) throw new IllegalArgumentException("Objective count must be positive");
        }
    }

    public record ItemAmount(ResourceLocation item, int count) {
        public static final Codec<ItemAmount> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("item").forGetter(ItemAmount::item),
                Codec.intRange(1, 32767).fieldOf("count").forGetter(ItemAmount::count)
        ).apply(i, ItemAmount::new));
        public ItemAmount { if (count < 1 || count > 32767) throw new IllegalArgumentException("Invalid item count"); }
    }

    public record Stage(String id, String title, List<QuestObjectives.Objective> objectives,
                        Optional<Navigation> navigation) {
        public static final Codec<Stage> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(Stage::id),
                Codec.STRING.fieldOf("title").forGetter(Stage::title),
                QuestObjectives.CODEC.listOf().fieldOf("objectives").forGetter(Stage::objectives),
                Navigation.CODEC.optionalFieldOf("navigation").forGetter(Stage::navigation)
        ).apply(i, Stage::new));
        public Stage {
            objectives = List.copyOf(objectives);
            if (id.isBlank() || objectives.isEmpty() || objectives.size() > 64)
                throw new IllegalArgumentException("Stage needs an id and 1..64 objectives");
        }
    }

    /** Either a stable NPC role or a fixed dimension/position. Never force-loads an entity or chunk. */
    public record Navigation(Optional<ResourceLocation> npc, Optional<ResourceLocation> dimension,
                             double x, double y, double z) {
        public static final Codec<Navigation> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.optionalFieldOf("npc").forGetter(Navigation::npc),
                ResourceLocation.CODEC.optionalFieldOf("dimension").forGetter(Navigation::dimension),
                Codec.DOUBLE.optionalFieldOf("x", 0D).forGetter(Navigation::x),
                Codec.DOUBLE.optionalFieldOf("y", 0D).forGetter(Navigation::y),
                Codec.DOUBLE.optionalFieldOf("z", 0D).forGetter(Navigation::z)
        ).apply(i, Navigation::new));
        public Navigation {
            if (npc.isPresent() == dimension.isPresent() || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
                throw new IllegalArgumentException("Navigation requires either npc or dimension/position");
        }
        public static Navigation npc(ResourceLocation id) { return new Navigation(Optional.of(id), Optional.empty(), 0, 0, 0); }
    }
}
