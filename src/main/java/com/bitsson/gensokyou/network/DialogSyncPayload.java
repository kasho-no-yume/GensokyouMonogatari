package com.bitsson.gensokyou.network;

import com.bitsson.gensokyou.Gensokyou;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C：对话节点同步（开启与推进共用）。
 * nodeId 为空串 = 关闭对话界面的哨兵包。
 */
public record DialogSyncPayload(int entityId, String nodeId, Component text,
                                List<Option> options) implements CustomPacketPayload {

    /** 选项视图：next/action 空串 = 无。 */
    public record Option(Component label, String next, String action) {
    }

    public static final Type<DialogSyncPayload> TYPE =
            new Type<>(Gensokyou.id("dialog_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DialogSyncPayload> STREAM_CODEC =
            StreamCodec.ofMember(DialogSyncPayload::write, DialogSyncPayload::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(this.entityId);
        buf.writeUtf(this.nodeId);
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, this.text);
        buf.writeVarInt(this.options.size());
        for (Option option : this.options) {
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, option.label());
            buf.writeUtf(option.next());
            buf.writeUtf(option.action());
        }
    }

    private static DialogSyncPayload read(RegistryFriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        String nodeId = buf.readUtf();
        Component text = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
        int count = buf.readVarInt();
        List<Option> options = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Component label = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
            options.add(new Option(label, buf.readUtf(), buf.readUtf()));
        }
        return new DialogSyncPayload(entityId, nodeId, text, options);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
