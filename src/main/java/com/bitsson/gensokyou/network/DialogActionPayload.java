package com.bitsson.gensokyou.network;

import com.bitsson.gensokyou.Gensokyou;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** C2S：点击对话选项，服务端校验会话与索引合法性后推进。 */
public record DialogActionPayload(int entityId, long revision, int choiceIndex) implements CustomPacketPayload {

    public static final Type<DialogActionPayload> TYPE =
            new Type<>(Gensokyou.id("dialog_action"));

    public static final StreamCodec<FriendlyByteBuf, DialogActionPayload> STREAM_CODEC =
            StreamCodec.ofMember((p, b) -> {
                b.writeVarInt(p.entityId()); b.writeVarLong(p.revision()); b.writeVarInt(p.choiceIndex());
            }, b -> new DialogActionPayload(b.readVarInt(), b.readVarLong(), b.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
