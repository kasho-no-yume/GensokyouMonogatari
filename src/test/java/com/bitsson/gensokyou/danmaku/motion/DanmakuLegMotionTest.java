package com.bitsson.gensokyou.danmaku.motion;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DanmakuRandomState} 与 {@link DanmakuLegMotion} 的纯函数真值表。
 *
 * <p>本类只测「给定输入，算出什么」—— 不测任何双端一致性。
 * 纪律类断言在 {@code DanmakuLegMotionContractTest}。
 */
class DanmakuLegMotionTest {

    private static final double EPS = 1.0E-12D;
    private static final Vec3 LAUNCH = new Vec3(0.0D, 0.0D, 1.0D);

    // ------------------------------------------------------------------
    // 组 1：随机容器
    // ------------------------------------------------------------------

    @Test
    @DisplayName("count 为 0 时全部取值为常量，且 size 为 0")
    void emptyStateYieldsConstants() {
        DanmakuRandomState empty = DanmakuRandomState.empty();
        assertEquals(0, empty.size());
        assertTrue(empty.isEmpty());
        for (int i = 0; i < DanmakuRandomState.MAX_SEEDS; i++) {
            assertEquals(0, empty.at(i), "空状态下第 " + i + " 项");
        }
        // 空状态造出的运动 MUST 与「什么都不给」等价，而不是抛异常或走随机
        DanmakuLegMotion motion = DanmakuLegMotion.fromSpec(3,
                new int[]{DanmakuLegMotion.pack(10, 0.2D), DanmakuLegMotion.pack(10, 0.2D),
                        DanmakuLegMotion.pack(10, 0.2D)},
                empty, LAUNCH, DanmakuLegMotion.Kind.SEED);
        assertEquals(3, motion.legCount());
    }

    @Test
    @DisplayName("越界读退化为常量 0，不抛异常")
    void outOfRangeReadsDegradeToConstant() {
        DanmakuRandomState state = DanmakuRandomState.of(new int[]{7, 8, 9}, 3);
        assertEquals(7, state.at(0));
        assertEquals(9, state.at(2));
        assertEquals(0, state.at(3), "index >= size");
        assertEquals(0, state.at(99), "远超上限");
        assertEquals(0, state.at(-1), "负下标");
    }

    @Test
    @DisplayName("count 被夹到 [0, 8]，不足补 0、多余丢弃")
    void countIsClamped() {
        assertEquals(8, DanmakuRandomState.of(new int[20], 20).size(), "超上限被夹");
        assertEquals(0, DanmakuRandomState.of(new int[]{1}, -5).size(), "负数被夹到 0");
        assertEquals(0, DanmakuRandomState.of(new int[]{1}, 0).at(0), "count=0 时不取值");

        DanmakuRandomState short2 = DanmakuRandomState.of(new int[]{1}, 5);
        assertEquals(5, short2.size());
        assertEquals(1, short2.at(0));
        assertEquals(0, short2.at(1), "给 1 个却声明 5 个 ⇒ 缺的补 0");
    }

