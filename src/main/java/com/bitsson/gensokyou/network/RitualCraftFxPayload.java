package com.bitsson.gensokyou.network;

import com.bitsson.gensokyou.Gensokyou;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C：造化合成演出指令（飞行阶段起点单发）。
 * 结构紫色升空粒子由客户端按包围盒程序化生成至到期，服务端不做逐 tick 粒子广播。
 */
public record RitualCraftFxPayload(BlockPos corePos, BlockPos min, BlockPos max,
                                   int durationTicks) implements CustomPacketPayload {

    public static final Type<RitualCraftFxPayload> TYPE =
            new Type<>(Gensokyou.id("ritual_craft_fx"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RitualCraftFxPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RitualCraftFxPayload::corePos,
                    BlockPos.STREAM_CODEC, RitualCraftFxPayload::min,
                    BlockPos.STREAM_CODEC, RitualCraftFxPayload::max,
                    ByteBufCodecs.VAR_INT, RitualCraftFxPayload::durationTicks,
                    RitualCraftFxPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
