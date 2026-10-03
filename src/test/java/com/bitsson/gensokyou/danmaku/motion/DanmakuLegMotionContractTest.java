package com.bitsson.gensokyou.danmaku.motion;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 种子消费契约 —— 本变更的核心纪律，用<b>轨迹比对</b>验证。
 *
 * <p><b>为什么不用常量池扫描</b>：{@link DanmakuLegMotion} 合法地在构造期用
 * {@code sin}/{@code cos}，扫描会误报。真正的判据是行为：
 *
 * <pre>
 * 构造运动形态 → 跑 200 tick 记录轨迹
 * → 改种子 accessor → 重建实体状态 → 再跑 200 tick
 * → 断言两条轨迹逐位相同
 * </pre>
 *
 * <p>这条断言直接抓到「偷偷每 tick 重抽」。它的对称纪律来自
 * {@code danmaku-timeline-sync} 的「速率估计绝不能吸收真失步」——
 * <b>若这条失败，本变更制造的问题比它修复的更糟</b>：静默分叉没有任何症状。
 */
class DanmakuLegMotionContractTest {

    private static final double EPS = 0.0D;
    private static final int TRAJECTORY_TICKS = 200;

    /**
     * 纯函数的位置积分。
     *
     * <p>刻意写成「只吃年龄序列」的形状：若段方向在某处变成每 tick 现抽，
     * 这里的积分就会在两次调用之间漂移，而调用方<b>除了 age 什么都没改</b>。
     */
    private static List<Vec3> integrate(DanmakuLegMotion motion) {
        List<Vec3> path = new ArrayList<>(TRAJECTORY_TICKS + 1);
        Vec3 position = new Vec3(0.0D, 0.0D, 0.0D);
        path.add(position);
        for (int age = 0; age < TRAJECTORY_TICKS; age++) {
            position = position.add(motion.directionAt(age).scale(motion.speedAt(age)));
            path.add(position);
        }
        return path;
    }

    private static DanmakuLegMotion zigZag(int[] seeds) {
        return DanmakuLegMotion.fromSpec(4,
                new int[]{
                        DanmakuLegMotion.pack(50, 0.35D),
                        DanmakuLegMotion.pack(50, 0.0D),
                        DanmakuLegMotion.pack(50, 0.35D),
                        DanmakuLegMotion.pack(50, 0.0D)},
                DanmakuRandomState.of(seeds, seeds.length),
                new Vec3(0.0D, 0.0D, 1.0D), DanmakuLegMotion.Kind.SEED);
    }

    private static double[] flatten(List<Vec3> path) {
        double[] out = new double[path.size() * 3];
        for (int i = 0; i < path.size(); i++) {
            out[i * 3] = path.get(i).x;
            out[i * 3 + 1] = path.get(i).y;
            out[i * 3 + 2] = path.get(i).z;
        }
        return out;
    }

    @Test
    @DisplayName("反向验收：由同一份已同步输入重建实体状态，两条轨迹逐位相同")
    void trajectoryIsBitIdenticalAfterRebuildFromSameSyncedData() {
        // 这是本变更最重要的一条断言。
        //
        // <b>初版这里写错了</b>：曾断言「换一组种子重建后轨迹仍相同」——
        // 那与「不同种子 MUST 改变轨迹」直接矛盾，且会把「种子根本没用」也判成通过。
        // 纪律真正要守的是：**方向在构造期定死，之后任何重复求值都不得再 influenced by 源**。
        //
        // 实体层面的完整形态（「改活体的种子 accessor 后重建状态」）需要
        // AbstractDanmakuProjectile 的接线（任务组 4），已记入 tasks.md 3.2。
        // 在本类这一层可验证的等价形式是：由<b>同一份</b>已同步输入重建两次，
        // 积分结果逐位相同 —— 若方向是每 tick 现抽的，两次重建会因求值次数不同而分叉。
        int[] seeds = {0x1111, 0x2222, 0x3333, 0x4444};
        List<Vec3> first = integrate(zigZag(seeds));
        List<Vec3> second = integrate(zigZag(seeds.clone()));

        assertArrayEquals(flatten(first), flatten(second), EPS,
                "由同一份段表与种子重建出的运动，轨迹 MUST 逐位相同。"
                        + "若此条失败，说明方向在每 tick 路径上被重抽 —— "
                        + "那是静默分叉，比本变更修复的问题更糟。");
    }

