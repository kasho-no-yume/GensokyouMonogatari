package com.bitsson.gensokyou.client.resident;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ResidentStreetCrowdTest {
    private static final ResidentRoute.Point VIEWER=new ResidentRoute.Point(0,72,0);
    private static List<ResidentStreetCrowd.Seed> interests() {
        var seeds=new ArrayList<ResidentStreetCrowd.Seed>();
        for(int x=-60;x<=60;x+=6) for(int z=-60;z<=60;z+=6) seeds.add(ResidentStreetCrowd.Seed.at(x,z,(x+z)&1));
        return seeds;
    }
    @Test void buildsDenseLocalPedestriansWithinABoundedProbeBudget() {
        var crowd=new ResidentStreetCrowd();var surface=new Street();
        for(int tick=0;tick<240;tick++) {
            surface.probes=0;crowd.update(VIEWER,ResidentStreetCrowdTest::interests,surface);
            assertTrue(surface.probes<=ResidentStreetCrowd.PROBES_PER_TICK);
            assertTrue(crowd.size()<=ResidentStreetCrowd.MAX_CACHED);
        }
        var slots=crowd.near(VIEWER,512);
        assertTrue(slots.size()>=96);
        assertTrue(slots.stream().filter(s->s.route().points().size()>1).count()>slots.size()*.65);
        for(var slot:slots) {
            assertTrue(slot.protectedGround());assertTrue(slot.route().points().size()<=16);
            double length=0;var points=slot.route().points();
            for(int i=1;i<points.size();i++) length+=points.get(i).distance(points.get(i-1));
            if(points.size()>1) assertTrue(length>=4 && length<=16);
        }
    }
    @Test void reusesNearbyRoutesRetiresDistantRoutesAndDoesNotRescanInterestsEveryTick() {
        var crowd=new ResidentStreetCrowd();var surface=new Street();var refreshes=new AtomicInteger();
        for(int tick=0;tick<40;tick++) crowd.update(VIEWER,()->{refreshes.incrementAndGet();return interests();},surface);
        assertEquals(1,refreshes.get());
        var existing=crowd.near(VIEWER,512).getFirst();
        crowd.update(new ResidentRoute.Point(1,72,0),()->{refreshes.incrementAndGet();return interests();},surface);
        assertEquals(2,refreshes.get());
        assertSame(existing,crowd.near(VIEWER,512).stream().filter(s->s.identity().equals(existing.identity())).findFirst().orElseThrow());
        crowd.update(new ResidentRoute.Point(1000,72,0),List::of,surface);
        assertTrue(crowd.near(new ResidentRoute.Point(1000,72,0),512).isEmpty());
        assertEquals(0,crowd.size());
        crowd.clear();assertEquals(0,crowd.size());
    }
    @Test void rejectedGroundAndBlockedSegmentsCannotBecomeWalkingRoutes() {
        var crowd=new ResidentStreetCrowd();var surface=new Street();surface.blocked=true;
        for(int tick=0;tick<100;tick++) crowd.update(VIEWER,ResidentStreetCrowdTest::interests,surface);
        assertTrue(crowd.near(VIEWER,512).stream().allMatch(s->s.route().points().size()==1));
        crowd.clear();surface.available=false;
        for(int tick=0;tick<40;tick++) crowd.update(VIEWER,ResidentStreetCrowdTest::interests,surface);
        assertEquals(0,crowd.size());
    }
    private static final class Street implements ResidentStreetCrowd.Streets {
        int probes;boolean available=true,blocked;
        public ResidentRoute.Point groundAt(double x,double z) {probes++;return available?new ResidentRoute.Point(x,72,z):null;}
        public boolean clear(ResidentRoute.Point a,ResidentRoute.Point b) {return !blocked;}
    }
}
