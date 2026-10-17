package com.bitsson.gensokyou.danmaku.program.gameplay;

import com.bitsson.gensokyou.danmaku.program.ops.Path;
import com.bitsson.gensokyou.danmaku.program.server.DanmakuWorldRuntime;

import java.util.Objects;

/** Immutable server-side input for one program member collision interval. */
public record ProgramCollisionSample(
        DanmakuWorldRuntime.Encounter encounter,
        DanmakuWorldRuntime.MemberId member,
        long startTick,
        long endTick,
        Path.State from,
        Path.State until,
        double collisionRadius,
        double damageScale,
        boolean damaging,
        boolean visible,
        Profile profile,
        double laserLength,
        boolean piercesBlocks,
        int damageIntervalTicks) {
    public enum Profile { PROJECTILE, LASER }

    public ProgramCollisionSample {
        Objects.requireNonNull(encounter);
        Objects.requireNonNull(member);
        Objects.requireNonNull(from);
        Objects.requireNonNull(until);
        Objects.requireNonNull(profile);
        if (startTick < 0 || endTick != startTick + 1) throw new IllegalArgumentException("one tick interval required");
        if (collisionRadius < 0 || damageScale < 0 || laserLength < 0 || damageIntervalTicks < 1) throw new IllegalArgumentException("invalid collision value");
        if (profile == Profile.LASER && laserLength <= 0) throw new IllegalArgumentException("laser length required");
    }
}
