package com.bitsson.gensokyou.worldgen.human_village;

import com.bitsson.gensokyou.client.resident.ResidentRoute;
import com.bitsson.gensokyou.client.resident.ResidentScene;
import com.bitsson.gensokyou.client.resident.VillageResidentAnchors;
import com.bitsson.gensokyou.network.HumanVillageLayoutSyncPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class VillageResidentAnchorsTest {
    @AfterEach void clear() { VillageResidentAnchors.clear(); }

    @Test void derivedDoorstepMatchesAllFourTemplateRotations() {
        int[][] offsets={{5,10},{-1,5},{5,-1},{10,5}};
        for(var rotation:Rotation.values()) {
            var b=new BuildingInstance(ResourceLocation.parse("gensokyou:hv_machiya_v02"),BuildingInstance.BuildingCategory.RESIDENTIAL,
                    600,78,400,11,12,10,rotation,0,0,0,0,true,false,1,2);
            var slot=VillageResidentAnchors.doorstep(HumanVillageLayoutSyncPayload.InstanceEntry.of(b));
            var point=slot.route().points().getFirst();
            assertEquals(600+offsets[rotation.ordinal()][0]+.5,point.x());
            assertEquals(400+offsets[rotation.ordinal()][1]+.5,point.z());
            assertEquals(79,point.y());
        }
    }

    @Test void coreHeightAdaptsOnceAndLegacyKeepsItsBaseline() {
        var original=new ResidentScene.Slot("gensokyou:human_village/check",new ResidentRoute(List.of(new ResidentRoute.Point(-200,73,100))),0,true);
        VillageResidentAnchors.install(new HumanVillageLayoutSyncPayload(2,List.of()));
        assertEquals(72,VillageResidentAnchors.coreSlot(original).route().points().getFirst().y());
        assertEquals(72,VillageResidentAnchors.coreSlot(original).route().points().getFirst().y());
        VillageResidentAnchors.install(new HumanVillageLayoutSyncPayload(1,List.of()));
        assertSame(original,VillageResidentAnchors.coreSlot(original));
    }

    @Test void candidatesRespectNeighborhoodAndSharedBudget() {
        var entries=new java.util.ArrayList<HumanVillageLayoutSyncPayload.InstanceEntry>();
        for(int i=0;i<100;i++) entries.add(new HumanVillageLayoutSyncPayload.InstanceEntry(ResourceLocation.parse("gensokyou:hv_machiya_v02"),
                600+i%10*3,78,400+i/10*3,11,12,10,(byte)0,(byte)0));
        VillageResidentAnchors.install(new HumanVillageLayoutSyncPayload(2,entries));
        var slots=VillageResidentAnchors.near(610,79,410,60);
        assertEquals(60,slots.size());
        assertEquals(ResidentScene.ACTIVE_LIMIT,ResidentScene.nearest(slots,new ResidentRoute.Point(610,79,410)).size());
        assertTrue(VillageResidentAnchors.near(-1000,79,-1000,512).isEmpty());
        assertTrue(VillageResidentAnchors.near(610,79,410,0).isEmpty());
    }
}
