package com.bitsson.gensokyou.danmaku.track;

import com.bitsson.gensokyou.danmaku.motion.DanmakuSpeedProfile;
import com.bitsson.gensokyou.danmaku.motion.FormationFrame;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 编队帧的<b>声明侧</b>约束——纯数据，不需要世界。
 *
 * <p>这批断言守三件事：互斥关系（编队与「改速度 / 清零速度」的运动不能同开）、
 * 烘焙的正确性（声明 + 出生点 ⇒ 逐字段无损的帧）、以及「一轨一帧、编队数不超轨道数」。
 *
 * <p>最后一条之所以是断言而不只是约定：编队数一旦失控，同步开销与视觉混乱都会
 * 线性增长，而症状（卡顿 / 看不清有几队）都指向别处。
 */
class BehaviourFormationTest {

    private static Behaviour.Formation spinning() {
        return Behaviour.Formation.spin(0, FormationFrame.HORIZONTAL_PITCH_DEG, 3.0D);
    }

    private static Behaviour.Formation breathing() {
        return Behaviour.Formation.breathing(0, FormationFrame.HORIZONTAL_PITCH_DEG,
                1.5D, 1.0D, 0.4D, 40.0D);
    }

    // ------------------------------------------------------------------
    // 烘焙
    // ------------------------------------------------------------------

    /**
     * 声明 MUST 逐字段无损地烘成帧——中间任何一层「顺手给个默认值」都会让花变形。
     *
     * <p>特别检查中心与偏移的差值关系：偏移是<b>相对中心</b>的，符号搞反不会崩，
     * 只会让整队飞到编队中心的镜像位置去。
     */
    @Test
    void declarationBakesIntoFrameWithoutLoss() {
        Vec3 center = new Vec3(120.5D, 64.0D, -300.25D);
        Vec3 origin = new Vec3(124.0D, 66.0D, -296.0D);
        FormationFrame frame = spinning().frameFor(center, origin);

        assertEquals(120.5D, frame.centerX(), 1.0E-12, "编队中心 x");
        assertEquals(64.0D, frame.centerY(), 1.0E-12, "编队中心 y");
        assertEquals(-300.25D, frame.centerZ(), 1.0E-12, "编队中心 z");
        assertEquals(3.5D, frame.offsetX(), 1.0E-12, "偏移 = 出生点 − 中心（x）");
        assertEquals(2.0D, frame.offsetY(), 1.0E-12, "偏移（y）");
        assertEquals(4.25D, frame.offsetZ(), 1.0E-12, "偏移（z）");
        assertEquals(3.0D, frame.rotRateDegPerTick(), 1.0E-12, "旋转角速度");
        assertTrue(frame.active(), "声明为启用时烘出的帧 MUST 也是启用的");
    }

    /** 出生点等于中心 ⇒ 偏移为零 ⇒ 弹钉在中心不动（这正是「花蕊」的数学形态）。 */
    @Test
    void birthAtCenterProducesPinnedStamen() {
        Vec3 center = new Vec3(10, 20, 30);
        FormationFrame frame = breathing().frameFor(center, center);
        assertEquals(Vec3.ZERO, frame.offset());
        for (int t = 0; t < 120; t++) {
            assertEquals(center, frame.framePositionAt(t),
                    "tick " + t + " 处花蕊被花带着飘走了");
        }
    }

    // ------------------------------------------------------------------
    // 互斥
    // ------------------------------------------------------------------

    /**
     * 编队帧与「清零速度」「改写速度向量」的运动互斥。
     *
     * <p>注意 {@code SPEED_PROFILE} <b>不</b>互斥：它是编队弹「沿弹道推进」那一项的
     * 来源，与编队帧相加。把��误判成互斥会让「边编队边推进」这个最常见的组合写不出来。
     */
    @Test
    void formationConflictsWithVelocitySeizingMotionsOnly() {
        Behaviour.Formation formation = breathing();
        assertTrue(formation.conflictsWith(Behaviour.Motion.mine(2.0D)),
                "溜め靠清零速度表达语义，会被编队帧每 tick 覆盖");
        assertTrue(formation.conflictsWith(Behaviour.Motion.hover(20)),
                "悬停同理：到点清零速度与编队帧争夺位置权威");
        assertTrue(formation.conflictsWith(Behaviour.Motion.curve(10.0D, 5.0D, 30.0D)),
                "曲射改写速度向量，在编队帧下会静默失效");

        assertFalse(formation.conflictsWith(Behaviour.Motion.none()));
        assertFalse(formation.conflictsWith(Behaviour.Motion.groundHug()));
        assertFalse(formation.conflictsWith(
                        Behaviour.Motion.speedProfile(DanmakuSpeedProfile.constant(0.3D))),
                "速率曲线是编队弹推进项的来源，不该被判为互斥");
    }

