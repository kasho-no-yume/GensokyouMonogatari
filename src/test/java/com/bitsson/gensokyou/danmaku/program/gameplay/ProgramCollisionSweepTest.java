package com.bitsson.gensokyou.danmaku.program.gameplay;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ProgramCollisionSweepTest {
    @Test void blockShapeSweepHitsRadiusAtWallEdgeButNotInflatedBoxCorner() {
        var edge=List.of(new ProgramCollisionSweep.Segment(new Vec3(0,1.15,.5),new Vec3(3,1.15,.5)));
        var block=new AABB(1,0,0,2,1,1);
        assertTrue(ProgramCollisionSweep.nearest(edge,List.of(block),b->b,0,false).isEmpty());
        var hit=ProgramCollisionSweep.nearest(edge,List.of(block),b->b,.2,false).orElseThrow();
        assertTrue(hit.point().x()<1,"sphere touches before its center reaches the wall");
        var corner=List.of(new ProgramCollisionSweep.Segment(new Vec3(0,1.15,1.15),new Vec3(3,1.15,1.15)));
        assertTrue(ProgramCollisionSweep.nearest(corner,List.of(block),b->b,.2,false).isEmpty());
    }
    @Test void curvedSweepRejectsAnUnresolvedHighCurvaturePath() {
        assertTrue(ProgramCollisionSweep.sample(t->new Vec3(t,Math.sin(t*40)*2,0),0,1).isEmpty());
    }
    @Test void quarterSamplesFindAnSCurveMissedByMidpointOnly() {
        var sweep=ProgramCollisionSweep.sample(t->new Vec3(t,Math.sin(t*Math.PI*2)*.1,0),0,1).orElseThrow();
        assertTrue(sweep.size()>1);
    }
    @Test void nearestHitIsOrderedAlongTheSweep() {
        var sweep=ProgramCollisionSweep.sample(t->new Vec3(t*10,0,0),0,1).orElseThrow();
        var far=new AABB(8,-1,-1,9,1,1); var near=new AABB(2,-1,-1,3,1,1);
        var hit=ProgramCollisionSweep.nearest(sweep,List.of("far","near"),s->s.equals("far")?far:near,.1,false);
        assertEquals("near",hit.orElseThrow().target());
    }
    @Test void broadPhaseIncludesRadiusAtTheFarEndOfAFastSweep() {
        var sweep=List.of(new ProgramCollisionSweep.Segment(Vec3.ZERO,new Vec3(10,0,0)));
        var target=new AABB(9.9,.15,-.1,10.1,.3,.1);
        assertTrue(ProgramCollisionSweep.bounds(sweep,.2).intersects(target));
        assertTrue(ProgramCollisionSweep.nearest(sweep,List.of(target),b->b,.2,false).isPresent());
    }
    @Test void pathDistanceOrdersHitsAcrossSegmentsAndStationaryContactWorks() {
        var segments=List.of(new ProgramCollisionSweep.Segment(Vec3.ZERO,new Vec3(1,0,0)),
                new ProgramCollisionSweep.Segment(new Vec3(1,0,0),new Vec3(1,0,2)));
        var first=new AABB(.8,-.1,-.1,.9,.1,.1);
        var second=new AABB(.9,-.1,1,1.1,.1,1.2);
        assertSame(first,ProgramCollisionSweep.nearest(segments,List.of(second,first),b->b,0,false).orElseThrow().target());
        var still=ProgramCollisionSweep.sample(t->Vec3.ZERO,0,1).orElseThrow();
        assertTrue(ProgramCollisionSweep.nearest(still,List.of(new AABB(-.1,-.1,-.1,.1,.1,.1)),b->b,.1,false).isPresent());
    }
}
