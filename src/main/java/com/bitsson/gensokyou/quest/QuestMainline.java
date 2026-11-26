package com.bitsson.gensokyou.quest;

import java.util.List;
import java.util.Optional;

/** Linear world progression also accepts isolated definitions for tests, without registering playable main quests. */
public final class QuestMainline {
    public static Optional<QuestDefinitionLoader.MainStage> current(WorldQuestData data, List<QuestDefinitionLoader.MainStage> stages) {
        if (data.cursor() >= stages.size()) return Optional.empty();
        var stage = stages.get(data.cursor());
        var definition = stage.definition().stages().get(stage.index());
        data.reconcile(stage.definition().id() + "/" + definition, definition.objectives().size());
        return Optional.of(stage);
    }
    public static QuestProgress progress(WorldQuestData data, QuestDefinitionLoader.MainStage stage) {
        return new QuestProgress(PlayerQuestData.MAINLINE, stage.definition().id(), stage.index(), data.progress(), false, false);
    }
    public static void apply(WorldQuestData data, QuestDefinitionLoader.MainStage stage,
                             QuestObjectives.Event event, QuestObjectives.Context context) {
        var next = progress(data, stage).apply(stage.definition(), event, context);
        data.update(next.progress(), next.ready() || next.stage() != stage.index());
    }
    private QuestMainline() {}
}
