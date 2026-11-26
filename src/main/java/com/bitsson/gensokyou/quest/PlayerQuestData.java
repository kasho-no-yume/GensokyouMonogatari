package com.bitsson.gensokyou.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/** Completed instances remain as a receipt for the static provider; they are omitted from the journal. */
public record PlayerQuestData(long epoch, List<QuestProgress> quests, String tracked) {
    public static final String MAINLINE = "mainline";
    public static final Codec<PlayerQuestData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("epoch").forGetter(PlayerQuestData::epoch),
            QuestProgress.CODEC.listOf().fieldOf("quests").forGetter(PlayerQuestData::quests),
            Codec.STRING.optionalFieldOf("tracked", "").forGetter(PlayerQuestData::tracked)
    ).apply(i, PlayerQuestData::new));
    public PlayerQuestData { quests = List.copyOf(quests); }
    public static PlayerQuestData empty() { return new PlayerQuestData(-1, List.of(), ""); }
    public PlayerQuestData forEpoch(long current) {
        return epoch == current ? this : new PlayerQuestData(current, List.of(), MAINLINE.equals(tracked) ? MAINLINE : "");
    }
    public PlayerQuestData replace(QuestProgress next) {
        return new PlayerQuestData(epoch, quests.stream().map(q -> q.instance().equals(next.instance()) ? next : q).toList(),
                next.complete() && tracked.equals(next.instance()) ? "" : tracked);
    }
}
