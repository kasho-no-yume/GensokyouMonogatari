package com.bitsson.gensokyou.danmaku.program;

import com.bitsson.gensokyou.danmaku.program.math.*;
import com.bitsson.gensokyou.danmaku.program.ops.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Third-party records: no edits to Path, Pattern, compiler, scheduler or ProgramCodec. */
class OperationExtensionTest {
    public record TestPath(double acceleration) implements Path {
        public TestPath { Deterministic.finite(acceleration); }
        public State at(double t) { Deterministic.nonnegative(t); return new State(Vec3.X.multiply(acceleration*t*t/2),Vec3.X.multiply(acceleration*t),false); }
        public double radiusBound(double t) { return StrictMath.abs(acceleration)*t*t/2; }
    }
    public record TestPattern(int count) implements Pattern {
        public TestPattern { if (count<1 || count>48) throw new IllegalArgumentException("test count limit"); }
        public List<Member> generate(Deterministic.Seed seed) {
            var result=new ArrayList<Member>(); for (int i=0;i<count;i++) result.add(new Member(i,Vec3.X.multiply(i),Vec3.Z,Style.DEFAULT,Set.of("test"),-1)); return List.copyOf(result);
        }
        public int countBound() { return count; }
        public double radiusBound() { return count-1; }
    }
    @Test void independentPathAndPatternUseTheExistingEnvelopeAndSchedule() {
        var data=OperationLibrary.dataRegistry().register("test:acceleration",TestPath.class).register("test:line",TestPattern.class).freeze();
        var pathType=OperationLibrary.pathType(data); var patternType=OperationLibrary.patternType(data);
        var registry=new OperationRegistry(); registry.register(pathType); registry.register(patternType); registry.freeze();
        var path=new OperationLibrary.PathParameters("group",100,OperationLibrary.PathSpec.of(new TestPath(.02)));
        var pattern=new OperationLibrary.PatternParameters("group",100,new TestPattern(8),new Deterministic.Seed(1,List.of()));
        var root=new ParallelNode(new NodeId(0),List.of(
                new OperationNode(new NodeId(1),pathType.id(),1,new TickSpan(100),pathType.contract(path).writes(),pathType.encode(path)),
                new OperationNode(new NodeId(2),patternType.id(),1,new TickSpan(0),patternType.contract(pattern).writes(),patternType.encode(pattern))));
        byte[] bytes=ProgramCodec.encode("test:extensions",new PlaybackPolicy.Once(TailPolicy.RETAIN_UNTIL_LIFETIME),root,ProgramLimits.defaults());
        var result=ProgramCodec.decode(bytes,ProgramLimits.defaults(),registry);
        assertTrue(result.success(),result.diagnostics().toString());
        assertEquals(2,ProgramSchedule.between(result.definition().orElseThrow(),0,100,10).size());
        var restored=data.decode(pathType.encode(path),OperationLibrary.PathParameters.class);
        assertEquals(new Vec3(4,0,0),restored.path().evaluator().at(20).position());
        assertEquals(8,result.definition().orElseThrow().bounds().peakMembersPerBatch());
    }
}
