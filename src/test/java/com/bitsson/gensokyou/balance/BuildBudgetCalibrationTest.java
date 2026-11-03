package com.bitsson.gensokyou.balance;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Proposed tables only. None of these values are connected to gameplay. */
class BuildBudgetCalibrationTest {
    private static final double[] POWER = {1D, 10D, 90D, 773D, 6523D};
    // 标准核阶带（参考配装 T1/T1/T2/T2/T3；原武器等级倍率迁移，数值不变，design D7）。
    private static final double[] STANDARD_CORE_TIER = {1D, 1D, 2D, 2D, 4D};
    private static final double[] HEALTH = {28D, 36D, 44D, 52D, 60D};
    private static final double[] POOL = {1000D, 10000D, 100000D, 1000000D, 10000000D};
    // Candidate final limits, not per-affix grants.
    private static final double[] CRIT = {.25D, .4D, .55D, .65D, .75D};
    private static final double[] CRIT_TOTAL = {2D, 2.5D, 3D, 3.5D, 4D};
    private static final double[] SPEED = {.6D, 1D, 1.6D, 2.2D, 3D};
    private static final double[] DAMAGE = {.3D, .5D, .75D, 1D, 1.2D};
    private static final double[] COST_REDUCTION = {.3D, .45D, .6D, .75D, .9D};
    private static final double[] SPELL_DAMAGE = {.8D, 1.5D, 2.5D, 3.75D, 5D};
    private static final double[] SPELL_DURATION = {.4D, .8D, 1.4D, 2.2D, 3D};
    private static final double[] SPELL_REDUCTION = {.2D, .35D, .5D, .65D, .75D};
    private static final double[] CDR = {.15D, .25D, .4D, .5D, .6D};

    @Test
    void smallHealthAndDefensiveTierLadderAreCompatible() {
        double previousEhp = 0D;
        for (int i = 0; i < 5; i++) {
            double lowerHealth = 20D + (HEALTH[i] - 20D) * .95D;
            assertTrue(HEALTH[i] <= 60D);
            assertTrue(HEALTH[i] / lowerHealth < 1.04D);
            // Fixed baseline EHP grows by ten. This does not use actual player gear.
            double ward = referenceWard(i);
            double ehp = BuildBudgetMath.effectiveHealth(HEALTH[i], ward, 0D);
            if (i > 0) {
                assertEquals(10D, ehp / previousEhp, 1e-10);
            }
            previousEhp = ehp;
            double low = ward - .2D;
            double high = low + BuildBudgetMath.wardSpan(3D);
            assertEquals(3D, Math.pow(2D, high - low), 1e-12);
        }
        assertEquals(60D, HEALTH[4]);
    }

    @Test
    void globalWardAndSmallHealthRangesMeetTwoToEightUnhealedHitBaseline() {
        for (int tier = 1; tier <= 5; tier++) {
            double hp = BuildNumbers.health(tier);
            double lowHp = 20D + (hp - 20D) * .95D;
            double lowWard = BuildNumbers.ward(tier) - .04D * tier;
            double highWard = lowWard + BuildBudgetMath.wardSpan(3D);
            // 2.5 nominal reference hits keeps the highest legal defense below nine lethal hits.
            double rawHit = hp * Math.pow(2D, BuildNumbers.ward(tier)) / 2.5D;
            long weakest = BuildBudgetMath.lethalHits(lowHp, rawHit, lowWard);
            long strongest = BuildBudgetMath.lethalHits(hp, rawHit, highWard);
            assertTrue(weakest >= 2L && weakest <= 8L);
            assertTrue(strongest >= 2L && strongest <= 8L);
        }
    }

