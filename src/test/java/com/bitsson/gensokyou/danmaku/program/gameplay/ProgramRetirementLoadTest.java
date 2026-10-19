package com.bitsson.gensokyou.danmaku.program.gameplay;

import com.bitsson.gensokyou.danmaku.program.*;
import com.bitsson.gensokyou.danmaku.program.math.*;
import com.bitsson.gensokyou.danmaku.program.ops.*;
import com.bitsson.gensokyou.danmaku.program.server.*;
import com.bitsson.gensokyou.danmaku.program.network.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Accelerated headless measurement, not real-client frame/wire or wall-time acceptance. */
class ProgramRetirementLoadTest {
    @Test void three180SecondLogicalWindowsAndInitialBurst() throws Exception {
        var rows=new ArrayList<String>();
        rows.add("mode,count,repeat,logicalSampleSeconds,advanceMedianMs,advanceP95Ms,advanceMaxMs,initialLogicalBytes,peakMembers,collisionDispatches");
        for (boolean stress:new boolean[]{false,true}) for (int count:new int[]{800,2000}) for (int repeat=1;repeat<=3;repeat++) {
            var world=new DecisionProvider() {
                public List<Player> players() { return List.of(); }
                public Optional<Actions.Pose> entity(UUID id) { return Optional.of(new Actions.Pose(Frame.IDENTITY,Vec3.ZERO)); }
                public boolean loaded(Vec3 point) { return true; }
                public Block block(int x,int y,int z) { return new Block(true,true,false,0); }
            };
            var runtime=new DanmakuWorldRuntime("test:retirement-load",new DanmakuWorldRuntime.Limits(4096,2048,8,64,16384),world);
            var session=new ProgramSession(runtime,new ProgramExecution().freeze());
            var encounter=runtime.startEncounter(new UUID(0,1));
            long group=runtime.createGroup(encounter,1,0,null,-1,false,true,Frame.IDENTITY,List.of());
            long[] collisionDispatches={0};
            runtime.collisions((r,id,from,until)->collisionDispatches[0]++);
            var batches=stress?ProgramStressLoad.batches(count):ProgramBenchmarkLoad.batches(count);
            var wire=new ProgramWire(NetworkLimits.defaults());
            var sync=new ProgramSyncServer(wire,bytes->{},20);
            var bounds=Map.of(group,new ProgramInterest.Box(new Vec3(-100,-100,-100),new Vec3(100,100,100)));
            long initial=0; int peak=0;
            long[] samples=new long[3600];
            for (int tick=0;tick<4200;tick++) {
                long begin=System.nanoTime();
                if (tick%(stress?200:100)==0) {
                    long birth=tick;
                    runtime.schedule(tick,DanmakuWorldRuntime.Phase.PREPARE,()->{
                        for (var child:batches) {
                            var plan=runtime.prepare(group,birth,new Actions.Pose(Frame.IDENTITY,Vec3.ZERO),child,Actions.Binding.snapshot(),new Deterministic.Seed(ProgramBenchmarkLoad.SEED,List.of()));
                            assertTrue(runtime.commit(plan,new AuthorityDescriptions.Commit(new ProgramRef.Plan(new NodeId(0)),0,2048,AuthorityDescriptions.Failure.REJECT_BATCH,true)));
                        }
                    });
                }
                session.advance(tick,4096);
                long duration=System.nanoTime()-begin;
                if (tick>=600) samples[tick-600]=duration;
                peak=Math.max(peak,runtime.liveMembers());
                runtime.drainEvents();
                if (tick==0) {
                    sync.publish(ReplicaState.capture(session.checkpoint(),wire),bounds,Vec3.ZERO,0);
                    initial=sync.stats().logicalBytes().values().stream().mapToLong(Long::longValue).sum();
                }
            }
            assertEquals(stress?count+8:count,peak);
            assertTrue(collisionDispatches[0]>0);
            runtime.endEncounter(encounter,"load-end"); runtime.advance(4200); runtime.drainEvents();
            assertEquals(0,runtime.liveMembers()); assertEquals(0,runtime.stats().plans());
            Arrays.sort(samples);
            rows.add((stress?"mixed":"linear")+","+count+","+repeat+",180,"+samples[1800]/1e6+","+samples[3419]/1e6+","+samples[3599]/1e6+","+initial+","+peak+","+collisionDispatches[0]);
        }
        var output=java.nio.file.Path.of("build/reports/program-retirement-load.csv");
        java.nio.file.Files.createDirectories(output.getParent());
        java.nio.file.Files.write(output,rows);
    }
}
