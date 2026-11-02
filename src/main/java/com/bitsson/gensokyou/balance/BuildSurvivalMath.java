package com.bitsson.gensokyou.balance;

import java.util.Comparator;
import java.util.List;

/**
 * Explicit survival timeline: no food healing, avoidance roll or hidden shield is assumed.
 * Shield expiry is explicit; this model does not infer buff duration or resource affordability.
 */
public final class BuildSurvivalMath {
    private BuildSurvivalMath() {
    }

    public record Event(double time, double damage, double healing, int shieldGranted,
                        double shieldExpiresAt) {
        public Event(double time, double damage, double healing, int shieldGranted) {
            this(time, damage, healing, shieldGranted, Double.POSITIVE_INFINITY);
        }

        public Event {
            if (!Double.isFinite(time) || time < 0D || !Double.isFinite(damage) || damage < 0D
                    || !Double.isFinite(healing) || healing < 0D || shieldGranted < 0
                    || Double.isNaN(shieldExpiresAt) || shieldExpiresAt < time) {
                throw new IllegalArgumentException("Invalid survival event");
            }
        }
    }

    public record Result(boolean survived, double livedSeconds, double health,
                         int hits, int blocked, double effectiveHealing, double overhealing) {
    }

    /** Equal-time grant/heal precedes damage: this is the optimistic ordering bound. */
    public static Result simulate(double maximumHealth, List<Event> timeline, double duration) {
        if (!Double.isFinite(maximumHealth) || maximumHealth <= 0D
                || !Double.isFinite(duration) || duration < 0D) {
            throw new IllegalArgumentException("Invalid survival window");
        }
        List<Event> sorted = timeline.stream().sorted(Comparator.comparingDouble(Event::time)
                .thenComparingDouble(Event::damage)).toList();
        double hp = maximumHealth;
        double healed = 0D;
        double over = 0D;
        int shield = 0;
        double shieldExpiry = 0D;
        int hits = 0;
        int blocked = 0;
        for (Event event : sorted) {
            if (event.time() > duration) {
                throw new IllegalArgumentException("Event outside survival window");
            }
            // Shield replacement matches withFlowerArmor; zero means no grant, not removal.
            if (event.time() >= shieldExpiry) {
                shield = 0;
            }
            if (event.shieldGranted() > 0) {
                shield = event.shieldGranted();
                shieldExpiry = event.shieldExpiresAt();
            }
            double gain = Math.min(maximumHealth - hp, event.healing());
            healed += gain;
            over += event.healing() - gain;
            hp += gain;
            if (event.damage() > 0D) {
                hits++;
                if (shield > 0) {
                    shield--;
                    blocked++;
                } else {
                    hp -= event.damage();
                    if (hp <= 0D) {
                        return new Result(false, event.time(), 0D, hits, blocked, healed, over);
                    }
                }
            }
        }
        return new Result(true, duration, hp, hits, blocked, healed, over);
    }
}
