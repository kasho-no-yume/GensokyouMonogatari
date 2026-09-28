package com.bitsson.gensokyou.danmaku.track;

import com.bitsson.gensokyou.danmaku.motion.RigOrbit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 编队装置的<b>声明侧</b>约束——纯数据，不需要世界。
 *
 * <p>这批断言守的是「装置只存一份、每弹只带一个相位角」这条带宽需求，
 * 以及装置与既有行为的互斥关系。两者一旦破掉，破坏都发生在实机里且<b>看不出来</b>：
 * 带宽破掉只是变卡，互斥破掉只是弹看起来「有点不对劲」。
 */
class BehaviourRigTest {

    private static Behaviour.Rig standard() {
        return Behaviour.Rig.around(new RigOrbit(0, 0, 0,
                0, RigOrbit.HORIZONTAL_PITCH_DEG, 4.0D, 2.0D, 2.0D,
                3.0D,
                1.5D, 0.0D,
                0, RigOrbit.HORIZONTAL_PITCH_DEG), 200);
    }

    /** 声明 MUST 逐字段无损地还原成轨道对象——中间任何一层「顺手改个默认值」都会走样。 */
    @Test
    void declarationRoundTripsToOrbit() {
        RigOrbit declared = new RigOrbit(1.5D, -2.25D, 3.0D,
                30.0D, RigOrbit.HORIZONTAL_PITCH_DEG, 4.0D, 2.0D, 1.5D,
                3.0D,
                1.5D, 0.25D,
                45.0D, RigOrbit.HORIZONTAL_PITCH_DEG);
        assertEquals(declared, Behaviour.Rig.around(declared, 60).orbit());
    }

    /** 寿命为 0 的装置 MUST 被夹到至少 1 tick，否则弹会挂在一个立刻死掉的装置上。 */
    @Test
    void lifetimeIsClampedToAtLeastOneTick() {
        assertEquals(1, Behaviour.Rig.around(standard().orbit(), 0).lifetimeTicks());
        assertEquals(1, Behaviour.Rig.around(standard().orbit(), -5).lifetimeTicks());
        assertEquals(60, Behaviour.Rig.around(standard().orbit(), 60).lifetimeTicks());
    }

    /** 默认声明必须是不启用，且轨道表不改任何东西时行为与改前完全一致。 */
    @Test
    void defaultIsDisabled() {
        assertFalse(Behaviour.Rig.NONE.active());
        assertFalse(Track.of("t", 0).at(0, Shape.RING, TargetMode.SELF_AXIS).build().rig().active());
    }

    /** 装置与三类「改速度 / 原地不动」的运动互斥。 */
    @Test
    void rigConflictsWithMotiveBehavioursOnly() {
        Behaviour.Rig rig = standard();
        assertTrue(rig.conflictsWith(Behaviour.Motion.mine(2.0D)), "溜め弹原地不动，与装置争夺位置");
        assertTrue(rig.conflictsWith(Behaviour.Motion.curve(10.0D, 5.0D, 30.0D)),
                "曲射每 tick 改速度，与装置的解析位置争夺权威");
        assertTrue(rig.conflictsWith(Behaviour.Motion.speedProfile(
                DanmakuSpeedProfileStub.reversing())), "速率曲线同样改速度");

        assertFalse(rig.conflictsWith(Behaviour.Motion.none()));
        assertFalse(rig.conflictsWith(Behaviour.Motion.hover(20)), "悬停只是到点定住，不与装置冲突");
        assertFalse(rig.conflictsWith(Behaviour.Motion.groundHug()));
    }

    /** 未启用装置时，任何运动都不构成冲突——否则 lint 会对全部既有符卡误报。 */
    @Test
    void disabledRigNeverConflicts() {
        assertFalse(Behaviour.Rig.NONE.conflictsWith(Behaviour.Motion.mine(2.0D)));
        assertFalse(Behaviour.Rig.NONE.conflictsWith(Behaviour.Motion.curve(10.0D, 5.0D, 30.0D)));
    }

