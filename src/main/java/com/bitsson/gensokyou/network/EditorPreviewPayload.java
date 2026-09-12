package com.bitsson.gensokyou.network;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.ritual.editor.EditorPreviewState;
import com.bitsson.gensokyou.ritual.editor.Workspace;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/**
 * S2C 编辑杖力建预览：有值 = 置入三色投影（客户端持合成 pattern JSON 每帧本地重算），
 * null = 清除。composedJson 为"原 pattern ⊕ 草稿"合成体（无草稿时为 null，客户端直接读 loader）。
 */
public record EditorPreviewPayload(@Nullable ResourceLocation patternId, int level,
                                   @Nullable BlockPos anchor, @Nullable Workspace workspace,
                                   @Nullable ResourceLocation dimension, @Nullable String composedJson)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<EditorPreviewPayload> TYPE =
            new Type<>(Gensokyou.id("editor_preview"));

    public static final EditorPreviewPayload CLEAR =
            new EditorPreviewPayload(null, 0, null, null, null, null);

    public static final StreamCodec<RegistryFriendlyByteBuf, EditorPreviewPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public EditorPreviewPayload decode(RegistryFriendlyByteBuf buf) {
                    if (!buf.readBoolean()) {
                        return CLEAR;
                    }
                    ResourceLocation patternId = ResourceLocation.STREAM_CODEC.decode(buf);
                    int level = buf.readVarInt();
                    BlockPos anchor = BlockPos.STREAM_CODEC.decode(buf);
                    Workspace workspace = Workspace.STREAM_CODEC.decode(buf);
                    ResourceLocation dimension = ResourceLocation.STREAM_CODEC.decode(buf);
                    String json = readBigString(buf);
                    return new EditorPreviewPayload(patternId, level, anchor, workspace, dimension,
                            json.isEmpty() ? null : json);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, EditorPreviewPayload payload) {
                    if (payload.patternId() == null) {
                        buf.writeBoolean(false);
                        return;
                    }
                    buf.writeBoolean(true);
                    ResourceLocation.STREAM_CODEC.encode(buf, payload.patternId());
                    buf.writeVarInt(payload.level());
                    BlockPos.STREAM_CODEC.encode(buf, payload.anchor());
                    Workspace.STREAM_CODEC.encode(buf, payload.workspace());
                    ResourceLocation.STREAM_CODEC.encode(buf, payload.dimension());
                    writeBigString(buf, payload.composedJson() == null ? "" : payload.composedJson());
                }
            };

    /** varint 长度 + UTF-8 字节：绕开 writeUtf 32767 字符上限（大 pattern 合成 JSON 可达数十 KB）。 */
    private static void writeBigString(RegistryFriendlyByteBuf buf, String value) {
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.writeVarInt(bytes.length);
        buf.writeBytes(bytes);
    }

    private static String readBigString(RegistryFriendlyByteBuf buf) {
        int len = buf.readVarInt();
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    public static EditorPreviewPayload of(EditorPreviewState state, @Nullable String composedJson) {
        return new EditorPreviewPayload(state.patternId(), state.level(), state.anchor(),
                state.workspace(), state.dimension().location(), composedJson);
    }

    public EditorPreviewState toState() {
        return new EditorPreviewState(patternId, level, anchor, workspace,
                net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.DIMENSION, dimension));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
