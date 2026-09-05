package com.bitsson.gensokyou.network;

import com.bitsson.gensokyou.Gensokyou;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** C2S：点击对话选项，服务端校验会话与索引合法性后推进。 */
public record DialogActionPayload(int entityId, int choiceIndex) implements CustomPacketPayload {

    public static final Type<DialogActionPayload> TYPE =
            new Type<>(Gensokyou.id("dialog_action"));

    public static final StreamCodec<FriendlyByteBuf, DialogActionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, DialogActionPayload::entityId,
                    ByteBufCodecs.VAR_INT, DialogActionPayload::choiceIndex,
                    DialogActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
