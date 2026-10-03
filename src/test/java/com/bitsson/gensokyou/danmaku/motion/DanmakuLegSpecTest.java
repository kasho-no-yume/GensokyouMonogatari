package com.bitsson.gensokyou.danmaku.motion;

import net.minecraft.world.phys.Vec3;
import com.bitsson.gensokyou.danmaku.track.TrackLint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 声明侧（{@link DanmakuLegSpec}）到运行时（{@link DanmakuLegMotion}）的翻译，
 * 以及「符卡表能写出段式卡」这条通路。
 *
 * <p><b>为什么单独测通路</b>：段式运动的数据层与 tick 层都有测试，但它们都不经过
 * 「符卡表 → 拍 → 发射器 → 实体」这条链。链子断在哪一环都不会有单元测试失败 ——
 * 只会表现为「实机里段式卡不生效」。这条测试把那根链的<b>声明侧</b>钉住。
 */
class DanmakuLegSpecTest {

    private static final Vec3 LAUNCH = new Vec3(0.0D, 0.0D, 1.0D);

    @Test
    @DisplayName("段表与种子被完整翻译成运行时形态")
    void specTranslatesToMotion() {
        DanmakuLegSpec spec = DanmakuLegSpec.seeded(DanmakuLegSpec.Kind.SEED, 2,
                new int[]{0xABCD, 0x1234},
                new double[]{30, 20}, new double[]{0.4, 0.0});

        DanmakuLegMotion motion = spec.toMotion(LAUNCH);
        assertEquals(2, motion.legCount());
        assertEquals(DanmakuLegMotion.Kind.SEED, motion.kind());
        // 速率经 1/4096 量化往返，容差取半个量化步长（与 DanmakuLegMotionTest 同理）
        double quantisation = 0.5D / DanmakuLegMotion.VELOCITY_SCALE;
        assertEquals(0.4D, motion.speedAt(0), quantisation);
        assertEquals(0.0D, motion.speedAt(30), quantisation, "第二段悬停 ⇒ 速率 0");
        assertEquals(30, motion.segmentStartAt(1), "第二段起始 = 第一段时长");
        assertEquals(20, motion.durationAt(1));
        assertEquals(50, motion.totalDuration());
    }

    @Test
    @DisplayName("三种段类型都能声明，且各自翻译正确")
    void allThreeKindsTranslate() {
        for (DanmakuLegSpec.Kind kind : DanmakuLegSpec.Kind.values()) {
            DanmakuLegSpec spec = DanmakuLegSpec.seeded(kind, 1, new int[]{7},
                    new double[]{30}, new double[]{0.3});
            DanmakuLegMotion motion = spec.toMotion(LAUNCH);
            assertEquals(1, motion.legCount(), kind.name());
            assertEquals(3, DanmakuLegSpec.Kind.values().length, "段类型恰为三种");
        }
    }

    @Test
    @DisplayName("FIXED 单段的形态与 straight() 一致")
    void fixedSingleSegment() {
        DanmakuLegSpec spec = DanmakuLegSpec.straight(200, 0.3);
        DanmakuLegMotion motion = spec.toMotion(LAUNCH);
        assertEquals(1, motion.legCount());
        assertEquals(DanmakuLegMotion.Kind.FIXED, motion.kind());
        assertEquals(LAUNCH, motion.directionAt(0), "FIXED 单段复用发射方向");
        assertEquals(200, motion.durationAt(0));
    }

    @Test
    @DisplayName("段数与种子数被夹到上限内")
    void countsAreClamped() {
        double[] durations = new double[20];
        double[] speeds = new double[20];
        int[] seeds = new int[20];
        DanmakuLegSpec spec = DanmakuLegSpec.seeded(DanmakuLegSpec.Kind.SEED, 20, seeds,
                durations, speeds);
        assertEquals(DanmakuLegMotion.MAX_LEGS, spec.packedLegs().length,
                "段数被夹到 MAX_LEGS");
        assertEquals(DanmakuRandomState.MAX_SEEDS, spec.seeds().length,
                "种子被夹到 MAX_SEEDS");
        assertEquals(DanmakuRandomState.MAX_SEEDS, spec.randomState().size(),
                "seedCount 亦被夹住 —— 否则越界读会退化");
    }

