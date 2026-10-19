package com.bitsson.gensokyou.danmaku.program.network;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProgramPresentationClockTest {
    @Test void jitteredArrivalCannotJumpDisplayedPoseAndUnderflowCannotExtrapolate() {
        var clock=new ProgramPresentationClock();
        long origin=1_000_000_000L;
        clock.observe(100,origin);
        double previous=clock.time(origin,false);
        long authority=100;
        for (int frame=1;frame<1000;frame++) {
            long now=origin+frame*8_000_000L;
            long due=100+(now-origin)/50_000_000L;
            // Realistic alternating 42/58ms arrivals sampled on an 8ms render grid.
            if (due>authority && now-origin>=(due-100)*50_000_000L+(due%2==0?8_000_000L:-8_000_000L)) {
                double before=clock.time(now,false);
                clock.observe(due,now); authority=due;
                assertEquals(before,clock.time(now,false),0,"receipt cannot reposition a frame");
            }
            double time=clock.time(now,false);
            assertTrue(time>=previous);
            assertTrue(time<=authority);
            assertTrue(time-previous<=.17600001,"bounded frame speed");
            previous=time;
        }
        double stopped=clock.time(origin+20_000_000_000L,false);
        assertEquals(authority,stopped,0);
        assertEquals(stopped,clock.time(origin+21_000_000_000L,false),0);
    }
    @Test void pauseResumePreservesFractionWithoutSpendingPausedWallTime() {
        var clock=new ProgramPresentationClock(); long origin=1_000_000_000L;
        clock.observe(100,origin); clock.time(origin,false);
        double before=clock.time(origin+20_000_000,false);
        assertEquals(before,clock.time(origin+25_000_000,true));
        assertEquals(before,clock.time(origin+10_000_000_000L,true));
        assertEquals(before,clock.time(origin+10_010_000_000L,false));
        assertTrue(clock.time(origin+10_020_000_000L,false)>before);
        assertEquals(0,clock.resets());
    }
}
