package com.bitsson.gensokyou.danmaku.program.server;

import com.bitsson.gensokyou.danmaku.program.ops.*;
import java.util.*;

/** Recoverable collision/control requests. No captured world references or executable payload. */
public sealed interface AuthorityCommand permits AuthorityCommand.Remove,AuthorityCommand.Cancel,AuthorityCommand.Replace,AuthorityCommand.Movement {
    void execute(DanmakuWorldRuntime runtime);
    record Remove(DanmakuWorldRuntime.MemberId member,String reason) implements AuthorityCommand {
        public void execute(DanmakuWorldRuntime runtime) { runtime.remove(member,reason); }
    }
    record Cancel(long group,long fadeTicks,String reason) implements AuthorityCommand {
        public void execute(DanmakuWorldRuntime runtime) { if (runtime.hasGroup(group)) runtime.cancel(group,fadeTicks,reason); }
    }
    record Movement(long group,boolean locked) implements AuthorityCommand {
        public void execute(DanmakuWorldRuntime runtime) { if (runtime.hasGroup(group)) runtime.movementLock(group,locked); }
    }
    record Replace(long group,List<DanmakuWorldRuntime.MemberId> parents,Actions.ReplaceMembers replacement,
                   AuthorityDescriptions.Commit policy,String trigger) implements AuthorityCommand {
        public Replace { parents=List.copyOf(parents); }
        public void execute(DanmakuWorldRuntime runtime) { if (runtime.hasGroup(group)) runtime.replace(group,parents,replacement,policy,trigger); }
    }
}
