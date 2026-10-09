package com.bitsson.gensokyou.danmaku.track;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code danmaku-track-composition} 与 {@code remnant-touhou-bosses} 的静态契约测试。
 *
 * <p>这些断言是 spec 里「可判定的部分」——R1/R2/R3、轨道视觉独占、签名色盘容量。
 * 全部纯静态，不需世界与实体。
 */
class BossCardLintTest {

    @Test
    void allBossCardTablesPassLint() {
        assertLintClean("大妖精", BossCards.bigFairy(), BossCards.BIG_FAIRY_PALETTE);
        assertLintClean("黑谷山女", BossCards.yamame(), BossCards.YAMAME_PALETTE);
        assertLintClean("狐火", BossCards.kitsuneBi(), BossCards.KITSUNEBI_PALETTE);
        assertLintClean("傩神楽面", BossCards.nomenMask(), BossCards.NOMEN_PALETTE);
    }

    private void assertLintClean(String owner, List<SpellCard> cards, SignaturePalette palette) {
        List<String> violations = TrackLint.lint(owner, cards, palette);
        assertTrue(violations.isEmpty(), owner + " 的符卡表未通过 lint：" + violations);
    }

    /**
     * 在役符卡的稳态并发密度 SHALL 留有余量。
     *
     * <p>本条存在的理由：R3 预算是一个<b>常量</b>（{@link TrackLint#STEADY_STATE_BUDGET}），
     * 而符卡表是持续生长的。若现有卡已经贴着预算，下一个作者加一条轨就会撞线，
     * 而撞线的表现是「符卡表编译期失败」——容易被误当成代码错误。
     * 留一半余量把这个问题提前暴露成可见的数字。
     *
     * <p><b>声明了密度豁免的卡不参与本条</b>，改由 {@link #densityWaiversAreExplicitAndBounded()}
     * 约束：豁免必须显式、必须有据、且被封顶在预算的 8 倍以内。豁免的存在本身就是
     * 「这条估值算错了」的记录，混进余量统计会让这条断言失去意义。
     */
    @Test
    void shippedCardsHaveDensityHeadroom() {
        record Entry(String owner, List<SpellCard> cards) {
        }
        List<Entry> tables = List.of(
                new Entry("大妖精", BossCards.bigFairy()),
                new Entry("黑谷山女", BossCards.yamame()),
                new Entry("狐火", BossCards.kitsuneBi()),
                new Entry("傩神楽面", BossCards.nomenMask()));
        double worst = 0.0D;
        String worstWhere = "-";
        for (Entry entry : tables) {
            for (SpellCard card : entry.cards()) {
                if (card.hasDensityWaiver()) {
                    continue;
                }
                double steady = 0.0D;
                for (Track track : card.tracks()) {
                    steady += TrackLint.steadyStateEstimate(track);
                }
                if (steady > worst) {
                    worst = steady;
                    worstWhere = entry.owner() + "/「" + card.name().getString() + "」";
                }
            }
        }
        double ceiling = TrackLint.STEADY_STATE_BUDGET * 0.5D;
        assertTrue(worst <= ceiling, String.format(
                "在役符卡稳态并发密度最高为 %.1f（%s），超过预算的一半（%.0f / 预算 %.0f）。"
                        + "符卡表继续增长会撞线，请复核预算取值或降低该卡密度。",
                worst, worstWhere, ceiling, TrackLint.STEADY_STATE_BUDGET));
    }

