package com.bitsson.gensokyou.ritual.editor;

import com.bitsson.gensokyou.item.BuilderSelection;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 编辑杖的完整会话态（design D1）：锚定点 + 维度 + 所选仪式/阶级 + 按阶级记忆的工作区。
 * 全存于杖 Data Component：跟随物品自然丢失/复制，语义"这根杖即当前编辑会话"。
 */
public record EditorState(@Nullable BlockPos anchor, @Nullable ResourceLocation dimension,
                          @Nullable BuilderSelection selection, Map<Integer, Workspace> workspaces) {

    public static final EditorState EMPTY = new EditorState(null, null, null, Map.of());

    private record WorkspaceEntry(int level, Workspace workspace) {
        static final Codec<WorkspaceEntry> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        Codec.intRange(0, 255).fieldOf("level").forGetter(WorkspaceEntry::level),
                        Workspace.CODEC.fieldOf("workspace").forGetter(WorkspaceEntry::workspace)
                ).apply(instance, WorkspaceEntry::new));
    }

    private static final Codec<Map<Integer, Workspace>> WORKSPACES =
            WorkspaceEntry.CODEC.listOf().xmap(
                    list -> {
                        Map<Integer, Workspace> map = new LinkedHashMap<>();
                        list.forEach(e -> map.put(e.level(), e.workspace()));
                        return map;
                    },
                    map -> map.entrySet().stream()
                            .map(e -> new WorkspaceEntry(e.getKey(), e.getValue())).toList());

    public static final Codec<EditorState> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    BlockPos.CODEC.optionalFieldOf("anchor").forGetter(s -> Optional.ofNullable(s.anchor())),
                    ResourceLocation.CODEC.optionalFieldOf("dimension").forGetter(s -> Optional.ofNullable(s.dimension())),
                    BuilderSelection.CODEC.optionalFieldOf("selection").forGetter(s -> Optional.ofNullable(s.selection())),
                    WORKSPACES.optionalFieldOf("workspaces", Map.<Integer, Workspace>of())
                            .forGetter(EditorState::workspaces)
            ).apply(instance, (a, d, s, w) -> new EditorState(
                    a.orElse(null), d.orElse(null), s.orElse(null), w)));

    public static final StreamCodec<RegistryFriendlyByteBuf, EditorState> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public EditorState decode(RegistryFriendlyByteBuf buf) {
                    BlockPos anchor = buf.readBoolean() ? BlockPos.STREAM_CODEC.decode(buf) : null;
                    ResourceLocation dimension = buf.readBoolean()
                            ? ResourceLocation.STREAM_CODEC.decode(buf) : null;
                    BuilderSelection selection = buf.readBoolean()
                            ? BuilderSelection.STREAM_CODEC.decode(buf) : null;
                    int n = buf.readVarInt();
                    Map<Integer, Workspace> workspaces = new LinkedHashMap<>();
                    for (int i = 0; i < n; i++) {
                        workspaces.put(buf.readVarInt(), Workspace.STREAM_CODEC.decode(buf));
                    }
                    return new EditorState(anchor, dimension, selection, Map.copyOf(workspaces));
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, EditorState state) {
                    buf.writeBoolean(state.anchor() != null);
                    if (state.anchor() != null) {
                        BlockPos.STREAM_CODEC.encode(buf, state.anchor());
                    }
                    buf.writeBoolean(state.dimension() != null);
                    if (state.dimension() != null) {
                        ResourceLocation.STREAM_CODEC.encode(buf, state.dimension());
                    }
                    buf.writeBoolean(state.selection() != null);
                    if (state.selection() != null) {
                        BuilderSelection.STREAM_CODEC.encode(buf, state.selection());
                    }
                    buf.writeVarInt(state.workspaces().size());
                    state.workspaces().forEach((level, workspace) -> {
                        buf.writeVarInt(level);
                        Workspace.STREAM_CODEC.encode(buf, workspace);
                    });
                }
            };

    public boolean anchored() {
        return anchor != null;
    }

    public EditorState withAnchor(@Nullable BlockPos pos, @Nullable ResourceLocation dim) {
        return new EditorState(pos, dim, selection, workspaces);
    }

    public EditorState withSelection(@Nullable BuilderSelection sel) {
        return new EditorState(anchor, dimension, sel, workspaces);
    }

    public EditorState withWorkspace(int levelNumber, Workspace workspace) {
        Map<Integer, Workspace> next = new LinkedHashMap<>(workspaces);
        next.put(levelNumber, workspace);
        return new EditorState(anchor, dimension, selection, Map.copyOf(next));
    }

    public EditorState clearedAnchor() {
        return new EditorState(null, null, selection, workspaces);
    }

    /** 该阶级工作区：未配置则回退切片默认（调用方传 fallback），再无则单格。 */
    public Workspace workspaceFor(int level, @Nullable Workspace fallback) {
        Workspace stored = workspaces.get(level);
        return stored != null ? stored : (fallback != null ? fallback : Workspace.of(1, 1, 1, 0));
    }
}
