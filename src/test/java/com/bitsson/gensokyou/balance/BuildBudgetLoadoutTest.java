package com.bitsson.gensokyou.balance;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Same-quality legal candidate cores; does not claim the production generator is implemented. */
class BuildBudgetLoadoutTest {
    static final String[][] PROFILES = {
            {"damage_pct", "crit_damage", "spirit_cost_pct"},
            {"crit_chance", "crit_damage", "spirit_cost_pct"},
            {"attack_rate_pct", "spirit_cost_pct", "damage_pct"},
            {"spell_effect_amp", "spirit_regen_rate", "spell_cost_reduction"},
            {"danmaku_reduce", "spirit_regen_rate", "damage_pct"},
            {"damage_pct", "attack_rate_pct", "spirit_cost_pct"}
    };
    static final String[] NAMES = {"heavy", "critical", "coverage", "spell", "defense", "balanced"};

    static Map<String, Double> core(int tier, int profile) {
        int coreTier = BuildNumbers.coreTier(tier);
        int count = coreTier + 2;
        Map<String, Double> values = new LinkedHashMap<>();
        // The score is a candidate generation metric, not an assumed equal-DPS budget.
        double[] strength = switch (profile) {
            case 0 -> new double[]{1D, .8D, .3D};
            case 1 -> new double[]{.9, .9, .3};
            case 2 -> new double[]{.55, .85, .2};
            case 3 -> new double[]{.65, .35, .8};
            case 5 -> new double[]{.55, .55, .65};
            default -> new double[]{.65, .65, .65};
        };
        for (int i = 0; i < 3; i++) {
            String key = PROFILES[profile][i];
            values.put(key, BuildNumbers.coreMaximum(key, coreTier) * strength[i]);
        }
        if (count >= 4) {
            String fourth = profile == 0 ? "attack_rate_pct"
                    : profile == 1 ? "damage_pct" : profile == 3 ? "range_pct"
                    : profile == 4 ? "spirit_cost_pct" : "range_pct";
            values.put(fourth, BuildNumbers.coreMaximum(fourth, coreTier)
                    * (profile == 0 || profile == 1 ? .2D : profile == 4 ? .65D : .1D));
        }
        if (count >= 5) {
            String fifth = profile == 0 ? "crit_chance" : profile == 1 ? "attack_rate_pct"
                    : profile == 2 ? "crit_chance" : profile == 3 ? "jump"
                    : profile == 4 ? "crit_chance" : "jump";
            values.put(fifth, BuildNumbers.coreMaximum(fifth, coreTier)
                    * (profile == 4 ? .4D : .1D));
        }
        // Compare the same generated-quality score, not merely the same nuclear tier.
        double scale = Math.sqrt(1D / BuildNumbers.coreScore(values, coreTier));
        values.replaceAll((key, value) -> value * scale);
        // Candidate legality must be satisfiable for every matched innate boundary, not
        // just this report's mean. Never label an invalid hand-picked profile as reachable.
        assertTrue(BuildNumbers.legalCore(values, coreTier),
                () -> "Candidate profile needs revision: " + values);
        return values;
    }

