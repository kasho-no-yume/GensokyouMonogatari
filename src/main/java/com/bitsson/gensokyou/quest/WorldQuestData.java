package com.bitsson.gensokyou.quest;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.List;

/** Stored in the overworld so all dimensions and players share the same linear mainline cursor. */
public final class WorldQuestData extends SavedData {
    public static final String NAME = "gensokyou_quests";
    public static final Factory<WorldQuestData> FACTORY = new Factory<>(WorldQuestData::new, WorldQuestData::load, null);
    private long epoch;
    private int cursor;
    private String fingerprint = "";
    private List<Integer> progress = List.of();
    public long epoch() { return epoch; }
    public int cursor() { return cursor; }
    public List<Integer> progress() { return progress; }
    public void reload() { epoch++; setDirty(); }
    public void reconcile(String currentFingerprint, int count) {
        if (!fingerprint.equals(currentFingerprint) || progress.size() != count) {
            fingerprint = currentFingerprint;
            progress = java.util.Collections.nCopies(count, 0);
            setDirty();
        }
    }
    public void update(List<Integer> next, boolean advance) {
        if (advance) { cursor++; fingerprint = ""; progress = List.of(); setDirty(); }
        else if (!progress.equals(next)) { progress = List.copyOf(next); setDirty(); }
    }
    public static WorldQuestData load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new WorldQuestData();
        data.epoch = tag.getLong("epoch");
        data.cursor = Math.max(0, tag.getInt("cursor"));
        data.fingerprint = tag.getString("fingerprint");
        data.progress = java.util.Arrays.stream(tag.getIntArray("progress")).boxed().toList();
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong("epoch", epoch);
        tag.putInt("cursor", cursor);
        tag.putString("fingerprint", fingerprint);
        tag.putIntArray("progress", progress.stream().mapToInt(Integer::intValue).toArray());
        return tag;
    }
}
