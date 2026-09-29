package com.bitsson.gensokyou.danmaku.track;

import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「几何与行为正交」这条契约的断言。
 *
 * <p>它是本次重构的<b>全部价值</b>所在：改前行为焊在几何里，于是「悬停的环」无法表达，
 * 必须新造一个 {@code HOVERING_RING} 形状；本类断言这类组合现在<b>直接可写</b>，
 * 且 lint 判得出。
 */
class BehaviourDecouplingTest {

    private static SpellCard cardOf(Track... tracks) {
        return new SpellCard("测试", 1.0D, List.of(tracks));
    }

    private static Track.Builder trackOf(String name) {
        return Track.of(name, 0xFFFFFF).terminates().repeatEvery(40);
    }

    // ------------------------------------------------------------------
    // 参数纪律
    // ------------------------------------------------------------------

    /**
     * 几何参数记录 MUST NOT 含任何行为字段。
     *
     * <p>用反射断言而非逐个 getter：行为字段名一旦加回来（例如 {@code hoverTicks}），
     * 本条立刻失败，不依赖「有没有人记得测那个 getter」。
     */
    @Test
    void shapeParamsCarriesNoBehaviourFields() {
        List<String> behaviourish = List.of("hover", "mine", "curve", "split", "phase", "duty");
        for (RecordComponent component : Shape.Params.class.getRecordComponents()) {
            String name = component.getName().toLowerCase(java.util.Locale.ROOT);
            for (String bad : behaviourish) {
                assertFalse(name.contains(bad),
                        "Shape.Params 是纯几何，MUST NOT 含行为字段 —— 发现: " + component.getName());
            }
        }
        assertEquals(11, Shape.Params.class.getRecordComponents().length,
                "几何参数应为 11 个字段（改前是 14，因混入了 7 个行为参数；"
                        + "现为 8 基础 + ROSETTE 的花瓣数与径向幅度 + LATTICE 的直瞄比例）");
    }

    // ------------------------------------------------------------------
    // 正交性：任一行为可配任一几何
    // ------------------------------------------------------------------

    /**
     * 同一几何配不同行为，MUST 各自独立成立。
     *
     * <p>这条是核心断言：改前几何名字里编码着行为（{@code HOVER_BURST}、
     * {@code CURVE_RING}、{@code MINE_RING}、{@code GAP_SPLIT}），所以「同一个环
     * 配悬停」根本写不出来——必须先发明一个形状。
     */
    @Test
    void sameGeometryAcceptsDifferentBehaviours() {
        List<Behaviour> behaviours = List.of(
                Behaviour.NONE,
                Behaviour.NONE.withMotion(Behaviour.Motion.hover(20)),
                Behaviour.NONE.withMotion(Behaviour.Motion.curve(0.0D, 90.0D, 90.0D)),
                Behaviour.NONE.withSplit(Behaviour.Split.at(15, 4)),
                Behaviour.NONE.withVisibility(Behaviour.Visibility.phaseHide(40, 0.5D, 0)));

        for (Shape shape : List.of(Shape.RING_FACING, Shape.FAN, Shape.CAGE, Shape.RING)) {
            for (Behaviour behaviour : behaviours) {
                Track track = trackOf("轨")
                        .identity(0, 0, 0)
                        .at(0, shape, shapeParamsFor(shape), behaviour, TargetMode.SELF_AXIS)
                        .build();
                List<String> violations = TrackLint.lint(
                        shape + "+" + behaviour.motion().kind(),
                        List.of(cardOf(track)), SignaturePalette.of(0xFFFFFF));
                assertTrue(violations.isEmpty(),
                        shape + " 配 " + behaviour.motion().kind() + " 应合法，实际: " + violations);
            }
        }
    }

    /** 行为与行为之间也应可组合——悬停弹同时会分裂，正是原需求⑤「停住后炸开」。 */
    @Test
    void behavioursComposeWithEachOther() {
        Track track = trackOf("停住后炸开")
                .identity(0, 0, 0)
                .at(0, Shape.FAN, Shape.Params.defaults().count(5).spread(60.0D).speed(0.3D),
                        Behaviour.NONE
                                .withMotion(Behaviour.Motion.hover(40))
                                .withSplit(Behaviour.Split.at(100, 8))
                                .withVisibility(Behaviour.Visibility.phaseHide(60, 0.5D, 0)),
                        TargetMode.SELF_AXIS)
                .build();
        assertTrue(TrackLint.lint("组合", List.of(cardOf(track)), SignaturePalette.of(0xFFFFFF))
                .isEmpty(), "悬停 + 分裂 + 相位隐藏三者应可同时成立");
    }