    @Test
    void emitCandidateTableAndExplicitHealingConflict() throws IOException {
        StringBuilder report = new StringBuilder("# Candidate calibration (not approved)\n\n");
        report.append("No production constants changed. No consumables/external supply.\n\n");
        report.append("|Tier|HP range|D0|Ward reference|Ward range|Extra power cap|Crit cap|Crit total cap|Speed cap|Damage cap|Weapon reduction cap|Spell damage cap|Spell duration cap|Spell reduction cap|CDR cap|\n");
        report.append("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (int i = 0; i < 5; i++) {
            double ward = referenceWard(i);
            report.append(String.format(Locale.ROOT,
                    "|%d|%.2f..%.2f|%.2f|%.5f|%.5f..%.5f|%.5f|%.0f%%|%.0f%%|%.0f%%|%.0f%%|%.0f%%|%.0f%%|%.0f%%|%.0f%%|%.0f%%|%n",
                    i + 1, 20D + (HEALTH[i] - 20D) * .95D, HEALTH[i],
                    BuildBudgetMath.referenceDps(POWER[i], STANDARD_CORE_TIER[i], 2D, 2.5D),
                    ward, ward - .2D, ward - .2D + BuildBudgetMath.wardSpan(3D),
                    POWER[i] * .8D * .03D, CRIT[i] * 100D, CRIT_TOTAL[i] * 100D,
                    SPEED[i] * 100D, DAMAGE[i] * 100D, COST_REDUCTION[i] * 100D,
                    SPELL_DAMAGE[i] * 100D, SPELL_DURATION[i] * 100D,
                    SPELL_REDUCTION[i] * 100D, CDR[i] * 100D));
        }
        report.append("\nWard = log2(56 * 10^(tier-1) / referenceHP). "
                + "Candidate explicitly replaces the old ward reference; not an approved constant.\n");
        report.append("\n## Current healing versus proposed HP (without amplification)\n\n");
        report.append("|Tier|Garden HP/s|Duration|Total healing / reference HP|Current cost / standard pool|\n");
        report.append("|---|---|---|---|---|\n");
        for (int i = 0; i < 5; i++) {
            double healing = .6D * Math.pow(POWER[i], .2D);
            double duration = 20D + i * 10D;
            report.append(String.format(Locale.ROOT, "|%d|%.4f|%.0f|%.2f%%|%.5f%%|%n",
                    i + 1, healing, duration, 100D * healing * duration / HEALTH[i],
                    100D * 80D / POOL[i]));
        }
        double t5Healing = .6D * Math.pow(POWER[4], .2D);
        assertTrue(t5Healing * 60D > 3D * HEALTH[4],
                "Current card already heals over three proposed T5 health bars per cast");
        report.append("\nBLOCKED: current T5 garden heals >3 full HP pools per cast before "
                + "new amplification/duration/CDR or overlapping fields. "
                + "Do not approve defensive builds using a made-up smaller heal.\n");
        Path output = Path.of("build", "reports", "build-budget", "candidate-calibration.md");
        Files.createDirectories(output.getParent());
        Files.writeString(output, report, StandardCharsets.UTF_8);
    }

