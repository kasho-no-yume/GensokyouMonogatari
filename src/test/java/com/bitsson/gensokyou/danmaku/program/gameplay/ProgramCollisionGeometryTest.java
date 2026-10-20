package com.bitsson.gensokyou.danmaku.program.gameplay;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgramCollisionGeometryTest {
    @Test void reducedHitRadiusAllowsGrazingButStillHitsTheCore() {
        AABB target=new AABB(0,0,0,1,1,1);
        for (boolean laser : new boolean[]{false,true}) {
            var style=new com.bitsson.gensokyou.danmaku.program.ops.Pattern.Style(
                    laser?"laser":"sphere","blue",.4,1,.8);
            assertEquals(.2,style.effectiveCollisionRadius(),1e-9);
            assertEquals(.8,style.visualRadius(),1e-9);
            var grazing=java.util.List.of(new ProgramCollisionSweep.Segment(new Vec3(-1,1.3,.5),new Vec3(2,1.3,.5)));
            assertTrue(ProgramCollisionSweep.nearest(grazing,java.util.List.of(target),box->box,style.collisionRadius(),laser).isPresent());
            assertTrue(ProgramCollisionSweep.nearest(grazing,java.util.List.of(target),box->box,style.effectiveCollisionRadius(),laser).isEmpty());
            var core=java.util.List.of(new ProgramCollisionSweep.Segment(new Vec3(-1,1.1,.5),new Vec3(2,1.1,.5)));
            assertTrue(ProgramCollisionSweep.nearest(core,java.util.List.of(target),box->box,style.effectiveCollisionRadius(),laser).isPresent());
        }
    }

    @Test void sphereDoesNotHitTheExtraCornersOfAnExpandedBox() {
        AABB target=new AABB(0,0,0,1,1,1);
        Vec3 from=new Vec3(-1,1.15,1.15),until=new Vec3(2,1.15,1.15);
        assertTrue(ProgramCollisionGeometry.segmentAabb(from,until,target,.2).isPresent());
        assertTrue(ProgramCollisionGeometry.sphereAabb(from,until,target,.2).isEmpty());
        assertTrue(ProgramCollisionGeometry.sphereAabb(new Vec3(-1,1.1,1.1),new Vec3(2,1.1,1.1),target,.2).isPresent());
    }
    @Test void sweptSphereFindsFaceEntryAndStationaryContact() {
        AABB target=new AABB(0,0,0,1,1,1);
        assertEquals(-.2,ProgramCollisionGeometry.sphereAabb(new Vec3(-1,.5,.5),new Vec3(2,.5,.5),target,.2).orElseThrow().x,1e-9);
        assertTrue(ProgramCollisionGeometry.sphereAabb(new Vec3(1.1,.5,.5),new Vec3(1.1,.5,.5),target,.2).isPresent());
        assertTrue(ProgramCollisionGeometry.sphereAabb(new Vec3(1.15,1.15,1.15),new Vec3(1.15,1.15,1.15),target,.2).isEmpty());
    }
    @Test
    void projectileRadiusExpandsTarget() {
        AABB target = new AABB(2, 1, -1, 4, 3, 1);
        assertTrue(ProgramCollisionGeometry.segmentAabb(
                new Vec3(0, 0, 0), new Vec3(6, 0, 0), target, 1).isPresent());
    }

    @Test
    void stationaryMemberUsesIntersection() {
        AABB target = new AABB(1, 1, 1, 2, 2, 2);
        var hit = ProgramCollisionGeometry.segmentAabb(
                new Vec3(1.5, 1.5, 1.5), new Vec3(1.5, 1.5, 1.5), target, 0);
        assertTrue(hit.isPresent());
        assertEquals(1.5, hit.orElseThrow().x, 1.0E-9);
    }

    @Test
    void laserAndProjectileShareFirstEntryGeometry() {
        AABB target = new AABB(2, -1, -1, 4, 1, 1);
        Vec3 start = Vec3.ZERO;
        Vec3 end = new Vec3(6, 0, 0);
        assertEquals(ProgramCollisionGeometry.segmentAabb(start, end, target, .2),
                ProgramCollisionGeometry.rayAabb(start, end, target, .2));
    }

    /**
     * 碰撞半径倍率只作用于弹射物型弹幕；激光按声明值判定。
     *
     * <p>倍率若也套在激光上，判定窗口会窄于视觉粗细一半 —— 看着擦到了却不掉血。
     */
    @Test
    void collisionRadiusScaleAppliesToProjectilesOnly() {
        var style = new com.bitsson.gensokyou.danmaku.program.ops.Pattern.Style("laser", "blue", .4, 1, .4);
        var projectile = com.bitsson.gensokyou.danmaku.program.ops.CollisionProfile.projectile();
        var laser = new com.bitsson.gensokyou.danmaku.program.ops.CollisionProfile.Laser(80, false, 10);
        assertEquals(.2, style.hitRadius(projectile), 1e-9);
        assertEquals(.2, style.effectiveCollisionRadius(), 1e-9);
        assertEquals(.4, style.hitRadius(laser), 1e-9);
        // 判定半径与视觉半径同口径（渲染读 visualRadius，两者独立声明）。
        assertEquals(style.visualRadius(), style.hitRadius(laser), 1e-9);
    }
}
