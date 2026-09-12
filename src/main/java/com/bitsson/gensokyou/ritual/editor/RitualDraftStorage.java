package com.bitsson.gensokyou.ritual.editor;

import com.bitsson.gensokyou.Gensokyou;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 世界级仪式草稿存储（design D3/D5）：按 {@code patternId + "/" + level} 存某阶 adds 草稿。
 * 阶级保存 = 写此（不校验不落盘）；仪式保存成功 = 清除该 pattern 全部阶草稿。
 * 允许仪式长期处于"施工中"残缺态——草稿只影响编辑器的合成视图，不碰已加载 pattern。
 */
public final class RitualDraftStorage {

    /** saved data 内一条记录：patternId + level → 草稿 JSON。 */
    private record Entry(String patternId, int level, String draftJson) {
    }

    public static final class Data extends SavedData {
        static final Codec<Data> CODEC = Data.makeCodec();
        private final List<Entry> entries = new ArrayList<>();

        private static Codec<Data> makeCodec() {
            Codec<Entry> entry = RecordCodecBuilder.create(i -> i.group(
                    Codec.STRING.fieldOf("pattern").forGetter(Entry::patternId),
                    Codec.INT.fieldOf("level").forGetter(Entry::level),
                    Codec.STRING.fieldOf("draft").forGetter(Entry::draftJson)
            ).apply(i, Entry::new));
            return entry.listOf().xmap(list -> {
                Data d = new Data();
                d.entries.addAll(list);
                return d;
            }, d -> d.entries);
        }

        @Override
        public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
            // 走 CODEC（List<Entry> 序列化）最稳
            Data saved = this;
            var result = CODEC.encodeStart(registries.createSerializationContext(
                    net.minecraft.nbt.NbtOps.INSTANCE), saved).resultOrPartial(msg ->
                    Gensokyou.LOGGER.warn("Failed to encode ritual drafts: {}", msg));
            result.ifPresent(nbt -> tag.put("drafts", nbt));
            return tag;
        }

        static Data load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
            Data data = new Data();
            if (tag.contains("drafts")) {
                CODEC.decode(registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE),
                        tag.get("drafts")).resultOrPartial(err ->
                        Gensokyou.LOGGER.warn("Failed to decode ritual drafts: {}", err))
                        .ifPresent(pair -> data.entries.addAll(pair.getFirst().entries));
            }
            return data;
        }
    }

    private static final SavedData.Factory<Data> FACTORY =
            new SavedData.Factory<>(Data::new, Data::load);

    private RitualDraftStorage() {
    }

    private static Data data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, "gensokyou_ritual_drafts");
    }

    private static String key(ResourceLocation patternId, int levelNumber) {
        return patternId + "/" + levelNumber;
    }

    /** 写入/覆盖某阶草稿（序列化为 JSON 字符串）。 */
    public static void saveDraft(ServerLevel level, ResourceLocation patternId, int levelNumber, RitualDraft draft) {
        Data data = data(level);
        data.entries.removeIf(e -> e.patternId().equals(patternId.toString()) && e.level() == levelNumber);
        data.entries.add(new Entry(patternId.toString(), levelNumber, draft.toJson()));
        data.setDirty();
    }

    public static @Nullable RitualDraft draft(ServerLevel level, ResourceLocation patternId, int levelNumber) {
        for (Entry e : data(level).entries) {
            if (e.patternId().equals(patternId.toString()) && e.level() == levelNumber) {
                return RitualDraft.fromJson(e.draftJson());
            }
        }
        return null;
    }

    /** 该 pattern 的全部草稿（level → draft），合成视图用。 */
    public static Map<Integer, RitualDraft> draftsOf(ServerLevel level, ResourceLocation patternId) {
        Map<Integer, RitualDraft> out = new LinkedHashMap<>();
        for (Entry e : data(level).entries) {
            if (e.patternId().equals(patternId.toString())) {
                out.put(e.level(), RitualDraft.fromJson(e.draftJson()));
            }
        }
        return out;
    }

    /** 仪式保存成功后清除该 pattern 全部草稿。 */
    public static void clearDrafts(ServerLevel level, ResourceLocation patternId) {
        Data data = data(level);
        boolean removed = data.entries.removeIf(e -> e.patternId().equals(patternId.toString()));
        if (removed) {
            data.setDirty();
        }
    }
}
