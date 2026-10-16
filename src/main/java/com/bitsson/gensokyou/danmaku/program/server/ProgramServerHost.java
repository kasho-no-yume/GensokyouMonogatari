package com.bitsson.gensokyou.danmaku.program.server;

import com.bitsson.gensokyou.Gensokyou;
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
    private record Hosted(ProgramSession session,int maxEvents,Consumer<List<DanmakuWorldRuntime.Event>> events) {}
    private static final Map<ServerLevel,Hosted> SESSIONS=new IdentityHashMap<>();
    private ProgramServerHost() {}
    public static void install(ServerLevel level,ProgramSession session,int maxEvents,Consumer<List<DanmakuWorldRuntime.Event>> events) {
        if (maxEvents<1 || SESSIONS.containsKey(level)) throw new IllegalArgumentException("one explicit session per dimension");
        SESSIONS.put(level,new Hosted(Objects.requireNonNull(session),maxEvents,Objects.requireNonNull(events)));
    }
    @SubscribeEvent public static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Hosted host=SESSIONS.get(level); if (host==null) return;
        long time=level.getGameTime();
        if (time==host.session().runtime().tick()) return;
        host.session().advance(time,host.maxEvents());
        var events=host.session().runtime().drainEvents();
        com.bitsson.gensokyou.danmaku.program.network.ProgramNetworkHost.publish(level,host.session(),events);
        host.events().accept(events);
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Hosted host=SESSIONS.remove(level); if (host==null) return;
        host.session().runtime().close("dimension-unload");
        var events=host.session().runtime().drainEvents();
        com.bitsson.gensokyou.danmaku.program.network.ProgramNetworkHost.publish(level,host.session(),events);
        com.bitsson.gensokyou.danmaku.program.network.ProgramNetworkHost.unload(level);
        host.events().accept(events);
    }
}
