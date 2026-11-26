package com.bitsson.gensokyou.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Immutable instance; a single event can advance only the stage that was active when it arrived. */
public record QuestProgress(String instance, ResourceLocation definition, int stage, List<Integer> progress,
                            boolean ready, boolean complete) {
    public static final Codec<QuestProgress> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("instance").forGetter(QuestProgress::instance),
            ResourceLocation.CODEC.fieldOf("definition").forGetter(QuestProgress::definition),
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf("stage").forGetter(QuestProgress::stage),
            Codec.INT.listOf().fieldOf("progress").forGetter(QuestProgress::progress),
            Codec.BOOL.fieldOf("ready").forGetter(QuestProgress::ready),
            Codec.BOOL.fieldOf("complete").forGetter(QuestProgress::complete)
    ).apply(i, QuestProgress::new));
    public QuestProgress { progress = List.copyOf(progress); }
    public static QuestProgress start(QuestDefinition def) { return at(def, UUID.randomUUID().toString(), 0); }
    public static QuestProgress at(QuestDefinition def, String instance, int stage) {
        return new QuestProgress(instance, def.id(), stage,
                Collections.nCopies(def.stages().get(stage).objectives().size(), 0), false, false);
    }
    public QuestProgress apply(QuestDefinition def, QuestObjectives.Event event, QuestObjectives.Context context) {
        if (ready || complete || !definition.equals(def.id()) || stage >= def.stages().size()) return this;
        var goals = def.stages().get(stage).objectives();
        if (progress.size() != goals.size()) return this;
        var next = new ArrayList<Integer>(goals.size());
        boolean done = true;
        for (int n = 0; n < goals.size(); n++) {
            var goal = goals.get(n);
            int old = progress.get(n);
            int value = old >= goal.required() ? goal.required()
                    : Math.clamp(QuestObjectives.handler(goal).update(goal, old, event, context), 0, goal.required());
            next.add(value);
            done &= value >= goal.required();
        }
        if (done && stage + 1 < def.stages().size()) return at(def, instance, stage + 1);
        return new QuestProgress(instance, definition, stage, next, done, false);
    }
    public QuestProgress finish() {
        return ready && !complete ? new QuestProgress(instance, definition, stage, progress, true, true) : this;
    }
    public boolean compatible(QuestDefinition def) {
        return definition.equals(def.id()) && stage >= 0 && stage < def.stages().size()
                && progress.size() == def.stages().get(stage).objectives().size();
    }
    public List<QuestDefinition.ItemAmount> costs(QuestDefinition def, ResourceLocation npc) {
        if (ready || complete || !compatible(def)) return List.of();
        var goals = def.stages().get(stage).objectives();
        var costs = new ArrayList<QuestDefinition.ItemAmount>();
        for (int n = 0; n < goals.size(); n++) if (progress.get(n) < goals.get(n).required())
            costs.addAll(QuestObjectives.handler(goals.get(n)).costs(goals.get(n), npc));
        return List.copyOf(costs);
    }
}