    @Test
    @DisplayName("打包字节与实体侧解码一致")
    void packedCountMatchesEntityDecoders() {
        DanmakuLegSpec spec = DanmakuLegSpec.seeded(DanmakuLegSpec.Kind.TARGET, 3,
                new int[]{1, 2, 3}, new double[]{10, 20, 30}, new double[]{0.1, 0.2, 0.3});
        int packed = spec.packedLegCountAndKind();
        assertEquals(3, DanmakuLegMotion.legCountOf(packed));
        assertEquals(DanmakuLegMotion.Kind.TARGET, DanmakuLegMotion.kindOf(packed));
    }

    @Test
    @DisplayName("符卡表能声明段式运动：builder 级 legMotion 作用于其后的每一拍")
    void cardTableCanDeclareLegMotion() {
        com.bitsson.gensokyou.danmaku.track.Track track =
                com.bitsson.gensokyou.danmaku.track.Track.of("段式测试", 0xFFAA00)
                        .terminates()
                        .legMotion(DanmakuLegSpec.seeded(DanmakuLegSpec.Kind.SEED, 2,
                                new int[]{11, 22}, new double[]{20, 20}, new double[]{0.4, 0.0}))
                        .at(0, com.bitsson.gensokyou.danmaku.track.Shape.FAN,
                                com.bitsson.gensokyou.danmaku.track.Shape.Params.defaults()
                                        .count(4).spread(30.0D).speed(0.3D),
                                com.bitsson.gensokyou.danmaku.track.TargetMode.SELF_AXIS)
                        .at(40, com.bitsson.gensokyou.danmaku.track.Shape.FAN,
                                com.bitsson.gensokyou.danmaku.track.Shape.Params.defaults()
                                        .count(4).spread(30.0D).speed(0.3D),
                                com.bitsson.gensokyou.danmaku.track.TargetMode.SELF_AXIS)
                        .build();

        List<com.bitsson.gensokyou.danmaku.track.Track.Beat> beats = track.beats();
        assertEquals(2, beats.size());
        for (com.bitsson.gensokyou.danmaku.track.Track.Beat beat : beats) {
            assertTrue(beat.hasLegSpec(), "tick=" + beat.tick() + " 应带上段式声明");
            assertNotNull(beat.legSpec());
        }
    }

    @Test
    @DisplayName("未声明段式运动的拍不带该字段（旧构造器路径零影响）")
    void beatsWithoutLegSpecCarryNull() {
        com.bitsson.gensokyou.danmaku.track.Track track =
                com.bitsson.gensokyou.danmaku.track.Track.of("普通", 0xFFFFFF)
                        .terminates()
                        .at(0, com.bitsson.gensokyou.danmaku.track.Shape.FAN,
                                com.bitsson.gensokyou.danmaku.track.Shape.Params.defaults()
                                        .count(4).spread(30.0D).speed(0.3D),
                                com.bitsson.gensokyou.danmaku.track.TargetMode.SELF_AXIS)
                        .build();
        assertFalse(track.beats().get(0).hasLegSpec());
        assertNull(track.beats().get(0).legSpec());
    }

    @Test
    @DisplayName("段数逐档正确：N 段 ⇒ N-1 次转向（上一版把「变向次数」与「段数」搞混了）")
    void segmentCountMatchesRequestExactly() {
        // 这条断言存在的理由：命令参数曾叫 turns 却按段数实现，
        // 于是 `/danmaku leg 3` 只有 1 次转向而读数说 3 次。
        // 「段数」与「转向次数」差 1 是数学事实，必须由测试钉住而不是靠命名。
        for (int segments = 1; segments <= DanmakuLegMotion.MAX_LEGS; segments++) {
            double[] durations = new double[segments];
            double[] speeds = new double[segments];
            int[] seeds = new int[segments];
            for (int i = 0; i < segments; i++) {
                durations[i] = 30;
                speeds[i] = 0.4;
                seeds[i] = 1000 + i;
            }
            DanmakuLegSpec spec = DanmakuLegSpec.seeded(DanmakuLegSpec.Kind.SEED, segments,
                    seeds, durations, speeds);
            DanmakuLegMotion motion = spec.toMotion(LAUNCH);

            assertEquals(segments, motion.legCount(), "请求 " + segments + " 段应得到 " + segments + " 段");
            assertEquals(segments, spec.packedLegs().length, "段表长度");
            assertEquals(segments, spec.randomState().size(), "种子数");

            // 相邻两段的方向必须真的不同 —— 否则「转向」只是纸面上的
            int distinctDirections = 1;
            for (int i = 1; i < motion.legCount(); i++) {
                if (!motion.directionAt(motion.segmentStartAt(i))
                        .equals(motion.directionAt(motion.segmentStartAt(i - 1)))) {
                    distinctDirections++;
                }
            }
            assertEquals(segments, distinctDirections,
                    "请求 " + segments + " 段应有 " + segments + " 个互不相同的方向"
                            + "（即 " + (segments - 1) + " 次转向）");
        }
    }

