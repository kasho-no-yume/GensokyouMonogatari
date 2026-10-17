package com.bitsson.gensokyou.danmaku.program.gameplay;

import com.bitsson.gensokyou.danmaku.program.math.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProgramRenderMathTest {
    @Test
    void worldDirectionDoesNotDependOnCamera() {
        Vec3 muzzle=ProgramRenderMath.worldDirection(new Vec3(2,0,0));
        assertEquals(1, muzzle.x(), 1.0E-9);
        assertEquals(0, muzzle.y(), 1.0E-9);
        assertEquals(0, muzzle.z(), 1.0E-9);
        assertEquals(muzzle, ProgramRenderMath.worldDirection(new Vec3(2,0,0)));
    }

    @Test
    void billboardNormalIsPresentationOnly() {
        assertEquals(new Vec3(0,0,-1), ProgramRenderMath.billboardNormal(Vec3.Z));
        assertEquals(new Vec3(-1,0,0), ProgramRenderMath.billboardNormal(Vec3.X));
    }
}