    @Test
    void emitResourceCandidatesWithAndWithoutLegacyArmor() throws IOException {
        StringBuilder report = new StringBuilder("# Resource candidates (not approved)\n\n");
        report.append("Sphere: 2.5 triggers/s; fixed standard pool * 0.0012 per trigger. "
                + "Regen: fixed standard pool * 0.0012/s. Weapon leech disabled for this sweep. "
                + "Spell probe: fixed pool * 0.04 per cast, base CD 30s. "
                + "This probe is NOT an existing card or a viable build.\n\n");
        report.append("|Tier|Window(s)|Shot base cost|Regen/s|Armor regen/s|Spell probe cost|\n");
        report.append("|---|---|---|---|---|---|\n");
        double[] windows = {210D, 330D, 480D, 780D, 1050D};
        for (int i = 0; i < 5; i++) {
            report.append(String.format(Locale.ROOT, "|%d|%.0f|%.3f|%.3f|%.3f|%.3f|%n",
                    i + 1, windows[i], POOL[i] * .0012D, POOL[i] * .0012D,
                    POOL[i] * .0018D, POOL[i] * .04D));
        }
        report.append("\n|Tier|Profile|Armor|Accepted|Starved|End pool %|Delivery %|\n");
        report.append("|---|---|---|---|---|---|---|\n");
        for (int i = 0; i < 5; i++) {
            for (int profile = 0; profile < 3; profile++) {
                String name = switch (profile) {
                    case 0 -> "unmodified weapon";
                    case 1 -> "speed/reduction maxima (not a legal core claim)";
                    default -> "weapon + spell stress probe";
                };
                double speed = profile == 1 ? SPEED[i] : 0D;
                double reduction = profile == 1 ? COST_REDUCTION[i] : 0D;
                double cost = BuildBudgetMath.discountedCost(POOL[i] * .0012D, reduction);
                double d0 = BuildBudgetMath.referenceDps(POWER[i], STANDARD_CORE_TIER[i], 2D, 2.5D);
                List<BuildBudgetMath.Action> actions = new ArrayList<>();
                double interval = 1D / (2.5D * (1D + speed));
                for (int shot = 0; shot * interval < windows[i]; shot++) {
                    actions.add(new BuildBudgetMath.Action(shot * interval, cost, d0 / 2.5D, true));
                }
                if (profile == 2) {
                    for (double t = 0D; t < windows[i]; t += 30D) {
                        actions.add(new BuildBudgetMath.Action(t, POOL[i] * .04D, 0D, false));
                    }
                }
                actions.sort(Comparator.comparingDouble(BuildBudgetMath.Action::atSeconds));
                for (boolean armor : new boolean[]{false, true}) {
                    var plan = new BuildBudgetMath.ResourcePlan(POOL[i], POOL[i],
                            POOL[i] * .0012D * (armor ? 1.5D : 1D), 0D, 0D);
                    var result = BuildBudgetMath.simulate(plan, actions, windows[i]);
                    assertEquals(plan.initialSpirit() + result.regenerated() + result.leeched()
                            - result.spent(), result.finalSpirit(), POOL[i] * 1e-9D);
                    assertTrue(result.finalSpirit() >= 0D);
                    report.append(String.format(Locale.ROOT, "|%d|%s|%s|%d|%d|%.3f|%.3f|%n",
                            i + 1, name, armor, result.accepted(), result.starved(),
                            result.finalSpirit() / POOL[i] * 100D,
                            100D * result.accepted() / actions.size()));
                }
            }
        }
        Path output = Path.of("build", "reports", "build-budget", "resource-candidates.md");
        Files.createDirectories(output.getParent());
        Files.writeString(output, report, StandardCharsets.UTF_8);
    }

    @Test
    void candidateHealingIsBoundedUnderMaxDurationAndAmplification() {
        // Candidate: one normal cast restores 25% reference HP over its base lifetime.
        // Same-kind fields must not add healing on one target; that rule is not implemented yet.
        for (int i = 0; i < 5; i++) {
            double duration = 20D + i * 10D;
            double baseRate = HEALTH[i] * .25D / duration;
            double maxHealAmp = .5D;
            double maxRate = baseRate * (1D + maxHealAmp);
            double maxLifetime = duration * (1D + SPELL_DURATION[i]);
            double baseTotal = baseRate * duration;
            assertEquals(.25D, baseTotal / HEALTH[i], 1e-12);
            assertTrue(maxRate * maxLifetime / HEALTH[i] <= 1.5D + 1e-12);
            // For a fixed target and the proposed non-additive rule, CDR can improve uptime
            // but cannot multiply simultaneous healing rate.
            assertTrue(maxRate / HEALTH[i] <= .01875D + 1e-12);
        }
    }

