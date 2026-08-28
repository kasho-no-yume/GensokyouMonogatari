package com.bitsson.gensokyou.spirit;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.List;

public record SkillStateData(List<String> learned, long cooldownUntilSlot0,
                             long cooldownUntilSlot1, long cooldownUntilSlot2) {

    public static final Codec<SkillStateData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.STRING.listOf().fieldOf("learned").forGetter(SkillStateData::learned),
                    Codec.LONG.optionalFieldOf("cd0", 0L).forGetter(SkillStateData::cooldownUntilSlot0),
                    Codec.LONG.optionalFieldOf("cd1", 0L).forGetter(SkillStateData::cooldownUntilSlot1),
                    Codec.LONG.optionalFieldOf("cd2", 0L).forGetter(SkillStateData::cooldownUntilSlot2)
            ).apply(instance, SkillStateData::new));

    public static SkillStateData initial() {
        return new SkillStateData(new ArrayList<>(), 0L, 0L, 0L);
    }

    public boolean hasLearned(String cardId) {
        return learned.contains(cardId);
    }

    public long cooldownUntil(int slot) {
        return switch (slot) {
            case 0 -> cooldownUntilSlot0;
            case 1 -> cooldownUntilSlot1;
            default -> cooldownUntilSlot2;
        };
    }

    public SkillStateData withLearned(String cardId) {
        if (hasLearned(cardId)) {
            return this;
        }
        List<String> copy = new ArrayList<>(learned);
        copy.add(cardId);
        return new SkillStateData(copy, cooldownUntilSlot0, cooldownUntilSlot1, cooldownUntilSlot2);
    }

    public SkillStateData withCooldown(int slot, long until) {
        return switch (slot) {
            case 0 -> new SkillStateData(learned, until, cooldownUntilSlot1, cooldownUntilSlot2);
            case 1 -> new SkillStateData(learned, cooldownUntilSlot0, until, cooldownUntilSlot2);
            default -> new SkillStateData(learned, cooldownUntilSlot0, cooldownUntilSlot1, until);
        };
    }
}
