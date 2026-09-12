package com.bitsson.gensokyou.spirit;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * 灵力核心已存灵力量（long 存储，仪式侧全额走 long）。
 * 容量与注灵速率不在组件内——它们是物品档位定值（见 SpiritCoreItem），
 * 分品阶 = 新物品新定值；组件只记"当前存了多少"。
 */
public record SpiritCoreData(long stored) {

    public static final SpiritCoreData EMPTY = new SpiritCoreData(0L);

    public static final Codec<SpiritCoreData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.LONG.optionalFieldOf("stored", 0L).forGetter(SpiritCoreData::stored)
            ).apply(instance, SpiritCoreData::new));

    public static final StreamCodec<ByteBuf, SpiritCoreData> STREAM_CODEC =
            ByteBufCodecs.VAR_LONG.map(SpiritCoreData::new, SpiritCoreData::stored);

    public SpiritCoreData withStored(long value) {
        return new SpiritCoreData(Math.max(0L, value));
    }
}