    @Test
    @DisplayName("同一组种子在两次构造中产出逐位相同的取值序列")
    void sameSeedsProduceIdenticalSequence() {
        int[] seeds = {0x1234ABCD, -98765, 42, 0, 7, 99, -1, 5};
        DanmakuRandomState first = DanmakuRandomState.of(seeds, seeds.length);
        DanmakuRandomState second = DanmakuRandomState.of(seeds.clone(), seeds.length);

        for (int i = 0; i < DanmakuRandomState.MAX_SEEDS; i++) {
            assertEquals(first.at(i), second.at(i), "第 " + i + " 项");
        }
        assertEquals(first, second, "equals MUST 按值比较");
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    @DisplayName("输入数组被复制：事后改动原数组不影响已构造的状态")
    void inputArrayIsDefensivelyCopied() {
        int[] seeds = {1, 2, 3};
        DanmakuRandomState state = DanmakuRandomState.of(seeds, 3);
        seeds[0] = 999;
        assertEquals(1, state.at(0), "构造后改原数组 MUST NOT 影响状态");
    }

    // ------------------------------------------------------------------
    // 组 2：段式运动
    // ------------------------------------------------------------------

    @Test
    @DisplayName("段数为 1 时退化为恒速直线")
    void singleLegDegeneratesToStraightLine() {
        DanmakuLegMotion motion = DanmakuLegMotion.straight(0.35D, 200, LAUNCH);
        assertEquals(1, motion.legCount());
        // 速率经 1/4096 量化，往返误差上界为半个量化步长 —— 用它当容差，
        // 而不是 1e-9：后者测的是「浮点精确」，而这条判据要测的是「不随年龄漂移」。
        double quantisation = 0.5D / DanmakuLegMotion.VELOCITY_SCALE;
        assertEquals(0.35D, motion.speedAt(0), quantisation);
        assertEquals(0.35D, motion.speedAt(199), quantisation, "整段速率恒定");
        assertEquals(0.35D, motion.speedAt(10_000), quantisation, "越界夹紧到末段");
        assertEquals(LAUNCH, motion.directionAt(0));
        assertEquals(LAUNCH, motion.directionAt(123), "单段 ⇒ 方向恒定");
    }

    @Test
    @DisplayName("FIXED：方向恒为发射方向（段表写死方向）")
    void fixedKeepsLaunchDirection() {
        Vec3 launch = new Vec3(1.0D, 0.0D, 0.0D);
        DanmakuLegMotion motion = DanmakuLegMotion.fromSpec(3,
                legs(20, 30, 40), DanmakuRandomState.empty(), launch,
                DanmakuLegMotion.Kind.FIXED);
        for (int age = 0; age < 90; age += 7) {
            assertEquals(launch, motion.directionAt(age), "age=" + age);
        }
    }

    @Test
    @DisplayName("SEED：方向 = f(种子, 段号)，段内恒定、跨段可不同")
    void seedDirectionsAreConstantPerSegment() {
        DanmakuRandomState random = DanmakuRandomState.of(
                new int[]{0xAABBCCDD, 0x12345678, 0x0F0F0F0F}, 3);
        DanmakuLegMotion motion = DanmakuLegMotion.fromSpec(3, legs(20, 30, 40),
                random, LAUNCH, DanmakuLegMotion.Kind.SEED);

        for (int segment = 0; segment < 3; segment++) {
            Vec3 first = motion.directionAt(motion.segmentStartAt(segment));
            Vec3 last = motion.directionAt(motion.segmentStartAt(segment)
                    + Math.max(0, motion.durationAt(segment) - 1));
            assertEquals(first, last, "第 " + segment + " 段内方向 MUST 恒定");
            assertEquals(1.0D, first.length(), 1.0E-9D, "方向 MUST 是单位向量");
        }
        // 三个不同种子 ⇒ 三段方向几乎必然不同（同一方向会让「随机变向」名存实亡）
        assertNotEquals(motion.directionAt(0), motion.directionAt(20));
        assertNotEquals(motion.directionAt(20), motion.directionAt(50));
    }

    @Test
    @DisplayName("SEED：同种子 ⇒ 同方向（纯函数，不掺入时间或调用次数）")
    void sameSeedGivesSameDirection() {
        DanmakuRandomState a = DanmakuRandomState.of(new int[]{777}, 1);
        DanmakuRandomState b = DanmakuRandomState.of(new int[]{777}, 1);
        DanmakuLegMotion first = DanmakuLegMotion.fromSpec(1, legs(50), a, LAUNCH,
                DanmakuLegMotion.Kind.SEED);
        DanmakuLegMotion second = DanmakuLegMotion.fromSpec(1, legs(50), b, LAUNCH,
                DanmakuLegMotion.Kind.SEED);
        assertEquals(first.directionAt(0), second.directionAt(0));
    }

    @Test
    @DisplayName("段边界按 Tᵢ 精确切换，不早也不晚")
    void segmentBoundariesAreExact() {
        DanmakuLegMotion motion = DanmakuLegMotion.fromSpec(3, legs(20, 30, 40),
                DanmakuRandomState.of(new int[]{1, 2, 3}, 3), LAUNCH,
                DanmakuLegMotion.Kind.SEED);

        assertEquals(0, motion.segmentStartAt(0));
        assertEquals(20, motion.segmentStartAt(1));
        assertEquals(50, motion.segmentStartAt(2));
        assertEquals(90, motion.totalDuration());

        assertEquals(0, motion.segmentAt(0));
        assertEquals(0, motion.segmentAt(19), "段末仍属本段");
        assertEquals(1, motion.segmentAt(20), "正好落在边界即切换");
        assertEquals(1, motion.segmentAt(49));
        assertEquals(2, motion.segmentAt(50));
        assertEquals(2, motion.segmentAt(89));
        assertEquals(2, motion.segmentAt(90), "超出总时长夹紧到末段");
        assertEquals(0, motion.segmentAt(-5), "负年龄归第 0 段");
    }

    @Test
    @DisplayName("同一段内重复查询返回相同值（无隐藏状态）")
    void repeatedQueriesWithinSegmentAreIdentical() {
        DanmakuLegMotion motion = DanmakuLegMotion.fromSpec(4, legs(15, 25, 35, 45),
                DanmakuRandomState.of(new int[]{11, 22, 33, 44}, 4), LAUNCH,
                DanmakuLegMotion.Kind.SEED);
        for (int age = 0; age < 120; age++) {
            Vec3 direction = motion.directionAt(age);
            double speed = motion.speedAt(age);
            for (int repeat = 0; repeat < 5; repeat++) {
                assertEquals(direction, motion.directionAt(age), "age=" + age);
                assertEquals(speed, motion.speedAt(age), EPS, "age=" + age);
            }
        }
    }

    @Test
    @DisplayName("打包与解包互逆，速率定标为 /4096")
    void packRoundTrips() {
        int packed = DanmakuLegMotion.pack(300, 0.5D);
        assertEquals(300, (packed >>> 16) & 0xFFFF, "时长占高 16 位");
        assertEquals(2048, packed & 0xFFFF, "0.5 × 4096 = 2048");

        DanmakuLegMotion motion = DanmakuLegMotion.fromSpec(1, new int[]{packed},
                DanmakuRandomState.empty(), LAUNCH, DanmakuLegMotion.Kind.FIXED);
        assertEquals(300, motion.durationAt(0));
        assertEquals(0.5D, motion.speedAt(0), 1.0E-9D);
    }

    @Test
    @DisplayName("段数被夹到 [1, 8]")
    void legCountIsClamped() {
        assertEquals(1, DanmakuLegMotion.fromSpec(0, new int[0], null, LAUNCH,
                DanmakuLegMotion.Kind.SEED).legCount(), "0 段 ⇒ 1 段");
        assertEquals(1, DanmakuLegMotion.fromSpec(-3, new int[0], null, LAUNCH,
                DanmakuLegMotion.Kind.SEED).legCount());
        assertEquals(8, DanmakuLegMotion.fromSpec(99, new int[16], null, LAUNCH,
                DanmakuLegMotion.Kind.SEED).legCount());
    }

    // ------------------------------------------------------------------
    // TARGET 段：档三的降级路径
    // ------------------------------------------------------------------

    @Test
    @DisplayName("TARGET 段构造期不自行求解方向")
    void targetDoesNotSelfSolveAtConstruction() {
        DanmakuLegMotion motion = DanmakuLegMotion.fromSpec(2, legs(20, 30),
                DanmakuRandomState.of(new int[]{5, 6}, 2), LAUNCH,
                DanmakuLegMotion.Kind.TARGET);
        assertTrue(motion.requiresServerDecisionAt(0), "缺方向时 MUST 报告需要服务端决策");
        assertTrue(motion.isSegmentStart(0));
        assertFalse(motion.isSegmentStart(5), "只在段起始处推，否则退化成每拍一包");
    }

    @Test
    @DisplayName("TARGET 段未收到快照时沿用上一段方向（两端一致的降级）")
    void targetDegradesToPreviousDirection() {
        DanmakuLegMotion motion = DanmakuLegMotion.fromSpec(3, legs(20, 30, 40),
                DanmakuRandomState.empty(), LAUNCH, DanmakuLegMotion.Kind.TARGET);
        Vec3 before = motion.directionAt(0);
        assertEquals(before, motion.directionAt(25), "第 1 段缺方向 ⇒ 沿用第 0 段");
        assertEquals(before, motion.directionAt(60), "第 2 段缺方向 ⇒ 沿用更早的已知方向");
    }

    @Test
    @DisplayName("收到快照后方向被装入，且不再请求下发")
    void authoritativeDirectionIsInstalled() {
        DanmakuLegMotion motion = DanmakuLegMotion.fromSpec(2, legs(20, 30),
                DanmakuRandomState.empty(), LAUNCH, DanmakuLegMotion.Kind.TARGET);
        Vec3 authoritative = new Vec3(0.0D, 1.0D, 0.0D);
        motion.applyAuthoritativeDirection(1, authoritative);

        assertEquals(authoritative, motion.directionAt(25), "第 1 段改用权威方向");
        assertFalse(motion.requiresServerDecisionAt(25), "已收到 ⇒ MUST NOT 再请求");
        assertTrue(motion.requiresServerDecisionAt(0), "第 0 段仍未收到");
    }

    @Test
    @DisplayName("applyAuthoritativeDirection 越界与非 TARGET 一律忽略")
    void authoritativeDirectionIsGuarded() {
        DanmakuLegMotion seed = DanmakuLegMotion.fromSpec(2, legs(20, 30),
                DanmakuRandomState.of(new int[]{1, 2}, 2), LAUNCH,
                DanmakuLegMotion.Kind.SEED);
        Vec3 before = seed.directionAt(25);
        seed.applyAuthoritativeDirection(0, new Vec3(0.0D, 1.0D, 0.0D));
        assertEquals(before, seed.directionAt(25), "SEED 段 MUST NOT 被权威方向覆盖");

        DanmakuLegMotion target = DanmakuLegMotion.fromSpec(2, legs(20, 30),
                DanmakuRandomState.empty(), LAUNCH, DanmakuLegMotion.Kind.TARGET);
        target.applyAuthoritativeDirection(9, new Vec3(0.0D, 1.0D, 0.0D));
        target.applyAuthoritativeDirection(-1, new Vec3(0.0D, 1.0D, 0.0D));
        target.applyAuthoritativeDirection(0, null);
        assertTrue(target.requiresServerDecisionAt(0), "越界/空方向 MUST NOT 被接受");
    }

    @Test
    @DisplayName("恒速直线的方向是单位向量且不随年龄漂移")
    void straightDirectionIsStable() {
        DanmakuLegMotion motion = DanmakuLegMotion.straight(0.4D, 300, LAUNCH);
        Vec3 atZero = motion.directionAt(0);
        assertSame(LAUNCH, atZero, "FIXED 单段 MUST 直接复用发射方向，不重算");
        assertEquals(atZero, motion.directionAt(299));
    }

    private static int[] legs(int... durations) {
        int[] out = new int[durations.length];
        for (int i = 0; i < durations.length; i++) {
            out[i] = DanmakuLegMotion.pack(durations[i], 0.3D);
        }
        return out;
    }
}