    // ------------------------------------------------------------------
    // 语义矛盾必须被检出
    // ------------------------------------------------------------------

    /**
     * 溜め MUST 配静止散布几何。
     *
     * <p>否则弹会「一边飞一边待发」——玩家既追不上也躲不掉，语义自相矛盾。
     * 改前这条由「溜め只存在于 {@code MINE_RING} 形状」隐式保证，故无判据。
     */
    @Test
    void mineOnMovingGeometryIsRejected() {
        Track bad = trackOf("飞着埋")
                .identity(0, 0, 0)
                .at(0, Shape.FAN, Shape.Params.defaults().count(4).spread(60.0D).speed(0.3D),
                        Behaviour.NONE.withMotion(Behaviour.Motion.mine(1.5D)),
                        TargetMode.SELF_AXIS)
                .build();
        assertTrue(containsViolation(cardOf(bad), "溜め"),
                "溜め配移动几何应被检出");
    }

    @Test
    void mineOnStaticScatterIsAccepted() {
        Track good = trackOf("埋着")
                .identity(0, 0, 0)
                .at(0, Shape.SCATTER_STATIC,
                        Shape.Params.defaults().count(5).radius(4.0D).speed(0.0D).size(0.55D),
                        Behaviour.NONE.withMotion(Behaviour.Motion.mine(1.5D)),
                        TargetMode.SELF_AXIS)
                .build();
        assertTrue(TrackLint.lint("溜め", List.of(cardOf(good)), SignaturePalette.of(0xFFFFFF))
                .isEmpty(), "溜め配静止散布几何应合法");
    }

    @Test
    void invalidMotionParamsAreRejected() {
        Track zeroCurve = trackOf("零速曲射")
                .identity(0, 0, 0)
                .at(0, Shape.RING, Shape.Params.defaults().count(6).speed(0.3D),
                        Behaviour.NONE.withMotion(Behaviour.Motion.curve(0.0D, 0.0D, 0.0D)),
                        TargetMode.SELF_AXIS)
                .build();
        assertTrue(containsViolation(cardOf(zeroCurve), "运动"),
                "零角速度的曲射应被检出（参数无效）");
    }

    // ------------------------------------------------------------------
    // R4：时间维度判据
    // ------------------------------------------------------------------

    /**
     * 可见段过短 MUST 被检出。
     *
     * <p>周期 200 tick、占空比 5% ⇒ 可见仅 10 tick（0.5 秒），短于反应下限。
     */
    @Test
    void tooShortVisibleWindowViolatesR4() {
        Track bad = trackOf("闪瞎")
                .identity(0, 0, 0)
                .at(0, Shape.CAGE, Shape.Params.defaults().count(8).gap(45.0D).speed(0.3D),
                        Behaviour.NONE.withVisibility(Behaviour.Visibility.phaseHide(200, 0.05D, 0)),
                        TargetMode.SELF_AXIS)
                .build();
        assertTrue(containsViolation(cardOf(bad), "R4"),
                "可见段短于反应下限应违反 R4");
    }

    /** 隐藏段过长 MUST 被检出——弹幕墙会读作「长时间无敌」而非「读节奏」。 */
    @Test
    void tooLongBlindWindowViolatesR4() {
        // 周期 200、占空比 50% ⇒ 可见 100（过下限）、隐藏 100（超上限 60）
        Track bad = trackOf("无敌墙")
                .identity(0, 0, 0)
                .at(0, Shape.CAGE, Shape.Params.defaults().count(8).gap(45.0D).speed(0.3D),
                        Behaviour.NONE.withVisibility(Behaviour.Visibility.phaseHide(200, 0.5D, 0)),
                        TargetMode.SELF_AXIS)
                .build();
        assertTrue(containsViolation(cardOf(bad), "R4"),
                "隐藏段超过致盲上限应违反 R4");
    }

