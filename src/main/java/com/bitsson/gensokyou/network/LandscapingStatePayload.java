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
 * 整地器配置 GUI 的服务端下行包：回发<b>当前生效的长 / 宽 / 高</b>。
 */
public record LandscapingStatePayload(BlockPos pos, boolean present, int sizeX, int sizeZ, int height)
        implements CustomPacketPayload {

    public static final Type<LandscapingStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Gensokyou.MODID, "landscaping_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LandscapingStatePayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public LandscapingStatePayload decode(RegistryFriendlyByteBuf buf) {
                    BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
                    boolean present = ByteBufCodecs.BOOL.decode(buf);
                    int x = ByteBufCodecs.VAR_INT.decode(buf);
                    int z = ByteBufCodecs.VAR_INT.decode(buf);
                    int h = ByteBufCodecs.VAR_INT.decode(buf);
                    return new LandscapingStatePayload(pos, present, x, z, h);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, LandscapingStatePayload payload) {
                    BlockPos.STREAM_CODEC.encode(buf, payload.pos());
                    ByteBufCodecs.BOOL.encode(buf, payload.present());
                    ByteBufCodecs.VAR_INT.encode(buf, payload.sizeX());
                    ByteBufCodecs.VAR_INT.encode(buf, payload.sizeZ());
                    ByteBufCodecs.VAR_INT.encode(buf, payload.height());
                }
            };

    /** 空态（整地器已不在世界上）：客户端据此关闭界面。 */
    public static LandscapingStatePayload absent(BlockPos pos) {
        return new LandscapingStatePayload(pos, false, 0, 0, 0);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
