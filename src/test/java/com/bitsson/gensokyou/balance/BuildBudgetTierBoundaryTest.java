package com.bitsson.gensokyou.balance;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.*;

class BuildBudgetTierBoundaryTest {
    @Test
    void borrowedHighWeaponHasToBeIncludedInCrossTierEvidence() throws IOException {
        StringBuilder report = new StringBuilder("# Borrowed-equipment cross-tier bound\n\n");
        report.append("Existing rule allows Lv3 weapon + T3 core (core tier mult ×4) on a tier1 player. "
                + "Actual-player final caps applied. High-tier core generation checks do not "
                + "implicitly establish player-tier equipment gates. Full-hit, no resource limit; "
                + "upper bound only, not a demonstrated reachable random roll.\n\n");
        report.append("|Player tier|Borrowed weapon D0|All-final-cap upper DPS|Matched next-tier base D0|Ratio|\n");
        report.append("|---|---|---|---|---|\n");
        for (int tier = 1; tier < 5; tier++) {
            double borrowedD0 = BuildBudgetMath.referenceDps(BuildNumbers.power(tier), 4D, 2D, 2.5D);
            double upper = BuildBudgetMath.weaponDps(borrowedD0,
                    BuildNumbers.cap("damage_pct", tier),
                    BuildNumbers.cap("attack_rate_pct", tier),
                    BuildNumbers.cap("crit_chance", tier),
                    1D + BuildNumbers.cap("crit_damage", tier));
            double nextBase = BuildBudgetMath.referenceDps(BuildNumbers.power(tier + 1),
                    tier + 1 <= 2 ? 1D : tier + 1 <= 4 ? 2D : 4D, 2D, 2.5D);
            report.append(String.format(Locale.ROOT, "|%d|%.3f|%.3f|%.3f|%.3f|%n",
                    tier, borrowedD0, upper, nextBase, upper / nextBase));
            assertTrue(upper > 0D && nextBase > 0D);
        }
        var allWeaponMax = Map.of("damage_pct", BuildNumbers.coreMaximum("damage_pct", 3),
                "attack_rate_pct", BuildNumbers.coreMaximum("attack_rate_pct", 3),
                "crit_chance", BuildNumbers.coreMaximum("crit_chance", 3),
                "crit_damage", BuildNumbers.coreMaximum("crit_damage", 3));
        assertFalse(BuildNumbers.legalCore(allWeaponMax, 3));
        report.append("\nAll-max weapon core is rejected by combination budget; this upper table "
                + "must not be presented as a legal build. A complete proof must search legal "
                + "high-tier cores against lower-tier caps and include defense/resources.\n");
        Path output = Path.of("build", "reports", "build-budget", "borrowed-equipment.md");
        Files.createDirectories(output.getParent());
        Files.writeString(output, report, StandardCharsets.UTF_8);
    }

    @Test
    void searchLegalBorrowedCoresInsteadOfOnlyImpossibleAllMaxUpperBound() throws IOException {
        StringBuilder report = new StringBuilder("# Legal borrowed core grid search\n\n");
        report.append("T3 cores checked against their own generation budget, then equipped by "
                + "lower-tier players with actual-player final caps. Mean innate and T3 core tier mult ×4, "
                + "21^4 grid, no resource limit. Empty remaining affixes are assumed arbitrarily "
                + "small nonzero utility rolls; this is a limiting bound, not an exact sampled core.\n\n");
        report.append("|Player tier|Best legal upper DPS|Next tier standard base DPS|Ratio|"
                + "Damage roll|Speed roll|Chance roll|Crit roll|\n");
        report.append("|---|---|---|---|---|---|---|---|\n");
        String[] keys = {"damage_pct", "attack_rate_pct", "crit_chance", "crit_damage"};
        for (int tier = 1; tier < 5; tier++) {
            double best = 0D;
            Map<String, Double> winner = Map.of();
            double base = BuildBudgetMath.referenceDps(BuildNumbers.power(tier), 4D, 2D, 2.5D);
            for (int a = 0; a <= 20; a++) for (int b = 0; b <= 20; b++)
                for (int c = 0; c <= 20; c++) for (int d = 0; d <= 20; d++) {
                    int[] point = {a, b, c, d};
                    Map<String, Double> values = new LinkedHashMap<>();
                    for (int k = 0; k < 4; k++) {
                        values.put(keys[k], BuildNumbers.coreMaximum(keys[k], 3) * point[k] / 20D);
                    }
                    if (!BuildNumbers.legalCore(values, 3)) continue;
                    double[] effective = new double[4];
                    for (int k = 0; k < 4; k++) {
                        effective[k] = Math.min(BuildNumbers.cap(keys[k], tier),
                                BuildNumbers.innateMean(keys[k], tier) + values.get(keys[k]));
                    }
                    double dps = BuildBudgetMath.weaponDps(base, effective[0], effective[1],
                            effective[2], 1D + effective[3]);
                    if (dps > best) {
                        best = dps;
                        winner = values;
                    }
                }
            double next = BuildBudgetMath.referenceDps(BuildNumbers.power(tier + 1),
                    tier + 1 <= 2 ? 1D : tier + 1 <= 4 ? 2D : 4D, 2D, 2.5D);
            assertTrue(best > 0D);
            assertTrue(BuildNumbers.legalCore(winner, 3));
            report.append(String.format(Locale.ROOT, "|%d|%.4f|%.4f|%.4f|%.4f|%.4f|%.4f|%.4f|%n",
                    tier, best, next, best / next, winner.get(keys[0]), winner.get(keys[1]),
                    winner.get(keys[2]), winner.get(keys[3])));
        }
        report.append("\nMatching next-tier *base* output does not alone prove an easy boss kill: "
                + "defensive EHP remains about one tier lower, and resources, standard core bonuses "
                + "and actual higher-tier boss damage still need evaluation.\n");
        report.append("\n## Direct survival against next-tier reference hit\n\n");
        report.append("Next-tier raw hit = next-tier reference EHP / 2.5. "
                + "Player uses highest candidate HP and highest candidate ward, no healing, "
                + "shields or avoidance. This is an upper durability bound.\n\n");
        report.append("|Player tier|Max direct EHP|Next raw hit|Lethal hits|\n|---|---|---|---|\n");
        for (int tier = 1; tier < 5; tier++) {
            double ward = BuildNumbers.ward(tier) - .04D * tier + BuildBudgetMath.wardSpan(3D);
            double health = BuildNumbers.health(tier);
            double ehp = BuildBudgetMath.effectiveHealth(health, ward, 0D);
            double nextDamage = 56D * Math.pow(10D, tier) / 2.5D;
            long hits = BuildBudgetMath.lethalHits(health, nextDamage, ward);
            assertEquals(1L, hits, "One-tier-higher baseline remains lethal even with best direct defense");
            report.append(String.format(Locale.ROOT, "|%d|%.4f|%.4f|%d|%n", tier, ehp, nextDamage, hits));
        }
        Path output = Path.of("build", "reports", "build-budget", "legal-borrowed-cores.md");
        Files.createDirectories(output.getParent());
        Files.writeString(output, report, StandardCharsets.UTF_8);
    }
}
