package com.bitsson.gensokyou.worldgen.human_village;

import com.bitsson.gensokyou.worldgen.GensokyoLayout;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HumanVillageTerrainTest {
    @Test void plateausStreamAndEasternSeamRemainStableAcrossSeedsAndNoise() {
        for(long seed:new long[]{0,1,42,-8712,Long.MAX_VALUE}) {
            var layout=new GensokyoLayout(seed);
            for(double noise:new double[]{-1,0,1}) {
                assertEquals(72,layout.surfaceHeight(-200,100,noise),1e-9);
                for(int x:new int[]{-238,-200,-163}) for(int z:new int[]{-44,0,19}) assertEquals(75,layout.surfaceHeight(x,z,noise),1e-9);
                for(int x=-300;x<=-60;x+=12) {
                    double y=layout.surfaceHeight(x,(int)Math.round(GensokyoLayout.villageStreamCenterZ(x)),noise);
                    assertTrue(y>=68.19 && y<=69,"stream bed "+x+": "+y);
                }
                for(int x=0;x<=40;x++) assertEquals(72,layout.surfaceHeight(x,100,noise),1e-8);
                assertTrue(Math.abs(layout.surfaceHeight(41,100,noise)-layout.surfaceHeight(40,100,noise))<0.1);
            }
        }
        assertEquals(164,GensokyoLayout.villageStreamCenterZ(-200),1e-9);
        for(int x=-160;x<=-100;x++) assertEquals(158,GensokyoLayout.villageStreamCenterZ(x),1e-9);
    }
}
