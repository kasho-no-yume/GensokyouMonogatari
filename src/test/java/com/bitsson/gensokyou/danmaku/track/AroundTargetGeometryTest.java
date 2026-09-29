package com.bitsson.gensokyou.danmaku.track;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「以目标周围区域为原点」的发射几何。
 *
 * <p>这批断言守的是需求里最容易悄悄失效的三条：发射点<b>不在发射者身上</b>、
 * 每发<b>方向各自独立</b>、方向被<b>夹角上限</b>约束。三者都不满足时现象都不报错，
 * 只是「看起来是另一种弹幕」。
 */
class AroundTargetGeometryTest {

    /** 发射者站在离目标很远的地方——需求明确「发射者不必移动到发射点」。 */
    private static final Vec3 BOSS = new Vec3(0, 64, -40);
    private static final Vec3 TARGET = new Vec3(0, 64, 0);
    private static final Vec3 FORWARD = TARGET.subtract(BOSS).normalize();
    private static final Vec3 WORLD_UP = new Vec3(0, 1, 0);

    private static List<Geometry.Shot> around(int count, double radius, double aimDeg) {
        return Geometry.build(Shape.AROUND_TARGET, BOSS, FORWARD, TARGET, WORLD_UP,
                Shape.Params.defaults().count(count).radius(radius).spread(aimDeg),
                0.0D, 0.0D);
    }

    /** 发射点 MUST NOT 落在发射者身上——这一条是整个形态存在的理由。 */
    @Test
    void originsAreNotOnTheBoss() {
        for (Geometry.Shot shot : around(12, 5.0D, 0.0D)) {
            assertTrue(shot.origin().distanceTo(BOSS) > 10.0D,
                    "发射点仍贴着 BOSS（距离 " + shot.origin().distanceTo(BOSS) + "），"
                            + "那就完全没解耦");
        }
    }

    /**
     * 每发 MUST 落在目标周围的区域内：到目标的距离 MUST NOT 超过声明的区域半径。
     *
     * <p>这条守的是「声明的区域半径」与「实际排布」一致。实现里横向与竖直两个分量
     * 是分别按比例取的，二者合成后 MUST 仍在半径内——早先没做这个反解，
     * 实际距离会达到 {@code 1.118·半径}，表现为「参数写 5、看着像 5.6」。
     */
    @Test
    void originsStayInsideTheDeclaredRegion() {
        for (double radius : new double[]{3.0D, 5.0D, 8.0D}) {
            for (Geometry.Shot shot : around(16, radius, 0.0D)) {
                double distance = shot.origin().distanceTo(TARGET);
                assertTrue(distance <= radius + 1.0E-6D,
                        "发射点离目标 " + distance + "，超出了声明的区域半径 " + radius);
                assertTrue(distance > 0.0D, "发射点与目标重合，射线无方向");
            }
        }
    }

    /**
     * 每发方向 MUST 独立——MUST NOT 全批共用一个方向。
     *
     * <p>共用的后果不是「少一点随机」，而是形态完全变样：一圈弹会变成
     * 一束平行光，玩家看到的是「一道墙」而不是「从四周射来」。
     */
    @Test
    void everyShotHasItsOwnDirection() {
        List<Geometry.Shot> shots = around(12, 5.0D, 0.0D);
        Set<Vec3> distinct = new HashSet<>();
        for (Geometry.Shot shot : shots) {
            distinct.add(shot.direction());
        }
        assertTrue(distinct.size() > shots.size() / 2,
                "12 发里只有 " + distinct.size() + " 个不同方向——方向没有逐发独立");
    }

    /** 零夹角时，每发 MUST 精确指向目标（此时方向可由原点唯一确定）。 */
    @Test
    void zeroAimCapPointsExactlyAtTheTarget() {
        for (Geometry.Shot shot : around(12, 5.0D, 0.0D)) {
            Vec3 expected = TARGET.subtract(shot.origin()).normalize();
            assertEquals(1.0D, shot.direction().dot(expected), 1.0E-9,
                    "夹角上限为 0 时方向必须精确指向目标");
        }
    }

