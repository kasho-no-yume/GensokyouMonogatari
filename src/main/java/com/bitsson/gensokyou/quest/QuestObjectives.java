package com.bitsson.gensokyou.quest;

import com.bitsson.gensokyou.Gensokyou;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Type codecs, event handling and optional NPC operations share one extension point. */
public final class QuestObjectives {
    public interface Objective {
        ResourceLocation type();
        String description();
        int required();
    }
    public interface Context { int count(ResourceLocation item); }
    public sealed interface Event permits Poll, Kill, Talk, Submit {}
    public record Poll() implements Event {}
    public record Kill(ResourceLocation entityType) implements Event {}
    public record Talk(ResourceLocation npc, ResourceLocation action) implements Event {}
    public record Submit(ResourceLocation npc) implements Event {}
    public record Prompt(ResourceLocation action, String question, String confirm) {}

    public interface Handler<T extends Objective> {
        MapCodec<T> codec();
        int update(T goal, int progress, Event event, Context context);
        default void validate(T goal) {}
        default List<QuestDefinition.ItemAmount> costs(T goal, ResourceLocation npc) { return List.of(); }
        default Optional<Prompt> prompt(T goal, ResourceLocation npc) { return Optional.empty(); }
        default List<ResourceLocation> dialogueNpcs(T goal) { return List.of(); }
    }
    private static final Map<ResourceLocation, Handler<?>> TYPES = new LinkedHashMap<>();
    public static final Codec<Objective> CODEC = ResourceLocation.CODEC.dispatch(Objective::type, id -> {
        Handler<?> handler = TYPES.get(id);
        if (handler == null) throw new IllegalArgumentException("Unknown quest objective type " + id);
        return handler.codec();
    });

    public static <T extends Objective> void register(ResourceLocation id, Handler<T> handler) {
        if (TYPES.putIfAbsent(id, handler) != null) throw new IllegalArgumentException("Duplicate objective type " + id);
    }
    @SuppressWarnings("unchecked")
    public static Handler<Objective> handler(Objective goal) {
        Handler<?> handler = TYPES.get(goal.type());
        if (handler == null) throw new IllegalArgumentException("Unknown objective " + goal.type());
        return (Handler<Objective>) handler;
    }
    private static ResourceLocation id(String path) { return Gensokyou.id(path); }
    private static void item(ResourceLocation id) {
        if (!BuiltInRegistries.ITEM.containsKey(id) || id.equals(ResourceLocation.withDefaultNamespace("air")))
            throw new IllegalArgumentException("Unknown/empty quest item " + id);
    }

    public record HaveItems(ResourceLocation item, int count, String description) implements Objective {
        public ResourceLocation type() { return id("have_items"); }
        public int required() { return count; }
        public static final MapCodec<HaveItems> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                ResourceLocation.CODEC.fieldOf("item").forGetter(HaveItems::item),
                Codec.intRange(1, 32767).fieldOf("count").forGetter(HaveItems::count),
                Codec.STRING.fieldOf("description").forGetter(HaveItems::description)
        ).apply(i, HaveItems::new));
    }
    public record SubmitItems(ResourceLocation item, int count, ResourceLocation npc, String description) implements Objective {
        public ResourceLocation type() { return id("submit_items"); }
        public int required() { return count; }
        public static final MapCodec<SubmitItems> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                ResourceLocation.CODEC.fieldOf("item").forGetter(SubmitItems::item),
                Codec.intRange(1, 32767).fieldOf("count").forGetter(SubmitItems::count),
                ResourceLocation.CODEC.fieldOf("npc").forGetter(SubmitItems::npc),
                Codec.STRING.fieldOf("description").forGetter(SubmitItems::description)
        ).apply(i, SubmitItems::new));
    }
    public record KillEntities(ResourceLocation entity, int count, String description) implements Objective {
        public ResourceLocation type() { return id("kill_entities"); }
        public int required() { return count; }
        public static final MapCodec<KillEntities> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                ResourceLocation.CODEC.fieldOf("entity").forGetter(KillEntities::entity),
                Codec.intRange(1, 32767).fieldOf("count").forGetter(KillEntities::count),
                Codec.STRING.fieldOf("description").forGetter(KillEntities::description)
        ).apply(i, KillEntities::new));
    }
    public record TalkToNpc(ResourceLocation npc, ResourceLocation action, String description,
                            String question, String confirm) implements Objective {
        public ResourceLocation type() { return id("talk_to_npc"); }
        public int required() { return 1; }
        public static final MapCodec<TalkToNpc> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                ResourceLocation.CODEC.fieldOf("npc").forGetter(TalkToNpc::npc),
                ResourceLocation.CODEC.fieldOf("action").forGetter(TalkToNpc::action),
                Codec.STRING.fieldOf("description").forGetter(TalkToNpc::description),
                Codec.STRING.fieldOf("question").forGetter(TalkToNpc::question),
                Codec.STRING.optionalFieldOf("confirm", "gui.gensokyou.quest.yes").forGetter(TalkToNpc::confirm)
        ).apply(i, TalkToNpc::new));
    }

    static {
        register(id("have_items"), new Handler<HaveItems>() {
            public MapCodec<HaveItems> codec() { return HaveItems.CODEC; }
            public void validate(HaveItems g) { item(g.item()); }
            public int update(HaveItems g, int p, Event e, Context c) {
                return e instanceof Poll ? Math.min(g.count(), c.count(g.item())) : p;
            }
        });
        register(id("submit_items"), new Handler<SubmitItems>() {
            public MapCodec<SubmitItems> codec() { return SubmitItems.CODEC; }
            public void validate(SubmitItems g) { item(g.item()); }
            public int update(SubmitItems g, int p, Event e, Context c) {
                return e instanceof Submit s && s.npc().equals(g.npc()) ? g.count() : p;
            }
            public List<QuestDefinition.ItemAmount> costs(SubmitItems g, ResourceLocation npc) {
                return npc.equals(g.npc()) ? List.of(new QuestDefinition.ItemAmount(g.item(), g.count())) : List.of();
            }
        });
        register(id("kill_entities"), new Handler<KillEntities>() {
            public MapCodec<KillEntities> codec() { return KillEntities.CODEC; }
            public void validate(KillEntities g) {
                if (!BuiltInRegistries.ENTITY_TYPE.containsKey(g.entity())) throw new IllegalArgumentException("Unknown entity " + g.entity());
            }
            public int update(KillEntities g, int p, Event e, Context c) {
                return e instanceof Kill k && k.entityType().equals(g.entity()) ? p + 1 : p;
            }
        });
        register(id("talk_to_npc"), new Handler<TalkToNpc>() {
            public MapCodec<TalkToNpc> codec() { return TalkToNpc.CODEC; }
            public int update(TalkToNpc g, int p, Event e, Context c) {
                return e instanceof Talk t && t.npc().equals(g.npc()) && t.action().equals(g.action()) ? 1 : p;
            }
            public Optional<Prompt> prompt(TalkToNpc g, ResourceLocation npc) {
                return npc.equals(g.npc()) ? Optional.of(new Prompt(g.action(), g.question(), g.confirm())) : Optional.empty();
            }
            public List<ResourceLocation> dialogueNpcs(TalkToNpc g) { return List.of(g.npc()); }
        });
    }
    private QuestObjectives() {}
}
