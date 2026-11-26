package com.bitsson.gensokyou.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Stored only in the server's dialogue graph, never supplied by a client completion request. */
public record QuestDialogueAction(String kind, String target, int stage, String action) {
    public static final Codec<QuestDialogueAction> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("kind").forGetter(QuestDialogueAction::kind),
            Codec.STRING.fieldOf("target").forGetter(QuestDialogueAction::target),
            Codec.INT.optionalFieldOf("stage", 0).forGetter(QuestDialogueAction::stage),
            Codec.STRING.optionalFieldOf("action", "").forGetter(QuestDialogueAction::action)
    ).apply(i, QuestDialogueAction::new));
}