    @Test
    @DisplayName("「快飞 / 悬停」交替段表产生 N 段且总时长正确")
    void alternatingFlyHoverPattern() {
        int segments = 6;
        double[] durations = new double[segments];
        double[] speeds = new double[segments];
        for (int i = 0; i < segments; i++) {
            boolean hover = (i % 2) == 1;
            durations[i] = hover ? 20 : 30;
            speeds[i] = hover ? 0.0 : 0.45;
        }
        DanmakuLegMotion motion = DanmakuLegSpec
                .seeded(DanmakuLegSpec.Kind.SEED, segments,
                        new int[]{1, 2, 3, 4, 5, 6}, durations, speeds)
                .toMotion(LAUNCH);

        assertEquals(6, motion.legCount());
        // 起始年龄：0, 30, 50, 80, 100, 130 ⇒ 总时长 150
        assertEquals(0, motion.segmentStartAt(0));
        assertEquals(30, motion.segmentStartAt(1));
        assertEquals(50, motion.segmentStartAt(2));
        assertEquals(80, motion.segmentStartAt(3));
        assertEquals(100, motion.segmentStartAt(4));
        assertEquals(130, motion.segmentStartAt(5));
        assertEquals(150, motion.totalDuration());

        double quantisation = 0.5D / DanmakuLegMotion.VELOCITY_SCALE;
        for (int i = 0; i < segments; i++) {
            int age = motion.segmentStartAt(i);
            double expected = (i % 2) == 1 ? 0.0 : 0.45;
            assertEquals(expected, motion.speedAt(age), quantisation, "第 " + i + " 段速率");
        }
    }

    @Test
    @DisplayName("末段零速率 + 寿命长于段表 ⇒ lint 必须拒绝（永久悬停是无诊断的静默状态）")
    void trailingHoverSegmentIsRejectedByLint() {
        // 复现的正是实机现象：`/danmaku leg 8` 的弹在段表走完后定住不动。
        // 根因：segmentAt(age) 越过段表末尾时夹紧到末段，末段速率 0 ⇒ 永久悬停。
        int segments = 8;
        double[] durations = new double[segments];
        double[] speeds = new double[segments];
        for (int i = 0; i < segments; i++) {
            durations[i] = 30;
            speeds[i] = (i % 2) == 1 ? 0.0 : 0.4;   // 末段（i=7）是悬停段
        }
        var spec = DanmakuLegSpec.seeded(DanmakuLegSpec.Kind.SEED, segments,
                new int[]{1, 2, 3, 4, 5, 6, 7, 8}, durations, speeds);
        var motion = spec.toMotion(LAUNCH);

        assertEquals(0.0D, motion.speedAt(motion.totalDuration() + 1000), 1.0E-9D,
                "越界年龄夹紧到末段 ⇒ 速率为 0 ⇒ 弹永久悬停（这就是实机看到的现象）");

        List<String> violations = lintOf(spec, 0);
        assertFalse(violations.isEmpty(), "末段零速率且寿命更长 ⇒ lint MUST 拒绝");
        assertTrue(violations.stream().anyMatch(v -> v.contains("永久悬停")),
                "拒绝理由 MUST 点明后果：" + violations);
    }

