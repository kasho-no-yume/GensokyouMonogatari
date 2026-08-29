package com.bitsson.gensokyou.item.weapon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** 单条增幅核词条：affixId 对应词条池配置中的效果类型，value 为区间内 roll 值。 */
public record RuneAffix(String affixId, float value) {

    public static final Codec<RuneAffix> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.STRING.fieldOf("affix_id").forGetter(RuneAffix::affixId),
                    Codec.FLOAT.fieldOf("value").forGetter(RuneAffix::value)
            ).apply(instance, RuneAffix::new));

    public static final StreamCodec<io.netty.buffer.ByteBuf, RuneAffix> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RuneAffix::affixId,
            ByteBufCodecs.FLOAT, RuneAffix::value,
            RuneAffix::new);
}
