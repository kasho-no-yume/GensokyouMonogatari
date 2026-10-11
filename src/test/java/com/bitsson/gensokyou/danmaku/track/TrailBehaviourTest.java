package com.bitsson.gensokyou.danmaku.track;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 拖尾行为（{@link Behaviour.Trail}）的断言。
 *
 * <p>它是行为轴上与 {@link Behaviour.Split} 并列的第四项：母弹一边飞、一边周期性留下静止尾珠。
 */
class TrailBehaviourTest {

    private static SpellCard cardOf(Track... tracks) {
        return new SpellCard(Component.literal("测试"), 1.0D, List.of(tracks));
    }

    private static Track.Builder trackOf(String name) {
        return Track.of(name, 0xFFFFFF).terminates().repeatEvery(40);
    }

    private static List<String> lint(SpellCard card) {
        return TrackLint.lint("拖尾", List.of(card), SignaturePalette.of(0xFFFFFF));
    }

    /** 缺省无拖尾；声明后生效。 */
    @Test
    void trailDefaultsOffAndActivatesWhenDeclared() {
        assertFalse(Behaviour.NONE.trail().active(), "默认行为不应含拖尾");
        Behaviour trailing = Behaviour.NONE.withTrail(Behaviour.Trail.every(2, 100));
        assertTrue(trailing.trail().active(), "声明后拖尾应生效");
        assertEquals(2, trailing.trail().everyTicks());
        assertEquals(100, trailing.trail().childLifetimeTicks());
    }

    /** 三参兼容构造仍可用（拖尾缺省为无）。 */
    @Test
    void threeArgConstructorStillCompiles() {
        Behaviour b = new Behaviour(Behaviour.Motion.none(), Behaviour.Split.NONE,
                Behaviour.Visibility.ALWAYS);
        assertFalse(b.trail().active());
    }

    /** 单独的拖尾合法。 */
    @Test
    void trailAloneIsValid() {
        Track track = trackOf("织丝")
                .identity(0, 0, 0)
                .at(0, Shape.FAN, Shape.Params.defaults().count(5).spread(60.0D).speed(0.3D),
                        Behaviour.NONE.withTrail(Behaviour.Trail.every(2, 100)),
                        TargetMode.SELF_AXIS)
                .build();
        assertTrue(lint(cardOf(track)).isEmpty(), "单独拖尾应合法: " + lint(cardOf(track)));
    }

    /** 拖尾与分裂互斥。 */
    @Test
    void trailAndSplitAreMutuallyExclusive() {
        Track track = trackOf("拖尾又分裂")
                .identity(0, 0, 0)
                .at(0, Shape.FAN, Shape.Params.defaults().count(5).spread(60.0D).speed(0.3D),
                        Behaviour.NONE
                                .withSplit(Behaviour.Split.at(100, 8))
                                .withTrail(Behaviour.Trail.every(2, 100)),
                        TargetMode.SELF_AXIS)
                .build();
        assertTrue(lint(cardOf(track)).stream().anyMatch(v -> v.contains("拖尾") && v.contains("分裂")),
                "拖尾 + 分裂应被检出: " + lint(cardOf(track)));
    }
}