    /**
     * 密度豁免 MUST 显式且有界。
     *
     * <p>豁免是「这条估值对这张卡结构性失真」的记录，不是让人随手放宽预算的口子。
     * 本条把它的用法钉死三件事：数量少（一张表至多一处）、有据（必须落在源码注释里）、
     * 有界（不超过预算的 8 倍）。越界的写法 SHOULD 在 lint 阶段就红。
     */
    @Test
    void densityWaiversAreExplicitAndBounded() {
        int waivers = 0;
        for (SpellCard card : BossCards.all()) {
            if (!card.hasDensityWaiver()) {
                continue;
            }
            waivers++;
            assertTrue(card.densityWaiver() <= TrackLint.STEADY_STATE_BUDGET
                            * TrackLint.MAX_DENSITY_WAIVER_FACTOR,
                    "符卡「" + card.name().getString() + "」的密度豁免 " + card.densityWaiver()
                            + " 超过预算的 " + (int) TrackLint.MAX_DENSITY_WAIVER_FACTOR + " 倍");
            double steady = 0.0D;
            for (Track track : card.tracks()) {
                steady += TrackLint.steadyStateEstimate(track);
            }
            // 豁免必须贴着实际值：报一个远高于实数的预算等于给后人留一把没锁的钥匙。
            assertTrue(card.densityWaiver() < steady * 1.5D,
                    "符卡「" + card.name().getString() + "」声明的豁免 " + card.densityWaiver()
                            + " 远高于实际密度 " + steady + "，应贴着实际值报");
        }
        assertTrue(waivers <= 1,
                "全表至多允许一处密度豁免，当前 " + waivers + " 处——每多一处都意味着"
                        + "估值模型又有一处结构性失真该先修");
    }

    /** R1：环形不留缺口 = 一半弹在玩家背后，必须被检出。 */
    @Test
    void ringWithoutGapViolatesR1() {
        List<SpellCard> bad = List.of(new SpellCard(Component.literal("无缺口环"), 1.0D, List.of(
                Track.of("环", 0xFFFFFF).terminates().repeatEvery(40)
                        .at(0, Shape.RING_FACING, Shape.Params.defaults()
                                .count(10).gap(0.0D).speed(0.3D), TargetMode.SELF_AXIS)
                        .build())));
        assertFalse(TrackLint.lint("坏卡", bad, SignaturePalette.of(0xFFFFFF)).isEmpty(),
                "gapDeg=0 的环形应违反 R1 前向威胁");
    }

    /** R2：自轴型在足够密度下不留任何间隙必须被检出。 */
    @Test
    void denseSelfAxisWithoutGapViolatesR2() {
        List<SpellCard> bad = List.of(new SpellCard(Component.literal("堵死"), 1.0D, List.of(
                Track.of("轨", 0xFFFFFF).terminates().repeatEvery(40)
                        .at(0, Shape.CONE_RANDOM, Shape.Params.defaults()
                                .count(TrackLint.R2_SEAL_COUNT + 2).spread(60.0D).speed(0.3D),
                                TargetMode.SELF_AXIS)
                        .build())));
        assertFalse(TrackLint.lint("坏卡", bad, SignaturePalette.of(0xFFFFFF)).isEmpty(),
                "高密度且无间隙的自轴型应违反 R2 解法全向");
    }

    /** 稀疏的自轴型扇（教学档）不构成封死——R2 是密度判据，不是形状身份。 */
    @Test
    void sparseSelfAxisFanPassesR2() {
        List<SpellCard> ok = List.of(new SpellCard(Component.literal("散華"), 1.0D, List.of(
                Track.of("扇", 0xFFFFFF).terminates().repeatEvery(40)
                        .at(0, Shape.FAN, Shape.Params.defaults()
                                .count(5).spread(60.0D).speed(0.3D), TargetMode.SELF_AXIS)
                        .build())));
        assertTrue(TrackLint.lint("稀疏扇", ok, SignaturePalette.of(0xFFFFFF)).isEmpty());
    }

    /** 轨道视觉独占：两轨标识全同必须被检出。 */
    @Test
    void identicalTrackIdentitiesAreRejected() {
        Track a = Track.of("甲", 0x111111).terminates().repeatEvery(40)
                .identity(1, 1, 1)
                .at(0, Shape.SHELL, Shape.Params.defaults()
                        .count(8).radius(6.0D).speed(0.3D), TargetMode.SELF_AXIS)
                .build();
        Track b = Track.of("乙", 0x222222).terminates().repeatEvery(40)
                .identity(1, 1, 1)
                .at(0, Shape.SHELL, Shape.Params.defaults()
                        .count(8).radius(6.0D).speed(0.3D), TargetMode.SELF_AXIS)
                .build();
        List<SpellCard> bad = List.of(new SpellCard(Component.literal("撞色"), 1.0D, List.of(a, b)));
        assertFalse(TrackLint.lint("坏卡", bad, SignaturePalette.of(0x111111, 0x222222)).isEmpty(),
                "视觉标识完全相同的两轨应被检出");
    }

