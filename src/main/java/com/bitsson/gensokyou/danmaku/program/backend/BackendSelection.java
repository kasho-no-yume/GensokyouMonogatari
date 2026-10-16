package com.bitsson.gensokyou.danmaku.program.backend;

import java.util.List;
import java.util.UUID;
import com.bitsson.gensokyou.danmaku.program.ProgramSchedule;

/** Immutable encounter-time selection. Shadow observers receive values only. */
public record BackendSelection(UUID encounterEpoch, Backend authoritative, boolean observeShadow) {
    public enum Backend { LEGACY_ENTITY, PROGRAM_DATA }
    public BackendSelection {
        java.util.Objects.requireNonNull(encounterEpoch);
        java.util.Objects.requireNonNull(authoritative);
    }
    public record ShadowSample(UUID epoch, String programHash, long serverTick,
                               List<ProgramSchedule.Event> predicted) {
        public ShadowSample { predicted = List.copyOf(predicted); }
    }
    @FunctionalInterface
    public interface ShadowObserver {
        void observe(ShadowSample sample);
    }
}
