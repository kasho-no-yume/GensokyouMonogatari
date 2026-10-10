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
 * 灵力引爆器配置 GUI 的客户端上行包：把滑块终值提交给服务端钳制并（可选）请求启动。
 *
 * <p><b>为什么参数与启动合成一个动作</b>：滑块的每一次松手都意味着一次参数变更，
 * 而"启动"必须基于<b>最终</b>参数计算灵力消耗。分成两个包会让"改参数后立即启动"
 * 出现一帧的参数不一致窗口。合成一个包后服务端先钳制参数、再用钳制后的值算钱，
 * 客户端随后由 {@link SpiritBombStatePayload} 看到回发值，天然自洽。
 *
 * <p>5 个字段仍在 {@code StreamCodec.composite} 的 4 字段上限之外吗？——不，5 个刚好
 * 超出 4 字段重载，故同样手写（见 {@link SpiritBombStatePayload} 的同类注释）。
 */
public record SpiritBombConfigPayload(BlockPos bombPos, boolean arm, int fuseTicks, float power,
                                      int radius) implements CustomPacketPayload {

    public static final Type<SpiritBombConfigPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Gensokyou.MODID, "spirit_bomb_config"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpiritBombConfigPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public SpiritBombConfigPayload decode(RegistryFriendlyByteBuf buf) {
                    return new SpiritBombConfigPayload(
                            BlockPos.STREAM_CODEC.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.FLOAT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf));
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, SpiritBombConfigPayload payload) {
                    BlockPos.STREAM_CODEC.encode(buf, payload.bombPos());
                    ByteBufCodecs.BOOL.encode(buf, payload.arm());
                    ByteBufCodecs.VAR_INT.encode(buf, payload.fuseTicks());
                    ByteBufCodecs.FLOAT.encode(buf, payload.power());
                    ByteBufCodecs.VAR_INT.encode(buf, payload.radius());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SpiritBombConfigPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof net.minecraft.server.level.ServerPlayer player)) {
                return;
            }
            if (!(player.level() instanceof net.minecraft.server.level.ServerLevel level)) {
                return;
            }
            // 用方块实体定位：引爆器现在是方块，pos 即其位置
            if (!(level.getBlockEntity(payload.bombPos())
                    instanceof com.bitsson.gensokyou.block.entity.SpiritBombBlockEntity bomb)) {
                com.bitsson.gensokyou.menu.SpiritBombMenu.sendAbsentTo(player, payload.bombPos());
                return;
            }
            bomb.setFuseTicks(payload.fuseTicks());
            bomb.setPower(payload.power());
            bomb.setBlastRadius(payload.radius());
            if (payload.arm()) {
                bomb.arm(player);
            }
            com.bitsson.gensokyou.menu.SpiritBombMenu.sendStateTo(player, bomb);
        });
    }
}