    @Test
    void emitCumulativeInnateQuantilesRatherThanSingleRollQuantiles() throws IOException {
        // Candidate innate maximum: 30% of the final crit cap; the core supplies the rest.
        // Each tier contributes the increase in the cumulative maximum, preserving reroll isolation.
        int samples = 100_000;
        Random random = new Random(20261102L);
        double[][] values = new double[5][samples];
        for (int sample = 0; sample < samples; sample++) {
            double total = 0D;
            double previousMaximum = 0D;
            for (int tier = 0; tier < 5; tier++) {
                double maximum = CRIT[tier] * .3D;
                total += (maximum - previousMaximum)
                        * BuildBudgetMath.lowBiasedQuantile(random.nextDouble(), 3D);
                values[tier][sample] = total;
                previousMaximum = maximum;
            }
        }
        StringBuilder report = new StringBuilder("# Innate crit distribution candidate\n\n");
        report.append("100000 cumulative samples, seed=20261102, q=u^3. "
                + "Zero lower contribution; cumulative innate maximum=30% of final crit cap. "
                + "No baseline 5% crit is implied. This is a candidate, not production data.\n\n");
        report.append("|Tier|P10 %|P50 %|P90 %|P99 %|Max legal %|Mean %|\n");
        report.append("|---|---|---|---|---|---|---|\n");
        for (int tier = 0; tier < 5; tier++) {
            Arrays.sort(values[tier]);
            double maximum = CRIT[tier] * .3D;
            double mean = Arrays.stream(values[tier]).average().orElseThrow();
            assertEquals(maximum / 4D, mean, .0005D);
            assertTrue(values[tier][samples / 2] < maximum / 3D);
            assertTrue(values[tier][samples - 1] <= maximum);
            report.append(String.format(Locale.ROOT, "|%d|%.4f|%.4f|%.4f|%.4f|%.4f|%.4f|%n",
                    tier + 1, values[tier][samples / 10] * 100D,
                    values[tier][samples / 2] * 100D, values[tier][samples * 9 / 10] * 100D,
                    values[tier][samples * 99 / 100] * 100D, maximum * 100D, mean * 100D));
        }
        report.append("\nRarity of a cumulative high roll is NOT the single-roll 3.45% tail: "
                + "multi-tier accumulation changes it. Other innate attributes still require their "
                + "own bands and this same cumulative check.\n");
        Path output = Path.of("build", "reports", "build-budget", "innate-candidates.md");
        Files.createDirectories(output.getParent());
        Files.writeString(output, report, StandardCharsets.UTF_8);
    }

    @Test
    void referenceDpsIsOnlyOneComponentOfTheStandardStrengthLadder() {
        double tierThree = BuildBudgetMath.referenceDps(POWER[2], STANDARD_CORE_TIER[2], 2D, 2.5D);
        double tierFour = BuildBudgetMath.referenceDps(POWER[3], STANDARD_CORE_TIER[3], 2D, 2.5D);
        assertEquals(773D / 90D, tierFour / tierThree, 1e-12);
        assertTrue(tierFour / tierThree < 10D,
                "Base-only growth may be below tenfold; standard total includes other attributes");
        // Low-crit builds need not independently meet a tenfold ratio. The full standard
        // profile and actual cross-tier viability must be evaluated separately.
        double lowCritThree = BuildBudgetMath.weaponDps(tierThree, 0D, 0D, 0D, 1.5D);
        double lowCritFour = BuildBudgetMath.weaponDps(tierFour, 0D, 0D, 0D, 1.5D);
        assertTrue(lowCritFour / lowCritThree < 10D);
    }

    @Test
    void emitFullStandardReferenceLadderSeparatelyFromBaseDps() throws IOException {
        // Existing standard crit values including the base 5% chance and 50% bonus.
        // These are a historical comparison, not new innate averages or selected gear rolls.
        double[] chance = {.11D, .16D, .23D, .31D, .40D};
        double[] totalCrit = {1.6D, 1.8D, 2.1D, 2.5D, 3D};
        StringBuilder report = new StringBuilder("# Historical standard ladder decomposition\n\n");
        report.append("Fixed sphere, matching core tier band, standard power and crit; "
                + "no amp core, armor, spell damage, consumables or external supply. "
                + "Weapon DPS is full-hit resource-unconstrained expectation, NOT final combat strength. "
                + "Actual resource/survival constraints require separate validation.\n\n");
        report.append("|Tier|D0|Crit expectation|Weapon DPS|D0 tier ratio|Weapon tier ratio|Defense reference EHP|\n");
        report.append("|---|---|---|---|---|---|---|\n");
        double previousBase = 0D;
        double previousDps = 0D;
        for (int i = 0; i < 5; i++) {
            double base = BuildBudgetMath.referenceDps(POWER[i], STANDARD_CORE_TIER[i], 2D, 2.5D);
            double crit = BuildBudgetMath.criticalExpectation(chance[i], totalCrit[i]);
            double dps = base * crit;
            report.append(String.format(Locale.ROOT, "|%d|%.3f|%.4f|%.3f|%s|%s|%.3f|%n",
                    i + 1, base, crit, dps,
                    i == 0 ? "-" : String.format(Locale.ROOT, "%.4f", base / previousBase),
                    i == 0 ? "-" : String.format(Locale.ROOT, "%.4f", dps / previousDps),
                    BuildBudgetMath.effectiveHealth(HEALTH[i], referenceWard(i), 0D)));
            if (i > 0) {
                assertTrue(dps / previousDps > 9.9D);
            }
            previousBase = base;
            previousDps = dps;
        }
        Path output = Path.of("build", "reports", "build-budget", "standard-ladder.md");
        Files.createDirectories(output.getParent());
        Files.writeString(output, report, StandardCharsets.UTF_8);
    }

