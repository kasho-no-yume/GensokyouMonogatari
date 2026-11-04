package com.bitsson.gensokyou.balance;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.function.Consumer;

/**
 * World-independent calibration math. No production values or gameplay are changed here.
 * Inputs are explicit so proposed budgets can be compared with current behavior.
 */
public final class BuildBudgetMath {
    private BuildBudgetMath() {
    }

    /** A fixed reference, not the actual player's equipped damage. */
    public static double referenceDps(double standardPower, double weaponMultiplier,
                                      double damagePerTrigger, double triggersPerSecond) {
        return nonnegative(standardPower) * nonnegative(weaponMultiplier)
                * nonnegative(damagePerTrigger) * nonnegative(triggersPerSecond);
    }

    public static double criticalExpectation(double chance, double totalMultiplier) {
        return 1D + probability(chance) * (Math.max(1D, finite(totalMultiplier)) - 1D);
    }

    public static double weaponDps(double referenceDps, double damageBonus, double speedBonus,
                                   double critChance, double critTotalMultiplier) {
        return nonnegative(referenceDps) * (1D + nonnegative(damageBonus))
                * (1D + nonnegative(speedBonus))
                * criticalExpectation(critChance, critTotalMultiplier);
    }

    /** Includes all pulses delivered by one trigger, not just one laser pulse. */
    public static double damageFactor(double damagePerHit, int hitsPerTrigger,
                                      double intervalTicks) {
        if (hitsPerTrigger < 1 || finite(intervalTicks) <= 0D) {
            throw new IllegalArgumentException("Invalid trigger definition");
        }
        return nonnegative(damagePerHit) * hitsPerTrigger * 20D / intervalTicks;
    }

    /** 目标减伤 r → 护壁指数增量 ΔP = -log2(1-r)。 */
    public static double wardExponent(double mitigation) {
        double r = Math.min(0.95D, Math.max(0D, mitigation));
        return -Math.log(1D - r) / Math.log(2D);
    }

    public static double wardSpan(double durabilityRatio) {
        if (finite(durabilityRatio) < 1D) {
            throw new IllegalArgumentException("Durability ratio must be at least one");
        }
        return Math.log(durabilityRatio) / Math.log(2D);
    }

    public static double effectiveHealth(double health, double ward, double grazeChance) {
        double graze = probability(grazeChance);
        if (graze == 1D) {
            throw new IllegalArgumentException("Certain avoidance has no finite EHP");
        }
        return nonnegative(health) * Math.pow(2D, nonnegative(ward)) / (1D - graze);
    }

    /** Direct hits needed to kill, without random avoidance, recovery or shields. */
    public static long lethalHits(double health, double rawDamage, double ward) {
        double taken = nonnegative(rawDamage) * Math.pow(2D, -nonnegative(ward));
        if (taken <= 0D) {
            throw new IllegalArgumentException("Damage must be positive");
        }
        // Log/pow roundoff must not turn an exact 30-hit threshold into 31 hits.
        return (long) Math.ceil(Math.nextDown(nonnegative(health) / taken));
    }

    public static double lowBiasedQuantile(double quantile, double exponent) {
        if (finite(exponent) <= 0D) {
            throw new IllegalArgumentException("Exponent must be positive");
        }
        return Math.pow(probability(quantile), exponent);
    }

    public static double upperTailProbability(double normalizedThreshold, double exponent) {
        if (finite(exponent) <= 0D) {
            throw new IllegalArgumentException("Exponent must be positive");
        }
        return 1D - Math.pow(probability(normalizedThreshold), 1D / exponent);
    }

    public static double discountedCost(double baseCost, double reduction) {
        return nonnegative(baseCost) * (1D - probability(reduction));
    }

    /** Pool and all rates use absolute spirit units; costs do not scale with actual pool. */
    public record ResourcePlan(double initialSpirit, double capacity, double regenPerSecond,
                               double leechRate, double leechCapPerSecond) {
        public ResourcePlan {
            nonnegative(initialSpirit);
            nonnegative(capacity);
            nonnegative(regenPerSecond);
            probability(leechRate);
            nonnegative(leechCapPerSecond);
            if (initialSpirit > capacity || leechRate < 0D || leechRate > 1D) {
                throw new IllegalArgumentException("Invalid resource plan");
            }
        }
    }

    /** One delivered trigger; damage includes all hits/pulses assumed to connect. */
    public record Action(double atSeconds, double cost, double damage, boolean weapon,
                         double pulseInterval, double pulseDuration, boolean effect) {
        public Action(double atSeconds, double cost, double damage, boolean weapon) {
            this(atSeconds, cost, damage, weapon, 0D, 0D, false);
        }

        public Action {
            nonnegative(atSeconds);
            nonnegative(cost);
            nonnegative(damage);
            nonnegative(pulseInterval);
            nonnegative(pulseDuration);
            if ((pulseInterval == 0D) != (pulseDuration == 0D)) {
                throw new IllegalArgumentException("Periodic interval and duration must both be positive");
            }
        }
    }

