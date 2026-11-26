package com.bitsson.gensokyou.worldgen.human_village;

import org.junit.jupiter.api.Test;
import static com.bitsson.gensokyou.worldgen.human_village.BuildingInstance.BuildingCategory.*;
import static com.bitsson.gensokyou.worldgen.human_village.HumanVillagePlan.*;
import static org.junit.jupiter.api.Assertions.*;

class VillageRoadRouterTest {
    @Test void unpavedNegativeSpaceConnectsHomesAndPavingStaysLocal() {
        for(long seed:VillageTestPlans.SEEDS) {
            var plan=VillageTestPlans.get(seed);var connected=VillageWalkability.reachableDoors(plan);
            long homes=0,reached=0;
            for(var b:plan.buildings()) {
                boolean hit=connected.contains(pack(b.doorWorldX(),b.doorWorldZ()));
                if(b.category()==SPECIAL_LANDMARK) assertTrue(hit,"unreachable landmark "+b);
                if(b.category()==RESIDENTIAL) { homes++;if(hit) reached++; }
            }
            assertTrue(reached>=homes*0.9,"connected homes="+reached+"/"+homes+" missing="+plan.buildings().stream()
                    .filter(b->b.category()==RESIDENTIAL && !connected.contains(pack(b.doorWorldX(),b.doorWorldZ())))
                    .map(b->b.doorWorldX()+","+b.doorWorldZ()+" r="+(int)b.radius()+" near="+plan.roadCells().stream()
                            .mapToInt(c->Math.abs(c.x()-b.doorWorldX())+Math.abs(c.z()-b.doorWorldZ())).min().orElse(-1)).toList());
            assertTrue(plan.roadCells().stream().anyMatch(c->c.type()==RoadType.L4_BRIDGE));
            for(var c:plan.roadCells()) {
                assertFalse(VillageRoadRouter.blocked(plan.buildings(),c.x(),c.z()),"road crosses building at "+c);
                if(VillageRoadRouter.streamDistance(c.x(),c.z())<=3.6) {
                    assertEquals(RoadType.L4_BRIDGE,c.type());assertEquals(71,c.surfaceY());
                }
                assertNotEquals(RoadType.L1_MAIN,c.type());
                assertNotEquals(RoadType.L2_RING,c.type());
            }
            assertTrue(plan.roadCells().stream().anyMatch(c->c.type()==RoadType.EARTH));
            // A normal household gets at most its nine-cell apron, never an A* spur across the landscape.
            long outside=plan.roadCells().stream().filter(c->!c.fixed()).count();
            assertTrue(outside<=homes*9);
        }
    }
}
