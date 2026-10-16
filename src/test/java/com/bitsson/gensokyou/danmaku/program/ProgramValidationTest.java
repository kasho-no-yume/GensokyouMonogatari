package com.bitsson.gensokyou.danmaku.program;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProgramValidationTest {
    private static OperationRegistry registry() {
        var registry=new OperationRegistry();
        registry.register(new OperationType<Map<String,String>>() {
            public String id() { return "test:ref"; }
            public int schemaVersion() { return 1; }
            public Map<String,String> decode(Map<String,String> p) { return p; }
            public Map<String,String> encode(Map<String,String> p) { return p; }
            public OperationContract contract(Map<String,String> p) {
                var reads=p.containsKey("read") ? List.of(new Reference(new NodeId(Long.parseLong(p.get("read"))),Reference.Kind.FRAME)) : List.<Reference>of();
                return new OperationContract("group",Set.of(Channel.POSITION),Set.of(Reference.Kind.FRAME),reads,0,100,0,0);
            }
        });
        return registry.freeze();
    }
    private static OperationNode op(long id,Long read) {
        return new OperationNode(new NodeId(id),"test:ref",1,new TickSpan(10),Set.of(Channel.POSITION),
                read==null ? Map.of() : Map.of("read",read.toString()));
    }
    private static CompileResult compile(ProgramNode root) {
        return ProgramCompiler.compile("test:refs",1,new PlaybackPolicy.Once(TailPolicy.RETAIN_UNTIL_LIFETIME),root,ProgramLimits.defaults(),registry());
    }
    @Test void referenceMustExistAndPrecedeConsumer() {
        assertFalse(compile(op(0,99L)).success());
        assertFalse(compile(new SequenceNode(new NodeId(2),List.of(op(0,1L),op(1,null)))).success());
        assertTrue(compile(new SequenceNode(new NodeId(2),List.of(op(0,null),op(1,0L)))).success());
    }
    @Test void referenceCyclesAndExpiredReferencesFail() {
        assertFalse(compile(new SequenceNode(new NodeId(2),List.of(op(0,1L),op(1,0L)))).success());
        assertFalse(compile(new SequenceNode(new NodeId(3),List.of(op(0,null),new WaitNode(new NodeId(2),new TickSpan(100)),op(1,0L)))).success());
    }
    @Test void repeatedReferencesRemainLocalAndValid() {
        var sequence=new SequenceNode(new NodeId(2),List.of(op(0,null),op(1,0L)));
        assertTrue(compile(new RepeatNode(new NodeId(3),10,new TickSpan(20),sequence)).success());
        assertFalse(compile(new SequenceNode(new NodeId(4),List.of(
                new RepeatNode(new NodeId(3),2,new TickSpan(20),op(0,null)),op(1,0L)))).success());
    }
    @Test void compositionRequiresExplicitOrdering() {
        assertFalse(compile(new ParallelNode(new NodeId(2),List.of(op(0,null),op(1,null)))).success());
        assertTrue(compile(new ComposeNode(new NodeId(2),Channel.POSITION,ComposeNode.Mode.ADD_OFFSET,List.of(op(0,null),op(1,null)))).success());
        assertTrue(compile(new ParallelNode(new NodeId(4),List.of(op(0,null),
                new OffsetNode(new NodeId(3),new TickSpan(10),op(1,null))))).success());
    }
    @Test void unknownOperationVersionAndDeepPayloadFail() {
        var unknown=new OperationNode(new NodeId(0),"test:ref",2,new TickSpan(1),Set.of(Channel.POSITION),Map.of());
        assertFalse(compile(unknown).success());
        ProgramNode nested=op(0,null);
        for(int i=1;i<12;i++) nested=new OffsetNode(new NodeId(i),new TickSpan(1),nested);
        assertFalse(compile(nested).success());
    }

    @Test void sameTickReferencesUseTopologyRatherThanNodeNumber() {
        // Explicit composition permits same-channel writes, and dependency determines
        // execution order even when the producer has the higher numeric identity.
        var result=compile(new ComposeNode(new NodeId(2),Channel.POSITION,ComposeNode.Mode.ADD_OFFSET,
                List.of(op(0,1L),op(1,null))));
        assertTrue(result.success(),result.diagnostics().toString());
        assertEquals(List.of(new NodeId(1),new NodeId(0)),
                ProgramSchedule.between(result.definition().orElseThrow(),0,1,10).stream().map(ProgramSchedule.Event::node).toList());
    }

    @Test void canonicalEncodingIsUnambiguousAndBounded() {
        var a=op(0,null);
        byte[] bytes=ProgramCodec.encode("test:a",new PlaybackPolicy.Once(TailPolicy.RETAIN_UNTIL_LIFETIME),a,ProgramLimits.defaults());
        byte[] trailing=Arrays.copyOf(bytes,bytes.length+1);
        assertThrows(IllegalArgumentException.class,()->ProgramCodec.decode(trailing,ProgramLimits.defaults(),registry()));
        var tiny=new ProgramLimits(10,8,512,4096,256,16);
        assertThrows(IllegalArgumentException.class,()->ProgramCodec.encode("test:a",new PlaybackPolicy.Once(TailPolicy.RETAIN_UNTIL_LIFETIME),a,tiny));
        byte[] hostile=bytes.clone();
        // First length is the program id; reject before allocating its claimed size.
        Arrays.fill(hostile,4,8,(byte)0x7f);
        assertThrows(IllegalArgumentException.class,()->ProgramCodec.decode(hostile,ProgramLimits.defaults(),registry()));
    }
}
