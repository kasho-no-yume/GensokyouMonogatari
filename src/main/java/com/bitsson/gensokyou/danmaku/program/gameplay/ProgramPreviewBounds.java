package com.bitsson.gensokyou.danmaku.program.gameplay;

import com.bitsson.gensokyou.danmaku.program.network.*;
import com.bitsson.gensokyou.danmaku.program.server.DanmakuWorldRuntime;
import java.util.Map;

/** Compatibility entry for existing preview tests; all computation is content-independent. */
public final class ProgramPreviewBounds {
    private ProgramPreviewBounds() {}
    public static Map<Long,ProgramInterest.Box> groups(ReplicaState.Projection projection) { return ProgramMotionBounds.groups(projection); }
    public static Map<DanmakuWorldRuntime.MemberId,ProgramInterest.Box> members(ReplicaState.Projection projection) { return ProgramMotionBounds.members(projection); }
}
