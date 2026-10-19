package com.bitsson.gensokyou.danmaku.program.server;

import com.bitsson.gensokyou.danmaku.program.*;
import com.bitsson.gensokyou.danmaku.program.author.MemberProgram;
import com.bitsson.gensokyou.danmaku.program.math.*;
import com.bitsson.gensokyou.danmaku.program.ops.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProgramAdmissionWorkTest {
    static final class CountingPath implements Path {
        int calls;
        public State at(double time) { calls++; return new State(Vec3.ZERO,Vec3.ZERO,false); }
        public double radiusBound(double time) { return 0; }
    }
    @Test void manyIndependentChildrenShareBoundaryEvaluationAndRespectIncrementalDensity() {
        for (int nearLimit:new int[]{150,250}) {
            var path=new CountingPath();
            UUID owner=new UUID(0,1),player=new UUID(0,2);
            var world=new DecisionProvider() {
                public List<Player> players() { return List.of(new Player(player,new Vec3(0,0,5),new Vec3(0,1,5),2,true,false,false,.3)); }
                public Optional<Actions.Pose> entity(UUID id) { return Optional.of(new Actions.Pose(Frame.IDENTITY,Vec3.ZERO)); }
                public boolean loaded(Vec3 point) { return true; }
                public Block block(int x,int y,int z) { return new Block(true,true,false,0); }
            };
            var runtime=new DanmakuWorldRuntime("test:work",new DanmakuWorldRuntime.Limits(1000,1000,10,1000,10000),world);
            runtime.admissionLimits(new DanmakuWorldRuntime.AdmissionLimits(16,nearLimit,Long.MAX_VALUE));
            var encounter=runtime.startEncounter(owner);
            long group=runtime.createGroup(encounter,1,0,null,-1,false,true,Frame.IDENTITY,List.of());
            runtime.advance(0);
            var child=MemberProgram.attack("child",new Patterns.Point(Vec3.ZERO,Vec3.Z),new Paths.Hold(),100).fixedDirection().build();
            var parent=MemberProgram.attack("parent",new Patterns.SphereDirections(100,Patterns.SphereDistribution.FIBONACCI,1),path,100)
                    .fixedDirection().emit(new Selection.AllOf(List.of()),1,1,1,child).build();
            var plan=runtime.prepare(group,0,new Actions.Pose(Frame.IDENTITY,Vec3.ZERO),parent,Actions.Binding.snapshot(),new Deterministic.Seed(1,List.of()));
            assertTrue(runtime.commit(plan,new AuthorityDescriptions.Commit(new ProgramRef.Plan(new NodeId(0)),0,1000,AuthorityDescriptions.Failure.REJECT_BATCH,true)));
            runtime.drainEvents(); path.calls=0;
            runtime.advance(1);
            // Boundary once plus the collision endpoint once per parent. Previously
            // every one-child commit evaluated all 100 parent paths again.
            assertTrue(path.calls<=200,"parent evaluations="+path.calls);
            assertEquals(Math.min(nearLimit,200),runtime.liveMembers());
            long rejected=runtime.drainEvents().stream().filter(e->e.kind()==DanmakuWorldRuntime.Kind.REJECT).count();
            assertEquals(nearLimit==150?50:0,rejected);
        }
    }
}
