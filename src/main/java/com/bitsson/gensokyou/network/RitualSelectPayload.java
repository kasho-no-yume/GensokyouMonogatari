package com.bitsson.gensokyou.network;

import com.bitsson.gensokyou.Gensokyou;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** C2S：构建器菜单选定图案 + 品阶，服务端校验后写回手上构建器组件。 */
public record RitualSelectPayload(ResourceLocation patternId, int tier) implements CustomPacketPayload {

    public static final Type<RitualSelectPayload> TYPE =
            new Type<>(Gensokyou.id("ritual_select"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RitualSelectPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC, RitualSelectPayload::patternId,
                    ByteBufCodecs.VAR_INT, RitualSelectPayload::tier,
                    RitualSelectPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
