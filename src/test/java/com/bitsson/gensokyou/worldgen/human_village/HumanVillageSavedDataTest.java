package com.bitsson.gensokyou.worldgen.human_village;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HumanVillageSavedDataTest {
    @Test void resolvedPlanAndProgressSurviveReloadExactly() {
        var data=new HumanVillageSavedData();
        data.setPlan(VillageTestPlans.get(42));
        long first=ChunkPos.asLong(-13,6),second=ChunkPos.asLong(-14,6);
        data.markChunk(first);data.markChunk(first);data.markChunk(second);data.markBuilding(3);
        var copy=HumanVillageSavedData.load(data.save(new CompoundTag(),null),null);
        assertEquals(data.plan(),copy.plan());assertTrue(copy.hasChunk(first));assertTrue(copy.hasChunk(second));
        assertEquals(2,copy.placedChunkCount());assertEquals(1,copy.placedBuildingCount());
        assertThrows(IllegalStateException.class,()->copy.setPlan(VillageTestPlans.get(1)));
        assertNull(HumanVillageSavedData.load(new CompoundTag(),null).plan());
    }
    @Test void channelHasClosedEndsAndEveryLateralWaterNeighborIsWaterOrBank() {
        for(int x=-345;x<=5;x++) for(int z=130;z<=180;z++) {
            if(!HumanVillageStreamPlacer.waterColumn(x,z)) continue;
            for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                assertTrue(HumanVillageStreamPlacer.waterColumn(x+d[0],z+d[1]) || HumanVillageStreamPlacer.bankColumn(x+d[0],z+d[1]),"open bank at "+x+","+z);
            }
        }
    }
}
