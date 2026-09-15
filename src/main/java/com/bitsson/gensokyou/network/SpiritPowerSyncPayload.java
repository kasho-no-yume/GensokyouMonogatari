package com.bitsson.gensokyou.network;

import com.bitsson.gensokyou.Gensokyou;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** S2C：灵力池 + 超人类阶级 + 飞行惯性开关（HUD 槽数与客户端飞行手感共用通道）。 */
public record SpiritPowerSyncPayload(int current, int max, int temper, boolean flightInertia)
        implements CustomPacketPayload {

    public static final Type<SpiritPowerSyncPayload> TYPE =
            new Type<>(Gensokyou.id("spirit_power_sync"));

    public static final StreamCodec<FriendlyByteBuf, SpiritPowerSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SpiritPowerSyncPayload::current,
                    ByteBufCodecs.VAR_INT, SpiritPowerSyncPayload::max,
                    ByteBufCodecs.VAR_INT, SpiritPowerSyncPayload::temper,
                    ByteBufCodecs.BOOL, SpiritPowerSyncPayload::flightInertia,
                    SpiritPowerSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
