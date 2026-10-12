package com.bitsson.gensokyou.danmaku.motion;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 动态激光姿态的<b>纯数学</b>不变量（{@code danmaku-laser-motion}）。
 *
 * <p>实体里的姿态是私有的，但它只由三块公共纯函数组成：
 * <ol>
 *   <li>扫过角度 = {@link DanmakuSpeedProfile#travelAt}（曲线把「格」重解释为「度」）；</li>
 *   <li>旋转 = {@link Rotation#about}（Rodrigues）；</li>
 *   <li>枢轴/出生点 = {@link FormationFrame} 的 center / offset。</li>
 * </ol>
 * 本类把实体用的那条公式
 * {@code origin(age) = center + R(offset, axis, θ(age)) + T·v·age} 原样复算，
 * 断言它的几何性质——不需要世界。
 */
class LaserMotionMathTest {

    private static final double EPS = 1.0E-9D;

    // ---------------------------------------------------------------
    // 角速度曲线 → 扫过角度
    // ---------------------------------------------------------------

    @Test
    @DisplayName("恒定角速度：扫过角度 = ω · age")
    void constantSweepsLinearly() {
        DanmakuSpeedProfile angular = DanmakuSpeedProfile.constant(6.0D);
        assertEquals(60.0D, angular.travelAt(10), EPS,
                "恒速扫射在 10 tick 后 MUST 扫过 60°");
        assertEquals(1200.0D, angular.travelAt(200), EPS,
                "恒速扫射 MUST 一直线性累加");
    }

    @Test
    @DisplayName("减速停住：扫过总角 = ω·N/2，之后不再增加")
    void decelerateAndHoldStops() {
        double omega = 6.0D;      // 度/tick
        double decel = 40.0D;     // tick
        DanmakuSpeedProfile angular = DanmakuSpeedProfile.decelerateAndHold(omega, decel);

        assertEquals(omega, angular.speedAt(0.0D), EPS);
        // 三角面积 = ω·N/2
        assertEquals(omega * decel / 2.0D, angular.travelAt((int) decel), 1.0E-6D,
                "减速段扫过角度 MUST 等于梯形面积 ω·N/2");
        // 减速完成后停住
        assertEquals(angular.travelAt((int) decel), angular.travelAt(1_000), 1.0E-6D,
                "减速到 0 后扫过角度 MUST 不再增加（真的停住）");
    }

    // ---------------------------------------------------------------
    // 轴 / 方向
    // ---------------------------------------------------------------

    @Test
    @DisplayName("轴 ↔ 角度 往返一致（竖直轴与斜轴）")
    void axisAngleRoundTrip() {
        assertAxisRoundTrip(new Vec3(0, 1, 0));
        assertAxisRoundTrip(new Vec3(1, 0, 0));
        assertAxisRoundTrip(new Vec3(1, 1, 1).normalize());
    }

    private static void assertAxisRoundTrip(Vec3 axis) {
        double[] yawPitch = Rotation.anglesFromAxis(axis);
        Vec3 back = Rotation.axisFromAngles(yawPitch[0], yawPitch[1]);
        assertEquals(1.0D, back.dot(axis), 1.0E-6D,
                "anglesFromAxis/axisFromAngles MUST 往返得到同一单位轴");
    }

    // ---------------------------------------------------------------
    // 枢轴旋转 + 平移（复算实体公式）
    // ---------------------------------------------------------------

    /** 实体里的公式：{@code center + R(offset, axis, θ) + advance}。 */
    private static Vec3 originAt(FormationFrame frame, Vec3 axis, double thetaDeg,
                                 Vec3 translateDir, double speed, int age) {
        Vec3 rotated = Rotation.about(frame.offset(), axis, Math.toRadians(thetaDeg));
        Vec3 advance = translateDir.scale(speed * age);
        return frame.center().add(rotated).add(advance);
    }

    @Test
    @DisplayName("枢轴 = 出生点：θ=0 时位置不动，方向只转")
    void rotateAboutOriginKeepsPosition() {
        Vec3 origin = new Vec3(10, 64, 20);
        // center = 枢轴 = 出生点 ⇒ offset = 0 ⇒ 位置恒为出生点
        FormationFrame frame = new FormationFrame(
                origin.x, origin.y, origin.z, 0, 0, 0,
                0, FormationFrame.HORIZONTAL_PITCH_DEG, 0, 1, 0, 0);
        Vec3 dir0 = new Vec3(1, 0, 0);

        for (int age = 0; age <= 100; age += 25) {
            Vec3 pos = originAt(frame, new Vec3(0, 1, 0), 12.0D * age, Vec3.ZERO, 0.0D, age);
            assertEquals(0.0D, pos.subtract(origin).length(), 1.0E-9D,
                    "原地旋转时位置 MUST 保持为出生点");
        }
        // 方向：绕 Y 转 90° 后 (1,0,0) → (0,0,-1)
        Vec3 rotated = Rotation.about(dir0, new Vec3(0, 1, 0), Math.toRadians(90.0D));
        assertEquals(0.0D, rotated.x, 1.0E-6D);
        assertEquals(0.0D, rotated.y, 1.0E-6D);
        assertEquals(-1.0D, rotated.z, 1.0E-6D);
    }

    @Test
    @DisplayName("绕外部点挥扫：位置沿圆弧走，到枢轴距离恒定")
    void sweepAboutExternalPivotStaysOnCircle() {
        Vec3 pivot = new Vec3(0, 64, 0);
        Vec3 birth = new Vec3(5, 64, 0);           // 出生点离枢轴 5 格
        Vec3 offset = birth.subtract(pivot);       // = 出生点 − 枢轴
        FormationFrame frame = new FormationFrame(
                pivot.x, pivot.y, pivot.z, offset.x, offset.y, offset.z,
                0, FormationFrame.HORIZONTAL_PITCH_DEG, 0, 1, 0, 0);
        Vec3 axisY = new Vec3(0, 1, 0);

        // θ=0 回到出生点
        assertEquals(0.0D,
                originAt(frame, axisY, 0.0D, Vec3.ZERO, 0.0D, 0).subtract(birth).length(), 1.0E-9D);

        for (int deg = 0; deg <= 360; deg += 30) {
            Vec3 pos = originAt(frame, axisY, deg, Vec3.ZERO, 0.0D, 0);
            assertEquals(5.0D, pos.subtract(pivot).length(), 1.0E-6D,
                    "绕枢轴挥扫时，位置到枢轴的距离 MUST 恒为半径");
        }
        // 半圈后到对侧
        Vec3 half = originAt(frame, axisY, 180.0D, Vec3.ZERO, 0.0D, 0);
        assertEquals(-5.0D, half.x, 1.0E-6D, "半圈后 MUST 到枢轴对侧");
    }

    @Test
    @DisplayName("平移：位置沿方向线性移动，方向不变")
    void translateMovesLinearly() {
        Vec3 birth = new Vec3(0, 64, 0);
        FormationFrame frame = new FormationFrame(
                birth.x, birth.y, birth.z, 0, 0, 0,
                0, FormationFrame.HORIZONTAL_PITCH_DEG, 0, 1, 0, 0);
        Vec3 dir = new Vec3(0, 0, 1);
        double speed = 0.5D;   // 格/tick

        assertEquals(0.0D,
                originAt(frame, new Vec3(0, 1, 0), 0.0D, dir, speed, 0).subtract(birth).length(), EPS);
        Vec3 at10 = originAt(frame, new Vec3(0, 1, 0), 0.0D, dir, speed, 10);
        assertEquals(5.0D, at10.subtract(birth).length(), 1.0E-9D,
                "平移 10 tick、0.5 格/tick MUST 走 5 格");
        assertTrue(at10.z > birth.z, "平移方向 MUST 指向 +Z");
    }

    @Test
    @DisplayName("旋转 + 平移复合：两者叠加，互不吞并")
    void rotationAndTranslationCompose() {
        Vec3 pivot = new Vec3(0, 64, 0);
        Vec3 birth = new Vec3(4, 64, 0);
        FormationFrame frame = new FormationFrame(
                pivot.x, pivot.y, pivot.z,
                birth.x - pivot.x, birth.y - pivot.y, birth.z - pivot.z,
                0, FormationFrame.HORIZONTAL_PITCH_DEG, 0, 1, 0, 0);
        Vec3 axisY = new Vec3(0, 1, 0);
        Vec3 slide = new Vec3(0, 1, 0);

        // 仅旋转 + 复合：复合结果 MUST 在旋转结果上再叠加平移项
        Vec3 rotOnly = originAt(frame, axisY, 90.0D, Vec3.ZERO, 0.0D, 0);
        Vec3 both = originAt(frame, axisY, 90.0D, slide, 0.5D, 20);

        // 复合结果 − 仅旋转 = 平移项（沿 slide、10 格）
        Vec3 translationPart = both.subtract(rotOnly);
        assertEquals(10.0D, translationPart.length(), 1.0E-6D,
                "复合结果 MUST 含 10 格的平移分量");
        assertEquals(0.0D, translationPart.cross(slide).length(), 1.0E-6D,
                "平移分量 MUST 与平移方向共线");
        assertTrue(both.y > rotOnly.y, "复合结果 MUST 含平移带来的竖直位移");
    }
}
