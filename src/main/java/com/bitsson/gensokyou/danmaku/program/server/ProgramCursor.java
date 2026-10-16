package com.bitsson.gensokyou.danmaku.program.server;

import com.bitsson.gensokyou.danmaku.program.*;
import java.util.*;

/** Monotonic schedule cursor. Restoring an offset never replays historical side effects. */
public final class ProgramCursor {
    private final ProgramDefinition definition;
    private final ClockAnchors anchors;
    private final Map<NodeId,OperationNode> operations=new HashMap<>();
    private long nextTick;
    public ProgramCursor(ProgramDefinition definition,ClockAnchors anchors,long nextTick) {
        this.definition=Objects.requireNonNull(definition); this.anchors=Objects.requireNonNull(anchors);
        if (nextTick<0) throw new IllegalArgumentException("negative cursor");
        this.nextTick=nextTick;
        for (ProgramNode n:definition.nodes()) if (n instanceof OperationNode op) operations.put(op.id(),op);
    }
    public long nextTick() { return nextTick; }
    public String programHash() { return definition.hash(); }
    public record State(ProgramDefinition definition,ClockAnchors anchors,long nextTick) {}
    public State snapshot() { return new State(definition,anchors,nextTick); }
    public static ProgramCursor restore(State state) { return new ProgramCursor(state.definition(),state.anchors(),state.nextTick()); }
    public List<ProgramSchedule.Event> pending(long untilExclusive,int maxEvents) {
        if (untilExclusive<nextTick) throw new IllegalArgumentException("cursor rewind");
        return ProgramSchedule.between(definition,anchors,nextTick,untilExclusive,maxEvents);
    }
    public OperationNode operation(NodeId id) { return Objects.requireNonNull(operations.get(id),"operation node"); }
    public void dispatch(long untilExclusive,int maxEvents,DanmakuWorldRuntime runtime,DanmakuWorldRuntime.OperationExecutor executor) {
        if (untilExclusive<nextTick) throw new IllegalArgumentException("cursor rewind");
        List<ProgramSchedule.Event> events=ProgramSchedule.between(definition,anchors,nextTick,untilExclusive,maxEvents);
        // Advance before callbacks: a callback failure must not duplicate earlier effects on retry.
        nextTick=untilExclusive;
        for (var event:events) executor.execute(runtime,event,operations.get(event.node()));
    }
    /** Install one boundary into an explicit authority phase, preserving compiler order. */
    public void enqueueTick(long tick,int maxEvents,DanmakuWorldRuntime.Phase phase,
                            DanmakuWorldRuntime runtime,DanmakuWorldRuntime.OperationExecutor executor) {
        if (tick!=nextTick) throw new IllegalArgumentException("cursor tick mismatch");
        dispatch(Math.addExact(tick,1),maxEvents,runtime,(r,event,op)->
                r.schedule(event.tick(),phase,()->executor.execute(r,event,op)));
    }
}
