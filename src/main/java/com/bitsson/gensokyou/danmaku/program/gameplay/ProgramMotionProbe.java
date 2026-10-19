package com.bitsson.gensokyou.danmaku.program.gameplay;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.danmaku.program.math.Vec3;
import com.bitsson.gensokyou.danmaku.program.network.ProgramRenderView;
import com.bitsson.gensokyou.danmaku.program.server.DanmakuWorldRuntime;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.atomic.LongAccumulator;

/** Low-overhead, opt-in integrated-server/client motion diagnosis. */
public final class ProgramMotionProbe {
    private static final AtomicBoolean ACTIVE=new AtomicBoolean();
    private static final LongAdder serverTicks=new LongAdder();
    private static final LongAdder serverNanos=new LongAdder();
    private static final LongAccumulator serverMaxNanos=new LongAccumulator(Long::max,0);
    private static final LongAdder serverIntervalNanos=new LongAdder();
    private static final LongAccumulator serverMaxIntervalNanos=new LongAccumulator(Long::max,0);
    private static final LongAdder frames=new LongAdder();
    private static final LongAdder frameNanos=new LongAdder();
    private static final LongAccumulator frameMaxNanos=new LongAccumulator(Long::max,0);
    private static final LongAdder projectionNanos=new LongAdder();
    private static final LongAccumulator projectionMaxNanos=new LongAccumulator(Long::max,0);
    private static final LongAdder renderNanos=new LongAdder();
    private static final LongAccumulator renderMaxNanos=new LongAccumulator(Long::max,0);
    private static final LongAdder positionSamples=new LongAdder();
    private static final LongAccumulator positionDeltaNanos=new LongAccumulator(Long::max,0);
    private static volatile long lastServerNanos;
    private static volatile long lastFrameNanos;
    private static volatile Vec3 lastPosition;
    private static volatile DanmakuWorldRuntime.MemberId trackedMember;
    private static volatile double lastRenderTime;
    private static volatile int maxLiveMembers;
    private static volatile int maxRenderedMembers;

    private ProgramMotionProbe() {}

    public static void start() {
        reset();
        ACTIVE.set(true);
        Gensokyou.LOGGER.info("[program-probe] START");
    }

    public static String stop() {
        ACTIVE.set(false);
        String result=summary();
        Gensokyou.LOGGER.info("[program-probe] STOP {}",result);
        return result;
    }

    public static boolean active() { return ACTIVE.get(); }

    public static void serverTick(long nowNanos,long tickNanos,int liveMembers) {
        if (!ACTIVE.get()) return;
        serverTicks.increment(); serverNanos.add(tickNanos); serverMaxNanos.accumulate(tickNanos);
        maxLiveMembers=Math.max(maxLiveMembers,liveMembers);
        long previous=lastServerNanos; lastServerNanos=nowNanos;
        if (previous>0) { long interval=nowNanos-previous; serverIntervalNanos.add(interval); serverMaxIntervalNanos.accumulate(interval); }
    }

    public static void frame(long nowNanos,long projection,long render,double renderTime,List<ProgramRenderView.Snapshot> views) {
        if (!ACTIVE.get()) return;
        long previousFrame=lastFrameNanos;
        frames.increment();
        if (previousFrame>0) {
            long interval=nowNanos-previousFrame;
            frameNanos.add(interval); frameMaxNanos.accumulate(interval);
        }
        projectionNanos.add(projection); projectionMaxNanos.accumulate(projection);
        renderNanos.add(render); renderMaxNanos.accumulate(render);
        lastFrameNanos=nowNanos; lastRenderTime=renderTime;
        for (var snapshot:views) {
            maxRenderedMembers=Math.max(maxRenderedMembers,snapshot.members().size());
            if (snapshot.members().isEmpty()) continue;
            var member=snapshot.members().stream().filter(m->m.id().equals(trackedMember)).findFirst().orElse(snapshot.members().getFirst());
            if (!member.id().equals(trackedMember)) { trackedMember=member.id(); lastPosition=member.position(); continue; }
            if (lastPosition!=null) { positionSamples.increment(); positionDeltaNanos.accumulate((long)(member.position().subtract(lastPosition).length()*1_000_000_000)); }
            lastPosition=member.position();
            return;
        }
    }

    public static String summary() {
        long st=serverTicks.sum(), fr=frames.sum(), ps=positionSamples.sum();
        return "active="+ACTIVE.get()+" serverTicks="+st
                +" serverAvgMs="+ms(st==0?0:serverNanos.sum()/st)+" serverMaxMs="+ms(serverMaxNanos.get())
                +" serverAvgIntervalMs="+ms(st<=1?0:serverIntervalNanos.sum()/(st-1))+" serverMaxIntervalMs="+ms(serverMaxIntervalNanos.get())
                +" maxLive="+maxLiveMembers+" frames="+fr
                +" frameAvgMs="+ms(fr<=1?0:frameNanos.sum()/(fr-1))+" frameMaxMs="+ms(frameMaxNanos.get())
                +" projectionAvgMs="+ms(fr==0?0:projectionNanos.sum()/fr)+" projectionMaxMs="+ms(projectionMaxNanos.get())
                +" renderAvgMs="+ms(fr==0?0:renderNanos.sum()/fr)+" renderMaxMs="+ms(renderMaxNanos.get())
                +" maxRendered="+maxRenderedMembers+" positionSamples="+ps
                +" maxPositionStepBlocks="+positionDeltaNanos.get()/1_000_000_000.0+" lastRenderTime="+lastRenderTime;
    }

    private static double ms(long nanos) { return nanos/1_000_000.0; }

    private static void reset() {
        serverTicks.reset(); serverNanos.reset(); serverMaxNanos.reset(); serverIntervalNanos.reset(); serverMaxIntervalNanos.reset();
        frames.reset(); frameNanos.reset(); frameMaxNanos.reset(); projectionNanos.reset(); projectionMaxNanos.reset(); renderNanos.reset(); renderMaxNanos.reset(); positionSamples.reset(); positionDeltaNanos.reset();
        lastServerNanos=0; lastFrameNanos=0; lastPosition=null; trackedMember=null; lastRenderTime=0; maxLiveMembers=0; maxRenderedMembers=0;
    }
}
