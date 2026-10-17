package com.bitsson.gensokyou.danmaku.program.gameplay;

import com.bitsson.gensokyou.danmaku.program.server.DanmakuWorldRuntime;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.atomic.LongAccumulator;

/** Low-overhead counters shared by program gameplay diagnostics and A/B probes. */
public final class ProgramGameplayStats {
    private final LongAdder collisionSamples=new LongAdder();
    private final LongAdder hits=new LongAdder();
    private final LongAdder damageRejected=new LongAdder();
    private final LongAdder laserClips=new LongAdder();
    private final LongAdder laserTicksSkipped=new LongAdder();
    private final LongAdder ticks=new LongAdder();
    private final LongAdder totalNanos=new LongAdder();
    private final LongAccumulator maxNanos=new LongAccumulator(Long::max,0L);
    private final ProgramTimingWindow timing=new ProgramTimingWindow(2048);
    private final EnumMap<DanmakuWorldRuntime.Kind,LongAdder> events=new EnumMap<>(DanmakuWorldRuntime.Kind.class);
    public record Snapshot(long collisionSamples,long hits,long damageRejected,long laserClips,long laserTicksSkipped,
                           long ticks,long totalNanos,long maxNanos,Map<DanmakuWorldRuntime.Kind,Long> events) {
        public Snapshot(long collisionSamples,long hits,long damageRejected,long laserClips,long laserTicksSkipped) {
            this(collisionSamples,hits,damageRejected,laserClips,laserTicksSkipped,0,0,0,Map.of());
        }
        public Snapshot {
            events=Map.copyOf(events);
        }
    }
    public void collisionSample() { collisionSamples.increment(); }
    public void hit() { hits.increment(); }
    public void damageRejected() { damageRejected.increment(); }
    public void laserClip() { laserClips.increment(); }
    public void laserSkipped() { laserTicksSkipped.increment(); }
    public void event(DanmakuWorldRuntime.Kind kind) {
        events.computeIfAbsent(kind,k->new LongAdder()).increment();
    }
    public void events(Iterable<DanmakuWorldRuntime.Event> batch) {
        for (var event:batch) {
            event(event.kind());
        }
    }
    public void tickNanos(long nanos) {
        if (nanos<0) throw new IllegalArgumentException("negative tick duration");
        ticks.increment(); totalNanos.add(nanos);
        maxNanos.accumulate(nanos);
        timing.record(nanos);
    }
    public ProgramTimingWindow.Snapshot timing() { return timing.snapshot(); }
    public Snapshot snapshot() {
        var counts=new EnumMap<DanmakuWorldRuntime.Kind,Long>(DanmakuWorldRuntime.Kind.class);
        events.forEach((kind,count)->counts.put(kind,count.sum()));
        return new Snapshot(collisionSamples.sum(),hits.sum(),damageRejected.sum(),laserClips.sum(),laserTicksSkipped.sum(),
                ticks.sum(),totalNanos.sum(),maxNanos.get(),counts);
    }
}