    @Test
    void candidateProfilesMeetCountAndBudgetButNotAnInventedDpsTarget() throws IOException {
        StringBuilder report = new StringBuilder("# Candidate legal loadout sweep\n\n");
        report.append("Core score=sum(weight*(value/typeMaximum)^2); weights: speed=2, "
                + "damage/crit=0.7, others=1; budgets=1.5/2/2.5. "
                + "Not a production generation probability claim. Innate values use proposed means. "
                + "Sphere + existing fox + healing garden; no food healing, consumables or external supply. "
                + "Fox base lifecycle budget=0.4 D0 * 20s per cast, lifetime=30..120s, CD=20s; "
                + "garden uses proposed 25% reference HP per cast with non-additive fields. "
                + "Cards are tested as future loadout capability, not early acquisition availability. "
                + "Periodic damage is scheduled only after successful casts; fox acquisition delay "
                + "is omitted, so this remains an optimistic full-hit bound.\n\n");
        report.append("|Tier|Profile|Armor|Core score|Weapon multiplier|Delivered DPS / D0|Starved|End pool %|Direct lethal hits|\n");
        report.append("|---|---|---|---|---|---|---|---|---|\n");
        for (int tier = 1; tier <= 5; tier++) {
            for (int profile = 0; profile < PROFILES.length; profile++) {
                var values = core(tier, profile);
                int coreTier = BuildNumbers.coreTier(tier);
                assertEquals(coreTier + 2, values.size());
                double score = BuildNumbers.coreScore(values, coreTier);
                assertTrue(score <= BuildNumbers.coreBudget(coreTier));
                assertTrue(BuildNumbers.legalCore(values, coreTier));
                double c = stat("crit_chance", tier, values);
                double k = 1D + stat("crit_damage", tier, values);
                double bonus = stat("damage_pct", tier, values);
                double speed = stat("attack_rate_pct", tier, values);
                double weaponMult = BuildBudgetMath.weaponDps(1D, bonus, speed, c, k);
                double d0 = BuildBudgetMath.referenceDps(BuildNumbers.power(tier),
                        tier <= 2 ? 1D : tier <= 4 ? 2D : 4D, 2D, 2.5D);
                for (boolean armor : new boolean[]{false, true}) {
                    var actions = actions(tier, profile, values, d0, weaponMult, speed);
                    double pool = BuildNumbers.pool(tier);
                    var plan = new BuildBudgetMath.ResourcePlan(pool, pool,
                            (pool * .0012D + values.getOrDefault("spirit_regen_rate", 0D))
                                    * (armor ? 1.5D : 1D), 0D, 0D);
                    var result = BuildBudgetMath.simulate(plan, actions, BuildNumbers.window(tier));
                    double ward = Math.min(BuildNumbers.ward(tier) - .04D * tier
                                    + BuildBudgetMath.wardSpan(3D),
                            BuildNumbers.innateMean("danmaku_reduce", tier)
                                    + values.getOrDefault("danmaku_reduce", 0D));
                    double rawHit = BuildNumbers.health(tier)
                            * Math.pow(2D, BuildNumbers.ward(tier)) / 2.5D;
                    long hits = BuildBudgetMath.lethalHits(BuildNumbers.health(tier), rawHit, ward);
                    report.append(String.format(Locale.ROOT, "|%d|%s|%s|%.4f|%.4f|%.4f|%d|%.3f|%d|%n",
                            tier, NAMES[profile], armor, score, weaponMult,
                            result.sustainedDps() / d0, result.starved(), result.finalSpirit() / pool * 100D,
                            hits));
                }
            }
        }
        Path path = Path.of("build", "reports", "build-budget", "loadout-candidates.md");
        Files.createDirectories(path.getParent());
        Files.writeString(path, report, StandardCharsets.UTF_8);
    }

    @Test
    void reportRemainingTimeAndDefenseConflictsInsteadOfApprovingByConstruction() throws IOException {
        StringBuilder report = new StringBuilder("# Cross-checks requiring calibration closure\n\n");
        report.append("These checks do not tune boss HP separately for each build. "
                + "Existing sphere DPS and paid-cast resource timelines are shared.\n\n");
        report.append("|Tier|Balanced optimistic delivered DPS/D0|Spell delivered DPS/D0|"
                + "Spell/Balance|Spell time if balance at target(s)|\n");
        report.append("|---|---|---|---|---|\n");
        for (int tier = 1; tier <= 5; tier++) {
            double d0 = BuildBudgetMath.referenceDps(BuildNumbers.power(tier),
                    tier <= 2 ? 1D : tier <= 4 ? 2D : 4D, 2D, 2.5D);
            double[] delivered = new double[2];
            for (int idx = 0; idx < 2; idx++) {
                int profile = idx == 0 ? 5 : 3;
                Map<String, Double> values = core(tier, profile);
                double speed = stat("attack_rate_pct", tier, values);
                double mult = BuildBudgetMath.weaponDps(1D, stat("damage_pct", tier, values), speed,
                        stat("crit_chance", tier, values), 1D + stat("crit_damage", tier, values));
                double pool = BuildNumbers.pool(tier);
                var result = BuildBudgetMath.simulate(new BuildBudgetMath.ResourcePlan(pool, pool,
                                (pool * .0012D + values.getOrDefault("spirit_regen_rate", 0D)) * 1.5D,
                                0D, 0D), actions(tier, profile, values, d0, mult, speed),
                        BuildNumbers.window(tier));
                delivered[idx] = result.sustainedDps();
            }
            report.append(String.format(Locale.ROOT, "|%d|%.4f|%.4f|%.4f|%.1f|%n", tier,
                    delivered[0] / d0, delivered[1] / d0, delivered[1] / delivered[0],
                    BuildNumbers.window(tier) * delivered[0] / delivered[1]));
        }
        report.append("\n## Historical shield baseline\n\n");
        report.append("Old T5 flower armor provides 15 blocked hits per 15s cooldown. "
                + "One incoming hit every 10s requires only 1.5 petals per cooldown. "
                + "The ability can negate that entire pattern even before defense amplification/CDR. "
                + "This is finite but not equivalent to a balanced threefold ward increase. "
                + "Resource cost can limit recasting, but a high-regeneration/decreased-cost build "
                + "must be checked before accepting defensive timelines.\n");
        assertEquals(15, BuildBudgetMath.lethalHits(15D, 1D, 0D));
        assertTrue(15D / 15D > 1D / 10D);
        Path path = Path.of("build", "reports", "build-budget", "remaining-cross-checks.md");
        Files.createDirectories(path.getParent());
        Files.writeString(path, report, StandardCharsets.UTF_8);
    }

