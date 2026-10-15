package com.bitsson.gensokyou.danmaku.motion;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 曲射角速度的单位换算回归（extend-danmaku-orchestration 实机回写 · 缺陷 2）。
 *
 * <p>曲射声明的单位是<b>度/秒</b>，而 {@link Rotation#about} 收<b>弧度</b>。
 * 首轮实机里调用点漏了换算，45°/s 被当成 2.25 rad/tick（≈129°/tick），
 * 现象是「高频抽搐 + 螺旋」。本类钉死「一个 tick 该转多少」这一唯一口径——
 * 换算若再次散回调用点或漏掉 {@code toRadians}，这里会立刻红。
 */
class RotationCurveAngleTest {

    private static final Vec3 FORWARD = new Vec3(1.0D, 0.0D, 0.0D);
    private static final Vec3 WORLD_UP = new Vec3(0.0D, 1.0D, 0.0D);

    /** 两个单位向量在包含它们的平面内的夹角（度）。 */
    private static double angleBetween(Vec3 a, Vec3 b) {
        return Math.toDegrees(Math.acos(Math.max(-1.0D, Math.min(1.0D, a.normalize().dot(b.normalize())))));
    }

    @Test
    @DisplayName("度/秒 → 每 tick 转角 = rate/20 度（不是 rate/20 弧度）")
    void degreesPerSecondIsConvertedToRadians() {
        // 90°/s ⇒ 每 tick 应转 4.5°。若把 4.5 当弧度用，则是 ≈257.8°。
        for (double rate : new double[]{1.8D, 45.0D, 90.0D, 360.0D}) {
            Vec3 turned = Rotation.aboutDegPerSec(FORWARD, WORLD_UP, rate);
            assertEquals(rate / 20.0D, angleBetween(FORWARD, turned), 1.0E-9D,
                    rate + "°/s 每 tick 应转 " + (rate / 20.0D) + "°");
        }
    }

    @Test
    @DisplayName("绕竖直轴扫一圈：20 tick 转过 rate/20 度后仍在水平面内（不是螺旋）")
    void staysPlanarForUpAxis() {
        Vec3 velocity = FORWARD;
        double rate = 90.0D;
        for (int i = 0; i < 20; i++) {
            velocity = Rotation.aboutDegPerSec(velocity, WORLD_UP, rate);
        }
        assertEquals(0.0D, velocity.y, 1.0E-12D, "绕世界 Y 轴旋转 MUST NOT 产生竖直分量");
        assertEquals(90.0D, angleBetween(FORWARD, velocity), 1.0E-9D,
                "20 tick × 4.5°/tick 应正好转过 90°");
    }
}
