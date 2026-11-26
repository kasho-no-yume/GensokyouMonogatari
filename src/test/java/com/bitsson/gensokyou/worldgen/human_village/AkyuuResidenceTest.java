package com.bitsson.gensokyou.worldgen.human_village;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AkyuuResidenceTest {
    private BuildingInstance building(int floor, Rotation rotation) {
        return new BuildingInstance(AkyuuResidence.TEMPLATE, BuildingInstance.BuildingCategory.SPECIAL_LANDMARK,
                -238, floor, -44, 76, 28, 64, rotation, 0, -200, floor, 20, true, true, 1, 2);
    }
    @Test void allChunkOrdersAndLegacyBuildingReceiptsUseTheSameCompletionState() {
        for (int version : List.of(1, 2)) for (boolean reverse : List.of(false, true)) {
            var b = building(version == 1 ? 76 : 75, Rotation.NONE);
            var data = new HumanVillageSavedData();
            data.setPlan(new HumanVillagePlan(version, 42, List.of(b), List.of(), Set.of()));
            var chunks = new ArrayList<Long>();
            for (int x = b.minX() >> 4; x <= b.maxX() >> 4; x++) for (int z = b.minZ() >> 4; z <= b.maxZ() >> 4; z++)
                chunks.add(ChunkPos.asLong(x, z));
            if (reverse) Collections.reverse(chunks);
            for (int n = 0; n < chunks.size(); n++) {
                assertFalse(AkyuuResidence.complete(data, 0));
                data.markChunk(chunks.get(n));
            }
            // Old worlds may have all chunks placed but no newer NPC completion receipt.
            data = HumanVillageSavedData.load(data.save(new CompoundTag(), null), null);
            assertTrue(AkyuuResidence.complete(data, 0));
            assertTrue(data.hasBuilding(0));
            UUID resident = UUID.randomUUID(); data.setAkyuuUuid(resident);
            var restored = HumanVillageSavedData.load(data.save(new CompoundTag(), null), null);
            assertTrue(AkyuuResidence.complete(restored, 0));
            assertEquals(resident, restored.akyuuUuid());
            assertEquals(b.minY() + 2, AkyuuResidence.anchor(restored.plan().buildings().getFirst()).y);
        }
    }
    @Test void rotatedAnchorsStayInsideSavedBuildingBounds() {
        for (Rotation rotation : Rotation.values()) {
            var b = building(75, rotation);
            var anchor = AkyuuResidence.anchor(b);
            assertTrue(anchor.x >= b.minX() && anchor.x <= b.maxX() + 1);
            assertTrue(anchor.z >= b.minZ() && anchor.z <= b.maxZ() + 1);
            assertEquals(77, anchor.y);
        }
    }
}