    /**
     * 方向 MUST 被夹角上限包络约束。
     *
     * <p>夹角 = 方向与「原点 → 目标」连线的夹角。这是需求点名的判据：
     * 超过上限的方向 MUST NOT 出现。
     */
    @Test
    void directionsAreBoundedByTheAimCap() {
        for (double cap : new double[]{0.0D, 15.0D, 45.0D, 90.0D}) {
            for (Geometry.Shot shot : around(20, 5.0D, cap)) {
                Vec3 toTarget = TARGET.subtract(shot.origin()).normalize();
                double degrees = Math.toDegrees(Math.acos(
                        Math.max(-1.0D, Math.min(1.0D, shot.direction().dot(toTarget)))));
                // 容差 1e-4 度：双端各自按同一条 Rodrigues 公式算，末位舍入会带来
                // 1e-6 量级的夹角差。给 1e-9 会让这条断言变成对浮点末位的检测，
                // 那不是它在守的东西。
                assertTrue(degrees <= cap + 1.0E-4D,
                        "实际夹角 " + degrees + " 超过了声明的上限 " + cap);
            }
        }
    }

    /**
     * 瞄准夹角 MUST 能开到 <b>180°（完全自由，含从背后射）</b>。
     *
     * <p><b>这条钉的是一个被撤回的设计决定。</b>早先把上限硬夹在 90°，理由是
     * 「超过就会从背后射向目标，读作护住玩家而非攻击，玩家的闪避直觉会失效」。
     * 那条理由是错的：激光有 {@code Phase.DELAY} 预警，玩家在射线亮起前就看得见
     * 它从哪来、指向哪——<b>公平性来自预警，不来自方向</b>。硬夹 90° 只会让
     * 「背后交叉火网」这类设计写不出来，换来的好处是零。
     *
     * <p>所以本测试反过来守：声明 180° 时 MUST NOT 被悄悄夹回 90°。
     */
    @Test
    void aimCapReachesFullFreedom() {
        double maxSeen = 0.0D;
        for (Geometry.Shot shot : around(32, 5.0D, 180.0D)) {
            Vec3 toTarget = TARGET.subtract(shot.origin()).normalize();
            double degrees = Math.toDegrees(Math.acos(
                    Math.max(-1.0D, Math.min(1.0D, shot.direction().dot(toTarget)))));
            maxSeen = Math.max(maxSeen, degrees);
        }
        assertTrue(maxSeen > 90.0D,
                "声明 180° 却只产出最大 " + maxSeen + "° 的方向——上限被悄悄夹回 90° 了，"
                        + "「背后交叉火网」这类设计就写不出来");
    }

    /** 声明 0° 时 MUST 全部精确指向目标（另一个极端也要能用）。 */
    @Test
    void zeroCapIsFullyAimed() {
        for (Geometry.Shot shot : around(16, 5.0D, 0.0D)) {
            Vec3 toTarget = TARGET.subtract(shot.origin()).normalize();
            assertTrue(shot.direction().dot(toTarget) > 1.0D - 1.0E-6D,
                    "声明 0° 时仍有方向偏离目标连线");
        }
    }

    /** 几何 MUST 是确定性的——同一入参两次调用给出同一批弹。 */
    @Test
    void evaluationIsDeterministic() {
        List<Geometry.Shot> a = around(16, 5.0D, 30.0D);
        List<Geometry.Shot> b = around(16, 5.0D, 30.0D);
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).origin(), b.get(i).origin());
            assertEquals(a.get(i).direction(), b.get(i).direction());
        }
    }

    /** 缺省目标（退化路径）MUST NOT 产生 NaN 方向。 */
    @Test
    void legacyOverloadStillWorksAndStaysFinite() {
        List<Geometry.Shot> shots = Geometry.build(Shape.AROUND_TARGET, BOSS, FORWARD, WORLD_UP,
                Shape.Params.defaults().count(8).radius(4.0D).spread(20.0D), 0.0D, 0.0D);
        assertFalse(shots.isEmpty(), "旧签名没有产出任何弹");
        for (Geometry.Shot shot : shots) {
            assertTrue(Double.isFinite(shot.direction().x)
                    && Double.isFinite(shot.direction().y)
                    && Double.isFinite(shot.direction().z), "退化路径产生了 NaN 方向");
        }
    }
}
