package com.bitsson.gensokyou.network;

import com.bitsson.gensokyou.Gensokyou;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SpiritPowerSyncPayload(int current, int max) implements CustomPacketPayload {

    public static final Type<SpiritPowerSyncPayload> TYPE =
            new Type<>(Gensokyou.id("spirit_power_sync"));

    public static final StreamCodec<FriendlyByteBuf, SpiritPowerSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SpiritPowerSyncPayload::current,
                    ByteBufCodecs.VAR_INT, SpiritPowerSyncPayload::max,
                    SpiritPowerSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
