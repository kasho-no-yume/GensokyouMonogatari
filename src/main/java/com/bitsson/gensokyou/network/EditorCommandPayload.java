package com.bitsson.gensokyou.network;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.ritual.editor.Workspace;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/** C2S 编辑杖命令：动作 + 载荷（选择用 patternId/level，设工作区用 level/workspace）。 */
public record EditorCommandPayload(int action, @Nullable ResourceLocation patternId, int level,
                                   @Nullable Workspace workspace) implements CustomPacketPayload {

    public static final int ACTION_SELECT = 0;
    public static final int ACTION_SET_WORKSPACE = 1;
    public static final int ACTION_CAPTURE_DRAFT = 2;
    public static final int ACTION_SAVE_RITUAL = 3;
    public static final int ACTION_CLEAR_ANCHOR = 4;

    public static final Type<EditorCommandPayload> TYPE =
            new Type<>(Gensokyou.id("editor_command"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EditorCommandPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public EditorCommandPayload decode(RegistryFriendlyByteBuf buf) {
                    int action = buf.readUnsignedByte();
                    ResourceLocation patternId = buf.readBoolean()
                            ? ResourceLocation.STREAM_CODEC.decode(buf) : null;
                    int level = buf.readVarInt();
                    Workspace workspace = buf.readBoolean()
                            ? Workspace.STREAM_CODEC.decode(buf) : null;
                    return new EditorCommandPayload(action, patternId, level, workspace);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, EditorCommandPayload payload) {
                    buf.writeByte(payload.action());
                    buf.writeBoolean(payload.patternId() != null);
                    if (payload.patternId() != null) {
                        ResourceLocation.STREAM_CODEC.encode(buf, payload.patternId());
                    }
                    buf.writeVarInt(payload.level());
                    buf.writeBoolean(payload.workspace() != null);
                    if (payload.workspace() != null) {
                        Workspace.STREAM_CODEC.encode(buf, payload.workspace());
                    }
                }
            };

    public static EditorCommandPayload select(ResourceLocation patternId, int level) {
        return new EditorCommandPayload(ACTION_SELECT, patternId, level, null);
    }

    public static EditorCommandPayload setWorkspace(int level, Workspace workspace) {
        return new EditorCommandPayload(ACTION_SET_WORKSPACE, null, level, workspace);
    }

    public static EditorCommandPayload capture() {
        return new EditorCommandPayload(ACTION_CAPTURE_DRAFT, null, 0, null);
    }

    public static EditorCommandPayload save() {
        return new EditorCommandPayload(ACTION_SAVE_RITUAL, null, 0, null);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
