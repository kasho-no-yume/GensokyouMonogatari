package com.bitsson.gensokyou.danmaku.program.gameplay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgramGameplayDiagnosticsTest {
    @Test
    void gameplayCountersCanBeReadWithoutMutation() {
        ProgramGameplayStats stats=new ProgramGameplayStats();
        stats.hit();
        assertEquals(1,stats.snapshot().hits());
        assertEquals(1,stats.snapshot().hits());
    }
    @Test
    void runtimeEventsAndTickCostAreStructuredForDiagnostics() {
        ProgramGameplayStats stats=new ProgramGameplayStats();
        stats.event(com.bitsson.gensokyou.danmaku.program.server.DanmakuWorldRuntime.Kind.COMMIT);
        stats.event(com.bitsson.gensokyou.danmaku.program.server.DanmakuWorldRuntime.Kind.REMOVE);
        stats.tickNanos(17);
        stats.tickNanos(23);
        var snapshot=stats.snapshot();
        assertEquals(2,snapshot.ticks());
        assertEquals(40,snapshot.totalNanos());
        assertEquals(23,snapshot.maxNanos());
        assertEquals(1,snapshot.events().get(com.bitsson.gensokyou.danmaku.program.server.DanmakuWorldRuntime.Kind.COMMIT));
        assertTrue(snapshot.events().containsKey(com.bitsson.gensokyou.danmaku.program.server.DanmakuWorldRuntime.Kind.REMOVE));
    }
}
