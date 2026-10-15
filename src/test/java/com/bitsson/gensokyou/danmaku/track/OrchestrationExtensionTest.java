package com.bitsson.gensokyou.danmaku.track;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code extend-danmaku-orchestration} 的静态契约测试。
 *
 * <p>全部纯静态、无需世界与实体：编排声明（锁轴组 / 领示 / 收束）的语义与
 * {@link TrackLint} 的三条新判据。伤害窗口与锁轴快照的运行期行为需实机命令验收
 * （见 change 的 proposal「验收指令」表）。
 */
class OrchestrationExtensionTest {

    private static final int COLOR = 0x66E8FF;

    @Test
    void orchestrationAccessorsAreSelfDescribing() {
        assertFalse(Track.Beat.Orchestration.NONE.locks(), "NONE 不锁轴");
        assertFalse(Track.Beat.Orchestration.NONE.telegraphs(), "NONE 无符印");
        assertFalse(Track.Beat.Orchestration.NONE.collapses(), "NONE 无收束");
        assertTrue(new Track.Beat.Orchestration(1, 0, 0).locks());
        assertTrue(new Track.Beat.Orchestration(0, 20, 0).telegraphs());
        assertTrue(new Track.Beat.Orchestration(0, 0, 220).collapses());
    }

    @Test
    void curveWindowIsOptionalAndDefaultsOff() {
        Behaviour.Motion plain = Behaviour.Motion.curve(10.0D, 20.0D, 30.0D);
        assertEquals(0, plain.curveWindowDuration(), "未声明窗口 ⇒ 全程曲射");
        Behaviour.Motion windowed = Behaviour.Motion.curve(10.0D, 20.0D, 30.0D, 30, 30);
        assertEquals(30, windowed.curveWindowStart());
        assertEquals(30, windowed.curveWindowDuration());
        assertEquals(30.0D, windowed.curveRateDegPerSec(), 1.0E-9D, "窗口不改角速度");
    }

    @Test
    void collapseBeyondCycleIsRejected() {
        SpellCard card = card(240, trackWithOrchestration(
                new Track.Beat.Orchestration(0, 0, 260)));
        List<String> violations = TrackLint.lintCard("t", card);
        assertTrue(violations.stream().anyMatch(v -> v.contains("收束年龄")),
                "收束年龄超出循环应被拒：" + violations);
    }

    @Test
    void collapseWithoutCycleIsRejected() {
        SpellCard card = card(0, trackWithOrchestration(
                new Track.Beat.Orchestration(0, 0, 100)));
        List<String> violations = TrackLint.lintCard("t", card);
        assertTrue(violations.stream().anyMatch(v -> v.contains("未声明循环长度")),
                "收束以循环内年龄表达，无循环应被拒：" + violations);
    }

    @Test
    void leadBeyondGroupFirstBeatIsRejected() {
        // 组首拍 t=0，另一拍 t=40 声明领示 60 > 0 ⇒ 符印落在循环之前。
        Track.Builder builder = Track.of("t", COLOR).repeatEvery(0);
        builder.orchestration(new Track.Beat.Orchestration(1, 0, 0))
                .at(0, Shape.FAN, Shape.Params.defaults().count(4).spread(30.0D).speed(0.3D),
                        Behaviour.NONE, TargetMode.SELF_AXIS);
        builder.orchestration(new Track.Beat.Orchestration(1, 60, 0))
                .at(40, Shape.FAN, Shape.Params.defaults().count(4).spread(30.0D).speed(0.3D),
                        Behaviour.NONE, TargetMode.SELF_AXIS);
        SpellCard card = new SpellCard(Component.literal("t"), 1.0D,
                List.of(builder.build()), 200);
        List<String> violations = TrackLint.lintCard("t", card);
        assertTrue(violations.stream().anyMatch(v -> v.contains("领示提前量")),
                "领示超过组首拍应被拒：" + violations);
    }

    @Test
    void legalOrchestrationPasses() {
        Track.Builder builder = Track.of("t", COLOR).repeatEvery(0);
        builder.orchestration(new Track.Beat.Orchestration(1, 0, 240))
                .at(0, Shape.FAN, Shape.Params.defaults().count(4).spread(30.0D).speed(0.3D),
                        Behaviour.NONE, TargetMode.SELF_AXIS);
        builder.orchestration(new Track.Beat.Orchestration(1, 0, 240))
                .at(40, Shape.FAN, Shape.Params.defaults().count(4).spread(30.0D).speed(0.3D),
                        Behaviour.NONE, TargetMode.SELF_AXIS);
        SpellCard card = new SpellCard(Component.literal("t"), 1.0D,
                List.of(builder.build()), 240);
        assertTrue(TrackLint.lintCard("t", card).isEmpty(),
                "合法编排应通过：" + TrackLint.lintCard("t", card));
    }

    private static SpellCard card(int cycle, Track track) {
        return new SpellCard(Component.literal("t"), 1.0D, List.of(track), cycle);
    }

    private static Track trackWithOrchestration(Track.Beat.Orchestration orchestration) {
        Track.Builder builder = Track.of("t", COLOR).repeatEvery(0);
        builder.orchestration(orchestration)
                .at(0, Shape.FAN, Shape.Params.defaults().count(4).spread(30.0D).speed(0.3D),
                        Behaviour.NONE, TargetMode.SELF_AXIS);
        return builder.build();
    }
}
