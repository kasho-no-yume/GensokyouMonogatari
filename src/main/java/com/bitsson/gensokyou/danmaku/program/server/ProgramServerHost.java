package com.bitsson.gensokyou.danmaku.program.server;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.danmaku.program.gameplay.ProgramCollisionAdapter;
import com.bitsson.gensokyou.danmaku.program.gameplay.ProgramGameplayStats;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import java.util.*;
import java.util.function.Consumer;

/** Only explicitly installed sessions tick. No BOSS migration or backend selection occurs here. */
@EventBusSubscriber(modid=Gensokyou.MODID)
public final class ProgramServerHost {
    private record Hosted(ProgramSession session,int maxEvents,Consumer<List<DanmakuWorldRuntime.Event>> events,
                          ProgramGameplayStats stats) {}
    public record Stats(long ticks,long totalNanos,long maxNanos,long events,
                        Map<DanmakuWorldRuntime.Kind,Long> eventKinds) {
        public Stats { eventKinds=Map.copyOf(eventKinds); }
        public Stats(long ticks,long totalNanos,long maxNanos,long events) {
            this(ticks,totalNanos,maxNanos,events,Map.of());
        }
    }
    private static final Map<ServerLevel,Hosted> SESSIONS=new IdentityHashMap<>();
    private ProgramServerHost() {}
    public static boolean installed(ServerLevel level) { return SESSIONS.containsKey(level); }
    public static Stats stats(ServerLevel level) {
        Hosted host=SESSIONS.get(level); if (host==null) return new Stats(0,0,0,0);
        var snapshot=host.stats().snapshot();
        long events=snapshot.events().values().stream().mapToLong(Long::longValue).sum();
        return new Stats(snapshot.ticks(),snapshot.totalNanos(),snapshot.maxNanos(),events,snapshot.events());
    }
    public static com.bitsson.gensokyou.danmaku.program.gameplay.ProgramTimingWindow.Snapshot timing(ServerLevel level) {
        Hosted host=SESSIONS.get(level);
        return host==null?new com.bitsson.gensokyou.danmaku.program.gameplay.ProgramTimingWindow.Snapshot(0,0,0,0):host.stats().timing();
    }
    /** Remove only the caller's session, after publishing its terminal facts. */
    public static void remove(ServerLevel level,ProgramSession session,String reason) {
        Hosted host=SESSIONS.get(level);
        if (host==null || host.session()!=session) return;
        session.runtime().close(reason);
        var events=session.runtime().drainEvents();
        com.bitsson.gensokyou.danmaku.program.network.ProgramNetworkHost.publish(level,session,events);
        host.events().accept(events);
        SESSIONS.remove(level);
        com.bitsson.gensokyou.danmaku.program.gameplay.IntegratedProgramTimeline.remove(level.dimension().location().toString());
        com.bitsson.gensokyou.danmaku.program.network.ProgramNetworkHost.unload(level);
    }
    public static void install(ServerLevel level,ProgramSession session,int maxEvents,Consumer<List<DanmakuWorldRuntime.Event>> events) {
        if (maxEvents<1 || SESSIONS.containsKey(level)) throw new IllegalArgumentException("one explicit session per dimension");
        session.runtime().collisions(new ProgramCollisionAdapter(level));
        SESSIONS.put(level,new Hosted(Objects.requireNonNull(session),maxEvents,Objects.requireNonNull(events),new ProgramGameplayStats()));
    }
    @SubscribeEvent public static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Hosted host=SESSIONS.get(level); if (host==null) return;
        long time=level.getGameTime();
        if (time==host.session().runtime().tick()) return;
        long started=System.nanoTime();
        host.session().advance(time,host.maxEvents());
        com.bitsson.gensokyou.danmaku.program.gameplay.IntegratedProgramTimeline.publish(
                level.dimension().location().toString(),time,System.nanoTime());
        var events=host.session().runtime().drainEvents();
        host.stats().events(events);
        com.bitsson.gensokyou.danmaku.program.network.ProgramNetworkHost.publish(level,host.session(),events);
        host.events().accept(events);
        long nanos=System.nanoTime()-started;
        host.stats().tickNanos(nanos);
        com.bitsson.gensokyou.netprof.NetProfile.programTick(time,nanos,host.session().runtime().liveMembers());
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        com.bitsson.gensokyou.danmaku.program.gameplay.IntegratedProgramTimeline.remove(level.dimension().location().toString());
        Hosted host=SESSIONS.remove(level); if (host==null) return;
        host.session().runtime().close("dimension-unload");
        var events=host.session().runtime().drainEvents();
        com.bitsson.gensokyou.danmaku.program.network.ProgramNetworkHost.publish(level,host.session(),events);
        com.bitsson.gensokyou.danmaku.program.network.ProgramNetworkHost.unload(level);
        host.events().accept(events);
    }
}
