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

    @Test void indoorCustomersUseInstanceHeightRotationAndOnlyTheThreeShops() {
        var counts=java.util.Map.of("hv_kourindou",3,"hv_geidontei",4,"hv_suzunaan",3,"hv_hieda_residence",0);
        for(var entry:counts.entrySet()) for(var rotation:Rotation.values()) {
            var b=new BuildingInstance(ResourceLocation.parse("gensokyou:"+entry.getKey()),BuildingInstance.BuildingCategory.SPECIAL_LANDMARK,
                    600,78,400,44,29,36,rotation,0,0,0,0,true,true,1,2);
            var instance=HumanVillageLayoutSyncPayload.InstanceEntry.of(b);
            var slots=VillageResidentAnchors.indoors(instance);
            assertEquals(entry.getValue(),slots.size());
            for(var slot:slots) for(var point:slot.route().points()) {
                assertTrue(VillageResidentAnchors.isIndoor(slot));
                assertEquals(79,point.y());
                assertTrue(b.containsColumn((int)Math.floor(point.x()),(int)Math.floor(point.z())));
            }
            VillageResidentAnchors.install(new HumanVillageLayoutSyncPayload(2,List.of(instance)));
            assertEquals(slots.stream().map(ResidentScene.Slot::identity).toList(),
                    VillageResidentAnchors.nearIndoors(new ResidentRoute.Point(620,79,420)).stream()
                            .map(ResidentScene.Slot::identity).sorted().toList());
            VillageResidentAnchors.clear();
            assertTrue(VillageResidentAnchors.nearIndoors(new ResidentRoute.Point(620,79,420)).isEmpty());
        }
    }

    @Test void candidatesRespectNeighborhoodAndSharedBudget() {
        var entries=new java.util.ArrayList<HumanVillageLayoutSyncPayload.InstanceEntry>();
        for(int i=0;i<100;i++) entries.add(new HumanVillageLayoutSyncPayload.InstanceEntry(ResourceLocation.parse("gensokyou:hv_machiya_v02"),
                600+i%10*3,78,400+i/10*3,11,12,10,(byte)0,(byte)0));
        VillageResidentAnchors.install(new HumanVillageLayoutSyncPayload(2,entries));
        var slots=VillageResidentAnchors.near(610,79,410,60);
        assertEquals(60,slots.size());
        assertEquals(60,ResidentScene.nearest(slots,new ResidentRoute.Point(610,79,410)).size());
        assertTrue(VillageResidentAnchors.near(-1000,79,-1000,512).isEmpty());
        assertTrue(VillageResidentAnchors.near(610,79,410,0).isEmpty());
    }

    @Test void interestsAlsoFillTheCoreAndOnlyPublicGroundCanBecomeStreetCrowds() {
        var house=new HumanVillageLayoutSyncPayload.InstanceEntry(ResourceLocation.parse("gensokyou:hv_machiya_v02"),
                -180,71,80,11,12,10,(byte)0,(byte)0);
        var plaza=new HumanVillageLayoutSyncPayload.InstanceEntry(ResourceLocation.parse("gensokyou:hv_dragon_plaza"),
                -214,70,86,29,16,29,(byte)0,(byte)-1);
        VillageResidentAnchors.install(new HumanVillageLayoutSyncPayload(2,List.of(house,plaza)));
        var seeds=VillageResidentAnchors.interests(new ResidentRoute.Point(-185,72,100));
        assertTrue(seeds.size()>20,"core no longer has a 240-block exclusion");
        assertEquals(seeds.size(),seeds.stream().map(s->s.identity()).distinct().count());
        assertFalse(VillageResidentAnchors.publicGround(-175,85,72));
        assertFalse(VillageResidentAnchors.publicGround(-175,85,84));
        assertTrue(VillageResidentAnchors.publicGround(-200,100,72));
        assertFalse(VillageResidentAnchors.publicGround(-200,100,86));
        assertTrue(VillageResidentAnchors.interests(new ResidentRoute.Point(2000,72,2000)).isEmpty());
        VillageResidentAnchors.clear();
        assertFalse(VillageResidentAnchors.publicGround(-185,100,72));
    }
}
