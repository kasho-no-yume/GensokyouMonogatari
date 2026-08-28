package com.bitsson.gensokyou.network;

import com.bitsson.gensokyou.Gensokyou;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** C2S：仪式界面的启动/停止按钮指令。 */
public record RitualTogglePayload(BlockPos pos, boolean start) implements CustomPacketPayload {

    public static final Type<RitualTogglePayload> TYPE =
            new Type<>(Gensokyou.id("ritual_toggle"));

    public static final StreamCodec<FriendlyByteBuf, RitualTogglePayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RitualTogglePayload::pos,
                    ByteBufCodecs.BOOL, RitualTogglePayload::start,
                    RitualTogglePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
