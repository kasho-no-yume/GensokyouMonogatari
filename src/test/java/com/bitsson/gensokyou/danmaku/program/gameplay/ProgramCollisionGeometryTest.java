package com.bitsson.gensokyou.danmaku.program.gameplay;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgramCollisionGeometryTest {
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
}
