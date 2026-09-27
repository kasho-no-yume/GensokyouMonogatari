package com.bitsson.gensokyou.danmaku.track;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code danmaku-track-composition} 与 {@code remnant-touhou-bosses} 的静态契约测试。
 *
 * <p>这些断言是 spec 里「可判定的部分」——R1/R2/R3、轨道视觉独占、缺段约束。
 * 全部纯静态，不需世界与实体。
 */
class BossCardLintTest {

    @Test
    void allBossCardTablesPassLint() {
        assertLintClean("大妖精", BossCards.bigFairy(), BossCards.BIG_FAIRY_PALETTE);
        assertLintClean("鬼蛛", BossCards.kuzumono(), BossCards.KUZUMONO_PALETTE);
        assertLintClean("狐火", BossCards.kitsuneBi(), BossCards.KITSUNEBI_PALETTE);
        assertLintClean("傩神楽面", BossCards.nomenMask(), BossCards.NOMEN_PALETTE);
    }

    private void assertLintClean(String owner, List<SpellCard> cards, SignaturePalette palette) {
        List<String> violations = TrackLint.lint(owner, cards, palette);
        assertTrue(violations.isEmpty(), owner + " 的符卡表未通过 lint：" + violations);
    }

    /** 缺「破」= 不主动攻击：整副表 MUST NOT 含瞄准型节拍。 */
    @Test
    void kuzumonoHasNoAimedTrack() {
        assertTrue(TrackLint.hasNoAimedTrack("鬼蛛", BossCards.kuzumono()),
                "鬼蛛缺「破」，其轨道里不得出现瞄准型节拍");
    }

    /** 缺「結」= 节拍无终止条件。 */
    @Test
    void nomenMaskTracksAreAllEndless() {
        assertTrue(TrackLint.allTracksEndless(BossCards.nomenMask()),
                "傩神楽面缺「結」，其全部轨道 MUST 无终止条件");
    }

    /** 反例：缺「結」的符卡里混入有终止轨道时必须被检出。 */
    @Test
    void endlessCheckRejectsTerminatedTracks() {
        List<SpellCard> mixed = List.of(new SpellCard("拍", 1.0D, List.of(
                Track.of("有终止", 0xFFFFFF).terminates().repeatEvery(40)
                        .at(0, Shape.RING_FACING, Shape.Params.defaults()
                                .count(10).gap(60.0D).speed(0.3D), TargetMode.SELF_AXIS)
                        .build())));
        assertFalse(TrackLint.allTracksEndless(mixed));
    }

    /** R1：环形不留缺口 = 一半弹在玩家背后，必须被检出。 */
    @Test
    void ringWithoutGapViolatesR1() {
        List<SpellCard> bad = List.of(new SpellCard("无缺口环", 1.0D, List.of(
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
        List<SpellCard> bad = List.of(new SpellCard("堵死", 1.0D, List.of(
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
        List<SpellCard> ok = List.of(new SpellCard("散華", 1.0D, List.of(
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
                .at(0, Shape.DOME, Shape.Params.defaults()
                        .count(8).radius(6.0D).speed(0.3D), TargetMode.SELF_AXIS)
                .build();
        Track b = Track.of("乙", 0x222222).terminates().repeatEvery(40)
                .identity(1, 1, 1)
                .at(0, Shape.DOME, Shape.Params.defaults()
                        .count(8).radius(6.0D).speed(0.3D), TargetMode.SELF_AXIS)
                .build();
        List<SpellCard> bad = List.of(new SpellCard("撞色", 1.0D, List.of(a, b)));
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

    /** R3：单拍密度超预算必须被检出。 */
    @Test
    void overDensityViolatesR3() {
        List<SpellCard> bad = List.of(new SpellCard("爆量", 1.0D, List.of(
                Track.of("轨", 0xFFFFFF).terminates().repeatEvery(40)
                        .at(0, Shape.CAGE, Shape.Params.defaults()
                                .count(TrackLint.DENSITY_BUDGET + 1).gap(45.0D).speed(0.3D),
                                TargetMode.SELF_AXIS)
                        .build())));
        assertFalse(TrackLint.lint("坏卡", bad, SignaturePalette.of(0xFFFFFF)).isEmpty(),
                "单拍超密度预算应违反 R3");
    }

    /** 随机必须被锥包络约束。 */
    @Test
    void unconstrainedRandomIsRejected() {
        List<SpellCard> bad = List.of(new SpellCard("全向乱", 1.0D, List.of(
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

    /** 符卡数分档：大妖精/鬼蛛/狐火 3 张，傩神楽面 5 张。 */
    @Test
    void spellCardCounts() {
        assertTrue(BossCards.bigFairy().size() == 3);
        assertTrue(BossCards.kuzumono().size() == 3);
        assertTrue(BossCards.kitsuneBi().size() == 3);
        assertTrue(BossCards.nomenMask().size() == 5);
    }

    /** 符卡起始占比 MUST 随血量递减（阶段切分的前提）。 */
    @Test
    void cardThresholdsDecrease() {
        for (List<SpellCard> cards : List.of(BossCards.bigFairy(), BossCards.kuzumono(),
                BossCards.kitsuneBi(), BossCards.nomenMask())) {
            double previous = Double.MAX_VALUE;
            for (SpellCard card : cards) {
                assertTrue(card.hpFraction() <= previous,
                        "符卡「" + card.name() + "」起始占比未随血量递减");
                previous = card.hpFraction();
            }
        }
    }

    /** 弹幕密度与封死阈值是纯常量，单测环境无 config 可读，故断言常量本身。 */
    @Test
    void densityBudgetsArePositive() {
        assertTrue(TrackLint.DENSITY_BUDGET >= TrackLint.R2_SEAL_COUNT);
        assertTrue(TrackLint.MAX_TRACKS >= TrackLint.MIN_TRACKS);
        assertTrue(TrackLint.CONE_RANDOM_MAX_SPREAD > 0.0D
                && TrackLint.CONE_RANDOM_MAX_SPREAD < 360.0D);
    }
}
