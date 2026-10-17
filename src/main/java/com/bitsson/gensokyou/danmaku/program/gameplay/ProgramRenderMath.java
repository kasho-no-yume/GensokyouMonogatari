package com.bitsson.gensokyou.danmaku.program.gameplay;

import com.bitsson.gensokyou.danmaku.program.math.Vec3;

/** Pure render math separating camera-facing presentation from world direction. */
public final class ProgramRenderMath {
    private ProgramRenderMath() {}

    public static Vec3 worldDirection(Vec3 direction) {
        return direction.length() < 1.0E-12D ? Vec3.Z : direction.unit();
    }

    public static Vec3 billboardNormal(Vec3 cameraForward) {
        return cameraForward.length() < 1.0E-12D ? Vec3.Z : cameraForward.unit().multiply(-1);
    }
}
