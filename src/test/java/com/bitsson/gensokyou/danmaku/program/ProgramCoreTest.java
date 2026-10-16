package com.bitsson.gensokyou.danmaku.program;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ProgramCoreTest {
    private static OperationRegistry registry() {
        OperationRegistry registry=new OperationRegistry();
        registry.register(new OperationType<Integer>() {
            public String id() { return "test:emit"; }
            public int schemaVersion() { return 1; }
            public Integer decode(Map<String,String> fields) { return Integer.parseInt(fields.get("count")); }
            public Map<String,String> encode(Integer count) { return Map.of("count",count.toString()); }
            public OperationContract contract(Integer count) {
                return new OperationContract("group",Set.of(),Set.of(),List.of(),count,100,0,0);
            }
        });
        return registry.freeze();
    }
    private static OperationNode emit(long id,int count) {
        return new OperationNode(new NodeId(id),"test:emit",1,new TickSpan(0),Set.of(),Map.of("count",Integer.toString(count)));
    }
    private static ProgramDefinition compile(ProgramNode root,PlaybackPolicy playback) {
        var result=ProgramCompiler.compile("test:program",1,playback,root,ProgramLimits.defaults(),registry());
        assertTrue(result.success(),result.diagnostics().toString());
        return result.definition().orElseThrow();
    }
    @Test void allOffsetBeatsRunAcrossNonDivisibleCycleBoundary() {
        var body=new ParallelNode(new NodeId(1),List.of(
                new OffsetNode(new NodeId(2),new TickSpan(0),emit(3,6)),
                new OffsetNode(new NodeId(4),new TickSpan(20),emit(5,6)),
                new OffsetNode(new NodeId(6),new TickSpan(40),emit(7,6))));
        var program=compile(new EveryNode(new NodeId(0),new TickSpan(60),new TickSpan(0),new TickSpan(200),body),
                new PlaybackPolicy.Loop(new TickSpan(200),TailPolicy.RETAIN_UNTIL_LIFETIME));
        var events=ProgramSchedule.between(program,0,400,100);
        assertEquals(20,events.size());
        assertEquals(List.of(180L,200L,220L),events.subList(9,12).stream().map(ProgramSchedule.Event::tick).toList());
        assertNotEquals(events.get(0).occurrence(),events.get(10).occurrence());
    }
    @Test void onceSequenceIncludesOffsetsAndDoesNotReplay() {
        var root=new SequenceNode(new NodeId(0),List.of(
                new OffsetNode(new NodeId(1),new TickSpan(20),emit(2,1)),
                new WaitNode(new NodeId(3),new TickSpan(10)),emit(4,1)));
        var program=compile(root,new PlaybackPolicy.Once(TailPolicy.RETAIN_UNTIL_LIFETIME));
        assertEquals(List.of(20L,30L),ProgramSchedule.between(program,0,1000,10).stream().map(ProgramSchedule.Event::tick).toList());
    }
    @Test void codecRoundTripPreservesHashAndRejectsTruncationAndUnknownVersion() {
        var program=compile(emit(0,46),new PlaybackPolicy.Once(TailPolicy.RETAIN_UNTIL_LIFETIME));
        byte[] bytes=ProgramCodec.encode(program.id(),program.playback(),program.root(),ProgramLimits.defaults());
        var decoded=ProgramCodec.decode(bytes,ProgramLimits.defaults(),registry());
        assertEquals(program.hash(),decoded.definition().orElseThrow().hash());
        assertEquals(46,program.bounds().peakMembersPerBatch());
        assertThrows(IllegalArgumentException.class,()->ProgramCodec.decode(java.util.Arrays.copyOf(bytes,bytes.length-1),ProgramLimits.defaults(),registry()));
        bytes[3]=2;
        assertThrows(IllegalArgumentException.class,()->ProgramCodec.decode(bytes,ProgramLimits.defaults(),registry()));
    }
    @Test void overBudgetAndDuplicateIdsAreRejected() {
        assertFalse(ProgramCompiler.compile("test:x",1,new PlaybackPolicy.Once(TailPolicy.RETAIN_UNTIL_LIFETIME),emit(0,513),ProgramLimits.defaults(),registry()).success());
        assertFalse(ProgramCompiler.compile("test:x",1,new PlaybackPolicy.Once(TailPolicy.RETAIN_UNTIL_LIFETIME),new ParallelNode(new NodeId(1),List.of(emit(2,1),emit(2,1))),ProgramLimits.defaults(),registry()).success());
    }
    @Test void farFutureQueryIsBoundedAndRetainsOccurrence() {
        var program=compile(emit(0,1),new PlaybackPolicy.Stream(new TickSpan(60),new TickSpan(20)));
        var events=ProgramSchedule.between(program,6000000000L,6000000060L,10);
        assertEquals(1,events.size());
        assertEquals(6000000020L,events.getFirst().tick());
    }

    @Test void absoluteClocksAreIndependentOfSequenceCursor() {
        for (TimeDomain domain : TimeDomain.values()) {
            var root = new SequenceNode(new NodeId(0), List.of(
                    new WaitNode(new NodeId(1), new TickSpan(900)),
                    new AtNode(new NodeId(2), domain, new TickSpan(20), emit(3,1))));
            var program = compile(root, new PlaybackPolicy.Once(TailPolicy.RETAIN_UNTIL_LIFETIME));
            var anchors = new ClockAnchors(1000,1240,1260L,1270L);
            var events = ProgramSchedule.between(program, anchors,1000,2000,10);
            assertEquals(anchors.origin(domain)+20,events.getFirst().tick());
        }
    }

    @Test void absoluteCardTimeIsNotRepeatedAsLocalOffset() {
        var root = new RepeatNode(new NodeId(0),3,new TickSpan(60),
                new AtNode(new NodeId(1),TimeDomain.CARD,new TickSpan(20),emit(2,1)));
        var program = compile(root,new PlaybackPolicy.Once(TailPolicy.RETAIN_UNTIL_LIFETIME));
        assertEquals(List.of(20L),ProgramSchedule.between(program,0,200,10).stream().map(ProgramSchedule.Event::tick).toList());
    }

    @Test void lifetimeEnvelopeIncludesPreviousCyclesAndUsesHalfOpenEndpoints() {
        var program = compile(emit(0,46),new PlaybackPolicy.Loop(new TickSpan(60),TailPolicy.RETAIN_UNTIL_LIFETIME));
        var report = ProgramBudgetReport.inspect(program,registry(),new ClockAnchors(0,0,null,null),120,180,10);
        assertEquals(46,report.emittedMembers());
        assertEquals(92,report.peakLiveMembers());
        assertEquals(2,report.batches().size());
    }

    @Test void cycleTailEndsMembersAtItsBoundaryInsideRepeat() {
        var cycle=new CycleNode(new NodeId(1),new TickSpan(20),TailPolicy.END_AT_BOUNDARY,emit(2,46));
        var program=compile(new RepeatNode(new NodeId(0),3,new TickSpan(20),cycle),new PlaybackPolicy.Once(TailPolicy.RETAIN_UNTIL_LIFETIME));
        var report=ProgramBudgetReport.inspect(program,registry(),new ClockAnchors(0,0,null,null),0,60,10);
        assertEquals(138,report.emittedMembers());
        assertEquals(46,report.peakLiveMembers());
        assertEquals(List.of(20L,40L,60L),report.batches().stream().map(ProgramBudgetReport.Batch::endExclusive).toList());
    }

    @Test void nominalCountsUseRealGeneratorExpansion() {
        var program=compile(new RepeatNode(new NodeId(0),16,new TickSpan(4),emit(1,46)),
                new PlaybackPolicy.Once(TailPolicy.RETAIN_UNTIL_LIFETIME));
        assertEquals(16,ProgramCompiler.operationOccurrences(program.root()));
        var report=ProgramBudgetReport.inspect(program,registry(),new ClockAnchors(0,0,null,null),0,64,100);
        assertEquals(736,report.emittedMembers());
        assertEquals(736,report.peakLiveMembers());
        assertEquals(46,report.peakBirthsPerTick());
    }
}