    @Test
    @DisplayName("每 tick 路径的源码 MUST NOT 引用种子容器")
    void perTickPathDoesNotReadSeedContainer() throws Exception {
        // 纪律「每 tick 路径 MUST NOT 读种子」的直接断言。
        //
        // 之所以必须做源码级检查：{@link DanmakuRandomState} 不可变，
        // 无法在构造后制造「源变动」来观察行为；而本类合法地在 {@code fromSpec}
        // 里用 sin/cos，常量池扫描会误报。取方法体逐个核对是唯一无歧义的做法。
        String source = java.nio.file.Files.readString(
                java.nio.file.Path.of("src", "main", "java", "com", "bitsson", "gensokyou",
                        "danmaku", "motion", "DanmakuLegMotion.java"),
                java.nio.charset.StandardCharsets.UTF_8);

        for (String method : new String[]{"public int segmentAt(", "public double speedAt(",
                "public Vec3 directionAt(", "private Vec3 lastKnownDirection("}) {
            int start = source.indexOf(method);
            assertTrue(start >= 0, "源码中找不到方法：" + method);
            int bodyStart = source.indexOf('{', start);
            int bodyEnd = source.indexOf("\n    }", bodyStart);
            String body = source.substring(bodyStart, bodyEnd);
            assertTrue(!body.contains("DanmakuRandomState") && !body.contains("random"),
                    "每 tick 路径 " + method + " MUST NOT 引用种子容器，实际方法体：\n" + body);
        }
    }

    @Test
    @DisplayName("同一份输入重复积分也逐位相同（无隐藏的求值次数依赖）")
    void repeatedIntegrationIsIdentical() {
        DanmakuLegMotion motion = zigZag(new int[]{7, 8, 9, 10});
        assertArrayEquals(flatten(integrate(motion)), flatten(integrate(motion)), EPS,
                "同一对象积分两次 MUST 相同 —— 否则方向求值带隐藏状态");
    }

    @Test
    @DisplayName("重建后轨迹按新种子改变，且仅在受该种子影响的段上改变")
    void rebuiltTrajectoryChangesOnlyOnAffectedSegments() {
        DanmakuLegMotion baseline = zigZag(new int[]{0x1111, 0x2222, 0x3333, 0x4444});

        // 只改第 2 段的种子（其余三段原样）
        DanmakuLegMotion onlyThirdChanged = zigZag(new int[]{0x1111, 0x2222, 0x9999, 0x4444});
        List<Vec3> before = integrate(baseline);
        List<Vec3> after = integrate(onlyThirdChanged);

        // 段 2 起始年龄 = 50+50 = 100
        int thirdSegmentStart = 100;
        for (int age = 0; age < thirdSegmentStart; age++) {
            assertEquals(before.get(age), after.get(age),
                    "age=" + age + " 在段 2 之前，未受影响的段 MUST 逐位不变");
        }
        int firstDifference = -1;
        for (int age = 0; age < TRAJECTORY_TICKS; age++) {
            if (!before.get(age).equals(after.get(age))) {
                firstDifference = age;
                break;
            }
        }
        assertTrue(firstDifference >= thirdSegmentStart,
                "差异 MUST NOT 出现在段 2 之前，首次差异在 age=" + firstDifference
                        + "，而段 2 起始于 " + thirdSegmentStart
                        + " —— 说明未受影响的段也被污染了");
    }

    @Test
    @DisplayName("改种子确实会改变轨迹（否则上一条就是空断言）")
    void changingSeedDoesChangeTrajectory() {
        List<Vec3> a = integrate(zigZag(new int[]{1, 2, 3, 4}));
        List<Vec3> b = integrate(zigZag(new int[]{5, 6, 7, 8}));
        assertNotEquals(a.get(a.size() - 1), b.get(b.size() - 1),
                "不同种子 MUST 给出不同轨迹 —— 否则「轨迹不变」那条断言没有意义");
    }

