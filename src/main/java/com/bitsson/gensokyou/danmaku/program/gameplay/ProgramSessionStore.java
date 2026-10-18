package com.bitsson.gensokyou.danmaku.program.gameplay;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Per-dimension disk slot for the shared {@code ProgramSession}.
 *
 * <p>Verified against NeoForge 21.1.248 source: {@code ServerLevel.save} calls
 * {@code saveLevelData()} (which flushes {@code DimensionDataStorage}) <b>before</b> posting
 * {@code LevelEvent.Save}. A {@code setDirty} in that event would therefore be too late, so this
 * store instead holds a live {@link Capture}: {@link #save(CompoundTag, HolderLookup.Provider)}
 * encodes the current session at flush time. The host marks the store dirty whenever the session
 * advances, so a normal autosave/shutdown flush writes the live state.
 *
 * <p>{@link #pending} keeps the last encoded bytes; it is written unchanged when no live capture is
 * attached (for example right after a reload, before the owner opens), so a valid snapshot is never
 * replaced by an empty one until a real end ({@link #clear}) or a newer valid save.
 */
public final class ProgramSessionStore extends SavedData {
    private static final String NAME = "gensokyou_program_sessions";
    private static final String KEY = "snapshot";
    private static final String KEY_TICK = "tick";

    @FunctionalInterface
    public interface Capture { byte[] encode(); }

    private transient Capture capture;
    private byte[] pending;
    private long pendingTick = -1;

    public ProgramSessionStore() {}

    private static final SavedData.Factory<ProgramSessionStore> FACTORY =
            new SavedData.Factory<>(ProgramSessionStore::new, ProgramSessionStore::load);

    public static ProgramSessionStore get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    /** Attach the live encoder; the next data flush writes the session as it is then. */
    public void attach(Capture encoder) {
        this.capture = encoder;
        setDirty();
    }

    public void detach() { this.capture = null; }

    public byte[] pending() { return pending == null ? null : pending.clone(); }

    /** Store bytes without a live capture (used at unload as a last good snapshot). */
    public void putPending(byte[] bytes, long tick) {
        if (bytes == null || bytes.length == 0) return;
        this.pending = bytes.clone();
        this.pendingTick = tick;
        setDirty();
    }

    public void markDirty() { setDirty(); }

    /** A real end: the snapshot must not be restored. */
    public void clear() {
        boolean had = pending != null || capture != null;
        pending = null;
        pendingTick = -1;
        capture = null;
        if (had) setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        byte[] bytes = capture != null ? capture.encode() : pending;
        if (bytes != null && bytes.length > 0) {
            pending = bytes;
            tag.putByteArray(KEY, bytes);
            tag.putLong(KEY_TICK, pendingTick);
        }
        return tag;
    }

    static ProgramSessionStore load(CompoundTag tag, HolderLookup.Provider registries) {
        ProgramSessionStore data = new ProgramSessionStore();
        if (tag.contains(KEY, Tag.TAG_BYTE_ARRAY)) {
            byte[] bytes = tag.getByteArray(KEY);
            if (bytes.length > 0) {
                data.pending = bytes;
                data.pendingTick = tag.contains(KEY_TICK, Tag.TAG_LONG) ? tag.getLong(KEY_TICK) : -1;
            }
        }
        return data;
    }
}
