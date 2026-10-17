package com.bitsson.gensokyou.danmaku.program.ops;

import java.util.Objects;

/** Typed gameplay collision description kept separate from visual style. */
public sealed interface CollisionProfile permits CollisionProfile.Projectile, CollisionProfile.Laser {
    record Projectile() implements CollisionProfile {}

    record Laser(double maxLength, boolean piercesBlocks, int damageIntervalTicks) implements CollisionProfile {
        public Laser {
            if (!(maxLength > 0) || !Double.isFinite(maxLength)) throw new IllegalArgumentException("positive laser length required");
            if (damageIntervalTicks < 1) throw new IllegalArgumentException("positive laser damage interval required");
        }
    }

    static CollisionProfile projectile() { return new Projectile(); }
    static CollisionProfile require(CollisionProfile profile) { return Objects.requireNonNull(profile); }
}
