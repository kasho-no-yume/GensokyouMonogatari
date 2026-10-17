package com.bitsson.gensokyou.danmaku.program.gameplay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProgramGameplayStatsTest {
    @Test
    void countersAreReportedSeparately() {
        ProgramGameplayStats stats=new ProgramGameplayStats();
        stats.collisionSample(); stats.collisionSample(); stats.hit(); stats.damageRejected();
        stats.laserClip(); stats.laserSkipped();
        assertEquals(new ProgramGameplayStats.Snapshot(2,1,1,1,1),stats.snapshot());
    }
}
