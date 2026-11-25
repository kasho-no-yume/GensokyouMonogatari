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
            assertTrue(homes>=55 && homes<=75,"homes="+homes);
            assertEquals(26,bs.stream().filter(b->b.category()==RESIDENTIAL && b.radius()<=110).count());
            assertEquals(24,bs.stream().filter(b->b.category()==RESIDENTIAL && b.radius()>110 && b.radius()<=160).count());
            assertEquals(12,bs.stream().filter(b->b.category()==RESIDENTIAL && b.radius()>160).count());
            long[] coverage=new long[3];
            for(int i=0;i<bs.size();i++) {
                var b=bs.get(i);
                assertTrue(b.radius()<=200,b.toString());
                assertTrue(HumanVillagePlanner.isSafeFromBiomeEdge(layout,(b.minX()+b.maxX())/2,(b.minZ()+b.maxZ())/2));
                for(int j=0;j<i;j++) {
                    var a=bs.get(j);
                    assertFalse(b.minX()<=a.maxX()+1 && b.maxX()+1>=a.minX() && b.minZ()<=a.maxZ()+1 && b.maxZ()+1>=a.minZ(),b+" near "+a);
                }
                for(int x=b.minX();x<=b.maxX();x++) for(int z=b.minZ();z<=b.maxZ();z++) {
                    if(!b.templateId().getPath().equals("hv_watermill")) assertTrue(VillageRoadRouter.streamDistance(x,z)>3,b+" intrudes stream at "+x+","+z);
                    double r=Math.hypot(x+200,z-100);
                    if(r<=200) coverage[r<=110?0:r<=160?1:2]++;
                }
            }
            double core=coverage[0]/(Math.PI*110*110),middle=coverage[1]/(Math.PI*(160*160-110*110)),outer=coverage[2]/(Math.PI*(200*200-160*160));
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
    @Test void completeSnapshotFitsThreeKiBAndSampledBoundaryMarginExceedsMinimum() {
        var plan=VillageTestPlans.get(42);
        var payload=new HumanVillageLayoutSyncPayload(plan.buildings().stream().map(HumanVillageLayoutSyncPayload.InstanceEntry::of).toList());
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try {
            HumanVillageLayoutSyncPayload.STREAM_CODEC.encode(buffer,payload);
            assertTrue(buffer.readableBytes()<3072,"layout packet bytes="+buffer.readableBytes());
            assertEquals(payload,HumanVillageLayoutSyncPayload.STREAM_CODEC.decode(buffer));
        } finally {buffer.release();}
        assertTrue(HumanVillagePlanner.sampledBiomeEdgeDistance(new GensokyoLayout(42),plan.buildings())>=220);
    }
    private static List<String> fixedGeometry(HumanVillagePlan p) {
        return p.buildings().stream().filter(BuildingInstance::fixed).map(b->b.templateId()+":"+b.minX()+","+b.minY()+","+b.minZ()+":"+b.rotation()).toList();
    }
}
