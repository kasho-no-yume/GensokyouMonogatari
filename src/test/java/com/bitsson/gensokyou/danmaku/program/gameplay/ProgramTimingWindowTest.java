package com.bitsson.gensokyou.danmaku.program.gameplay;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProgramTimingWindowTest {
    @Test void reportsNearestRankPercentilesAndDropsOldSamplesAtCapacity() {
        var window=new ProgramTimingWindow(100);
        assertEquals(0,window.snapshot().samples());
        for (int i=1;i<=100;i++) window.record(i);
        assertEquals(new ProgramTimingWindow.Snapshot(100,50,95,100),window.snapshot());
        for (int i=0;i<100;i++) window.record(7);
        assertEquals(new ProgramTimingWindow.Snapshot(100,7,7,7),window.snapshot());
        assertThrows(IllegalArgumentException.class,()->window.record(-1));
        window.clear(); assertEquals(new ProgramTimingWindow.Snapshot(0,0,0,0),window.snapshot());
    }
}
