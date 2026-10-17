package com.bitsson.gensokyou.danmaku.program.gameplay;

import com.bitsson.gensokyou.danmaku.program.*;
import com.bitsson.gensokyou.danmaku.program.math.*;
import com.bitsson.gensokyou.danmaku.program.ops.*;
import com.bitsson.gensokyou.danmaku.program.server.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProgramBenchmarkLoadTest {
    @Test void stressHasExactParentsDerivedAndLaserBudget() {
        var seed=new Deterministic.Seed(ProgramBenchmarkLoad.SEED,List.of());
        for (int count:new int[]{800,2000}) {
            var batches=ProgramStressLoad.batches(count);
            assertEquals(count-160+8,batches.stream().mapToInt(p->p.pattern().generate(seed).size()).sum());
            assertEquals(1,batches.stream().filter(p->!p.actions().isEmpty()).count());
            assertTrue(batches.stream().allMatch(p->p.lifetimeTicks()==200));
        }
    }
    @Test void sharedBatchesHaveExactCountsAndWorldLinearTrajectories() {
        var seed=new Deterministic.Seed(ProgramBenchmarkLoad.SEED,List.of());
        var pose=new Actions.Pose(Frame.along(new Vec3(10,70,-10),Vec3.X,Vec3.Y),Vec3.ZERO);
        for (int count:new int[]{48,480,800,2000}) {
            var batches=ProgramBenchmarkLoad.batches(count);
            assertEquals(count,batches.stream().mapToInt(p->p.pattern().generate(seed).size()).sum());
            for (var child:batches) for (var member:child.pattern().generate(seed)) {
                var start=EmissionPlan.state(child,Actions.Binding.snapshot(),pose,seed,member,0);
                var end=EmissionPlan.state(child,Actions.Binding.snapshot(),pose,seed,member,99.5);
                assertEquals(start.position().add(Vec3.X.multiply(99.5*ProgramBenchmarkLoad.SPEED)),end.position());
                assertEquals(ProgramBenchmarkLoad.RADIUS,member.style().collisionRadius());
                assertEquals(ProgramBenchmarkLoad.LIFETIME,child.lifetimeTicks());
            }
        }
    }
    @Test void repeatedHighLoadUsesRuntimeNetworkAndTerminalCleanup() {
        verifyRuntime(false);
        verifyRuntime(true);
    }
    private void verifyRuntime(boolean stress) {
        var world=new DecisionProvider() {
            public List<Player> players() { return List.of(); }
            public Optional<Actions.Pose> entity(UUID id) { return Optional.of(new Actions.Pose(Frame.IDENTITY,Vec3.ZERO)); }
            public boolean loaded(Vec3 point) { return true; }
            public Block block(int x,int y,int z) { return new Block(true,true,false,0); }
        };
        var runtime=new DanmakuWorldRuntime("test:benchmark",new DanmakuWorldRuntime.Limits(4096,2048,8,64,8192),world);
        var encounter=runtime.startEncounter(new UUID(0,1));
        long group=runtime.createGroup(encounter,1,0,null,-1,false,true,Frame.IDENTITY,List.of());
        for (int tick=0;tick<=300;tick++) {
            if (tick%(stress?200:100)==0) {
                // Real LevelTick.Pre sees the last completed gameTime, then Post
                // advances the authority to the next boundary.
                long birth=ProgramBenchmarkHost.nextBoundary(tick-1L);
                assertEquals(tick,birth);
                runtime.schedule(tick,DanmakuWorldRuntime.Phase.PREPARE,()->{
                    for (var child:stress?ProgramStressLoad.batches(2000):ProgramBenchmarkLoad.batches(2000)) {
                        var plan=runtime.prepare(group,birth,new Actions.Pose(Frame.IDENTITY,Vec3.ZERO),child,Actions.Binding.snapshot(),new Deterministic.Seed(ProgramBenchmarkLoad.SEED,List.of()));
                        assertTrue(runtime.commit(plan,new AuthorityDescriptions.Commit(new ProgramRef.Plan(new NodeId(0)),0,2048,AuthorityDescriptions.Failure.REJECT_BATCH,true)));
                    }
                });
            }
            runtime.advance(tick); runtime.drainEvents();
            int age=tick%200;
            assertEquals(stress?1848+(age>=60 && age<140?160:0):2000,runtime.liveMembers(),"tick "+tick);
        }
        runtime.close("test");
        assertEquals(0,runtime.liveMembers()); assertEquals(0,runtime.stats().groups());
    }
}
