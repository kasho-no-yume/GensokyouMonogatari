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
    @Test void visibleForegroundDoesNotConsumeSlotsOrStarveOffscreenCandidates() {
        var population=new ResidentPopulation();var surface=new Surface();
        var candidates=new ArrayList<ResidentScene.Slot>();
        for(int i=0;i<64;i++) candidates.add(slot("foreground/"+i,10+i*.01,0));
        for(int i=0;i<120;i++) candidates.add(slot("offscreen/"+i,20+i*.01,0));
        for(int tick=0;tick<25;tick++) population.tick(candidates,VIEWER,surface,
                walker->walker.slot().identity().startsWith("offscreen/"));
        assertEquals(96,population.size());
        assertTrue(population.visible().stream().allMatch(w->w.slot().identity().startsWith("offscreen/")));
    }
    @Test void alreadyAdmittedResidentsCanEnterViewAndDeferredResidentsRetry() {
        var population=new ResidentPopulation();var surface=new Surface();var point=slot("normal",20,0);
        population.tick(List.of(point),VIEWER,surface,walker->false);
        assertEquals(0,population.size());
        for(int tick=0;tick<ResidentPopulation.VIEW_RETRY_TICKS;tick++) population.tick(List.of(point),VIEWER,surface,walker->true);
        assertEquals(1,population.size());
        var admitted=population.visible().getFirst();
        population.tick(List.of(point),VIEWER,surface,walker->{fail("Existing resident must not be tested for a new spawn");return false;});
        assertSame(admitted,population.visible().getFirst());
    }
    @Test void admissionUsesTheResolvedSpawnPositionAndClearResetsDeferral() {
        var population=new ResidentPopulation();var point=slot("shifted",20,0);
        ResidentWalker.Surface shifted=new ResidentWalker.Surface() {
            public ResidentRoute.Point resolve(ResidentRoute.Point p,float scale,boolean protect) {return new ResidentRoute.Point(30,p.y(),p.z());}
            public boolean clear(ResidentRoute.Point a,ResidentRoute.Point b,float scale) {return true;}
        };
        population.tick(List.of(point),VIEWER,shifted,walker->{assertEquals(30,walker.frame(1).position().x());return false;});
        assertEquals(0,population.size());
        population.clear();
        population.tick(List.of(point),VIEWER,shifted,walker->true);
        assertEquals(1,population.size());
    }
    @Test void indoorCustomersCanFillNearbyRoomsAfterInitialAdmissionEnds() {
        var population=new ResidentPopulation();var surface=new Surface();
        for(int tick=0;tick<45;tick++) population.tickAoi(List.of(),VIEWER,surface);
        var indoor=slot("gensokyou:village_indoor/hv_suzunaan/one",5,0);
        var street=slot("gensokyou:street/near",10,0);
        var far=slot("gensokyou:village_indoor/hv_suzunaan/far",60,0);
        population.tickAoi(List.of(indoor,street,far),VIEWER,surface);
        assertEquals(1,population.size());
        var customer=population.visible().getFirst();
        assertSame(indoor,customer.slot());
        assertTrue(population.opacity(customer,0)<1);
        population.tickAoi(List.of(indoor),VIEWER,surface);
        assertSame(customer,population.visible().getFirst());
        population.tickAoi(List.of(),new ResidentRoute.Point(100,72,0),surface);
        assertEquals(0,population.size());
    }

    private static final class Surface implements ResidentWalker.Surface {
        int probes;
        public ResidentRoute.Point resolve(ResidentRoute.Point p,float scale,boolean protectedGround) {probes++;return p.x()<0?null:p;}
        public boolean clear(ResidentRoute.Point a,ResidentRoute.Point b,float scale) {return true;}
    }
}
