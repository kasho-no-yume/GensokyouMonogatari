package com.bitsson.gensokyou.danmaku.program.server;

import com.bitsson.gensokyou.danmaku.program.*;
import java.util.*;

/** Installed authority handlers are selected by registered operation id, never pattern identity. */
public final class ProgramExecution {
    public interface Handler<P> {
        DanmakuWorldRuntime.Phase phase();
        void execute(DanmakuWorldRuntime runtime, ProgramSchedule.Event occurrence, P parameters);
        default void execute(DanmakuWorldRuntime runtime,Context context,ProgramSchedule.Event occurrence,P parameters) {
            execute(runtime,occurrence,parameters);
        }
    }
    public record Context(long instance,DanmakuWorldRuntime.Encounter encounter,long card,boolean background,
                           ClockAnchors anchors,PlaybackPolicy playback,ProgramSession session) {
        public Context(long instance,DanmakuWorldRuntime.Encounter encounter,long card,boolean background,ClockAnchors anchors,PlaybackPolicy playback) {
            this(instance,encounter,card,background,anchors,playback,null);
        }
    }
    private record Installed<P>(OperationType<P> type,Handler<P> handler) {
        @SuppressWarnings("unchecked")
        void enqueue(DanmakuWorldRuntime runtime,Context context,ProgramSchedule.Event event,Object decoded) {
            P parameters=(P)decoded;
            runtime.schedule(event.tick(),handler.phase(),()->{
                if (context==null) handler.execute(runtime,event,parameters);
                else if (runtime.active(context.encounter())) handler.execute(runtime,context,event,parameters);
            });
        }
    }
    private final Map<String,Installed<?>> handlers=new HashMap<>();
    private boolean frozen;
    private final Set<ProgramDefinition> validated=Collections.newSetFromMap(new WeakHashMap<>());
    private final Map<OperationNode,Object> decoded=new WeakHashMap<>();
    private Object parameters(Installed<?> installed,OperationNode node) {
        return decoded.computeIfAbsent(node,n->installed.type().decode(n.parameters()));
    }
    public <P> void register(OperationType<P> type,Handler<P> handler) {
        if (frozen || handlers.putIfAbsent(type.id(),new Installed<>(type,handler))!=null)
            throw new IllegalStateException("frozen or duplicate authority handler");
    }
    public ProgramExecution freeze() { frozen=true; return this; }
    public void validate(ProgramDefinition definition) {
        if (!frozen) throw new IllegalStateException("freeze authority registry first");
        if (validated.contains(definition)) return;
        for (ProgramNode n:definition.nodes()) if (n instanceof OperationNode node) {
            Installed<?> installed=handlers.get(node.operationId());
            if (installed==null || installed.type().schemaVersion()!=node.schemaVersion()) throw new IllegalArgumentException("missing authority implementation: "+node.operationId());
            parameters(installed,node);
        }
        validateHandles(definition.root(),new HashSet<>(),new HashSet<>());
        validated.add(definition);
    }
    private void validateHandles(ProgramNode node,Set<String> groups,Set<String> targets) {
        if (node instanceof OperationNode operation) {
            Object value=parameters(handlers.get(operation.operationId()),operation);
            if (value instanceof ServerControlOperations.Capture capture) {
                if (!targets.add(capture.name())) throw new IllegalArgumentException("duplicate target handle "+capture.name());
            } else if (value instanceof ServerControlOperations.Group group) {
                if (!targets.contains(group.targets())) throw new IllegalArgumentException("unavailable target handle "+group.targets());
                if (!groups.add(group.name())) throw new IllegalArgumentException("duplicate group handle "+group.name());
            } else if (value instanceof ServerControlOperations.OffsetGroup offset) {
                if (!groups.contains(offset.source()) || !groups.add(offset.name())) throw new IllegalArgumentException("invalid shared frame handle "+offset.name());
            } else {
                String name=value instanceof ServerControlOperations.Fire p?p.group():value instanceof ServerControlOperations.Sample p?p.group():
                        value instanceof ServerControlOperations.Surface p?p.group():value instanceof ServerControlOperations.Emitter p?p.group():
                        value instanceof ServerControlOperations.Replace p?p.group():value instanceof ServerControlOperations.BindSource p?p.group():
                        value instanceof ServerControlOperations.RadialRelease p?p.group():value instanceof ServerControlOperations.RebaseTarget p?p.group():value instanceof ServerControlOperations.Corridor p?p.group():value instanceof ServerControlOperations.Control p?p.group():null;
                boolean whole=value instanceof ServerControlOperations.Control p && (p.kind()==com.bitsson.gensokyou.danmaku.program.ops.Actions.LifecycleKind.END_CARD
                        || p.kind()==com.bitsson.gensokyou.danmaku.program.ops.Actions.LifecycleKind.END_ENCOUNTER);
                if (name!=null && !whole && !groups.contains(name)) throw new IllegalArgumentException("unavailable group handle "+name);
            }
        } else if (node instanceof RepeatNode || node instanceof EveryNode || node instanceof CycleNode) {
            for (ProgramNode child:node.children()) validateHandles(child,new HashSet<>(groups),new HashSet<>(targets));
        } else if (node instanceof ParallelNode) {
            // Cross-branch string handles have no typed dependency edge; require an explicit Sequence.
            for (ProgramNode child:node.children()) validateHandles(child,new HashSet<>(groups),new HashSet<>(targets));
        } else for (ProgramNode child:node.children()) validateHandles(child,groups,targets);
    }
    public void enqueueTick(ProgramCursor cursor,long tick,int maxEvents,DanmakuWorldRuntime runtime) {
        enqueueTick(cursor,tick,maxEvents,runtime,null);
    }
    public void enqueueTick(ProgramCursor cursor,long tick,int maxEvents,DanmakuWorldRuntime runtime,Context context) {
        if (!frozen) throw new IllegalStateException("freeze authority registry first");
        if (cursor.nextTick()!=tick) throw new IllegalArgumentException("cursor tick mismatch");
        validate(cursor.snapshot().definition());
        long until=Math.addExact(tick,1);
        List<ProgramSchedule.Event> events=cursor.pending(until,maxEvents);
        runtime.reserveScheduled(events.size());
        for (var event:events) {
            OperationNode node=cursor.operation(event.node());
            Installed<?> installed=handlers.get(node.operationId());
            parameters(installed,node);
            runtime.requireSchedulable(event.tick(),installed.handler().phase());
        }
        cursor.dispatch(until,maxEvents,runtime,(r,event,node)->{
            Installed<?> installed=handlers.get(node.operationId());
            if (installed==null || installed.type().schemaVersion()!=node.schemaVersion())
                throw new IllegalArgumentException("missing authority implementation: "+node.operationId());
            installed.enqueue(r,context,event,parameters(installed,node));
        });
    }
}