    /**
     * 占空比接近 1（隐藏段远短于上限）MUST 通过。
     *
     * <p>反向用例：防止 R4 变成「凡相位隐藏皆不合格」。周期 400、占空比 90%
     * ⇒ 隐藏仅 40 tick，远低于 60 的致盲上限。
     */
    @Test
    void briefBlindWindowPassesR4() {
        Track good = trackOf("微闪")
                .identity(0, 0, 0)
                .at(0, Shape.CAGE, Shape.Params.defaults().count(8).gap(45.0D).speed(0.3D),
                        Behaviour.NONE.withVisibility(Behaviour.Visibility.phaseHide(400, 0.9D, 0)),
                        TargetMode.SELF_AXIS)
                .build();
        assertTrue(TrackLint.lint("微闪", List.of(cardOf(good)), SignaturePalette.of(0xFFFFFF))
                .isEmpty(), "隐藏段很短时相位隐藏应合规");
    }

    /** 均衡的相位隐藏 MUST 通过——这是弹幕墙的典型参数（2 秒周期、50% 占空比）。 */
    @Test
    void balancedPhaseHidePasses() {
        Track good = trackOf("标准墙")
                .identity(0, 0, 0)
                .at(0, Shape.CAGE, Shape.Params.defaults().count(8).gap(45.0D).speed(0.3D),
                        Behaviour.NONE.withVisibility(Behaviour.Visibility.phaseHide(40, 0.5D, 0)),
                        TargetMode.SELF_AXIS)
                .build();
        assertTrue(TrackLint.lint("弹幕墙", List.of(cardOf(good)), SignaturePalette.of(0xFFFFFF))
                .isEmpty(), "2 秒周期 50% 占空比是弹幕墙的典型参数，应合规");
    }

    // ------------------------------------------------------------------
    // 视觉独占标识含行为轴
    // ------------------------------------------------------------------

    /**
     * 两轨若色相/速度/尺寸/形状全同但<b>行为不同</b>，lint MUST 判为合规。
     *
     * <p>spec 的四轴是「色相 / 速度 / 尺寸 / <b>行为</b>」，而改前的
     * {@code VisualIdentity} 只有 colorStep/speedStep/sizeStep/shapeIndex
     * ——<b>行为维度从未参与断言</b>。本条锁住该缺口已被补上。
     */
    @Test
    void behaviourCountsAsVisualIdentityAxis() {
        Track hovering = trackOf("甲")
                .identity(1, 1, 1)
                .at(0, Shape.RING_FACING, Shape.Params.defaults()
                        .count(8).gap(60.0D).speed(0.3D),
                        Behaviour.NONE.withMotion(Behaviour.Motion.hover(20)), TargetMode.SELF_AXIS)
                .build();
        Track plain = trackOf("乙")
                .identity(1, 1, 1)
                .at(0, Shape.RING_FACING, Shape.Params.defaults()
                        .count(8).gap(60.0D).speed(0.3D), Behaviour.NONE, TargetMode.SELF_AXIS)
                .build();
        // 色相/速度/尺寸三轴全同，形状亦同——唯一区别是行为
        assertFalse(hovering.identity().equals(plain.identity()),
                "行为不同应使视觉标识不同（行为轴须参与 identity）");
        assertTrue(TrackLint.lint("异行为", List.of(cardOf(hovering, plain)),
                        SignaturePalette.of(0x111111, 0x222222)).isEmpty(),
                "靠行为轴区分的两轨应合规");
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    private static boolean containsViolation(SpellCard card, String needle) {
        return TrackLint.lint("坏卡", List.of(card), SignaturePalette.of(0xFFFFFF)).stream()
                .anyMatch(v -> v.contains(needle));
    }

    /**
     * 选一组让各形状都能过 lint 的几何参数。
     *
     * <p>{@code FAN} 与 {@code GAP_FAN} 的发数刻意取 5（低于 R2 封死阈值 8）：
     * 这两个形状 {@code guaranteesGap} 为 false，8 发 + 自轴型会<b>正确地</b>触发
     * R2，与本用例要验的「行为与几何正交」无关。
     */
    private static Shape.Params shapeParamsFor(Shape shape) {
        return switch (shape) {
            case SCATTER_STATIC -> Shape.Params.defaults().count(5).radius(4.0D)
                    .speed(0.0D).size(0.55D);
            case FALL_FROM_ABOVE -> Shape.Params.defaults().count(6).radius(7.0D).speed(0.25D);
            case SHELL -> Shape.Params.defaults().count(8).radius(6.0D).speed(0.25D);
            case FAN, GAP_FAN -> Shape.Params.defaults().count(5).spread(60.0D).gap(45.0D)
                    .speed(0.3D);
            default -> Shape.Params.defaults().count(8).gap(45.0D).speed(0.3D);
        };
    }
}
