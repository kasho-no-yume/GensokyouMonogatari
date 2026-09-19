package com.bitsson.gensokyou.network;

import com.bitsson.gensokyou.Gensokyou;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/** C2S：无尽藏晶网格手势（携带被点条目的类型键，服务端按组件定位防错扣）。 */
public record CrystalStorageClickPayload(int containerId, int action, ItemStack key)
        implements CustomPacketPayload {

    public static final int TAKE_STACK = 0;
    public static final int TAKE_HALF = 1;
    public static final int TAKE_STACK_INV = 2;
    public static final int TAKE_ALL_INV = 3;
    public static final int PUT_ONE = 4;
    public static final int PUT_ALL = 5;

    public static final Type<CrystalStorageClickPayload> TYPE =
            new Type<>(Gensokyou.id("crystal_storage_click"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CrystalStorageClickPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, CrystalStorageClickPayload::containerId,
                    ByteBufCodecs.VAR_INT, CrystalStorageClickPayload::action,
                    ItemStack.OPTIONAL_STREAM_CODEC, CrystalStorageClickPayload::key,
                    CrystalStorageClickPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