    /** 色盘容量不足必须被检出。 */
    @Test
    void undersizedPaletteIsRejected() {
        List<SpellCard> cards = BossCards.nomenMask();
        List<String> violations = TrackLint.lint("傩神楽面", cards, SignaturePalette.of(0x000001));
        assertFalse(violations.isEmpty(), "色盘容量不足应被检出");
    }

    /**
     * R3：稳态并发密度超预算必须被检出。
     *
     * <p>刻意用<b>低弹速 + 高每拍发数</b>的组合：这类图案每拍发数不算夸张，
     * 但弹在玩家包络内停留很久，稳态并发会堆起来。只按每拍发数判定会漏掉它。
     *
     * <p>用 {@link Shape#RING_HORIZONTAL} + 留缺口，故 R1/R2 均不触发，
     * 断言只反映 R3。
     */
    @Test
    void overSteadyStateDensityViolatesR3() {
        List<SpellCard> bad = List.of(new SpellCard(Component.literal("爆量"), 1.0D, List.of(
                Track.of("轨", 0xFFFFFF).terminates().repeatEvery(10)
                        .at(0, Shape.RING_HORIZONTAL, Shape.Params.defaults()
                                .count(200).gap(45.0D).speed(0.2D),
                                TargetMode.SELF_AXIS)
                        .build())));
        assertFalse(TrackLint.lint("坏卡", bad, SignaturePalette.of(0xFFFFFF)).isEmpty(),
                "稳态并发超预算应违反 R3");
    }

    /**
     * R3 反向断言：<b>持续型图案</b>每拍发数少但稳态并发高，必须被检出。
     *
     * <p>这条是本次量纲修正的核心——旧判据（每拍发数 ≤ 48）会放过它。
     */
    @Test
    void sustainedPatternCaughtByPerBeatCountAlone() {
        int perBeatCount = 20;                                  // 远低于旧的单拍预算 48
        List<SpellCard> sustained = List.of(new SpellCard(Component.literal("壁"), 1.0D, List.of(
                Track.of("轨", 0xFFFFFF).terminates().repeatEvery(5)
                        .at(0, Shape.RING_HORIZONTAL, Shape.Params.defaults()
                                .count(perBeatCount).gap(45.0D).speed(0.2D),
                                TargetMode.SELF_AXIS)
                        .build())));
        assertTrue(perBeatCount < 48, "本用例的前提是每拍发数低于旧单拍预算");
        assertFalse(TrackLint.lint("弹幕墙", sustained, SignaturePalette.of(0xFFFFFF)).isEmpty(),
                "每拍发数不高但稳态并发超预算的持续型图案应违反 R3");
    }

    /**
     * R3 反向断言：<b>瞬发型图案</b>每拍发数高但弹速极快（迅速离场），
     * 稳态并发低，MUST NOT 被误判。
     *
     * <p>这条是修正另一半——旧判据会把「200 颗一发出去就飞走」误判为超预算。
     */
    @Test
    void fastOneShotPatternNotOverFlagged() {
        List<SpellCard> fast = List.of(new SpellCard(Component.literal("连射"), 1.0D, List.of(
                Track.of("轨", 0xFFFFFF).terminates().repeatEvery(5)
                        .at(0, Shape.RING_HORIZONTAL, Shape.Params.defaults()
                                .count(100).gap(45.0D).speed(3.0D),
                                TargetMode.SELF_AXIS)
                        .build())));
        List<String> violations = TrackLint.lint("连射", fast, SignaturePalette.of(0xFFFFFF));
        assertTrue(violations.stream().noneMatch(v -> v.contains("R3")),
                "弹速极快的瞬发型图案不应被判 R3 超限，实际: " + violations);
    }

    /** {@code CAGE} 单拍实际发数是 count 的三倍，密度估值 MUST 反映该倍率。 */
    @Test
    void cageDensityCountsTripleEmission() {
        double ring = TrackLint.steadyStateEstimate(ringTrack(100, 0.5D, 5));
        double cage = TrackLint.steadyStateEstimate(Track.of("笼", 0xFFFFFF).terminates()
                .repeatEvery(5)
                .at(0, Shape.CAGE, Shape.Params.defaults()
                        .count(100).gap(45.0D).speed(0.5D), TargetMode.SELF_AXIS)
                .build());
        assertEquals(ring * 3.0D, cage, 1.0E-6D,
                "CAGE 发出三个正交面，密度估值应为同参数环的三倍");
    }

