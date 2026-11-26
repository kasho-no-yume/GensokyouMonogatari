package com.bitsson.gensokyou.network;

import com.bitsson.gensokyou.Gensokyou;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record QuestSyncPayload(String json, boolean openBook) implements CustomPacketPayload {
    public static final Type<QuestSyncPayload> TYPE = new Type<>(Gensokyou.id("quest_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, QuestSyncPayload> STREAM_CODEC =
            StreamCodec.ofMember(QuestSyncPayload::write, QuestSyncPayload::read);
    private void write(RegistryFriendlyByteBuf buf) { buf.writeUtf(json, 262144); buf.writeBoolean(openBook); }
    private static QuestSyncPayload read(RegistryFriendlyByteBuf buf) { return new QuestSyncPayload(buf.readUtf(262144), buf.readBoolean()); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
