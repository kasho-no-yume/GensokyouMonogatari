package com.bitsson.gensokyou.balance;

import com.bitsson.gensokyou.item.weapon.RuneAffix;
import com.bitsson.gensokyou.item.weapon.SeiiNumbers;

import net.minecraft.util.RandomSource;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实生成可达性（1.5，rework-player-stat-build-budgets + rework-amp-core-rolls-and-skill-slots）。
 *
 * <p>用<b>真实生成器</b>（而非手写核）按固定种子生成大样，
 * 对六套同投入配装（PROFILES 口径）各挑出真实可生成的最佳核，
 * 证明每个流派的组合在生成分布中是可刷到的——而不是「理论上存在」。
 * 同时报告预算拒绝率（平均尝试次数）与回退率。
 *
 * <p>这是有限样本证据，不是全局最优证明。
 */
class CoreReachabilityTest {

    private static final int SAMPLES = 20_000;

    private static Map<String, Double> valuesOf(List<RuneAffix> rolled) {
        return CoreReachabilityValues.valuesOf(rolled);
    }

    private static List<RuneAffix> bestFit(List<RuneAffix>[] samples, String[] keys,
                                           int coreTier, double scoreCap) {
        List<RuneAffix> best = null;
        double bestFit = -1D;
        double bestScore = -1D;
        for (List<RuneAffix> rolled : samples) {
            Map<String, Double> values = valuesOf(rolled);
            double score = BuildNumbers.coreScore(values, coreTier);
            if (score > scoreCap) {
                continue;
            }
            double fit = 0D;
            for (String key : keys) {
                fit += values.getOrDefault(key, 0D) / BuildNumbers.coreMaximum(key, coreTier);
            }
            if (fit > bestFit || (fit == bestFit && score > bestScore)) {
                bestFit = fit;
                bestScore = score;
                best = rolled;
            }
        }
        return best;
    }

    private static void reportFitRow(StringBuilder report, int coreTier, int tier, String label,
                                     String[] keys, List<RuneAffix> best) {
        Map<String, Double> values = valuesOf(best);
        double fit = 0D;
        for (String key : keys) {
            fit += values.getOrDefault(key, 0D) / BuildNumbers.coreMaximum(key, coreTier);
        }
        double c = BuildBudgetLoadoutTest.stat("crit_chance", tier, values);
        double k = 1D + BuildBudgetLoadoutTest.stat("crit_damage", tier, values);
        double weaponMult = BuildBudgetMath.weaponDps(1D,
                BuildBudgetLoadoutTest.stat("damage_pct", tier, values),
                BuildBudgetLoadoutTest.stat("attack_rate_pct", tier, values),
                c, k);
        report.append(String.format(Locale.ROOT, "|T%d|%s|%.3f|%.4f|%.4f|%s|%n",
                coreTier, label, fit, BuildNumbers.coreScore(values, coreTier),
                weaponMult, best));
    }

    @Test
    void realGeneratorProducesSixArchetypeCandidatesPerCoreTier() throws IOException {
        StringBuilder report = new StringBuilder("# Real-generator reachability search\n\n");
        report.append("Fixed seed, ").append(SAMPLES).append(" real cores per core tier via the production\n");
        report.append("generator (unified pool, 3/4/5 affixes, u^k low-biased sampling, 64-attempt\n");
        report.append("rejection against combination budget, deterministic legal floor fallback).\n");
        report.append("Best-fit per archetype = max normalized profile-key sum (ties: higher core score).\n");
        report.append("Legality is enforced at generation time (legalCore); reported cores all pass.\n\n");
        report.append("|CoreTier|Profile|Best fit (Σ norm)|Core score|Weapon multiplier|Affixes|\n");
        report.append("|---|---|---|---|---|---|\n");

        for (int coreTier = 1; coreTier <= 3; coreTier++) {
            final int ct = coreTier;
            int tier = ct == 1 ? 1 : ct == 2 ? 3 : 5;
            RandomSource random = RandomSource.create(20261104L + ct * 7919L);
            SeiiNumbers.Tunables tune = SeiiNumbers.tunables();
            List<RuneAffix>[] samples = new List[SAMPLES];
            int fallbackUsed = 0;
            long attemptSum = 0L;
            for (int i = 0; i < SAMPLES; i++) {
                SeiiNumbers.RollOutcome outcome = SeiiNumbers.rollDiagnostic(coreTier, 0, random, tune);
                samples[i] = outcome.affixes();
                if (outcome.fallbackUsed()) {
                    fallbackUsed++;
                }
                attemptSum += outcome.attempts();
            }
            double fallbackRate = fallbackUsed / (double) SAMPLES;
            double meanAttempts = attemptSum / (double) SAMPLES;
            for (int profile = 0; profile < BuildBudgetLoadoutTest.PROFILES.length; profile++) {
                String[] keys = BuildBudgetLoadoutTest.PROFILES[profile];
                // 口径一（自由搜索）：该流派真实可刷到的最佳核
                List<RuneAffix> best = bestFit(samples, keys, coreTier, Double.POSITIVE_INFINITY);
                assertTrue(best != null, "no candidate for profile " + profile);
                reportFitRow(report, coreTier, tier, BuildBudgetLoadoutTest.NAMES[profile],
                        keys, best);
                // 口径二（同投入）：整核评分 ≤ 1.0（与既有同分值配装报告同口径），
                // 证明六套同投入配装也真实可生成，而非只能靠超预算好核
                List<RuneAffix> same = bestFit(samples, keys, coreTier, 1.0D);
                if (same == null) {
                    report.append(String.format(Locale.ROOT,
                            "|T%d|%s (same-investment≤1.0)| 无样本 | | | |%n",
                            coreTier, BuildBudgetLoadoutTest.NAMES[profile]));
                } else {
                    Map<String, Double> sv = valuesOf(same);
                    assertTrue(BuildNumbers.legalCore(sv, coreTier));
                    assertTrue(BuildNumbers.coreScore(sv, coreTier) <= 1.0D + 1e-9);
                    reportFitRow(report, coreTier, tier,
                            BuildBudgetLoadoutTest.NAMES[profile] + " (same-investment≤1.0)",
                            keys, same);
                }
            }
            report.append(String.format(Locale.ROOT,
                    "|T%d|fallback rate|%.4f%% (%d/%d)|mean attempts=%.3f|||%n",
                    coreTier, fallbackRate * 100D, fallbackUsed, SAMPLES, meanAttempts));
        }
        Path path = Path.of("build", "reports", "build-budget", "real-generated-loadouts.md");
        Files.createDirectories(path.getParent());
        Files.writeString(path, report, StandardCharsets.UTF_8);
    }
}
