package com.bitsson.gensokyou.network;

import com.bitsson.gensokyou.Gensokyou;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record CastSkillPayload(int slot) implements CustomPacketPayload {

    public static final Type<CastSkillPayload> TYPE =
            new Type<>(Gensokyou.id("cast_skill"));

    public static final StreamCodec<FriendlyByteBuf, CastSkillPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, CastSkillPayload::slot,
                    CastSkillPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
