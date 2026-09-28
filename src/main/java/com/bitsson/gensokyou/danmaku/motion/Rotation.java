package com.bitsson.gensokyou.danmaku.motion;

import net.minecraft.world.phys.Vec3;

/**
 * 三维旋转工具。
 *
 * <p>只有 Rodrigues 公式，没有四元数、没有 {@code Matrix4f}——因为这些轨迹都要
 * <b>双端各自算一遍</b>，表达形式越少越容易核对，而「一个公式算两处」比
 * 「两处各算一遍」更难走偏。
 */
public final class Rotation {

    private Rotation() {
    }

    /**
     * 绕单位轴 {@code axis} 旋转 {@code angleRad}（Rodrigues 公式）。
     *
     * <p>既有的曲射轴与新 rig 的公转/自转共用本实现，故「曲射的手感」与
     * 「装置公转的手感」在数值上是同一条曲线族，不会出现两套近似的旋转各偏一点。
     */
    public static Vec3 about(Vec3 vec, Vec3 axis, double angleRad) {
        double dot = axis.dot(vec);
        Vec3 cross = axis.cross(vec);
        return vec.scale(Math.cos(angleRad))
                .add(cross.scale(Math.sin(angleRad)))
                .add(axis.scale(dot * (1.0D - Math.cos(angleRad))));
    }

    /**
     * 由（下转角, 俯仰角）还原环绕平面的法线轴。
     *
     * <p>沿用 {@code Geometry.axisFrom} 的角度打包约定：只同步两个角度，
     * 不同步三轴向量或四元数。两处刻意保持同一套约定，改一处必须改两处。
     */
    public static Vec3 axisFromAngles(double yawDeg, double pitchDeg) {
        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(pitchDeg);
        double cosPitch = Math.cos(pitch);
        return new Vec3(-Math.sin(yaw) * cosPitch, -Math.sin(pitch), Math.cos(yaw) * cosPitch);
    }
}
