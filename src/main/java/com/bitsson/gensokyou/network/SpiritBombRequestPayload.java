package com.bitsson.gensokyou.network;

import com.bitsson.gensokyou.Gensokyou;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 引爆器配置界面的「请求状态」上行包：界面 init 时发一次，服务端回 {@link SpiritBombStatePayload}。
 *
 * <p>由已存在的界面发出，故回应必在界面存活时到达，规避「开屏同 tick 发状态包」的竞态；
 * 也避免了每 tick 补发带来的闪烁。
 */
public record SpiritBombRequestPayload(BlockPos pos) implements CustomPacketPayload {

    public static final Type<SpiritBombRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Gensokyou.MODID, "spirit_bomb_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpiritBombRequestPayload> STREAM_CODEC =
            StreamCodec.composite(BlockPos.STREAM_CODEC, SpiritBombRequestPayload::pos,
                    SpiritBombRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SpiritBombRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof net.minecraft.server.level.ServerPlayer player)
                    || !(player.level() instanceof net.minecraft.server.level.ServerLevel level)) {
                return;
            }
            if (level.getBlockEntity(payload.pos())
                    instanceof com.bitsson.gensokyou.block.entity.SpiritBombBlockEntity bomb) {
                com.bitsson.gensokyou.menu.SpiritBombMenu.sendStateTo(player, bomb);
            } else {
                com.bitsson.gensokyou.menu.SpiritBombMenu.sendAbsentTo(player, payload.pos());
            }
        });
    }
}
