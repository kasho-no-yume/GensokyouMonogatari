package com.bitsson.gensokyou.danmaku.program.network;

import com.bitsson.gensokyou.danmaku.program.math.Vec3;
import com.bitsson.gensokyou.danmaku.program.server.*;
import com.bitsson.gensokyou.network.*;
import net.minecraft.server.level.*;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.*;
import java.util.function.Function;

/** Explicit network installation, invoked by ProgramServerHost after the authority boundary. */
public final class ProgramNetworkHost {
    private record Host(ProgramSession session,ProgramWire wire,double margin,int clockPeriod,
                        Function<ReplicaState.Projection,Map<Long,ProgramInterest.Box>> envelopes,
                        Function<ReplicaState.Projection,ReplicaState.Projection> facts,
                        Function<ReplicaState.Projection,Map<DanmakuWorldRuntime.MemberId,ProgramInterest.Box>> memberBounds,
                        double shardSpan,int shardMembers,
                        Map<UUID,ProgramSyncServer> connections) {}
    private static final Map<ServerLevel,Host> HOSTS=new IdentityHashMap<>();
    private ProgramNetworkHost() {}
    public static void install(ServerLevel level,ProgramSession session,NetworkLimits limits,double margin,int clockPeriod,
                               Function<ReplicaState.Projection,Map<Long,ProgramInterest.Box>> envelopes) {
        install(level,session,limits,margin,clockPeriod,envelopes,Function.identity());
    }
    public static void install(ServerLevel level,ProgramSession session,NetworkLimits limits,double margin,int clockPeriod,
                               Function<ReplicaState.Projection,Map<Long,ProgramInterest.Box>> envelopes,
                               Function<ReplicaState.Projection,ReplicaState.Projection> facts) {
        com.bitsson.gensokyou.danmaku.program.math.Deterministic.nonnegative(margin);
        if (clockPeriod<1 || HOSTS.containsKey(level)) throw new IllegalArgumentException("duplicate network host/period");
        HOSTS.put(level,new Host(session,new ProgramWire(limits),margin,clockPeriod,Objects.requireNonNull(envelopes),Objects.requireNonNull(facts),p->Map.of(),256,128,new HashMap<>()));
    }
    public static void spatial(ServerLevel level,Function<ReplicaState.Projection,Map<DanmakuWorldRuntime.MemberId,ProgramInterest.Box>> bounds,double maxSpan,int maxMembers) {
        Host host=Objects.requireNonNull(HOSTS.get(level));
        com.bitsson.gensokyou.danmaku.program.math.Deterministic.positive(maxSpan);
        if (maxMembers<1 || maxMembers>512) throw new IllegalArgumentException("shard budget");
        HOSTS.put(level,new Host(host.session(),host.wire(),host.margin(),host.clockPeriod(),host.envelopes(),host.facts(),Objects.requireNonNull(bounds),maxSpan,maxMembers,host.connections()));
    }
    public static void publish(ServerLevel level,ProgramSession session,List<DanmakuWorldRuntime.Event> events) {
        Host host=HOSTS.get(level); if (host==null) return;
        if (host.session()!=session) throw new IllegalArgumentException("network/authority session mismatch");
        Set<UUID> online=new HashSet<>();
        for (ServerPlayer player:level.players()) {
            online.add(player.getUUID());
            host.connections().computeIfAbsent(player.getUUID(),id->new ProgramSyncServer(host.wire(),bytes->PacketDistributor.sendToPlayer(player,new ProgramDataPayload(bytes)),host.clockPeriod()));
        }
        host.connections().keySet().retainAll(online);
        var snapshot=session.checkpoint();
        for (var event:events) if (event.kind()==DanmakuWorldRuntime.Kind.END && event.group()==0)
            for (var connection:host.connections().values()) connection.end(snapshot.runtime().dimension(),event.encounter(),event.tick(),event.revision());
        var projection=host.facts().apply(ReplicaState.capture(snapshot,host.wire())); Map<Long,ProgramInterest.Box> envelopes=host.envelopes().apply(projection);
        var members=host.memberBounds().apply(projection);
        for (ServerPlayer player:level.players()) host.connections().get(player.getUUID()).publishSpatial(projection,envelopes,members,new Vec3(player.getX(),player.getY(),player.getZ()),host.margin(),host.shardSpan(),host.shardMembers());
    }
    public static void handleResync(ProgramResyncPayload payload,IPayloadContext context) {
        context.enqueueWork(()->{
            if (!(context.player() instanceof ServerPlayer player)) return;
            // Search installed connections, not the client's claimed dimension or owner tracking set.
            for (var entry:HOSTS.entrySet()) {
                ProgramSyncServer connection=entry.getValue().connections().get(player.getUUID()); if (connection==null) continue;
                ProgramWire.Envelope request=entry.getValue().wire().decode(payload.bytes());
                connection.recover(request,(int)entry.getKey().getGameTime());
            }
        });
    }
    public static void unload(ServerLevel level) { HOSTS.remove(level); }
}