    static List<BuildBudgetMath.Action> actions(int tier, int profile,
                                                       Map<String, Double> values,
                                                       double d0, double multiplier, double speed) {
        return actions(tier, profile, values, d0, multiplier, speed, BuildNumbers.window(tier));
    }

    static List<BuildBudgetMath.Action> actions(int tier, int profile,
                                                       Map<String, Double> values,
                                                       double d0, double multiplier, double speed,
                                                       double window) {
        List<BuildBudgetMath.Action> actions = new ArrayList<>();
        double nominalFrequency = 2.5D * (1D + speed);
        // Spell-first rotation intentionally spends less time firing to fund sustained casts.
        double frequency = nominalFrequency * (profile == 3 ? .5D : 1D);
        double cost = BuildNumbers.sphereCost(tier)
                * (1D - stat("spirit_cost_pct", tier, values));
        for (int shot = 0; shot / frequency < window; shot++) {
            actions.add(new BuildBudgetMath.Action(shot / frequency, cost,
                    d0 * multiplier / nominalFrequency, true));
        }
        if (profile == 3) {
            double duration = BuildNumbers.foxDuration(tier)
                    * (1D + stat("spell_duration", tier, values));
            double perSecond = Math.min(100000D, BuildNumbers.foxDamagePerSecond(tier)
                    * (1D + stat("spell_effect_amp", tier, values)));
            double cooldown = 20D * (1D - stat("spell_cdr", tier, values));
            assertTrue(duration > 0D && perSecond > 0D);
            for (double t = 0D; t < window; t += cooldown) {
                actions.add(new BuildBudgetMath.Action(t, BuildNumbers.spellBaseCost("fox_servant", tier)
                        * (1D - stat("spell_cost_reduction", tier, values)), perSecond, false,
                        1D, duration, false));
            }
        }
        if (profile == 4) {
            // Reapply only near expiration: avoids paying repeatedly for already full coverage.
            double duration = (20D + (tier - 1) * 10D)
                    * (1D + stat("spell_duration", tier, values));
            for (double t = 0D; t < window; t += duration) {
                actions.add(new BuildBudgetMath.Action(t, BuildNumbers.spellBaseCost("healing_garden", tier)
                        * (1D - stat("spell_cost_reduction", tier, values)), 0D, false));
            }
        }
        actions.sort(Comparator.comparingDouble(BuildBudgetMath.Action::atSeconds));
        return actions;
    }

    static double stat(String key, int tier, Map<String, Double> values) {
        return Math.min(BuildNumbers.cap(key, tier),
                BuildNumbers.innateMean(key, tier) + values.getOrDefault(key, 0D));
    }