    /** lint MUST 真的报出冲突，否则这条「静态拒绝」等于没有。 */
    @Test
    void lintRejectsRigCombinedWithConflictingMotion() {
        SpellCard bad = new SpellCard("rig-mine", 1.0D,
                List.of(Track.of("rig轨", 0)
                        .rig(standard())
                        .at(0, Shape.SCATTER_STATIC, Shape.Params.defaults(),
                                new Behaviour(Behaviour.Motion.mine(2.0D),
                                        Behaviour.Split.none(), Behaviour.Visibility.ALWAYS),
                                TargetMode.SELF_AXIS)
                        .identity(0, 0, 0)
                        .build()));
        List<String> violations = TrackLint.lintCard("测试", bad);
        assertTrue(violations.stream().anyMatch(v -> v.contains("编队") && v.contains("MINE")),
                "lint 漏报了「编队 + 溜め」，实际报出：" + violations);
    }

    /** 合法组合 MUST NOT 被误报——lint 噪声比漏报更费时间。 */
    @Test
    void lintAcceptsLegalRigCombination() {
        SpellCard good = new SpellCard("rig-ok", 1.0D,
                List.of(Track.of("rig轨", 0)
                        .rig(standard())
                        .at(0, Shape.RING, Shape.Params.defaults(), Behaviour.NONE,
                                TargetMode.SELF_AXIS)
                        .identity(0, 0, 0)
                        .build()));
        assertTrue(TrackLint.lintCard("测试", good).isEmpty(),
                "合法编队组合被误报：" + TrackLint.lintCard("测试", good));
    }

    /** 每轨至多一个装置：重复调用 rig() 是覆盖而非追加。 */
    @Test
    void oneRigPerTrackIsReplacementNotAppend() {
        Behaviour.Rig first = standard();
        Behaviour.Rig second = Behaviour.Rig.around(
                new RigOrbit(0, 0, 0, 0, RigOrbit.HORIZONTAL_PITCH_DEG,
                        9.0D, 0, 0, 0, 1.0D, 0, 0, RigOrbit.HORIZONTAL_PITCH_DEG), 30);
        Track track = Track.of("t", 0)
                .rig(first)
                .rig(second)
                .at(0, Shape.RING, TargetMode.SELF_AXIS)
                .identity(0, 0, 0)
                .build();
        assertEquals(second, track.rig(), "后声明的装置应覆盖前者");
        assertEquals(30, track.rig().lifetimeTicks());
    }

    /** 装置数 MUST NOT 超过轨道数——一个符卡至多 3 轨，故至多 3 个装置。 */
    @Test
    void rigCountNeverExceedsTrackCount() {
        SpellCard card = new SpellCard("三轨三装置", 1.0D,
                List.of(Track.of("a", 0).rig(standard())
                                .at(0, Shape.RING, TargetMode.SELF_AXIS).identity(0, 0, 0).build(),
                        Track.of("b", 1).rig(standard())
                                .at(0, Shape.RING, TargetMode.SELF_AXIS).identity(1, 1, 1).build(),
                        Track.of("c", 2).rig(standard())
                                .at(0, Shape.RING, TargetMode.SELF_AXIS).identity(2, 2, 2).build()));
        long rigCount = card.tracks().stream().filter(t -> t.rig().active()).count();
        assertEquals(card.tracks().size(), rigCount, "装置数应与轨道数一致");
        assertTrue(TrackLint.lintCard("测试", card).isEmpty(),
                "三条合规的编队轨道被误报：" + TrackLint.lintCard("测试", card));
    }

    /** 测试用的速率曲线构造，避免本测试类直接依赖 motion 包的工厂名。 */
    private static final class DanmakuSpeedProfileStub {
        static com.bitsson.gensokyou.danmaku.motion.DanmakuSpeedProfile reversing() {
            return com.bitsson.gensokyou.danmaku.motion.DanmakuSpeedProfile
                    .decelerateAndReturn(0.3D, 10.0D, 5.0D, 10.0D, 0.3D);
        }
    }
}
