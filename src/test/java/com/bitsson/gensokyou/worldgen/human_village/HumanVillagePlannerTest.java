package com.bitsson.gensokyou.worldgen.human_village;

import com.bitsson.gensokyou.landmark.LocalLandmarkColors;
import com.bitsson.gensokyou.worldgen.GensokyoLayout;
import org.junit.jupiter.api.Test;

import java.util.List;
import com.bitsson.gensokyou.network.HumanVillageLayoutSyncPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

import static com.bitsson.gensokyou.worldgen.human_village.BuildingInstance.BuildingCategory.RESIDENTIAL;
import static org.junit.jupiter.api.Assertions.*;

class HumanVillagePlannerTest {
    @Test void buildingsRespectBoundariesSeparationRiverAndRadialDensity() {
        for(long seed:VillageTestPlans.SEEDS) {
            var plan=VillageTestPlans.get(seed); var layout=new GensokyoLayout(seed); var bs=plan.buildings();
            long homes=bs.stream().filter(b->b.category()==RESIDENTIAL).count();
            assertEquals(2,plan.layoutVersion());
            assertTrue(homes>=500 && homes<=HumanVillagePlan.MAX_BUILDINGS,"homes="+homes);
            assertTrue(bs.stream().anyMatch(b->b.category()==RESIDENTIAL && b.radius()>700));
            assertTrue(bs.stream().filter(b->b.category()==RESIDENTIAL && b.radius()>200).count()>homes*.6);
            var field=new VillageSettlementField(layout);
            var neighborhood=new java.util.HashMap<Long,java.util.List<BuildingInstance>>();
            for(var b:bs) if(b.category()==RESIDENTIAL) neighborhood.computeIfAbsent(
                    HumanVillagePlan.pack(((b.minX()+b.maxX())/2)>>7,((b.minZ()+b.maxZ())/2)>>7),key->new java.util.ArrayList<>()).add(b);
            long[] coverage=new long[3];
            long[] area=new long[3];
            int habitableSamples=0,settledSamples=0;
            var occupied=new OccupancyGrid();
            for(int x=-2200;x<=2000;x+=64) for(int z=-1900;z<=2000;z+=64) {
                if(!field.containsLot(x,z,1,1)) continue;
                double t=field.outward(x,z);area[t<.38?0:t<.72?1:2]+=4096;
                habitableSamples++;
                boolean settled=false;
                for(int cx=(x>>7)-2;cx<=(x>>7)+2;cx++) for(int cz=(z>>7)-2;cz<=(z>>7)+2;cz++) {
                    for(var b:neighborhood.getOrDefault(HumanVillagePlan.pack(cx,cz),List.of())) {
                        int dx=Math.max(0,Math.max(b.minX()-x,x-b.maxX())),dz=Math.max(0,Math.max(b.minZ()-z,z-b.maxZ()));
                        if(dx*dx+dz*dz<=96*96) settled=true;
                    }
                }
                if(settled) settledSamples++;
            }
            assertTrue(settledSamples>habitableSamples*.65,"settlement covers too little biome: "+settledSamples+"/"+habitableSamples);
            for(int i=0;i<bs.size();i++) {
                var b=bs.get(i);
                assertTrue(HumanVillagePlanner.isSafeFromBiomeEdge(layout,(b.minX()+b.maxX())/2,(b.minZ()+b.maxZ())/2));
                assertTrue(occupied.available(b.minX(),b.minZ(),b.rotSizeX(),b.rotSizeZ(),1),"overlapping lot "+b);
                occupied.occupy(b);
                for(int x=b.minX();x<=b.maxX();x++) for(int z=b.minZ();z<=b.maxZ();z++) {
                    if(!b.templateId().getPath().equals("hv_watermill")) assertTrue(VillageRoadRouter.streamDistance(x,z)>3,b+" intrudes stream at "+x+","+z);
                    double t=field.outward(x,z);
                    coverage[t<.38?0:t<.72?1:2]++;
                }
            }
            double core=(double)coverage[0]/area[0],middle=(double)coverage[1]/area[1],outer=(double)coverage[2]/area[2];
            assertTrue(core>.18,"inner area is still too empty: "+core);
            assertTrue(core>middle && middle>outer,"density="+core+","+middle+","+outer);
        }
    }
    @Test void fixedStageIsStableAndSeededOutskirtsVary() {
        var first=VillageTestPlans.get(0); var second=VillageTestPlans.get(1);
        assertEquals(fixedGeometry(first),fixedGeometry(second));
        assertNotEquals(first.buildings().stream().filter(b->!b.fixed()).toList(),second.buildings().stream().filter(b->!b.fixed()).toList());
        assertEquals(first,HumanVillagePlanner.plan(0));
    }
    @Test void planDimensionsMatchEveryExportedColorResource() throws Exception {
        for(var b:VillageTestPlans.get(0).buildings()) {
            try(var in=getClass().getResourceAsStream("/assets/gensokyou/landmark/"+b.templateId().getPath()+".local.bin")) {
                assertNotNull(in);var colors=LocalLandmarkColors.read(in);
                assertEquals(b.sizeX(),colors.sizeX());assertEquals(b.sizeY(),colors.sizeY());assertEquals(b.sizeZ(),colors.sizeZ());
                assertEquals(colors.placementPivot(b.minX(),b.minY(),b.minZ(),b.rotation()),b.placementPivot());
            }
        }
    }
    @Test void completeSnapshotIsBoundedAndBoundaryBufferFollowsTheBiome() {
        var plan=VillageTestPlans.get(42);
        var payload=new HumanVillageLayoutSyncPayload(plan.layoutVersion(),plan.buildings().stream().map(HumanVillageLayoutSyncPayload.InstanceEntry::of).toList());
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try {
            HumanVillageLayoutSyncPayload.STREAM_CODEC.encode(buffer,payload);
            assertTrue(buffer.readableBytes()<262144,"layout packet bytes="+buffer.readableBytes());
            assertEquals(payload,HumanVillageLayoutSyncPayload.STREAM_CODEC.decode(buffer));
        } finally {buffer.release();}
        var field=new VillageSettlementField(new GensokyoLayout(42));
        for(var b:plan.buildings()) if(!b.fixed()) assertTrue(field.containsLot(b.minX(),b.minZ(),b.rotSizeX(),b.rotSizeZ()));
    }
    private static List<String> fixedGeometry(HumanVillagePlan p) {
        return p.buildings().stream().filter(BuildingInstance::fixed).map(b->b.templateId()+":"+b.minX()+","+b.minY()+","+b.minZ()+":"+b.rotation()).toList();
    }
}
