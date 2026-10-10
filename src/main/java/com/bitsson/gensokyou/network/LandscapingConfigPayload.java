package com.bitsson.gensokyou.network;

import com.bitsson.gensokyou.Gensokyou;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 整地器配置 GUI 的客户端上行包：提交长 / 宽 / 高，可选请求启动。
 */
public record LandscapingConfigPayload(BlockPos pos, int sizeX, int sizeZ, int height, boolean activate)
        implements CustomPacketPayload {

    public static final Type<LandscapingConfigPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Gensokyou.MODID, "landscaping_config"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LandscapingConfigPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public LandscapingConfigPayload decode(RegistryFriendlyByteBuf buf) {
                    BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
                    int x = ByteBufCodecs.VAR_INT.decode(buf);
                    int z = ByteBufCodecs.VAR_INT.decode(buf);
                    int h = ByteBufCodecs.VAR_INT.decode(buf);
                    boolean activate = ByteBufCodecs.BOOL.decode(buf);
                    return new LandscapingConfigPayload(pos, x, z, h, activate);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, LandscapingConfigPayload payload) {
                    BlockPos.STREAM_CODEC.encode(buf, payload.pos());
                    ByteBufCodecs.VAR_INT.encode(buf, payload.sizeX());
                    ByteBufCodecs.VAR_INT.encode(buf, payload.sizeZ());
                    ByteBufCodecs.VAR_INT.encode(buf, payload.height());
                    ByteBufCodecs.BOOL.encode(buf, payload.activate());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LandscapingConfigPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof net.minecraft.server.level.ServerPlayer player)) {
                return;
            }
            if (!(player.level() instanceof net.minecraft.server.level.ServerLevel level)) {
                return;
            }
            if (!(level.getBlockEntity(payload.pos())
                    instanceof com.bitsson.gensokyou.block.entity.LandscapingBlockEntity device)) {
                com.bitsson.gensokyou.menu.LandscapingMenu.sendAbsentTo(player, payload.pos());
                return;
            }
            device.setParams(payload.sizeX(), payload.sizeZ(), payload.height());
            if (payload.activate()) {
                device.activate(player);
            }
            com.bitsson.gensokyou.menu.LandscapingMenu.sendStateTo(player, device);
        });
    }
}
