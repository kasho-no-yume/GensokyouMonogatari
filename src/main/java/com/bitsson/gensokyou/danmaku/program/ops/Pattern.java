package com.bitsson.gensokyou.danmaku.program.ops;

import com.bitsson.gensokyou.danmaku.program.math.*;
import java.util.List;
import java.util.Set;

/** Ordered templates, never world entities. Implementations supply conservative bounds. */
public interface Pattern {
    record Style(String projectile, String visual, double collisionRadius, double damageScale, double visualRadius) {
        /** 实际判伤/碰墙半径；内容与存档保留基准半径，统一在使用处应用平衡倍率。 */
        public double effectiveCollisionRadius() {
            return collisionRadius * com.bitsson.gensokyou.balance.BalanceNumbers.PROGRAM_DANMAKU_COLLISION_RADIUS_SCALE;
        }
        public Style(String projectile,String visual,double collisionRadius,double damageScale) {
            this(projectile,visual,collisionRadius,damageScale,collisionRadius);
        }
        public static final Style DEFAULT = new Style("sphere","default",.2,1);
        public Style {
            if (projectile==null || projectile.isBlank() || visual==null || visual.isBlank()) throw new IllegalArgumentException("style identity required");
            Deterministic.nonnegative(collisionRadius); Deterministic.nonnegative(damageScale);
            Deterministic.nonnegative(visualRadius);
        }
    }
    record Member(int index, Vec3 position, Vec3 direction, Style style, Set<String> tags, int targetSlot) {
        public Member {
            if (index<0 || targetSlot < -1 || targetSlot>4) throw new IllegalArgumentException("invalid member identity");
            direction=direction.unit(); tags=Set.copyOf(tags); java.util.Objects.requireNonNull(position); java.util.Objects.requireNonNull(style);
        }
    }
    List<Member> generate(Deterministic.Seed seed);
    int countBound();
    double radiusBound();
}
