package com.bitsson.gensokyou.danmaku.program.gameplay;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IntegratedProgramTimelineTest {
    @Test void variableTickCompletionDoesNotResetTheRenderPhase() {
        String dimension="test:jitter";
        long start=1_000_000_000L;
        IntegratedProgramTimeline.remove(dimension);
        IntegratedProgramTimeline.publish(dimension,100,start);
        for(int i=1;i<=200;i++) {
            long completed=start+i*50_000_000L+(i%2==0?8_000_000L:-8_000_000L);
            var old=IntegratedProgramTimeline.boundary(dimension);
            double before=old.time(completed,false);
            IntegratedProgramTimeline.publish(dimension,100+i,completed);
            var next=IntegratedProgramTimeline.boundary(dimension);
            double after=next.time(completed,false);
            assertTrue(Math.abs(after-before)<.19,"no large backward/forward phase reset");
            assertTrue(Math.abs(next.nanos()-(start+i*50_000_000L))<1_000_000L,
                    "completion jitter is not used as interpolation phase");
        }
        IntegratedProgramTimeline.remove(dimension);
    }
    @Test void authorityCatchupNeverLeavesPresentationBehindDamage() {
        long now=1_000_000_000L;
        var initial=new IntegratedProgramTimeline.Boundary(100,now);
        assertEquals(100.5,initial.time(now+25_000_000,false));
        // A burst of server ticks must immediately re-anchor, not retain a slow wall clock.
        var catchup=new IntegratedProgramTimeline.Boundary(140,now+30_000_000);
        assertEquals(140.1,catchup.time(now+35_000_000,false),1e-9);
        assertEquals(141,catchup.time(now+10_000_000_000L,false));
        assertEquals(140,catchup.time(now+10_000_000_000L,true));
    }

    @Test void steadyFramesAreSmoothAndInsideTheAuthorityCollisionSweep() {
        long start=1_000_000_000L;
        double previous=100;
        for(int frame=1;frame<=1200;frame++) {
            long elapsed=frame*8_000_000L;
            long ticks=elapsed/50_000_000L;
            var boundary=new IntegratedProgramTimeline.Boundary(100+ticks,start+ticks*50_000_000L);
            double render=boundary.time(start+elapsed,false);
            assertEquals(.16,render-previous,1e-9);
            assertTrue(render>=boundary.tick() && render<=boundary.tick()+1);
            previous=render;
        }
    }
}
