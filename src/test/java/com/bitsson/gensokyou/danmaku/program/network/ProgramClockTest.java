package com.bitsson.gensokyou.danmaku.program.network;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProgramClockTest {
    @Test void sparseRecoveryNeverBlanksAndFreezesUntilIndependentSample() {
        var clock=new ProgramClock(200);
        assertFalse(clock.renderable(),"no pose before the first authority observation");
        clock.observe(1000,0);
        for (int t=1;t<=40;t++) { clock.time(t+.5); if (t%20==0) clock.observe(1000+t,t); }
        clock.observe(1044,41);
        double frozen=clock.time(42.5);
        for (int t=43;t<61;t++) for (double partial:new double[]{0,.25,.5,.99}) {
            assertEquals(frozen,clock.time(t+partial),1e-9);
            assertTrue(clock.renderable(),"recovery must never blank the group");
        }
        clock.observe(1064,61);
        assertTrue(clock.renderable()); assertTrue(clock.trusted());
        assertEquals(1064.5,clock.time(61.5),1e-9);
        assertTrue(clock.time(62)>1064.5);
    }
    @Test void staleMappingStaysFrozenAndVisibleWithoutUnboundedExtrapolation() {
        var clock=new ProgramClock(20); clock.observe(1000,0);
        double last=clock.time(10);
        for (int tick=21;tick<=500;tick++) {
            assertEquals(last,clock.time(tick),1e-9);
            assertTrue(clock.renderable()); assertFalse(clock.trusted());
        }
    }
    @Test void jitterBurstNearDisplayUsesBoundedHoldoverButSevereQueueRevokesIt() {
        var clock=new ProgramClock(200); clock.observe(1000,0);
        for (int tick=1;tick<=40;tick++) { clock.observe(1000+tick,tick); clock.time(tick+.5); }
        // Three older samples and the current sample drain in one client tick.
        clock.observe(1041,44); clock.observe(1042,44); clock.observe(1044,44);
        assertFalse(clock.trusted());
        assertTrue(clock.renderable(),clock.diagnostic());
        double before=clock.time(45);
        clock.observe(1045,45);
        assertTrue(clock.trusted());
        assertEquals(before,clock.time(45),1e-9);
        clock.observe(1046,48); clock.observe(1049,48);
        clock.time(51);
        assertTrue(clock.renderable(),"expired holdover keeps a frozen pose");
        for (int server=1050;server<=1100;server++) clock.observe(server,51);
        assertTrue(clock.renderable(),"severe backlog freezes instead of blanking");
    }
    @Test void staleOwnerSampleCannotDisruptAHealthySharedClock() {
        var clock=new ProgramClock(200); clock.observe(1000,0);
        for (int tick=1;tick<=100;tick++) {
            clock.observe(1000+tick,tick);
            clock.time(tick+.5);
            // Another owner may publish an older checkpoint after this owner.
            clock.observe(990+tick,tick);
            assertTrue(clock.renderable(),"old owner timestamp disrupted dimension: "+clock.diagnostic());
        }
        assertEquals(0,clock.disruptions());
    }
    @Test void heartbeatAfterRenderingUsesTheSameFractionalBoundary() {
        var clock=new ProgramClock(200); clock.observe(1000,0);
        for (int tick=1;tick<=1200;tick++) {
            clock.time(tick+.99);
            if (tick%20==0) clock.observe(1000+tick+(tick%60==0?-2:0),tick);
            assertTrue(clock.renderable(),"blank after frame-before-packet at "+tick+" "+clock.diagnostic());
        }
        assertEquals(0,clock.disruptions(),clock.diagnostic());
    }
    @Test void modestDisplayRecoveryCanFreezeOneTickButBacklogRevokesGrace() {
        var clock=new ProgramClock(200); clock.observe(1000,0);
        for (int t=1;t<=40;t++) { clock.time(t); if (t%20==0) clock.observe(1000+t,t); }
        clock.observe(1044,41);
        assertFalse(clock.trusted(),clock.diagnostic());
        {
            double frozen=clock.time(41.25);
            assertTrue(clock.renderable(),clock.diagnostic());
            assertEquals(frozen,clock.time(41.5));
            clock.time(43); assertTrue(clock.renderable());
        }
        for (int s=1045;s<1100;s++) clock.observe(s,43);
        assertTrue(clock.renderable());
    }
    @Test void sparseHeartbeatsWithOneTickJitterNeverBlankTheAttack() {
        var clock=new ProgramClock(200); clock.observe(1000,0);
        for (int local=1;local<=1200;local++) {
            if (local%20==0) clock.observe(1000+local+(local%60==0?-1:0),local);
            for (double partial:new double[]{0,.25,.5,.75,.99}) {
                clock.time(local+partial);
                assertTrue(clock.renderable(),"blank frame at "+local+" "+clock.diagnostic());
            }
        }
        assertEquals(0,clock.disruptions(),clock.diagnostic());
    }
    @Test void smallMappingCorrectionKeepsFramesVisibleButHoldoverExpires() {
        var clock=new ProgramClock(200); clock.observe(1000,0);
        clock.time(10);
        // Establishing a new mapping after stale observations must not grant an unbounded grace.
        clock.time(211);
        clock.observe(1011,211);
        assertFalse(clock.trusted());
        assertTrue(clock.renderable());
        assertTrue(clock.time(211.5)>1010);
        assertTrue(clock.renderable());
        clock.time(214);
        assertTrue(clock.renderable());
    }
    @Test void severeBacklogFreezesInsteadOfExtrapolating() {
        var clock=new ProgramClock(200); clock.observe(1000,0);
        clock.time(1);
        for (int s=1001;s<1050;s++) clock.observe(s,1);
        assertTrue(clock.renderable());
        double frozen=clock.time(2);
        assertEquals(frozen,clock.time(100),1e-9);
    }
    @Test void smallRecoveryKeepsTheDisplayedBoundaryContinuous() {
        var clock=new ProgramClock(200); clock.observe(1000,0);
        clock.time(10); clock.time(211);
        clock.observe(1011,211);
        double before=clock.time(212);
        assertTrue(clock.renderable());
        clock.observe(1012,212);
        assertTrue(clock.trusted());
        assertEquals(before,clock.time(212),1e-9);
        assertTrue(clock.time(212.5)>before);
    }
    @Test void newObservationsNeverMoveAnAlreadyRenderedBoundary() {
        var clock=new ProgramClock(200); clock.observe(100,0);
        for (int tick=1;tick<=180;tick++) {
            double before=clock.time(tick);
            if (tick%20==0) clock.observe(100+tick+(tick%40==0?1:0),tick);
            assertEquals(before,clock.time(tick),1e-9,"observation at "+tick);
            double next=clock.time(tick+.5);
            assertTrue(next>before,"no correction freeze");
            assertTrue(next-before<.7,"no correction jump");
        }
    }
    @Test void staleClockFreezesAndRecoversAtAnExplicitBoundary() {
        var clock=new ProgramClock(20); clock.observe(100,0);
        double displayed=clock.time(10.5);
        assertEquals(displayed,clock.time(30));
        clock.observe(140,40);
        assertEquals(displayed,clock.time(40));
        assertFalse(clock.trusted());
        assertEquals(displayed,clock.time(40.5));
        clock.observe(141,41);
        assertTrue(clock.trusted());
        assertEquals(141.5,clock.time(41.5),1e-9);
        assertEquals(1,clock.recoveries());
    }
    @Test void processingBacklogCannotLeaveTrustedDisplayFiftyTicksBehind() {
        var clock=new ProgramClock(200); clock.observe(1000,0);
        for (int tick=1;tick<=60;tick++) { clock.observe(1000+tick,tick); clock.time(tick+.5); }
        // Client stopped ticking while server advanced; its queued messages drain together.
        for (int server=1061;server<=1115;server++) clock.observe(server,61);
        assertFalse(clock.trusted());
        clock.observe(1116,62);
        assertTrue(clock.trusted());
        assertEquals(1116.5,clock.time(62.5),1e-9);
        for (int tick=63;tick<=200;tick++) {
            clock.observe(1054+tick,tick);
            assertTrue(Math.abs(clock.error(tick))<=2);
        }
    }
}