    /** 未启用编队时，任何运动都不构成冲突——否则 lint 会对全部既有符卡误报。 */
    @Test
    void disabledFormationNeverConflicts() {
        assertFalse(Behaviour.Formation.NONE.active());
        assertFalse(Behaviour.Formation.NONE.conflictsWith(Behaviour.Motion.mine(2.0D)));
        assertFalse(Behaviour.Formation.NONE.conflictsWith(Behaviour.Motion.curve(10.0D, 5.0D, 30.0D)));
        assertFalse(Behaviour.Formation.NONE.conflictsWith(Behaviour.Motion.hover(20)));
    }

    // ------------------------------------------------------------------
    // lint
    // ------------------------------------------------------------------

    /** lint MUST 真的报出冲突，否则「静态拒绝」等于没有。 */
    @Test
    void lintRejectsFormationCombinedWithConflictingMotion() {
        SpellCard bad = new SpellCard("编队+溜め", 1.0D,
                List.of(Track.of("轨", 0)
                        .formation(breathing())
                        .at(0, Shape.SCATTER_STATIC, Shape.Params.defaults(),
                                new Behaviour(Behaviour.Motion.mine(2.0D),
                                        Behaviour.Split.none(), Behaviour.Visibility.ALWAYS),
                                TargetMode.SELF_AXIS)
                        .identity(0, 0, 0)
                        .build()));
        List<String> violations = TrackLint.lintCard("测试", bad);
        assertTrue(violations.stream().anyMatch(v -> v.contains("编队帧") && v.contains("MINE")),
                "lint 漏报了「编队帧 + 溜め」，实际报出：" + violations);
    }

    /**
     * 合法组合 MUST NOT 被误报——lint 噪声比漏报更费时间。
     *
     * <p>用一条<b>真正有变化</b>的速率曲线（匀速曲线 {@code varies()} 为 false，
     * 等价于没有曲线，lint 会以「参数无效」报出——那是既有契约，不是本次引入的）。
     */
    @Test
    void lintAcceptsLegalFormationCombination() {
        SpellCard good = new SpellCard("编队+推进", 1.0D,
                List.of(Track.of("轨", 0)
                        .formation(breathing())
                        .at(0, Shape.RING, Shape.Params.defaults(),
                                new Behaviour(
                                        Behaviour.Motion.speedProfile(
                                                DanmakuSpeedProfile.decelerateAndHold(0.2D, 20.0D)),
                                        Behaviour.Split.none(), Behaviour.Visibility.ALWAYS),
                                TargetMode.SELF_AXIS)
                        .identity(0, 0, 0)
                        .build()));
        assertTrue(TrackLint.lintCard("测试", good).isEmpty(),
                "合法的「编队 + 推进」被误报：" + TrackLint.lintCard("测试", good));
    }

    // ------------------------------------------------------------------
    // 轨道级声明
    // ------------------------------------------------------------------

    /** 默认不编队，且不改任何东西时行为与改前完全一致。 */
    @Test
    void defaultIsDisabled() {
        assertFalse(Track.of("t", 0).at(0, Shape.RING, TargetMode.SELF_AXIS)
                .build().formation().active());
    }

    /** 一轨一编队：重复声明是覆盖而非追加。 */
    @Test
    void oneFormationPerTrackIsReplacementNotAppend() {
        Track track = Track.of("t", 0)
                .formation(spinning())
                .formation(breathing())
                .at(0, Shape.RING, TargetMode.SELF_AXIS)
                .identity(0, 0, 0)
                .build();
        assertEquals(breathing(), track.formation(), "后声明的编队应覆盖前者");
        assertEquals(1.5D, track.formation().rotRateDegPerTick(), 1.0E-12,
                "自转角速度应取后者的 1.5（而非前者的 3.0，也非两者之和）");
        assertEquals(0.4D, track.formation().scaleAmp(), 1.0E-12,
                "缩放幅度应取后者的 0.4（前者不自转、不呼吸）");
    }

    /**
     * 「过原点销毁」MUST NOT 是曲线的默认行为。
     *
     * <p>早期实现把它焊在曲线上（任何回头曲线到点自毁）。后果不是「弹消失得早一点」，
     * 而是编队花后撤时整朵花在推进项穿过零点的那一 tick 集体消失——判据问的是
     * 推进项，弹的实际位置却在花瓣上，两者不是同一件事。
     */
    @Test
    void reverseProfileDoesNotDieUnlessExplicitlyAsked() {
        DanmakuSpeedProfile reverse = DanmakuSpeedProfile.decelerateAndReturn(0.3D, 20.0D, 60.0D,
                40.0D, 0.25D);
        assertFalse(Behaviour.Motion.speedProfile(reverse).diesAtOrigin(),
                "默认必须不销毁——反向加速是纯运动，弹可以退回去继续飞");
        assertTrue(Behaviour.Motion.speedProfile(reverse, true).diesAtOrigin(),
                "显式勾选后 MUST 生效");
    }

