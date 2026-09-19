package com.bitsson.gensokyou.network;

import com.bitsson.gensokyou.Gensokyou;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** C2S：无尽藏晶界面的搜索 / 滚动 / 排序变更（sortMode 取绝对值，-1 = 不变）。 */
public record CrystalStorageNavPayload(int containerId, String query, int scrollDelta, int sortMode)
        implements CustomPacketPayload {

    public static final Type<CrystalStorageNavPayload> TYPE =
            new Type<>(Gensokyou.id("crystal_storage_nav"));

    public static final StreamCodec<FriendlyByteBuf, CrystalStorageNavPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, CrystalStorageNavPayload::containerId,
                    ByteBufCodecs.STRING_UTF8, CrystalStorageNavPayload::query,
                    ByteBufCodecs.VAR_INT, CrystalStorageNavPayload::scrollDelta,
                    ByteBufCodecs.VAR_INT, CrystalStorageNavPayload::sortMode,
                    CrystalStorageNavPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
