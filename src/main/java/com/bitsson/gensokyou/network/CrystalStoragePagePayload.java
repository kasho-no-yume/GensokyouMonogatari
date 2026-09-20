package com.bitsson.gensokyou.network;

import com.bitsson.gensokyou.Gensokyou;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C：无尽藏晶可见页快照（图标 + 长计数），随导航与内容变更重推。
 *
 * <p>{@code mode} 为存储模式（{@link #MODE_TYPED}=类型制 / {@link #MODE_TOTAL}=总量制）；
 * 底部计数由 {@code used}/{@code limit} 承载，其含义随模式而变（类型数/类型上限 或 件数/总量上限），
 * 均以 long 传输避免溢出。
 */
public record CrystalStoragePagePayload(int containerId, int offset, int entryCount,
                                        int mode, long used, long limit, int sortMode,
                                        List<View> views) implements CustomPacketPayload {

    public static final int MODE_TYPED = 0;
    public static final int MODE_TOTAL = 1;

    /** 单个可见格：类型键 + 真实计数。 */
    public record View(ItemStack stack, long count) {
    }

    public static final Type<CrystalStoragePagePayload> TYPE =
            new Type<>(Gensokyou.id("crystal_storage_page"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CrystalStoragePagePayload> STREAM_CODEC =
            StreamCodec.ofMember(CrystalStoragePagePayload::write, CrystalStoragePagePayload::read);

    private static void write(CrystalStoragePagePayload payload, RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(payload.containerId);
        buf.writeVarInt(payload.offset);
        buf.writeVarInt(payload.entryCount);
        buf.writeVarInt(payload.mode);
        buf.writeVarLong(payload.used);
        buf.writeVarLong(payload.limit);
        buf.writeVarInt(payload.sortMode);
        buf.writeVarInt(payload.views.size());
        for (View view : payload.views) {
            ItemStack.STREAM_CODEC.encode(buf, view.stack());
            buf.writeVarLong(view.count());
        }
    }

    private static CrystalStoragePagePayload read(RegistryFriendlyByteBuf buf) {
        int containerId = buf.readVarInt();
        int offset = buf.readVarInt();
        int entryCount = buf.readVarInt();
        int mode = buf.readVarInt();
        long used = buf.readVarLong();
        long limit = buf.readVarLong();
        int sortMode = buf.readVarInt();
        int size = buf.readVarInt();
        List<View> views = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ItemStack stack = ItemStack.STREAM_CODEC.decode(buf);
            long count = buf.readVarLong();
            views.add(new View(stack, count));
        }
        return new CrystalStoragePagePayload(containerId, offset, entryCount, mode, used, limit,
                sortMode, List.copyOf(views));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