    public record ResourceResult(int accepted, int starved, double damage, double spent,
                                 double regenerated, double leeched, double finalSpirit,
                                 double durationSeconds) {
        public double sustainedDps() {
            return durationSeconds == 0D ? 0D : damage / durationSeconds;
        }
    }

    /**
     * Event simulation: starvation skips actions, never borrows future spirit.
     * Recovery is continuous; weapon leech is limited per fixed one-second bucket.
     * Paid periodic casts enqueue future pulses only within the simulation window.
     * This is a declared calibration approximation, not the game's tick ledger.
     * No consumables, hit delay, natural health healing or external supply are implied.
     */
    public static ResourceResult simulate(ResourcePlan plan, Iterable<Action> actions,
                                           double durationSeconds) {
        return simulateUntilDamage(plan, actions, durationSeconds, Double.POSITIVE_INFINITY);
    }

    /** Stops on the actual paid hit/pulse crossing the target, not average-DPS extrapolation. */
    public static ResourceResult simulateUntilDamage(ResourcePlan plan, Iterable<Action> actions,
                                                      double durationSeconds, double targetDamage) {
        return simulateUntilDamage(plan, actions, durationSeconds, targetDamage, action -> { });
    }

    /** Observer receives successful paid actions only, enabling coupled survival timelines. */
    public static ResourceResult simulateUntilDamage(ResourcePlan plan, Iterable<Action> actions,
                                                      double durationSeconds, double targetDamage,
                                                      Consumer<Action> acceptedAction) {
        nonnegative(durationSeconds);
        if (Double.isNaN(targetDamage) || targetDamage <= 0D) {
            throw new IllegalArgumentException("Target damage must be positive");
        }
        double spirit = plan.initialSpirit();
        double previous = 0D;
        double damage = 0D;
        double spent = 0D;
        double regenerated = 0D;
        double leeched = 0D;
        double bucketUsed = 0D;
        long bucket = -1L;
        int accepted = 0;
        int starved = 0;
        var queue = new PriorityQueue<Action>(Comparator.comparingDouble(Action::atSeconds)
                .thenComparing(Action::effect).thenComparing(Action::weapon));
        double lastInputTime = 0D;
        for (Action action : actions) {
            if (action.atSeconds() < lastInputTime || action.atSeconds() > durationSeconds) {
                throw new IllegalArgumentException("Actions must be ordered and within the window");
            }
            lastInputTime = action.atSeconds();
            queue.add(action);
        }
        while (!queue.isEmpty()) {
            Action action = queue.remove();
            double gain = Math.min(plan.capacity() - spirit,
                    (action.atSeconds() - previous) * plan.regenPerSecond());
            spirit += gain;
            regenerated += gain;
            previous = action.atSeconds();
            long currentBucket = (long) Math.floor(previous);
            if (currentBucket != bucket) {
                bucket = currentBucket;
                bucketUsed = 0D;
            }
            if (spirit < action.cost()) {
                starved++;
                continue;
            }
            if (!action.effect()) {
                accepted++;
                acceptedAction.accept(action);
            }
            spirit -= action.cost();
            spent += action.cost();
            if (action.pulseInterval() == 0D) {
                damage += action.damage();
            } else {
                // Only a paid cast creates damage pulses; unfinished lifetime is not pre-credited.
                int pulseCount = (int) Math.floor(action.pulseDuration() / action.pulseInterval());
                for (int pulse = 1; pulse <= pulseCount; pulse++) {
                    double at = action.atSeconds() + pulse * action.pulseInterval();
                    if (at <= durationSeconds) {
                        queue.add(new Action(at, 0D, action.damage(), false, 0D, 0D, true));
                    }
                }
            }
            if (action.weapon()) {
                double leech = Math.min(plan.capacity() - spirit,
                        Math.min(action.damage() * plan.leechRate(),
                                Math.max(0D, plan.leechCapPerSecond() - bucketUsed)));
                spirit += leech;
                leeched += leech;
                bucketUsed += leech;
            }
            if (damage >= targetDamage) {
                return new ResourceResult(accepted, starved, damage, spent, regenerated,
                        leeched, spirit, action.atSeconds());
            }
        }
        double gain = Math.min(plan.capacity() - spirit,
                (durationSeconds - previous) * plan.regenPerSecond());
        spirit += gain;
        regenerated += gain;
        return new ResourceResult(accepted, starved, damage, spent, regenerated,
                leeched, spirit, durationSeconds);
    }

    private static double probability(double value) {
        return Math.max(0D, Math.min(1D, finite(value)));
    }

    private static double nonnegative(double value) {
        if (finite(value) < 0D) {
            throw new IllegalArgumentException("Value must be nonnegative");
        }
        return value;
    }

    private static double finite(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Value must be finite");
        }
        return value;
    }
}