    @Test
    @DisplayName("末段有速度 ⇒ lint 通过（修正后的形态）")
    void trailingFlyingSegmentPassesLint() {
        int segments = 8;
        double[] durations = new double[segments];
        double[] speeds = new double[segments];
        for (int i = 0; i < segments; i++) {
            boolean last = i == segments - 1;
            durations[i] = ((i % 2) == 1 && !last) ? 20 : 30;
            speeds[i] = ((i % 2) == 1 && !last) ? 0.0 : 0.4;
        }
        var spec = DanmakuLegSpec.seeded(DanmakuLegSpec.Kind.SEED, segments,
                new int[]{1, 2, 3, 4, 5, 6, 7, 8}, durations, speeds);
        var motion = spec.toMotion(LAUNCH);
        assertTrue(motion.speedAt(motion.totalDuration() + 1000) > 0.0D,
                "末段有速度 ⇒ 越界后继续沿末段方向飞");

        assertTrue(lintOf(spec, 0).isEmpty(),
                "末段有速度时不该被这条判据拦：" + lintOf(spec, 0));
    }

    @Test
    @DisplayName("寿命短于段表 ⇒ 豁免（弹在段表走完前就消失）")
    void shortLifetimeIsExempt() {
        int segments = 2;
        var spec = DanmakuLegSpec.seeded(DanmakuLegSpec.Kind.SEED, 2, new int[]{1, 2},
                new double[]{20, 20}, new double[]{0.4, 0.0});
        assertTrue(lintOf(spec, 30).isEmpty(),
                "寿命 30 tick < 段表总时长 40 tick ⇒ 弹走不到悬停段，不该被拦："
                        + lintOf(spec, 30));
    }

    /**
     * 走公开的 {@code lintCard} 入口判一张卡 —— {@code lintBeat} 是包级私有，
     * 而本测试类在 {@code motion} 包。
     */
    private static List<String> lintOf(DanmakuLegSpec spec, int lifetimeTicks) {
        var card = new com.bitsson.gensokyou.danmaku.track.SpellCard(
                net.minecraft.network.chat.Component.literal("段式测试"), 1.0D,
                List.of(com.bitsson.gensokyou.danmaku.track.Track.of("段式", 0xFFAA00)
                        .terminates()
                        .legMotion(spec)
                        .at(0, com.bitsson.gensokyou.danmaku.track.Shape.FAN,
                                com.bitsson.gensokyou.danmaku.track.Shape.Params.defaults()
                                        .count(4).spread(30.0D).speed(0.3D),
                                com.bitsson.gensokyou.danmaku.track.Behaviour.NONE,
                                com.bitsson.gensokyou.danmaku.track.TargetMode.SELF_AXIS,
                                com.bitsson.gensokyou.danmaku.track.Projectile.SPHERE,
                                lifetimeTicks, 0,
                                com.bitsson.gensokyou.danmaku.track.Track.Beat.SpawnAnchor.NONE,
                                1.0D, 1.0D)
                        .build()));
        return TrackLint.lintCard("段式测试", card);
    }

    private static com.bitsson.gensokyou.danmaku.track.Track trackOf() {
        return com.bitsson.gensokyou.danmaku.track.Track.of("空", 0xFFFFFF).terminates()
                .repeatEvery(40)
                .at(0, com.bitsson.gensokyou.danmaku.track.Shape.RADIAL_BURST,
                        com.bitsson.gensokyou.danmaku.track.Shape.Params.defaults()
                                .count(3).speed(0.3D),
                        com.bitsson.gensokyou.danmaku.track.TargetMode.SELF_AXIS)
                .build();
    }

    @Test
    @DisplayName("11 参旧构造器与 12 参新构造器等价（既有调用点零影响）")
    void legacyBeatConstructorIsEquivalent() {
        com.bitsson.gensokyou.danmaku.track.Shape.Params params =
                com.bitsson.gensokyou.danmaku.track.Shape.Params.defaults().count(3).speed(0.2D);
        com.bitsson.gensokyou.danmaku.track.Track.Beat legacy =
                new com.bitsson.gensokyou.danmaku.track.Track.Beat(0,
                        com.bitsson.gensokyou.danmaku.track.Shape.FAN, params,
                        com.bitsson.gensokyou.danmaku.track.Behaviour.NONE,
                        com.bitsson.gensokyou.danmaku.track.TargetMode.SELF_AXIS,
                        com.bitsson.gensokyou.danmaku.track.Projectile.SPHERE,
                        0, 0,
                        com.bitsson.gensokyou.danmaku.track.Track.Beat.SpawnAnchor.NONE,
                        1.0D, 1.0D);
        assertFalse(legacy.hasLegSpec());
        assertNull(legacy.legSpec());
        assertEquals(0, legacy.tick());
    }
}