    private static Track ringTrack(int count, double speed, int repeatEvery) {
        return Track.of("环", 0xFFFFFF).terminates().repeatEvery(repeatEvery)
                .at(0, Shape.RING_HORIZONTAL, Shape.Params.defaults()
                        .count(count).gap(45.0D).speed(speed), TargetMode.SELF_AXIS)
                .build();
    }

    /** 稳态估值随弹速单调下降（弹越快，包络内停留越短）。 */
    @Test
    void steadyStateDecreasesWithSpeed() {
        double slow = TrackLint.steadyStateEstimate(trackWith(20, 0.2D, 5));
        double fast = TrackLint.steadyStateEstimate(trackWith(20, 2.0D, 5));
        assertTrue(slow > fast,
                "慢弹稳态并发应高于快弹: slow=" + slow + " fast=" + fast);
    }

    private static Track trackWith(int count, double speed, int repeatEvery) {
        return Track.of("轨", 0xFFFFFF).terminates().repeatEvery(repeatEvery)
                .at(0, Shape.FAN, Shape.Params.defaults().count(count).spread(60.0D).speed(speed),
                        TargetMode.SELF_AXIS)
                .build();
    }

    /** 随机必须被锥包络约束。 */
    @Test
    void unconstrainedRandomIsRejected() {
        List<SpellCard> bad = List.of(new SpellCard(Component.literal("全向乱"), 1.0D, List.of(
                Track.of("轨", 0xFFFFFF).terminates().repeatEvery(40)
                        .at(0, Shape.CONE_RANDOM, Shape.Params.defaults()
                                .count(6).spread(360.0D).speed(0.3D), TargetMode.SELF_AXIS)
                        .build())));
        assertFalse(TrackLint.lint("坏卡", bad, SignaturePalette.of(0xFFFFFF)).isEmpty(),
                "全向无约束随机应违反 R1");
    }

    /** 瞄准型按人复制，自轴型不复制——单人难度不随人数线性加难。 */
    @Test
    void targetModeCopySemantics() {
        assertTrue(TargetMode.AIMED.copiesPerTarget());
        assertFalse(TargetMode.SELF_AXIS.copiesPerTarget());
        assertFalse(TargetMode.ARENA.copiesPerTarget());
    }

    /** 符卡数分档：大妖精/狐火 3 张、黑谷山女 4 张，傩神楽面 5 张。 */
    @Test
    void spellCardCounts() {
        assertTrue(BossCards.bigFairy().size() == 3);
        assertTrue(BossCards.yamame().size() == 4);
        assertTrue(BossCards.kitsuneBi().size() == 3);
        assertTrue(BossCards.nomenMask().size() == 5);
    }

    /** 符卡起始占比 MUST 随血量递减（阶段切分的前提）。 */
    @Test
    void cardThresholdsDecrease() {
        for (List<SpellCard> cards : List.of(BossCards.bigFairy(), BossCards.yamame(),
                BossCards.kitsuneBi(), BossCards.nomenMask())) {
            double previous = Double.MAX_VALUE;
            for (SpellCard card : cards) {
                assertTrue(card.hpFraction() <= previous,
                        "符卡「" + card.name().getString() + "」起始占比未随血量递减");
                previous = card.hpFraction();
            }
        }
    }

    /** R3 预算是稳态并发量，SHALL 大于 R2 的封死阈值。 */
    @Test
    void steadyStateBudgetExceedsSealCount() {
        assertTrue(TrackLint.STEADY_STATE_BUDGET > TrackLint.R2_SEAL_COUNT);
    }

    /** 弹幕密度与封死阈值是纯常量，单测环境无 config 可读，故断言常量本身。 */
    @Test
    void densityBudgetsArePositive() {
        assertTrue(TrackLint.MAX_TRACKS >= TrackLint.MIN_TRACKS);
        assertTrue(TrackLint.CONE_RANDOM_MAX_SPREAD > 0.0D
                && TrackLint.CONE_RANDOM_MAX_SPREAD < 360.0D);
    }
}
