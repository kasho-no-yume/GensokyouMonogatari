package com.bitsson.gensokyou.balance;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * 控制/召唤/增益卡片的最大占空（uptime）报告——校准关卡审核用。
 *
 * <p>口径：每张卡 × 玩家阶（CDR 封顶影响有效冷却）× 卡片满品时长；含旧 spell_cdr 结算、
 * 不含资源充分性（无限灵力）与 aim/索敌损失。覆盖率 ≥ 100% 表示可无缝常驻（perma-control 风险），
 * 与「CDR 不得突破卡片最短冷却」规则一起审核——卡片有专属最短冷却时以它封盖。
 */
class BuildControlCoverageTest {

    private record Case(String id, String name, String kind,
                        double baseDurationSeconds, boolean extendable,
                        int baseCooldownTicks) {
        double maxDuration(int tier) {
            double ext = extendable ? BuildNumbers.cap("spell_duration", tier) : 0D;
            return baseDurationSeconds * (1D + ext);
        }

        double effectiveCooldownSeconds(int tier) {
            double cd = baseCooldownTicks / 20D;
            double reduced = Math.max(1L, Math.round(cd * 20D * (1D - BuildNumbers.cap("spell_cdr", tier)))) / 20D;
            long minTicks = SpellParameterPolicies.card(id).minimumCooldownTicks();
            return minTicks > 0L ? Math.max(reduced, minTicks / 20D) : reduced;
        }
    }

    @Test
    void emitControlCoverageReport() throws IOException {
        StringBuilder report = new StringBuilder("# 控制/召唤/增益最大占空报告 (control-coverage)\n\n");
        report.append("口径：满品卡 × 玩家阶（CDR 按该阶封顶）；时长含 +300% 上限的延长（对可延长卡）；");
        report.append("冷却取「CDR 折减后」与「卡片专属最短冷却」的较大者；不列资源充分性与命中损失。\n");
        report.append("覆盖率 = 最大持续 / 有效冷却；≥ 100% = 可无缝常驻。\n\n");
        List<Case> cases = List.of(
                new Case("demarcation", "黑暗结界", "控制", BalanceNumbers.DEMARCATION_DURATION_SECONDS, false,
                        BalanceNumbers.DEMARCATION_COOLDOWN),
                new Case("spider_web", "蛛网", "控制", BalanceNumbers.SPIDER_WEB_DURATION_SECONDS.get(4), false,
                        BalanceNumbers.SPIDER_WEB_COOLDOWN),
                new Case("plague_repay", "疫符", "控制/免疫", BalanceNumbers.PLAGUE_REPAY_DURATION_SECONDS.get(4), false,
                        BalanceNumbers.PLAGUE_REPAY_COOLDOWN),
                new Case("healing_garden", "花园", "治疗", BalanceNumbers.HEALING_GARDEN_DURATION_SECONDS.get(4), true,
                        BalanceNumbers.HEALING_GARDEN_COOLDOWN),
                new Case("flower_armor", "花瓣", "防御", BalanceNumbers.FLOWER_ARMOR_DURATION_SECONDS, true,
                        BalanceNumbers.FLOWER_ARMOR_COOLDOWN),
                new Case("musou_fuuin", "无想封印", "输出", BalanceNumbers.MUSOU_DURATION_TICKS / 20D, true,
                        BalanceNumbers.SKILL_MUSOU_COOLDOWN),
                new Case("fox_servant", "狐召唤", "召唤", BuildNumbers.foxDuration(5), true,
                        BalanceNumbers.FOX_SERVANT_COOLDOWN));
        report.append("| 卡 | 类型 | 时长(满品) | 时长含+300%延长(5阶) | 基础CD | 专属最短冷却 | 有效CD@5阶(CDR60%) | 覆盖率@5阶 | 结论 |\n");
        report.append("|---|---|---|---|---|---|---|---|---|\n");
        for (Case c : cases) {
            long minTicks = SpellParameterPolicies.card(c.id).minimumCooldownTicks();
            double maxDur = c.maxDuration(5);
            double effCd = c.effectiveCooldownSeconds(5);
            double coverage = maxDur / effCd;
            String verdict = coverage >= 1.0D ? "**FULL**" : coverage >= 0.75D ? "HIGH" : "OK";
            report.append(String.format(Locale.ROOT, "| %s | %s | %.0fs | %.0fs | %.1fs | %s | %.1fs | %.1f%% | %s |\n",
                    c.name, c.kind, c.baseDurationSeconds, maxDur,
                    c.baseCooldownTicks / 20D,
                    minTicks > 0L ? String.format(Locale.ROOT, "%.0fs", minTicks / 20D) : "无",
                    effCd, coverage * 100D, verdict));
        }
        report.append("\n按阶明细（黑暗结界 / 蛛网 / 疫符；满品时长、该阶 CDR 封顶、无专属最短冷却）：\n\n");
        report.append("| 阶 | 黑暗结界 覆盖率 | 蛛网 覆盖率 | 疫符 覆盖率 |\n|---|---|---|---|\n");
        for (int tier = 1; tier <= 5; tier++) {
            double dem = coverage(c( BalanceNumbers.DEMARCATION_DURATION_SECONDS, false,
                    BalanceNumbers.DEMARCATION_COOLDOWN, tier), tier);
            double spider = coverage(c(BalanceNumbers.SPIDER_WEB_DURATION_SECONDS.get(tier - 1), false,
                    BalanceNumbers.SPIDER_WEB_COOLDOWN, tier), tier);
            double plague = coverage(c(BalanceNumbers.PLAGUE_REPAY_DURATION_SECONDS.get(tier - 1), false,
                    BalanceNumbers.PLAGUE_REPAY_COOLDOWN, tier), tier);
            report.append(String.format(Locale.ROOT, "| %d | %.0f%% | %.0f%% | %.0f%% |\n",
                    tier, dem * 100D, spider * 100D, plague * 100D));
        }
        report.append("\n说明：花园/花瓣有专属最短冷却（60s/120s），无想/狐为输出/召唤向且吃 +300% 延长上限；");
        report.append("全部卡均按无限灵力计算，不含成本——成本不阻止任何一条达成全覆盖（资源约束见 resource-candidates.md）。\n");
        Path path = Path.of("build", "reports", "build-budget", "control-coverage.md");
        Files.createDirectories(path.getParent());
        Files.writeString(path, report, StandardCharsets.UTF_8);
    }

    private static Case c(double duration, boolean extendable, int cooldownTicks, int tier) {
        return new Case("x_" + tier, "x", "x", duration, extendable, cooldownTicks);
    }

    private static double coverage(Case c, int tier) {
        return c.maxDuration(tier) / c.effectiveCooldownSeconds(tier);
    }
}
