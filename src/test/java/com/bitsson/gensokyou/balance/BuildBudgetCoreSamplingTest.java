package com.bitsson.gensokyou.balance;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Sampling the post-rejection distribution, not claiming raw u^3 is the output distribution. */
class BuildBudgetCoreSamplingTest {
    @Test
    void rareHighValuesAndLegalFallbackRemainPossible() throws IOException {
        StringBuilder report = new StringBuilder("# Candidate post-rejection sampling\n\n");
        report.append("Equal type weights; bounded 64 attempts; legal low-valued fallback. "
                + "100000 cores/tier; deterministic seed. Weapon multiplier and spell throughput "
                + "caps are applied, but actual multi-card resource/survival synergy still "
                + "requires timeline validation.\n\n");
        report.append("|Core|Cores|Rejected proposals|Fallbacks|Any >=90% type max|At least two >=75%|\n");
        report.append("|---|---|---|---|---|---|\n");
        for (int tier = 1; tier <= 3; tier++) {
            Random random = new Random(20261102L + tier);
            int rejected = 0;
            int fallback = 0;
            int high = 0;
            int multi = 0;
            for (int sample = 0; sample < 100000; sample++) {
                Map<String, Double> core = null;
                for (int attempt = 0; attempt < 64; attempt++) {
                    var keys = new ArrayList<>(java.util.List.of(BuildBudgetCandidate.keys()));
                    Collections.shuffle(keys, random);
                    var candidate = new LinkedHashMap<String, Double>();
                    for (int i = 0; i < tier + 2; i++) {
                        String key = keys.get(i);
                        candidate.put(key, BuildBudgetCandidate.coreMaximum(key, tier)
                                * Math.pow(random.nextDouble(), 3D));
                    }
                    if (BuildBudgetCandidate.legalCore(candidate, tier)) {
                        core = candidate;
                        break;
                    }
                    rejected++;
                }
                if (core == null) {
                    fallback++;
                    core = new LinkedHashMap<>();
                    String[] keys = BuildBudgetCandidate.keys();
                    for (int i = 0; i < tier + 2; i++) {
                        core.put(keys[i], BuildBudgetCandidate.coreMaximum(keys[i], tier) * .01D);
                    }
                }
                assertEquals(tier + 2, core.size());
                assertTrue(BuildBudgetCandidate.legalCore(core, tier));
                int good = 0;
                boolean top = false;
                for (var entry : core.entrySet()) {
                    double ratio = entry.getValue() / BuildBudgetCandidate.coreMaximum(entry.getKey(), tier);
                    if (ratio >= .9D) {
                        top = true;
                    }
                    if (ratio >= .75D) {
                        good++;
                    }
                }
                if (top) high++;
                if (good >= 2) multi++;
            }
            assertTrue(high > 0 && high < 30000);
            assertTrue(multi > 0);
            report.append(String.format(Locale.ROOT, "|%d|100000|%d|%d|%d|%d|%n",
                    tier, rejected, fallback, high, multi));
        }
        Path output = Path.of("build", "reports", "build-budget", "core-sampling.md");
        Files.createDirectories(output.getParent());
        Files.writeString(output, report, StandardCharsets.UTF_8);
    }
}
