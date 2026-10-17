package com.bitsson.gensokyou.danmaku.program.gameplay;

import com.bitsson.gensokyou.danmaku.program.network.ProgramReplica;
import com.bitsson.gensokyou.danmaku.program.network.ProgramSyncServer;
import com.bitsson.gensokyou.danmaku.program.server.DanmakuWorldRuntime;
import com.bitsson.gensokyou.danmaku.program.server.ProgramServerHost;

import java.util.Map;

/** Unified read-only diagnostics snapshot for probes, commands and A/B reports. */
public final class ProgramGameplayDiagnostics {
    public record Snapshot(DanmakuWorldRuntime.RuntimeStats runtime,
                           ProgramGameplayStats.Snapshot gameplay,
                           ProgramSyncServer.Stats network,
                           ProgramServerHost.Stats server,
                           long lateEvents,long duplicateEvents,long replicaFailures,
                           int pendingTransfers) {}
    private ProgramGameplayDiagnostics() {}

    public static Snapshot capture(DanmakuWorldRuntime runtime,ProgramGameplayStats gameplay,
                                    ProgramSyncServer network,ProgramReplica replica) {
        return capture(runtime,gameplay,network,replica,new ProgramServerHost.Stats(0,0,0,0));
    }
    public static Snapshot capture(DanmakuWorldRuntime runtime,ProgramGameplayStats gameplay,
                                   ProgramSyncServer network,ProgramReplica replica,
                                   ProgramServerHost.Stats server) {
        return new Snapshot(runtime.stats(),gameplay.snapshot(),network.stats(),server,replica.lateEvents(),
                replica.duplicates(),replica.failures(),replica.pendingTransfers());
    }
}
