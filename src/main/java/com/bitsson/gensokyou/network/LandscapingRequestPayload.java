package com.bitsson.gensokyou.network;

import com.bitsson.gensokyou.Gensokyou;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 整地器配置界面的「请求状态」上行包：界面 init 时发一次，服务端回 {@link LandscapingStatePayload}。
 */
public record LandscapingRequestPayload(BlockPos pos) implements CustomPacketPayload {

    public static final Type<LandscapingRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Gensokyou.MODID, "landscaping_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LandscapingRequestPayload> STREAM_CODEC =
            StreamCodec.composite(BlockPos.STREAM_CODEC, LandscapingRequestPayload::pos,
                    LandscapingRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LandscapingRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof net.minecraft.server.level.ServerPlayer player)
                    || !(player.level() instanceof net.minecraft.server.level.ServerLevel level)) {
                return;
            }
            if (level.getBlockEntity(payload.pos())
                    instanceof com.bitsson.gensokyou.block.entity.LandscapingBlockEntity device) {
                com.bitsson.gensokyou.menu.LandscapingMenu.sendStateTo(player, device);
            } else {
                com.bitsson.gensokyou.menu.LandscapingMenu.sendAbsentTo(player, payload.pos());
            }
        });
    }
}
