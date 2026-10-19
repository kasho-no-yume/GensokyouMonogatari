package com.bitsson.gensokyou.danmaku.program.gameplay;

import com.bitsson.gensokyou.danmaku.program.*;
import com.bitsson.gensokyou.danmaku.program.math.*;
import com.bitsson.gensokyou.danmaku.program.ops.*;
import com.bitsson.gensokyou.danmaku.program.server.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProgramRetirementTest {
    @Test void twentyMixedEncountersReleaseAllRuntimeOwnership() {
        var world=new DecisionProvider() {
            public List<Player> players() { return List.of(); }
            public Optional<Actions.Pose> entity(UUID id) { return Optional.of(new Actions.Pose(Frame.IDENTITY,Vec3.ZERO)); }
            public boolean loaded(Vec3 point) { return true; }
            public Block block(int x,int y,int z) { return new Block(true,true,false,0); }
        };
        var runtime=new DanmakuWorldRuntime("test:retirement",new DanmakuWorldRuntime.Limits(4096,2048,8,64,16384),world);
        for (int round=0;round<20;round++) {
            var encounter=runtime.startEncounter(new UUID(0,1));
            long group=runtime.createGroup(encounter,1,0,null,-1,false,true,Frame.IDENTITY,List.of());
            long birth=runtime.tick()+1;
            runtime.schedule(birth,DanmakuWorldRuntime.Phase.PREPARE,()->{
                for (var child:ProgramStressLoad.batches(2000)) {
                    var plan=runtime.prepare(group,birth,new Actions.Pose(Frame.IDENTITY,Vec3.ZERO),child,Actions.Binding.snapshot(),new Deterministic.Seed(7,List.of()));
                    assertTrue(runtime.commit(plan,new AuthorityDescriptions.Commit(new ProgramRef.Plan(new NodeId(0)),0,2048,AuthorityDescriptions.Failure.REJECT_BATCH,true)));
                }
            });
            for (long tick=birth;tick<=birth+65;tick++) { runtime.advance(tick); runtime.drainEvents(); }
            assertEquals(2008,runtime.liveMembers());
            runtime.movementLock(group,true);
            runtime.endEncounter(encounter,"retirement-test");
            runtime.advance(runtime.tick()+1);
            runtime.drainEvents();
            assertEquals(new DanmakuWorldRuntime.RuntimeStats(0,0,0,0,0,0,0),runtime.stats());
            var checkpoint=runtime.checkpoint();
            assertTrue(checkpoint.encounters().isEmpty());
            assertTrue(checkpoint.emitters().isEmpty());
            assertTrue(checkpoint.commits().isEmpty());
            assertTrue(checkpoint.nozzles().isEmpty());
            assertTrue(checkpoint.groupMotions().isEmpty());
            assertTrue(checkpoint.parentSources().isEmpty());
        }
    }
}
