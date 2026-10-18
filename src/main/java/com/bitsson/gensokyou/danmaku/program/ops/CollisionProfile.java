package com.bitsson.gensokyou.danmaku.program.ops;

import java.util.Objects;

/** Typed gameplay collision description kept separate from visual style. */
public sealed interface CollisionProfile permits CollisionProfile.Projectile, CollisionProfile.Laser {
    /**
     * @param piercesBlocks when true the projectile skips block clipping entirely (spider
     *        threads etc.); it still hits entities. Mirrors the legacy {@code piercingSphere}.
     */
    record Projectile(boolean piercesBlocks) implements CollisionProfile {}

    record Laser(double maxLength, boolean piercesBlocks, int damageIntervalTicks) implements CollisionProfile {
        public Laser {
            if (!(maxLength > 0) || !Double.isFinite(maxLength)) throw new IllegalArgumentException("positive laser length required");
            if (damageIntervalTicks < 1) throw new IllegalArgumentException("positive laser damage interval required");
        }
    }

    static CollisionProfile projectile() { return new Projectile(false); }
    static CollisionProfile piercing() { return new Projectile(true); }
    static CollisionProfile require(CollisionProfile profile) { return Objects.requireNonNull(profile); }
}