    @Test
    void searchAllFourWeaponMultipliersOnDeclaredGrid() throws IOException {
        StringBuilder report = new StringBuilder("# Candidate weapon combination search\n\n");
        report.append("21^4 normalized grid for damage/speed/crit chance/crit bonus; "
                + "up to core affix count, declared weighted budget, proposed mean innate values. "
                + "Also enforced: worst-innate direct weapon multipliers <=2.5/4/6 for T1/T2/T3. "
                + "Not a continuous global proof. The grid search itself ignores resources; the winning "
                + "combo is then re-evaluated with the fixed-pool resource schedule (no armor, no leech, "
                + "no cost/regen affixes in this four-key grid) and the fixed-hit survival at innate ward. "
                + "This searches mixed allocation rather than only named loadouts.\n\n");
        report.append("|Tier|Legal grid points|Best DPS / D0|Damage normalized|Speed normalized|Chance normalized|Crit normalized|"
                + "Resource DPS / D0|Starved|End pool %|Direct lethal hits|\n");
        report.append("|---|---|---|---|---|---|---|---|---|---|---|\n");
        String[] keys = {"damage_pct", "attack_rate_pct", "crit_chance", "crit_damage"};
        for (int tier = 1; tier <= 5; tier++) {
            int coreTier = BuildNumbers.coreTier(tier);
            double best = 0D;
            int legal = 0;
            int[] winner = new int[4];
            double bestSpeed = 0D;
            Map<String, Double> winnerValues = new LinkedHashMap<>();
            for (int a = 0; a <= 20; a++) {
                for (int b = 0; b <= 20; b++) {
                    for (int c = 0; c <= 20; c++) {
                        for (int d = 0; d <= 20; d++) {
                            int[] grid = {a, b, c, d};
                            Map<String, Double> values = new LinkedHashMap<>();
                            for (int k = 0; k < 4; k++) {
                                if (grid[k] != 0) {
                                    values.put(keys[k], BuildNumbers.coreMaximum(keys[k], coreTier)
                                            * grid[k] / 20D);
                                }
                            }
                            if (!BuildNumbers.legalCore(values, coreTier)) {
                                continue;
                            }
                            legal++;
                            double damage = BuildBudgetMath.weaponDps(1D,
                                    stat(keys[0], tier, values), stat(keys[1], tier, values),
                                    stat(keys[2], tier, values), 1D + stat(keys[3], tier, values));
                            if (damage > best) {
                                best = damage;
                                winner = grid.clone();
                                bestSpeed = stat(keys[1], tier, values);
                                winnerValues = values;
                            }
                        }
                    }
                }
            }
            assertTrue(legal > 0);
            assertTrue(best < 28.6D);
            // 极端组合的真实资源 DPS（固定池 + 标准回灵；本四键网格不含减耗/回灵词条）
            double d0 = BuildBudgetMath.referenceDps(BuildNumbers.power(tier),
                    tier <= 2 ? 1D : tier <= 4 ? 2D : 4D, 2D, 2.5D);
            double pool = BuildNumbers.pool(tier);
            var scheduled = actions(tier, 5, winnerValues, d0, best, bestSpeed);
            var resource = BuildBudgetMath.simulate(new BuildBudgetMath.ResourcePlan(
                            pool, pool, pool * .0012D, 0D, 0D), scheduled, BuildNumbers.window(tier));
            // 固定受击生存：极端组合没有防御词条，仅先天护壁上界
            double ward = Math.min(BuildNumbers.ward(tier) - .04D * tier
                            + BuildBudgetMath.wardSpan(3D),
                    BuildNumbers.innateMean("danmaku_reduce", tier));
            double rawHit = BuildNumbers.health(tier)
                    * Math.pow(2D, BuildNumbers.ward(tier)) / 2.5D;
            long hits = BuildBudgetMath.lethalHits(BuildNumbers.health(tier), rawHit, ward);
            report.append(String.format(Locale.ROOT, "|%d|%d|%.4f|%.2f|%.2f|%.2f|%.2f|%.4f|%d|%.3f|%d|%n",
                    tier, legal, best, winner[0] / 20D, winner[1] / 20D, winner[2] / 20D,
                    winner[3] / 20D, resource.sustainedDps() / d0, resource.starved(),
                    resource.finalSpirit() / pool * 100D, hits));
        }
        Path path = Path.of("build", "reports", "build-budget", "weapon-search.md");
        Files.createDirectories(path.getParent());
        Files.writeString(path, report, StandardCharsets.UTF_8);
    }

