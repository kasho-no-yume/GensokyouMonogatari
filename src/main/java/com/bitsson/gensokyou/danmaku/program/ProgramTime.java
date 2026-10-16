package com.bitsson.gensokyou.danmaku.program;

/** Computes an end cursor with explicit clock anchors; no world state is consulted. */
public final class ProgramTime {
    private ProgramTime() {}
    public static long end(ProgramNode node,long start,ClockAnchors clocks) {
        return switch(node) {
            case AtNode at -> end(at.child(),Math.addExact(clocks.origin(at.domain()),at.offset().ticks()),clocks);
            case OffsetNode offset -> end(offset.child(),Math.addExact(start,offset.offset().ticks()),clocks);
            case SequenceNode sequence -> {
                long cursor=start;
                for(ProgramNode child:sequence.children()) cursor=end(child,cursor,clocks);
                yield cursor;
            }
            case ParallelNode parallel -> {
                long latest=start;
                for(ProgramNode child:parallel.children()) latest=Math.max(latest,end(child,start,clocks));
                yield latest;
            }
            case ComposeNode compose -> {
                long latest=start;
                for(ProgramNode child:compose.children()) latest=Math.max(latest,end(child,start,clocks));
                yield latest;
            }
            default -> Math.addExact(start,node.duration().ticks());
        };
    }
}
