package com.bitsson.gensokyou.danmaku.program.gameplay;

import com.bitsson.gensokyou.danmaku.program.server.DanmakuWorldRuntime;
import net.minecraft.world.entity.Entity;

import java.util.Objects;

/** Diagnostic context for a hit caused by a data-backed program member. */
public record ProgramHitContext(
        DanmakuWorldRuntime.Encounter encounter,
        DanmakuWorldRuntime.MemberId member,
        Entity attacker,
        Entity target,
        long tick,
        double damage,
        double damageScale,
        double distance,
        int targetSlot,
        ProgramCollisionSample.Profile profile) {
    public ProgramHitContext {
        Objects.requireNonNull(encounter);
        Objects.requireNonNull(member);
        Objects.requireNonNull(attacker);
        Objects.requireNonNull(target);
        Objects.requireNonNull(profile);
        if (tick < 0 || damage < 0 || damageScale < 0 || distance < 0) {
            throw new IllegalArgumentException("invalid hit context");
        }
    }
}
