package com.bitsson.gensokyou.quest;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Publication policy is separate from instance progression. The built-in source publishes test commissions once. */
public final class QuestProviders {
    @FunctionalInterface public interface Provider {
        List<QuestDefinition> available(ResourceLocation npc, PlayerQuestData player);
    }
    private static final List<Provider> SOURCES = new ArrayList<>();
    static {
        register((npc, player) -> QuestDefinitionLoader.all().values().stream()
                .filter(d -> d.test() && d.kind() == QuestDefinition.Kind.COMMISSION && d.provider().equals(npc))
                .filter(d -> player.quests().stream().noneMatch(q -> q.definition().equals(d.id())))
                .toList());
    }
    public static void register(Provider source) { SOURCES.add(source); }
    public static List<QuestDefinition> available(ResourceLocation npc, PlayerQuestData player) {
        return SOURCES.stream().flatMap(s -> s.available(npc, player).stream())
                .filter(d -> d.scope() == QuestDefinition.Scope.PLAYER && d.provider().equals(npc))
                .distinct().sorted(Comparator.comparing(d -> d.id().toString())).toList();
    }
    private QuestProviders() {}
}