    /** design D7：核阶倍率替代武器等级倍率；匹配装不变、换核 ×2/阶、低核在高阶武器不再继承倍率。 */
    @Test
    void emitCoreTierLadderAndSwapGradient() throws IOException {
        StringBuilder report = new StringBuilder("# Core tier ladder and swap gradient (design D7)\n\n");
        report.append("Core tier mult T1 x1 / T2 x2 / T3 x4 replaces the old weapon-level mult. "
                + "Matched builds keep the same totals as before (BOSS unchanged); within one weapon, "
                + "swapping up a core tier doubles output; a low-tier core on a high weapon no longer "
                + "inherits the weapon band.\n\n");
        report.append("|Player tier|Case|Core tier mult|D0|Swap step|Note|\n|---|---|---|---|---|---|\n");
        double t3Matched = BuildBudgetMath.referenceDps(POWER[2], 2D, 2D, 2.5D);
        double t3Low = BuildBudgetMath.referenceDps(POWER[2], 1D, 2D, 2.5D);
        double t5Matched = BuildBudgetMath.referenceDps(POWER[4], 4D, 2D, 2.5D);
        double t5Mid = BuildBudgetMath.referenceDps(POWER[4], 2D, 2D, 2.5D);
        double t5Low = BuildBudgetMath.referenceDps(POWER[4], 1D, 2D, 2.5D);
        report.append(String.format(Locale.ROOT, "|3|T1 low core on Lv2 weapon|x1|%.3f|-|no level inheritance|\n", t3Low));
        report.append(String.format(Locale.ROOT, "|3|T2 matched (Lv2 weapon)|x2|%.3f|x2.0|matched build unchanged|\n", t3Matched));
        report.append(String.format(Locale.ROOT, "|5|T1 low core on Lv3 weapon|x1|%.3f|-|was x4=%.0f before D7; now 1/4|\n", t5Low, t5Matched));
        report.append(String.format(Locale.ROOT, "|5|T2 intermediate on Lv3 weapon|x2|%.3f|x2.0|still obeys the gate|\n", t5Mid));
        report.append(String.format(Locale.ROOT, "|5|T3 matched (Lv3 weapon)|x4|%.3f|x2.0|matched build unchanged|\n", t5Matched));
        report.append("\nT1 core on Lv3 weapon loses the old x4: 20.0 -> 5.0 per-spirit equivalent; "
                + "boss HP stays at the matched-build reference, so the swap-up is real progression pressure. "
                + "Actual reachability of T2/T3 cores and real combat still need the open calibration/live tasks.\n");
        assertEquals(2.0D, t3Matched / t3Low, 1e-9);
        assertEquals(2.0D, t5Matched / t5Mid, 1e-9);
        assertEquals(2.0D, t5Mid / t5Low, 1e-9);
        assertEquals(t5Matched / 4D, t5Low, 1e-9);
        Path output = Path.of("build", "reports", "build-budget", "core-tier-ladder.md");
        Files.createDirectories(output.getParent());
        Files.writeString(output, report, StandardCharsets.UTF_8);
    }

