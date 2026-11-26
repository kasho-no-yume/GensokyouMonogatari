package com.bitsson.gensokyou.client.resident;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResidentSpawnVisibilityTest {
    @Test void unavailableOrVisibleCameraViewDefersAdmission() {
        var feet=new ResidentRoute.Point(0,72,20);
        assertFalse(ResidentSpawnVisibility.mayAppear(feet,1,null));
        assertFalse(ResidentSpawnVisibility.mayAppear(feet,1,bounds->true));
        assertTrue(ResidentSpawnVisibility.mayAppear(feet,1,bounds->false));
    }
    @Test void fullBodyAndEdgeMarginMustLeaveTheView() {
        var feet=new ResidentRoute.Point(1,72,20);
        assertFalse(ResidentSpawnVisibility.mayAppear(feet,1,bounds->{
            assertEquals(-.55,bounds.minX(),1e-9);
            assertEquals(2.55,bounds.maxX(),1e-9);
            assertEquals(71,bounds.minY(),1e-9);
            assertEquals(75.4,bounds.maxY(),1e-9);
            assertEquals(18.45,bounds.minZ(),1e-9);
            assertEquals(21.55,bounds.maxZ(),1e-9);
            // Feet are right of this fake view, but part of the body still overlaps it.
            return bounds.minX()<=0;
        }));
    }
    @Test void cameraTurnBeforeTheFirstRenderCannotExposeANewSpawn() {
        var entries=new java.util.HashSet<ResidentWalker>();
        var resident=walker("gensokyou:street/one");
        assertFalse(ResidentSpawnVisibility.firstFrameAllowed(entries,resident,()->true));
        assertTrue(entries.isEmpty());
        assertTrue(ResidentSpawnVisibility.firstFrameAllowed(entries,resident,()->false));
        assertTrue(ResidentSpawnVisibility.firstFrameAllowed(entries,resident,()->{
            fail("Established residents can enter view without another admission check");return true;
        }));
        // Reusing an identity after retirement creates a new walker and needs a new unseen entry.
        assertFalse(ResidentSpawnVisibility.firstFrameAllowed(entries,walker("gensokyou:street/one"),()->true));
    }
    @Test void explicitPreviewCanAppearInFront() {
        assertTrue(ResidentSpawnVisibility.firstFrameAllowed(new java.util.HashSet<>(),walker("gensokyou:preview/resident_0"),()->true));
    }
    private static ResidentWalker walker(String id) {
        return new ResidentWalker(new ResidentScene.Slot(id,new ResidentRoute(java.util.List.of(new ResidentRoute.Point(0,72,20))),0,true));
    }
}