    @Test
    void emitDynamicKillTimesAgainstOneFixedTargetPerTier() throws IOException {
        StringBuilder report = new StringBuilder("# Dynamic resource-constrained kill times\n\n");
        report.append("Fixed HP per tier = declared balanced reference DPS * target window. "
                + "Not retuned per build; no armor, no leech, no consumables; 3600s horizon. "
                + "Spell pulses require paid casts, travel omitted. Targets remain candidates.\n\n");
        report.append("|Tier|Profile|Target HP|Killed|Time(s)|Starved|\n|---|---|---|---|---|---|\n");
        for (int tier = 1; tier <= 5; tier++) {
            double d0 = BuildBudgetMath.referenceDps(BuildNumbers.power(tier),
                    tier <= 2 ? 1D : tier <= 4 ? 2D : 4D, 2D, 2.5D);
            var standardCore = core(tier, 5);
            double standard = BuildBudgetMath.weaponDps(d0, stat("damage_pct", tier, standardCore),
                    stat("attack_rate_pct", tier, standardCore), stat("crit_chance", tier, standardCore),
                    1D + stat("crit_damage", tier, standardCore));
            double target = standard * BuildNumbers.window(tier);
            for (int profile = 0; profile < PROFILES.length; profile++) {
                var values = core(tier, profile);
                double speed = stat("attack_rate_pct", tier, values);
                double mult = BuildBudgetMath.weaponDps(1D, stat("damage_pct", tier, values), speed,
                        stat("crit_chance", tier, values), 1D + stat("crit_damage", tier, values));
                // Repeat the schedule-generation window explicitly, rather than extrapolating its DPS.
                var schedule = actions(tier, profile, values, d0, mult, speed, 3600D);
                double pool = BuildNumbers.pool(tier);
                var result = BuildBudgetMath.simulateUntilDamage(new BuildBudgetMath.ResourcePlan(
                                pool, pool, pool * .0012D + values.getOrDefault("spirit_regen_rate", 0D),
                                0D, 0D), schedule, 3600D, target);
                report.append(String.format(Locale.ROOT, "|%d|%s|%.3f|%s|%.3f|%d|%n", tier,
                        NAMES[profile], target, result.damage() >= target,
                        result.durationSeconds(), result.starved()));
            }
        }
        Path path = Path.of("build", "reports", "build-budget", "dynamic-ttk.md");
        Files.createDirectories(path.getParent());
        Files.writeString(path, report, StandardCharsets.UTF_8);
    }

    @Test
    void emitCoreRangesCostsAndCardBudgetsForReview() throws IOException {
        StringBuilder report = new StringBuilder("# Candidate resource and card coefficients\n\n");
        report.append("Costs are based on fixed standard pool, not actual pool. Fox lifecycle "
                + "budget=0.4D0 per base-cooldown second; healing normal lifecycle=25% reference HP. "
                + "These are candidate coefficients, not production values.\n\n");
        report.append("|Tier|Sphere cost|Regen/s|Legacy armor regen/s|Fox cost|Fox damage/s|Garden cost|Garden heal/s|\n");
        report.append("|---|---|---|---|---|---|---|---|\n");
        for (int tier = 1; tier <= 5; tier++) {
            report.append(String.format(Locale.ROOT, "|%d|%.6f|%.3f|%.3f|%.3f|%.3f|%.3f|%.6f|%n", tier,
                    BuildNumbers.sphereCost(tier), BuildNumbers.pool(tier) * .0012D,
                    BuildNumbers.pool(tier) * .0018D,
                    BuildNumbers.spellBaseCost("fox_servant", tier),
                    BuildNumbers.foxDamagePerSecond(tier),
                    BuildNumbers.spellBaseCost("healing_garden", tier),
                    BuildNumbers.health(tier) * .25D / (20D + (tier - 1) * 10D)));
        }
        report.append("\nUnresolved: seven-core utility cost validation; real multi-card "
                + "rotation; actual leech allowance; fixed-hit timelines with permitted vanilla "
                + "food/potion healing; flower armor final capacity and minimum cooldown. "
                + "No approval of those missing budgets is implied.\n");
        report.append("\n## Seven-core payload-normalized cost proposal\n\n");
        report.append("Formula: sphereCost(tier) * fullTriggerPayload / 2. "
                + "Laser includes pulses; rate is not counted a second time. "
                + "Penetration/area/multitarget utility is not priced by this first-pass coefficient.\n\n");
        report.append("|Core|T1 cost|T2 cost|T3 cost|T4 cost|T5 cost|\n|---|---|---|---|---|---|\n");
        for (String key : new String[]{"qilin", "sphere", "knife", "shotgun", "talisman",
                "laser_machine", "laser_cannon"}) {
            report.append(String.format(Locale.ROOT, "|%s|%.6f|%.6f|%.6f|%.6f|%.6f|%n", key,
                    BuildNumbers.bulletCost(key, 1), BuildNumbers.bulletCost(key, 2),
                    BuildNumbers.bulletCost(key, 3), BuildNumbers.bulletCost(key, 4),
                    BuildNumbers.bulletCost(key, 5)));
        }
        Path path = Path.of("build", "reports", "build-budget", "resource-card-budget.md");
        Files.createDirectories(path.getParent());
        Files.writeString(path, report, StandardCharsets.UTF_8);
    }
}
