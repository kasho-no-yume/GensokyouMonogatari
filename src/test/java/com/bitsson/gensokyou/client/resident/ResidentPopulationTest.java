package com.bitsson.gensokyou.client.resident;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ResidentPopulationTest {
    private static final ResidentRoute.Point VIEWER=new ResidentRoute.Point(0,72,0);
    private static ResidentScene.Slot slot(String id,double x,double z) {
        return new ResidentScene.Slot(id,new ResidentRoute(List.of(new ResidentRoute.Point(x,72,z))),0,true);
    }
    @Test void invalidNearestPointsDoNotStarveNinetySixValidResidents() {
        var candidates=new ArrayList<ResidentScene.Slot>();
        for(int i=0;i<64;i++) candidates.add(slot("invalid/"+i,-1-i*.01,0));
        for(int i=0;i<120;i++) candidates.add(slot("valid/"+i,20+i*.01,0));
        var population=new ResidentPopulation();var surface=new Surface();
        population.tick(candidates,VIEWER,surface);
        assertTrue(surface.probes<=ResidentPopulation.ADMISSIONS_PER_TICK);
        for(int tick=0;tick<20;tick++) population.tick(candidates,VIEWER,surface);
        assertEquals(96,population.size());
        assertEquals(96,population.visible().size());
        assertTrue(population.visible().stream().allMatch(w->w.slot().identity().startsWith("valid/")));
    }
    @Test void validWalkersSurviveCandidateReorderingAndDistantOnesAreNotTicked() {
        var population=new ResidentPopulation();var surface=new Surface();
        population.tick(List.of(slot("one",20,0)),VIEWER,surface);
        var original=population.visible().getFirst();
        population.tick(List.of(),new ResidentRoute.Point(1,72,0),surface);
        assertSame(original,population.visible().getFirst());
        surface.probes=0;
        population.tick(List.of(),new ResidentRoute.Point(1000,72,0),surface);
        assertEquals(0,population.size());assertEquals(0,surface.probes);
    }
    @Test void streetAdmissionsAvoidThePlayerAndExistingBodiesAndCanBeCleared() {
        var population=new ResidentPopulation();var surface=new Surface();
        population.tick(List.of(slot("gensokyou:street/near",3,0),slot("gensokyou:street/a",20,0),
                slot("gensokyou:street/b",20.1,0),slot("gensokyou:street/c",23,0)),VIEWER,surface);
        assertEquals(2,population.size());
        population.clear();assertEquals(0,population.size());assertTrue(population.visible().isEmpty());
    }
    private static final class Surface implements ResidentWalker.Surface {
        int probes;
        public ResidentRoute.Point resolve(ResidentRoute.Point p,float scale,boolean protectedGround) {probes++;return p.x()<0?null:p;}
        public boolean clear(ResidentRoute.Point a,ResidentRoute.Point b) {return true;}
    }
}