    @Test
    void emitAllAttributeBandsAndCumulativeQuantiles() throws IOException {
        StringBuilder report = new StringBuilder("# Complete candidate attribute bands\n\n");
        report.append("Not production constants. Flow innate uses independent tier increments with q=u^3; "
                + "HP uses 95..100% of +8 per tier; power/pool use old 80..120% bounds; "
                + "ward innate has a cumulative span of 0.08*tier, centered on the reference "
                + "before the low-biased sampling. "
                + "Core type weights candidate: 6 each, preserving old equal player weights. "
                + "Core minima=0 and u^3; exact zero has zero probability for a continuous source.\n\n");
        report.append("|Tier|Key|Final lower|Final upper|Innate P10|Innate P50|Innate P90|Innate P99|\n");
        report.append("|---|---|---|---|---|---|---|---|\n");
        for (String key : BuildNumbers.keys()) {
            Random random = new Random(20261102L ^ key.hashCode());
            double[][] samples = new double[5][20000];
            for (int sample = 0; sample < 20000; sample++) {
                double cumulative = key.equals("crit_damage") ? .5D : 0D;
                double previousMaximum = 0D;
                for (int tier = 1; tier <= 5; tier++) {
                    double maximum = BuildNumbers.innateMaximum(key, tier);
                    cumulative += (maximum - previousMaximum)
                            * Math.pow(random.nextDouble(), 3D);
                    samples[tier - 1][sample] = cumulative;
                    previousMaximum = maximum;
                }
            }
            for (int tier = 1; tier <= 5; tier++) {
                Arrays.sort(samples[tier - 1]);
                double[] s = samples[tier - 1];
                // Special dimensional/core rows are emitted separately; zero innate rows are
                // intentional for weapon-only or bounded utility affixes in this candidate.
                double lower = key.equals("crit_damage") ? .5D : 0D;
                double upper = BuildNumbers.cap(key, tier);
                if (key.equals("danmaku_reduce")) {
                    lower = BuildNumbers.ward(tier) - .04D * tier;
                    upper = lower + BuildBudgetMath.wardSpan(3D);
                    for (int n = 0; n < s.length; n++) {
                        s[n] += lower;
                    }
                }
                report.append(String.format(Locale.ROOT, "|%d|%s|%.6f|%.6f|%.6f|%.6f|%.6f|%.6f|%n",
                        tier, key, lower, upper, s[2000], s[10000], s[18000], s[19800]));
            }
        }
        report.append("\n## Nuclear ranges\n\n|Core tier|Key|Min|Max|Type weight|\n|---|---|---|---|---|\n");
        for (int coreTier = 1; coreTier <= 3; coreTier++) {
            for (String key : BuildNumbers.keys()) {
                report.append(String.format(Locale.ROOT, "|%d|%s|0|%.6f|6|%n", coreTier, key,
                        BuildNumbers.coreMaximum(key, coreTier)));
            }
        }
        report.append("\n## Core innate dimensional bands\n\n");
        report.append("|Tier|Power min|Power standard|Power max|Pool min|Pool standard|Pool max|HP min|HP standard/max|Ward innate min|Ward innate max|\n");
        report.append("|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (int tier = 1; tier <= 5; tier++) {
            double p = BuildNumbers.power(tier);
            double pool = BuildNumbers.pool(tier);
            double hp = BuildNumbers.health(tier);
            double w = BuildNumbers.ward(tier);
            report.append(String.format(Locale.ROOT,
                    "|%d|%.6f|%.6f|%.6f|%.3f|%.3f|%.3f|%.3f|%.3f|%.6f|%.6f|%n",
                    tier, p * .8D, p, p * 1.2D, pool * .8D, pool, pool * 1.2D,
                    20D + (hp - 20D) * .95D, hp, w - .04D * tier, w + .04D * tier));
        }
        Path output = Path.of("build", "reports", "build-budget", "attribute-bands.md");
        Files.createDirectories(output.getParent());
        Files.writeString(output, report, StandardCharsets.UTF_8);
    }

    private static double referenceWard(int tierIndex) {
        return Math.log(56D * Math.pow(10D, tierIndex) / HEALTH[tierIndex]) / Math.log(2D);
    }
}
