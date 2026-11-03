package com.bitsson.gensokyou.balance;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Searches allocation within each declared style, instead of assuming a hand-written core is best. */
class BuildBudgetProfileSearchTest {
    @Test
    void optimizeResourceConstrainedProfilesAgainstSharedTargets() throws IOException {
        StringBuilder report = new StringBuilder("# Candidate profile allocation search\n\n");
        report.append("256 proposals/profile/tier, deterministic seed, score <=1.0, "
                + "same fixed target as dynamic-ttk; 3600s horizon. No armor/leech/consumables. "
                + "This is a finite feasible search, NOT an optimum proof. Named core IDs "
                + "are fixed by profile; only value allocation changes.\n\n");
        report.append("|Tier|Profile|Feasible|Killed|Best seconds|Core score|Core|\n");
        report.append("|---|---|---|---|---|---|---|\n");
        for (int tier = 1; tier <= 5; tier++) {
            double d0 = BuildBudgetMath.referenceDps(BuildNumbers.power(tier),
                    tier <= 2 ? 1D : tier <= 4 ? 2D : 4D, 2D, 2.5D);
            var standard = BuildBudgetLoadoutTest.core(tier, 5);
            double target = d0 * multiplier(tier, standard) * BuildNumbers.window(tier);
            for (int profile = 0; profile < 6; profile++) {
                int ct = BuildNumbers.coreTier(tier);
                Random random = new Random(20261102L + tier * 100L + profile);
                double bestTime = Double.POSITIVE_INFINITY;
                Map<String, Double> best = Map.of();
                boolean killed = false;
                int feasible = 0;
                for (int sample = 0; sample < 256; sample++) {
                    var template = BuildBudgetLoadoutTest.core(tier, profile);
                    Map<String, Double> values = new LinkedHashMap<>();
                    for (String key : template.keySet()) {
                        // Utility fillers remain low so they do not erase the named style.
                        double fraction = key.equals("jump") || key.equals("range_pct") ? .02D
                                : profile == 0 && key.equals("damage_pct") ? .8D + random.nextDouble() * .2D
                                : profile == 0 && key.equals("attack_rate_pct") ? random.nextDouble() * .2D
                                : .05D + random.nextDouble() * .95D;
                        values.put(key, BuildNumbers.coreMaximum(key, ct) * fraction);
                    }
                    double score = BuildNumbers.coreScore(values, ct);
                    if (score > 1D) {
                        double scale = Math.sqrt(1D / score);
                        values.replaceAll((key, value) -> value * scale);
                    }
                    if (!BuildNumbers.legalCore(values, ct)) continue;
                    // Preserve style identity. Otherwise a 'heavy' search can simply choose
                    // speed and a 'defense' search can allocate everything to damage.
                    double normalizedDamage = values.getOrDefault("damage_pct", 0D)
                            / BuildNumbers.coreMaximum("damage_pct", ct);
                    double normalizedSpeed = values.getOrDefault("attack_rate_pct", 0D)
                            / BuildNumbers.coreMaximum("attack_rate_pct", ct);
                    double normalizedWard = values.getOrDefault("danmaku_reduce", 0D)
                            / BuildNumbers.coreMaximum("danmaku_reduce", ct);
                    if (profile == 0 && (normalizedDamage < .65D || normalizedSpeed > .25D)) continue;
                    if (profile == 2 && (normalizedSpeed < .5D
                            || values.getOrDefault("spirit_cost_pct", 0D)
                            / BuildNumbers.coreMaximum("spirit_cost_pct", ct) < .4D)) continue;
                    if (profile == 4 && normalizedWard < .6D) continue;
                    feasible++;
                    double speed = BuildBudgetLoadoutTest.stat("attack_rate_pct", tier, values);
                    var actions = BuildBudgetLoadoutTest.actions(tier, profile, values, d0,
                            multiplier(tier, values), speed, 3600D);
                    double pool = BuildNumbers.pool(tier);
                    var result = BuildBudgetMath.simulateUntilDamage(new BuildBudgetMath.ResourcePlan(
                                    pool, pool, pool * .0012D + values.getOrDefault("spirit_regen_rate", 0D),
                                    0D, 0D), actions, 3600D, target);
                    boolean success = result.damage() >= target;
                    if (success && result.durationSeconds() < bestTime) {
                        killed = true;
                        bestTime = result.durationSeconds();
                        best = values;
                    }
                }
                // No feasible sampled allocation is reported as such; do not silently relax
                // the style constraint or relabel a balanced allocation as specialized.
                report.append(String.format(Locale.ROOT, "|%d|%s|%d|%s|%.2f|%.4f|%s|%n", tier,
                        BuildBudgetLoadoutTest.NAMES[profile], feasible, killed,
                        bestTime, best.isEmpty() ? 0D : BuildNumbers.coreScore(best, ct), best));
            }
        }
        Path path = Path.of("build", "reports", "build-budget", "profile-search.md");
        Files.createDirectories(path.getParent());
        Files.writeString(path, report, StandardCharsets.UTF_8);
    }

    private static double multiplier(int tier, Map<String, Double> values) {
        return BuildBudgetMath.weaponDps(1D, BuildBudgetLoadoutTest.stat("damage_pct", tier, values),
                BuildBudgetLoadoutTest.stat("attack_rate_pct", tier, values),
                BuildBudgetLoadoutTest.stat("crit_chance", tier, values),
                1D + BuildBudgetLoadoutTest.stat("crit_damage", tier, values));
    }
}
