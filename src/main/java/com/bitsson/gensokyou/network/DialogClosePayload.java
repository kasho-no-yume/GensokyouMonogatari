package com.bitsson.gensokyou.network;

import com.bitsson.gensokyou.Gensokyou;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** C2S：对话界面关闭（ESC/点结束），服务端校验 npcId 匹配后销毁会话。 */
public record DialogClosePayload(int entityId) implements CustomPacketPayload {

    public static final Type<DialogClosePayload> TYPE =
            new Type<>(Gensokyou.id("dialog_close"));

    public static final StreamCodec<FriendlyByteBuf, DialogClosePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, DialogClosePayload::entityId,
                    DialogClosePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
