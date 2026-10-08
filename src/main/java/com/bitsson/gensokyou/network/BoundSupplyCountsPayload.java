package com.bitsson.gensokyou.network;

import com.bitsson.gensokyou.Gensokyou;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * S2C：绑定无尽藏仓储的计数快照。
 *
 * <p>仅含当前构建器选择/预览图案**所需的方块子集**；{@code status} 携带闸门状态
 * （0=可用 / 1=未成形 / 2=已停止 / 3=异维度），供客户端标注绑定可用性。
 */
public record BoundSupplyCountsPayload(BlockPos corePos, int status, List<Entry> entries)
        implements CustomPacketPayload {

    /** 单条目：条目键（数量恒 1）+ 仓储聚合数量。 */
    public record Entry(ItemStack key, long count) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC =
                StreamCodec.composite(
                        ItemStack.STREAM_CODEC, Entry::key,
                        ByteBufCodecs.VAR_LONG, Entry::count,
                        Entry::new);
    }

    public static final Type<BoundSupplyCountsPayload> TYPE =
            new Type<>(Gensokyou.id("bound_supply_counts"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BoundSupplyCountsPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, BoundSupplyCountsPayload::corePos,
                    ByteBufCodecs.VAR_INT, BoundSupplyCountsPayload::status,
                    Entry.STREAM_CODEC.apply(ByteBufCodecs.list()),
                    BoundSupplyCountsPayload::entries,
                    BoundSupplyCountsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
