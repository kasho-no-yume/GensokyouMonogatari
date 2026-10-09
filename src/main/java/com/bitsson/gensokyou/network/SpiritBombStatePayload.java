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
 * 灵力引爆器配置 GUI 的服务端下行包。
 *
 * <p>只发<b>当前生效值</b>与<b>启动物理状态</b>：客户端滑块的拖动是纯本地的，
 * 松手时才由 {@link SpiritBombConfigPayload} 上行申请，服务端钳制后回发本包，
 * 因此滑块永远显示的是服务端认可的值（而不是客户端一厢情愿的中间态）。
 *
 * <p><b>为什么手写 codec 而不用 {@code StreamCodec.composite}</b>：本包有 9 个字段，
 * 而 {@code composite} 只提供到 4 字段的重载（再多就得套
 * {@code ByteBufCodecs.apply}，可读性更差）。手写 decode/encode 一共十行，
 * 且字段顺序集中在一处，比嵌套 apply 好维护。
 */
public record SpiritBombStatePayload(BlockPos bombPos, boolean present, boolean armed,
                                     int fuseTicks, float power, int radius,
                                     long estimatedCost, boolean affordable)
        implements CustomPacketPayload {

    public static final Type<SpiritBombStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Gensokyou.MODID, "spirit_bomb_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpiritBombStatePayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public SpiritBombStatePayload decode(RegistryFriendlyByteBuf buf) {
                    BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
                    boolean present = ByteBufCodecs.BOOL.decode(buf);
                    boolean armed = ByteBufCodecs.BOOL.decode(buf);
                    int fuse = ByteBufCodecs.VAR_INT.decode(buf);
                    float power = ByteBufCodecs.FLOAT.decode(buf);
                    int radius = ByteBufCodecs.VAR_INT.decode(buf);
                    long cost = ByteBufCodecs.VAR_LONG.decode(buf);
                    boolean affordable = ByteBufCodecs.BOOL.decode(buf);
                    return new SpiritBombStatePayload(pos, present, armed, fuse, power, radius,
                            cost, affordable);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, SpiritBombStatePayload payload) {
                    BlockPos.STREAM_CODEC.encode(buf, payload.bombPos());
                    ByteBufCodecs.BOOL.encode(buf, payload.present());
                    ByteBufCodecs.BOOL.encode(buf, payload.armed());
                    ByteBufCodecs.VAR_INT.encode(buf, payload.fuseTicks());
                    ByteBufCodecs.FLOAT.encode(buf, payload.power());
                    ByteBufCodecs.VAR_INT.encode(buf, payload.radius());
                    ByteBufCodecs.VAR_LONG.encode(buf, payload.estimatedCost());
                    ByteBufCodecs.BOOL.encode(buf, payload.affordable());
                }
            };

    /** 空态（引爆器已不在世界上）：客户端据此关闭界面。 */
    public static SpiritBombStatePayload absent(BlockPos pos) {
        return new SpiritBombStatePayload(pos, false, false, 0, 0f, 0, 0L, false);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
