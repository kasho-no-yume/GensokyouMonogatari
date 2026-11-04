package com.bitsson.gensokyou.balance;

import com.bitsson.gensokyou.item.weapon.RuneAffix;
import com.bitsson.gensokyou.item.weapon.SeiiNumbers;

import net.minecraft.util.RandomSource;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 新版生成分布统计（1.3/1.4/5.1）：固定种子 10 万核/阶，输出
 * {@code build/reports/build-budget/core-generation.md}。断言只锁结构性事实
 * （条数、唯一性、合法性），其余为观测值，交用户审核（1.6）。
 */
class CoreGenerationStatsTest {

    private static final int SAMPLES = 100_000;

    @Test
    void generateDistributionReport() throws IOException {
        StringBuilder report = new StringBuilder("# Core generation statistics (fixed seed, ")
                .append(SAMPLES).append(" cores per tier)\n\n");
        report.append("Unified pool; counts 3/4/5; q=u^k(t), k(t)=3-2t, cap=")
                .append(com.bitsson.gensokyou.balance.BalanceNumbers.RUNE_PITY_CAP)
                .append("; 64-attempt rejection; deterministic floor fallback.\n");
        report.append("Fraction = (value-lo)/(hi-lo) per affix (attr bands scaled by reference-tier standard).\n\n");
        report.append("|CoreTier|Fallback rate|Mean attempts|Median frac|>=90% frac (1+)|>=75% frac (2+)|All <=25% frac|Mean score|Mean score (pity=cap)|\n");
        report.append("|---|---|---|---|---|---|---|---|---|\n");
        for (int coreTier = 1; coreTier <= 3; coreTier++) {
            final int ct = coreTier;
            RandomSource random = RandomSource.create(20261104L + ct * 313L);
            SeiiNumbers.Tunables tune = SeiiNumbers.tunables();
            Map<String, CoreAffixCatalog.Definition> defs = CoreAffixCatalog.defaults().stream()
                    .collect(java.util.stream.Collectors.toMap(CoreAffixCatalog.Definition::id, d -> d));
            long fallbackUsed = 0;
            long attemptSum = 0;
            List<Double> fracs = new ArrayList<>();
            int oneOver90 = 0, twoOver75 = 0, allLow = 0;
            double scoreSum = 0D;
            Map<String, Integer> picks = new TreeMap<>();
            Map<String, Integer> over75ById = new TreeMap<>();
            int legalCount = 0;
            for (int i = 0; i < SAMPLES; i++) {
                SeiiNumbers.RollOutcome outcome = SeiiNumbers.rollDiagnostic(coreTier, 0, random, tune);
                List<RuneAffix> rolled = outcome.affixes();
                assertEquals(new int[]{3, 4, 5}[coreTier - 1], rolled.size());
                java.util.Set<String> ids = new java.util.HashSet<>();
                int over75 = 0;
                double sumFrac = 0D;
                boolean anyOver90 = false;
                for (RuneAffix a : rolled) {
                    assertTrue(ids.add(a.affixId()), "duplicate id in generated core");
                    picks.merge(a.affixId(), 1, Integer::sum);
                    double frac = fractionOf(defs.get(a.affixId()), coreTier, a.value());
                    fracs.add(frac);
                    sumFrac += frac;
                    if (frac >= 0.90D) {
                        anyOver90 = true;
                    }
                    if (frac >= 0.75D) {
                        over75++;
                        over75ById.merge(a.affixId(), 1, Integer::sum);
                    }
                }
                Map<String, Double> values = CoreReachabilityValues.valuesOf(rolled);
                double score = BuildNumbers.coreScore(values, coreTier);
                assertTrue(BuildNumbers.legalCore(values, coreTier), "generated core must be legal");
                legalCount++;
                scoreSum += score;
                if (anyOver90) {
                    oneOver90++;
                }
                if (over75 >= 2) {
                    twoOver75++;
                }
                if (sumFrac / rolled.size() <= 0.25D) {
                    allLow++;
                }
                if (outcome.fallbackUsed()) {
                    fallbackUsed++;
                }
                attemptSum += outcome.attempts();
            }
            fracs.sort(Double::compareTo);
            double median = fracs.get(fracs.size() / 2);
            // 满保底成色（同一批种子另计一遍，避免移动主分布）
            RandomSource pityRandom = RandomSource.create(20261104L + coreTier * 313L);
            double pityScoreSum = 0D;
            int pitySamples = 2000;
            for (int i = 0; i < pitySamples; i++) {
                List<RuneAffix> rolled = SeiiNumbers.roll(coreTier, BalanceNumbers.RUNE_PITY_CAP,
                        pityRandom, tune);
                pityScoreSum += BuildNumbers.coreScore(CoreReachabilityValues.valuesOf(rolled), coreTier);
            }
            report.append(String.format(Locale.ROOT, "|T%d|%.4f%%|%.4f|%.4f|%.2f%%|%.2f%%|%.2f%%|%.4f|%.4f|%n",
                    coreTier, fallbackUsed / (double) SAMPLES * 100D,
                    attemptSum / (double) SAMPLES, median,
                    oneOver90 / (double) SAMPLES * 100D, twoOver75 / (double) SAMPLES * 100D,
                    allLow / (double) SAMPLES * 100D, scoreSum / SAMPLES, pityScoreSum / pitySamples));
            report.append("\nT").append(coreTier).append(" per-affix pick frequency (top):\n\n");
            report.append("|id|count|≥75% band|\n|---|---:|---:|\n");
            picks.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .forEach(e -> report.append(String.format(Locale.ROOT, "|%s|%d|%d|%n",
                            e.getKey(), e.getValue(), over75ById.getOrDefault(e.getKey(), 0))));
            report.append('\n');
        }
        Path path = Path.of("build", "reports", "build-budget", "core-generation.md");
        Files.createDirectories(path.getParent());
        Files.writeString(path, report, StandardCharsets.UTF_8);
    }

    private static double fractionOf(CoreAffixCatalog.Definition def, int tier, double value) {
        return switch (def.kind()) {
            case WEAPON_ABS, ATTR_ABS -> (value - def.loOf(tier)) / (def.hiOf(tier) - def.loOf(tier));
            case WARD_MIT -> {
                double lo = SeiiNumbers.wardExponentFor(def.loOf(tier));
                double hi = SeiiNumbers.wardExponentFor(def.hiOf(tier));
                yield (value - lo) / (hi - lo);
            }
        };
    }
}
