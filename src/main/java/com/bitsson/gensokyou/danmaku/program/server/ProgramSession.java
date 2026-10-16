package com.bitsson.gensokyou.danmaku.program.server;

import com.bitsson.gensokyou.danmaku.program.*;
import java.util.*;

/** Recoverable definition instances. Future operations remain in definitions, not opaque callbacks. */
public final class ProgramSession {
    public record Instance(long id, DanmakuWorldRuntime.Encounter encounter,long card,boolean background,
                           ProgramCursor.State cursor) {}
    public record Snapshot(DanmakuWorldRuntime.Checkpoint runtime,long nextInstance,List<Instance> instances) {
        public Snapshot { instances=List.copyOf(instances); }
    }
    private final DanmakuWorldRuntime runtime;
    private final ProgramExecution execution;
    private final Map<Long,Instance> instances=new LinkedHashMap<>();
    private long nextInstance=1;
    public ProgramSession(DanmakuWorldRuntime runtime,ProgramExecution execution) {
        this.runtime=Objects.requireNonNull(runtime); this.execution=Objects.requireNonNull(execution);
    }
    public long install(DanmakuWorldRuntime.Encounter encounter,long card,boolean background,
                        ProgramDefinition definition,ClockAnchors anchors,long firstTick) {
        execution.validate(definition);
        if (!runtime.active(encounter)) throw new IllegalArgumentException("inactive encounter");
        if (firstTick<=runtime.tick()) throw new IllegalArgumentException("instance starts after completed boundary");
        long id=nextInstance++;
        instances.put(id,new Instance(id,encounter,card,background,new ProgramCursor(definition,anchors,firstTick).snapshot())); return id;
    }
    public void advance(long tick,int maxEvents) {
        if (tick<0 || tick<=runtime.tick() || runtime.tick()>=0 && tick!=runtime.tick()+1) throw new IllegalArgumentException("session must advance every authority boundary");
        for (Instance instance:List.copyOf(instances.values())) {
            if (!runtime.active(instance.encounter())) { instances.remove(instance.id()); runtime.releaseInstanceHandles(instance.id()); continue; }
            if (instance.cursor().nextTick()>tick) continue;
            if (instance.cursor().definition().playback() instanceof PlaybackPolicy.Loop loop) {
                long cycle=(tick-instance.cursor().anchors().card())/loop.length().ticks();
                if (cycle>0 && (tick-instance.cursor().anchors().card())%loop.length().ticks()==0) {
                    runtime.endCycle(instance.encounter(),instance.card(),cycle-1);
                    runtime.releaseOldOccurrences(instance.id(),cycle);
                }
            }
            ProgramCursor cursor=ProgramCursor.restore(instance.cursor());
            execution.enqueueTick(cursor,tick,maxEvents,runtime,new ProgramExecution.Context(instance.id(),instance.encounter(),instance.card(),instance.background(),
                    instance.cursor().anchors(),instance.cursor().definition().playback()));
            instances.put(instance.id(),new Instance(instance.id(),instance.encounter(),instance.card(),instance.background(),cursor.snapshot()));
        }
        runtime.advance(tick);
        for (Instance instance:List.copyOf(instances.values())) if (!runtime.active(instance.encounter())) {
            instances.remove(instance.id()); runtime.releaseInstanceHandles(instance.id());
        }
    }
    public void endCard(DanmakuWorldRuntime.Encounter encounter,long card) {
        for (Instance i:List.copyOf(instances.values())) if (i.encounter().equals(encounter) && i.card()==card && !i.background()) runtime.releaseInstanceHandles(i.id());
        instances.values().removeIf(i->i.encounter().equals(encounter) && i.card()==card && !i.background());
        runtime.endCard(encounter,card);
    }
    public Snapshot checkpoint() { return new Snapshot(runtime.checkpoint(),nextInstance,List.copyOf(instances.values())); }
    public static ProgramSession restore(Snapshot snapshot,DanmakuWorldRuntime.Limits limits,DecisionProvider world,ProgramExecution execution) {
        ProgramSession session=new ProgramSession(DanmakuWorldRuntime.restore(snapshot.runtime(),limits,world),execution);
        session.nextInstance=snapshot.nextInstance();
        for (Instance instance:snapshot.instances()) { execution.validate(instance.cursor().definition()); session.instances.put(instance.id(),instance); }
        return session;
    }
    public DanmakuWorldRuntime runtime() { return runtime; }
}
