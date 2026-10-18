package com.bitsson.gensokyou.danmaku.program.gameplay;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Store semantics without a live server: never overwrite a good snapshot with empty until a real end. */
class ProgramSessionStoreTest {
    @Test void keepsTheLastSnapshotAcrossFlushesUntilCleared() {
        var store = new ProgramSessionStore();
        byte[] snapshot = {1, 2, 3};
        store.putPending(snapshot, 10);
        var tag = new CompoundTag();
        store.save(tag, null);
        var loaded = ProgramSessionStore.load(tag, null);
        assertArrayEquals(snapshot, loaded.pending());

        // No live capture yet (for example just after reload): the pending bytes are written unchanged.
        var next = new CompoundTag();
        loaded.save(next, null);
        assertArrayEquals(snapshot, ProgramSessionStore.load(next, null).pending());

        // A real end clears it, so the next flush writes nothing and nothing is restored.
        loaded.clear();
        var ended = new CompoundTag();
        loaded.save(ended, null);
        assertNull(ProgramSessionStore.load(ended, null).pending());
    }

    @Test void liveCaptureEncodesAtFlushTime() {
        var store = new ProgramSessionStore();
        byte[] live = {9, 9, 9};
        store.attach(() -> live);
        var tag = new CompoundTag();
        store.save(tag, null);
        assertArrayEquals(live, ProgramSessionStore.load(tag, null).pending());
    }

    @Test void corruptStoreDegradesToEmpty() {
        var tag = new CompoundTag();
        tag.putString("snapshot", "not-bytes");
        assertNull(ProgramSessionStore.load(tag, null).pending());
    }
}
