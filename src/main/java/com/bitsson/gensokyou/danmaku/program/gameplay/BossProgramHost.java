package com.bitsson.gensokyou.danmaku.program.gameplay;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.danmaku.program.author.*;
import com.bitsson.gensokyou.danmaku.program.network.*;
import com.bitsson.gensokyou.danmaku.program.server.*;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import java.util.*;

/** Opt-in shared dimension host. Existing entities do not call this until content migration. */
@EventBusSubscriber(modid=Gensokyou.MODID)
public final class BossProgramHost {
    public record Settings(DanmakuWorldRuntime.Limits runtime,int maxEvents,NetworkLimits network,
                           double interestMargin,int clockPeriod,double shardSpan,int shardMembers) {
        public Settings {
            Objects.requireNonNull(runtime); Objects.requireNonNull(network);
            if (maxEvents<1 || !Double.isFinite(interestMargin) || interestMargin<0 || clockPeriod<1
                    || !Double.isFinite(shardSpan) || shardSpan<=0 || shardMembers<1 || shardMembers>512)
                throw new IllegalArgumentException("invalid shared host settings");
        }
    }
    private record Hosted(ProgramSession session,Settings settings,Map<UUID,BossProgramController> battles) {}
    private static final Map<ServerLevel,Hosted> HOSTS=new IdentityHashMap<>();
    private BossProgramHost() {}
    public static BossProgramController open(ServerLevel level,UUID owner,Settings settings) {
        Hosted host=HOSTS.get(level);
        if (host==null) {
            if (ProgramServerHost.installed(level)) throw new IllegalStateException("dimension is owned by another program host");
            var runtime=new DanmakuWorldRuntime(level.dimension().location().toString(),settings.runtime(),new ServerLevelDecisions(level));
            var session=new ProgramSession(runtime,StandardProgramOperations.execution());
            host=new Hosted(session,settings,new HashMap<>());
            ProgramServerHost.install(level,session,settings.maxEvents(),events->{});
            var collision=new ProgramCollisionAdapter(level);
            runtime.collisions(collision);
            ProgramNetworkHost.install(level,session,settings.network(),settings.interestMargin(),settings.clockPeriod(),ProgramMotionBounds::groups,collision::project);
            ProgramNetworkHost.spatial(level,ProgramMotionBounds::members,settings.shardSpan(),settings.shardMembers());
            HOSTS.put(level,host);
        } else if (!host.settings().equals(settings)) throw new IllegalArgumentException("shared dimension settings must agree");
        var previous=host.battles().get(owner);
        if (previous!=null && previous.active()) throw new IllegalArgumentException("owner already has an active battle");
        var battle=new BossProgramController(host.session(),owner); host.battles().put(owner,battle); return battle;
    }
    public static void close(ServerLevel level,BossProgramController battle) {
        Hosted host=HOSTS.get(level); if (host==null) return;
        if (host.battles().get(battle.encounter().owner())!=battle) return;
        battle.close(); host.battles().remove(battle.encounter().owner());
        if (host.battles().isEmpty()) {
            ProgramServerHost.remove(level,host.session(),"last-boss-ended"); HOSTS.remove(level);
        }
    }
    @SubscribeEvent public static void tick(LevelTickEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Hosted host=HOSTS.get(level); if (host==null) return;
        for (var battle:List.copyOf(host.battles().values())) if (!battle.active()) close(level,battle);
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) HOSTS.remove(level);
    }
}
