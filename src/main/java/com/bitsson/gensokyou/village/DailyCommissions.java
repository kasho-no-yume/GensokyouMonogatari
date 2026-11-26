package com.bitsson.gensokyou.village;

import com.bitsson.gensokyou.quest.QuestDefinition;
import com.bitsson.gensokyou.quest.QuestDefinitionLoader;
import com.bitsson.gensokyou.quest.QuestObjectives;
import com.bitsson.gensokyou.quest.QuestService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class DailyCommissions {
    public static final int DAILY_COUNT = 3, ACTIVE_LIMIT = 6;
    public static boolean daily(QuestDefinition definition) {
        return definition.kind() == QuestDefinition.Kind.COMMISSION && !definition.test()
                && definition.provider().equals(VillageRole.AKYUU.id())
                && definition.id().getNamespace().equals("gensokyou") && definition.id().getPath().startsWith("village/commissions/");
    }
    public static List<QuestDefinition> available(ServerPlayer player) {
        var quests = QuestService.data(player).quests();
        var state = VillageServices.data(player);
        if (state.commissions().isEmpty()) {
            var pool = QuestDefinitionLoader.all().values().stream().filter(DailyCommissions::daily)
                    .filter(d -> quests.stream().noneMatch(q -> !q.complete() && q.definition().equals(d.id())))
                    .sorted(Comparator.comparing(d -> d.id().toString())).map(QuestDefinition::id).toList();
            var selected = DailySelection.pick(pool, DAILY_COUNT, VillageServices.seed(player, "commissions"));
            if (!selected.isEmpty()) { state = state.publish(selected); VillageServices.store(player, state); }
        }
        if (quests.stream().filter(q -> !q.complete() && q.instance().startsWith("village/")).count() >= ACTIVE_LIMIT) return List.of();
        final var dailyState = state;
        return state.commissions().stream().filter(id -> !dailyState.accepted().contains(id))
                .map(QuestDefinitionLoader.all()::get).filter(d -> d != null && daily(d))
                .filter(d -> quests.stream().noneMatch(q -> !q.complete() && q.definition().equals(d.id()))).toList();
    }
    public static String instance(ServerPlayer player, QuestDefinition definition) {
        return "village/" + VillageServices.data(player).day() + "/" + definition.id();
    }
    public static void accepted(ServerPlayer player, QuestDefinition definition) {
        VillageServices.store(player, VillageServices.data(player).accept(definition.id()));
    }
    /** All formal commission inputs, not only today's selection. Item-level exclusion also covers component variants. */
    public static Set<ResourceLocation> turnInItems() {
        var result = new HashSet<ResourceLocation>();
        for (var definition : QuestDefinitionLoader.all().values()) {
            if (definition.test() || definition.kind() != QuestDefinition.Kind.COMMISSION) continue;
            for (var stage : definition.stages()) for (var goal : stage.objectives()) {
                if (goal instanceof QuestObjectives.SubmitItems submit) result.add(submit.item());
                for (var role : VillageRole.values())
                    QuestObjectives.handler(goal).costs(goal, role.id()).forEach(cost -> result.add(cost.item()));
            }
        }
        return Set.copyOf(result);
    }
    private DailyCommissions() {}
}
