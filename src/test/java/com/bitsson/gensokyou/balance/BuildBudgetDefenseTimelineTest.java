package com.bitsson.gensokyou.balance;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class BuildBudgetDefenseTimelineTest {
    @Test
    void reportRecoveryAsExtraToleranceNotAsPartOfUnhealedBaseline() throws IOException {
        StringBuilder report = new StringBuilder("# Candidate survival timelines\n\n");
        report.append("Raw baseline hit = fixed reference EHP / 2.5. No avoidance. "
                + "Events at equal time heal before damage (optimistic bound). "
                + "Potion probe: two scheduled 8HP heals at 30s/90s, not unlimited supply. "
                + "Food probe: 1HP per 4s illustrative timeline, NOT an exact vanilla hunger simulation. "
                + "Garden strongest-only .25*referenceHP/baseLifetime, with +50% healing. "
                + "Shield probe: capacity 1, duration20s, cooldown120s; limits are candidates.\n\n");
        report.append("|Tier|Defense|Recovery|Incoming interval(s)|Unhealed lethal hits|Alive|Lived(s)|Blocked|Effective healing|Overheal|\n");
        report.append("|---|---|---|---|---|---|---|---|---|---|\n");
        for (int tier = 1; tier <= 5; tier++) {
            for (boolean highDefense : new boolean[]{false, true}) {
                double health = BuildNumbers.health(tier);
                double lowWard = BuildNumbers.ward(tier) - .04D * tier;
                double ward = lowWard + (highDefense ? BuildBudgetMath.wardSpan(3D) : 0D);
                double rawDamage = health * Math.pow(2D, BuildNumbers.ward(tier)) / 2.5D;
                double damage = rawDamage * Math.pow(2D, -ward);
                long lethalHits = BuildBudgetMath.lethalHits(health, rawDamage, ward);
                assertTrue(lethalHits >= 2 && lethalHits <= 8);
                for (int recovery = 0; recovery < 5; recovery++) {
                    for (double interval : new double[]{10D, 30D}) {
                        List<BuildSurvivalMath.Event> events = new ArrayList<>();
                        double window = BuildNumbers.window(tier);
                        for (int hit = 1; hit * interval <= window; hit++) {
                            events.add(new BuildSurvivalMath.Event(hit * interval, damage, 0D, 0));
                        }
                        switch (recovery) {
                            case 1 -> {
                                for (int second = 4; second <= window; second += 4) {
                                    events.add(new BuildSurvivalMath.Event(second, 0D, 1D, 0));
                                }
                            }
                            case 2 -> {
                                events.add(new BuildSurvivalMath.Event(30D, 0D, 8D, 0));
                                events.add(new BuildSurvivalMath.Event(90D, 0D, 8D, 0));
                            }
                            case 3 -> {
                                double heal = health * .25D / (20D + (tier - 1) * 10D) * 1.5D;
                                for (int second = 1; second <= window; second++) {
                                    events.add(new BuildSurvivalMath.Event(second, 0D, heal, 0));
                                }
                            }
                            case 4 -> {
                                for (int time = 0; time <= window; time += 120) {
                                    events.add(new BuildSurvivalMath.Event(time, 0D, 0D, 1, time + 20D));
                                }
                            }
                            default -> { }
                        }
                        var result = BuildSurvivalMath.simulate(health, events, window);
                        report.append(String.format(Locale.ROOT, "|%d|%s|%d|%.0f|%d|%s|%.1f|%d|%.3f|%.3f|%n",
                                tier, highDefense ? "upper" : "lower", recovery, interval, lethalHits,
                                result.survived(), result.livedSeconds(), result.blocked(),
                                result.effectiveHealing(), result.overhealing()));
                    }
                }
            }
        }
        report.append("\nThese are survival-only probes: assuming every scheduled garden/shield "
                + "cast succeeds is optimistic and must be coupled to paid-cast resources before "
                + "claiming a playable defense build. Recovery may legitimately increase total "
                + "hits beyond eight; the baseline constraint does not forbid this.\n");
        Path path = Path.of("build", "reports", "build-budget", "survival-timelines.md");
        Files.createDirectories(path.getParent());
        Files.writeString(path, report, StandardCharsets.UTF_8);
    }

    @Test
    void recoveryTimelineUsesOnlyPaidCasts() throws IOException {
        StringBuilder report = new StringBuilder("# Resource-coupled defense probe\n\n");
        report.append("Fixed lower/upper ward boundaries, no innate avoidance. Sphere fires "
                + "at 50% duty to reserve spirit. Garden minimum CD60s, shield minimum CD120s; "
                + "shield base1/cap2, garden+50% healing. These limits remain candidates. "
                + "Resource timeline does not stop on death: survival does, so no future "
                + "healing can resurrect a dead player. No food/potions/external supply.\n\n");
        report.append("|Tier|Upper ward|Armor|Paid casts|Starved actions|Survived|Lived(s)|Blocked|Effective heal|\n");
        report.append("|---|---|---|---|---|---|---|---|---|\n");
        for (int tier = 1; tier <= 5; tier++) {
            double pool = BuildNumbers.pool(tier);
            double window = BuildNumbers.window(tier);
            List<BuildBudgetMath.Action> actions = new ArrayList<>();
            java.util.Map<BuildBudgetMath.Action, String> kinds = new java.util.HashMap<>();
            for (int shot = 0; shot / 1.25D < window; shot++) {
                actions.add(new BuildBudgetMath.Action(shot / 1.25D,
                        BuildNumbers.sphereCost(tier), 0D, true));
            }
            for (int time = 0; time < window; time += 60) {
                var cast = new BuildBudgetMath.Action(time,
                        BuildNumbers.spellBaseCost("healing_garden", tier), 0D, false);
                actions.add(cast);
                kinds.put(cast, "garden");
            }
            for (int time = 0; time < window; time += 120) {
                var cast = new BuildBudgetMath.Action(time,
                        BuildNumbers.spellBaseCost("flower_armor", tier), 0D, false);
                actions.add(cast);
                kinds.put(cast, "shield");
            }
            actions.sort(java.util.Comparator.comparingDouble(BuildBudgetMath.Action::atSeconds));
            for (boolean armor : new boolean[]{false, true}) {
                List<BuildBudgetMath.Action> paid = new ArrayList<>();
                var resource = BuildBudgetMath.simulateUntilDamage(new BuildBudgetMath.ResourcePlan(
                                pool, pool, pool * .0012D * (armor ? 1.5D : 1D), 0D, 0D),
                        actions, window, Double.POSITIVE_INFINITY, action -> {
                            if (!action.weapon()) paid.add(action);
                        });
                for (boolean highWard : new boolean[]{false, true}) {
                    double hp = BuildNumbers.health(tier);
                    double ward = BuildNumbers.ward(tier) - .04D * tier
                            + (highWard ? BuildBudgetMath.wardSpan(3D) : 0D);
                    double damage = hp * Math.pow(2D, BuildNumbers.ward(tier) - ward) / 2.5D;
                    List<BuildSurvivalMath.Event> events = new ArrayList<>();
                    for (int hit = 1; hit * 30D <= window; hit++) {
                        events.add(new BuildSurvivalMath.Event(hit * 30D, damage, 0D, 0));
                    }
                    double[] strongestHealing = new double[(int) window + 1];
                    for (var cast : paid) {
                        if (kinds.get(cast).equals("shield")) {
                            events.add(new BuildSurvivalMath.Event(cast.atSeconds(), 0D, 0D,
                                    2, cast.atSeconds() + 20D));
                        } else {
                            double duration = 20D + (tier - 1) * 10D;
                            double healing = hp * .25D / duration * 1.5D;
                            for (int second = (int) cast.atSeconds() + 1;
                                 second <= Math.min(window, cast.atSeconds() + duration); second++) {
                                strongestHealing[second] = Math.max(strongestHealing[second], healing);
                            }
                        }
                    }
                    for (int second = 1; second < strongestHealing.length; second++) {
                        if (strongestHealing[second] > 0D) {
                            events.add(new BuildSurvivalMath.Event(second, 0D, strongestHealing[second], 0));
                        }
                    }
                    var survival = BuildSurvivalMath.simulate(hp, events, window);
                    report.append(String.format(Locale.ROOT, "|%d|%s|%s|%d|%d|%s|%.1f|%d|%.3f|%n",
                            tier, highWard, armor, paid.size(), resource.starved(), survival.survived(),
                            survival.livedSeconds(), survival.blocked(), survival.effectiveHealing()));
                }
            }
        }
        Path path = Path.of("build", "reports", "build-budget", "coupled-defense.md");
        Files.createDirectories(path.getParent());
        Files.writeString(path, report, StandardCharsets.UTF_8);
    }
}