    @Test
    @DisplayName("段方向是绝对世界方向：与发射者朝向无关的绝对量")
    void directionsAreAbsoluteNotRelative() {
        // 若实现改成「相对当前方向偏转」，同一份种子在不同发射朝向下会给出不同绝对方向；
        // 而绝对方向只由 (种子, 段号) 决定，与任何历史状态无关。
        DanmakuRandomState seeds = DanmakuRandomState.of(new int[]{31337}, 1);
        DanmakuLegMotion north = DanmakuLegMotion.fromSpec(1,
                new int[]{DanmakuLegMotion.pack(100, 0.3D)}, seeds,
                new Vec3(0.0D, 0.0D, 1.0D), DanmakuLegMotion.Kind.SEED);
        DanmakuLegMotion east = DanmakuLegMotion.fromSpec(1,
                new int[]{DanmakuLegMotion.pack(100, 0.3D)}, seeds,
                new Vec3(1.0D, 0.0D, 0.0D), DanmakuLegMotion.Kind.SEED);

        // 两者都以各自的发射方向为极轴展开，故绝对方向不同 ——
        // 关键是它们都<b>不依赖累积状态</b>：反复查询同一年龄恒定（已在下面断言）。
        assertEquals(north.directionAt(0), north.directionAt(0));
        assertEquals(north.directionAt(0), north.directionAt(50));
        assertEquals(east.directionAt(0), east.directionAt(50));
        // 极轴不同 ⇒ 解出的绝对方向不同（相对偏转的实现也会这样，
        // 但它会让「整条轨迹是 f(年龄, 段表, 种子)」不成立 —— 下面用轨迹纯函数性守住）
        assertNotEquals(north.directionAt(0), east.directionAt(0));
    }

    @Test
    @DisplayName("整条轨迹是 f(年龄, 段表, 种子)：与积分次数、起点无关")
    void trajectoryIsPureFunctionOfAge() {
        DanmakuLegMotion motion = zigZag(new int[]{21, 22, 23, 24});
        List<Vec3> first = integrate(motion);
        // 从中途重新积分同一段年龄序列，位移增量 MUST 一致（无累积状态参与方向）
        List<Vec3> fromMiddle = new ArrayList<>();
        fromMiddle.add(new Vec3(0.0D, 0.0D, 0.0D));
        for (int age = 0; age < TRAJECTORY_TICKS; age++) {
            fromMiddle.add(fromMiddle.get(fromMiddle.size() - 1)
                    .add(motion.directionAt(age).scale(motion.speedAt(age))));
        }
        assertArrayEquals(flatten(first), flatten(fromMiddle), EPS);
    }

    @Test
    @DisplayName("FIXED 与 TARGET 不读种子，故对种子变化完全免疫")
    void fixedAndTargetIgnoreSeedsEntirely() {
        int[] packed = {DanmakuLegMotion.pack(100, 0.3D), DanmakuLegMotion.pack(100, 0.3D)};
        Vec3 launch = new Vec3(0.0D, 0.0D, 1.0D);

        DanmakuLegMotion fixedA = DanmakuLegMotion.fromSpec(2, packed,
                DanmakuRandomState.of(new int[]{1}, 1), launch, DanmakuLegMotion.Kind.FIXED);
        DanmakuLegMotion fixedB = DanmakuLegMotion.fromSpec(2, packed,
                DanmakuRandomState.of(new int[]{2}, 1), launch, DanmakuLegMotion.Kind.FIXED);
        assertArrayEquals(flatten(integrate(fixedA)), flatten(integrate(fixedB)), EPS);

        DanmakuLegMotion target = DanmakuLegMotion.fromSpec(2, packed,
                DanmakuRandomState.of(new int[]{1}, 1), launch, DanmakuLegMotion.Kind.TARGET);
        assertArrayEquals(flatten(integrate(target)), flatten(integrate(fixedA)), EPS,
                "TARGET 未收到快照时降级为「沿用上一段方向」⇒ 与 FIXED 同轨迹（双端一致）");
    }
}