package com.bitsson.gensokyou.network;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.quest.QuestService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** An empty instance cancels tracking. Accept/submit/claim are exclusively dialogue session actions. */
public record QuestTrackPayload(String instance) implements CustomPacketPayload {
    public static final Type<QuestTrackPayload> TYPE = new Type<>(Gensokyou.id("quest_track"));
    public static final StreamCodec<RegistryFriendlyByteBuf, QuestTrackPayload> STREAM_CODEC =
            StreamCodec.ofMember((p, b) -> b.writeUtf(p.instance(), 64), b -> new QuestTrackPayload(b.readUtf(64)));
    public static void handle(QuestTrackPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) QuestService.track(player, payload.instance());
        });
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
