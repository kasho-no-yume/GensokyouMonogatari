package com.bitsson.gensokyou.spirit;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** 玩家恶意致死 NPC 计数（三振逐出），跨死亡与换维度持久。 */
public record NpcOffenseData(int count) {

    public static final Codec<NpcOffenseData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.optionalFieldOf("count", 0).forGetter(NpcOffenseData::count)
            ).apply(instance, NpcOffenseData::new));

    public static NpcOffenseData initial() {
        return new NpcOffenseData(0);
    }

    public NpcOffenseData withCount(int newCount) {
        return new NpcOffenseData(newCount);
    }
}