    /**
     * 返程曲线本身仍能回答「什么时候回到零点」——销毁决策与这个查询分离。
     *
     * <p>并列峰值取<b>更晚</b>的 knot：减速结束（t=20）与开始反打（t=80）位移同为峰值，
     * 只有 80 之后才真的在往回走。
     */
    @Test
    void profileStillAnswersWhenTheAdvanceTermReturns() {
        DanmakuSpeedProfile reverse = DanmakuSpeedProfile.decelerateAndReturn(0.3D, 20.0D, 60.0D,
                40.0D, 0.25D);
        assertTrue(reverse.reverses());
        assertEquals(80L, reverse.peakTravelTick(), "最远点在开始反打处（更晚的并列峰值）");
        assertTrue(reverse.returnedToOrigin(120), "曲线仍能回答「推进项何时回到零点」");
    }

    /** 编队帧 + 「过原点销毁」MUST 被拒——那个判据在有帧时没有意义。 */
    @Test
    void lintRejectsFormationCombinedWithDiesAtOrigin() {
        SpellCard bad = new SpellCard("编队+销毁", 1.0D,
                List.of(Track.of("轨", 0)
                        .formation(breathing())
                        .at(0, Shape.ROSETTE, Shape.Params.defaults().count(60).radius(3.0D)
                                        .rose(5, 1.8D),
                                new Behaviour(
                                        Behaviour.Motion.speedProfile(
                                                DanmakuSpeedProfile.decelerateAndReturn(
                                                        0.3D, 20.0D, 60.0D, 40.0D, 0.25D), true),
                                        Behaviour.Split.none(), Behaviour.Visibility.ALWAYS),
                                TargetMode.SELF_AXIS)
                        .identity(0, 0, 0)
                        .build()));
        List<String> violations = TrackLint.lintCard("测试", bad);
        assertTrue(violations.stream().anyMatch(v -> v.contains("编队帧") && v.contains("SPEED_PROFILE")),
                "lint 漏报了「编队帧 + 过原点销毁」，实际报出：" + violations);
    }

    /**
     * 编队帧 + 不销毁的推进项 MUST 合法——这正是「花后撤」。
     *
     * <p>只断言「没有编队相关的违规」而不断言整个列表为空：60 颗弹同时在场会撞上
     * R3 密度预算，那是另一条独立且正确的判据，与本条要验的东西无关。
     */
    @Test
    void formationWithRetreatingAdvanceIsLegal() {
        SpellCard good = new SpellCard("花后撤", 1.0D,
                List.of(Track.of("轨", 0)
                        .formation(breathing())
                        .at(0, Shape.ROSETTE, Shape.Params.defaults().count(12).radius(3.0D)
                                        .rose(5, 1.8D),
                                new Behaviour(
                                        Behaviour.Motion.speedProfile(
                                                DanmakuSpeedProfile.decelerateAndReturn(
                                                        0.3D, 20.0D, 60.0D, 40.0D, 0.25D), false),
                                        Behaviour.Split.none(), Behaviour.Visibility.ALWAYS),
                                TargetMode.SELF_AXIS)
                        .repeatEvery(200)
                        .identity(0, 0, 0)
                        .build()));
        List<String> violations = TrackLint.lintCard("测试", good);
        assertTrue(violations.stream().noneMatch(v -> v.contains("编队帧")),
                "合法的「编队 + 后撤（不销毁）」被误报：" + violations);
    }

    /** 编队数 MUST NOT 超过轨道数——一个符卡至多 3 轨，故至多 3 个编队。 */
    @Test
    void formationCountNeverExceedsTrackCount() {
        SpellCard card = new SpellCard("三轨三编队", 1.0D,
                List.of(Track.of("a", 0).formation(spinning())
                                .at(0, Shape.RING, TargetMode.SELF_AXIS)
                                .identity(0, 0, 0).build(),
                        Track.of("b", 1).formation(breathing())
                                .at(0, Shape.RING, TargetMode.SELF_AXIS)
                                .identity(1, 1, 1).build(),
                        Track.of("c", 2).formation(spinning())
                                .at(0, Shape.RING, TargetMode.SELF_AXIS)
                                .identity(2, 2, 2).build()));
        long count = card.tracks().stream().filter(t -> t.formation().active()).count();
        assertEquals(card.tracks().size(), count, "编队数应与轨道数一致");
        assertTrue(TrackLint.lintCard("测试", card).isEmpty(),
                "三条合规的编队轨道被误报：" + TrackLint.lintCard("测试", card));
    }
}
