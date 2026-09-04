package com.bitsson.gensokyou.network;

import com.bitsson.gensokyou.Gensokyou;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/** S2C：搭建因冲突中止时下发全部冲突方块坐标，客户端限时红框渲染。 */
public record RitualConflictPayload(List<BlockPos> positions) implements CustomPacketPayload {

    public static final Type<RitualConflictPayload> TYPE =
            new Type<>(Gensokyou.id("ritual_conflict"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RitualConflictPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), RitualConflictPayload::positions,
                    RitualConflictPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
