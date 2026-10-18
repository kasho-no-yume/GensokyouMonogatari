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

/**
 * Opt-in shared dimension host. Existing entities do not call this until content migration.
 *
 * <p>Persistent boss battles share one session per dimension; previews share the same runtime but
 * are <b>ephemeral</b> (never written to disk). The store captures only encounters whose owner was
 * opened as persistent, so a preview can never be saved.
 *
 * <p>Disk flush ordering (verified against NeoForge 21.1.248 {@code ServerLevel.save}): SavedData
 * is flushed <b>before</b> {@code LevelEvent.Save}, so the store is a live {@code Capture} that
 * encodes the session during the flush, and the host marks it dirty whenever the session advances.
 */
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
    private static final class Hosted {
        final ProgramSession session; final Settings settings;
        final Map<UUID,BossProgramController> battles=new HashMap<>();
        final boolean restored;
        final Set<UUID> persistentOwners=new HashSet<>();
        long lastRevision=-1;
        Hosted(ProgramSession session,Settings settings,boolean restored) { this.session=session; this.settings=settings; this.restored=restored; }
    }
    private static final Map<ServerLevel,Hosted> HOSTS=new IdentityHashMap<>();
    private BossProgramHost() {}
    public static ProgramSession session(ServerLevel level) {
        Hosted host=HOSTS.get(level);
        if (host==null) throw new IllegalStateException("program host not opened");
        return host.session;
    }
    /** A real boss battle: persisted to disk. */
    public static BossProgramController open(ServerLevel level,UUID owner,Settings settings) {
        return bind(level,owner,settings,true);
    }
    /** A preview: shares the runtime but is never persisted. */
    public static BossProgramController openEphemeral(ServerLevel level,UUID owner,Settings settings) {
        return bind(level,owner,settings,false);
    }
    private static BossProgramController bind(ServerLevel level,UUID owner,Settings settings,boolean persistent) {
        Hosted host=HOSTS.get(level);
        if (host==null) {
            if (ProgramServerHost.installed(level)) throw new IllegalStateException("dimension is owned by another program host");
            ProgramSession restored=restore(level,settings);
            ProgramSession session=restored!=null?restored:new ProgramSession(
                    new DanmakuWorldRuntime(level.dimension().location().toString(),settings.runtime(),new ServerLevelDecisions(level)),
                    StandardProgramOperations.execution());
            host=new Hosted(session,settings,restored!=null);
            if (restored!=null) for (var encounter:restored.runtime().encounters()) host.persistentOwners.add(encounter.owner());
            ProgramServerHost.install(level,session,settings.maxEvents(),events->{});
            var collision=new ProgramCollisionAdapter(level);
            session.runtime().collisions(collision);
            ProgramNetworkHost.install(level,session,settings.network(),settings.interestMargin(),settings.clockPeriod(),ProgramMotionBounds::groups,collision::project);
            ProgramNetworkHost.spatial(level,ProgramMotionBounds::members,settings.shardSpan(),settings.shardMembers());
            HOSTS.put(level,host);
            final Hosted created=host;
            if (!created.persistentOwners.isEmpty()) ProgramSessionStore.get(level).attach(()->encodePersistent(created));
        } else if (!host.settings.equals(settings)) throw new IllegalArgumentException("shared dimension settings must agree");
        if (persistent) {
            host.persistentOwners.add(owner);
            final Hosted persistentHost=host;
            ProgramSessionStore.get(level).attach(()->encodePersistent(persistentHost));
        }
        var previous=host.battles.get(owner);
        if (previous!=null && previous.active()) throw new IllegalArgumentException("owner already has an active battle");
        if (host.restored) {
            var existing=host.session.runtime().encounter(owner);
            if (existing.isPresent() && host.session.runtime().active(existing.get())) {
                var resumed=BossProgramController.resumed(host.session,existing.get());
                host.battles.put(owner,resumed);
                return resumed;
            }
        }
        var battle=new BossProgramController(host.session,owner); host.battles.put(owner,battle); return battle;
    }
    public static void close(ServerLevel level,BossProgramController battle) {
        Hosted host=HOSTS.get(level); if (host==null) return;
        if (host.battles.get(battle.encounter().owner())!=battle) return;
        battle.close(); host.battles.remove(battle.encounter().owner());
        if (!host.battles.isEmpty()) return;
        // Unbound restored encounters (their owner has not loaded yet) must keep the host alive.
        if (!host.session.runtime().encounters().isEmpty()) {
            ProgramSessionStore.get(level).markDirty();
            return;
        }
        ProgramSessionStore.get(level).clear();
        ProgramServerHost.remove(level,host.session,"last-boss-ended"); HOSTS.remove(level);
    }
    @SubscribeEvent public static void tick(LevelTickEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Hosted host=HOSTS.get(level); if (host==null) return;
        for (var battle:List.copyOf(host.battles.values())) if (!battle.active()) close(level,battle);
        // Cursor position, member age and pending warning time advance even when no
        // network revision changes. Every authority tick needs a fresh disk capture.
        if (!host.persistentOwners.isEmpty()) {
            ProgramSessionStore.get(level).markDirty();
        }
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Hosted host=HOSTS.remove(level); if (host==null) return;
        // Last good snapshot: keep it if the session still has encounters; never clear with empty.
        byte[] bytes=encodePersistent(host);
        if (bytes!=null) ProgramSessionStore.get(level).putPending(bytes,host.session.runtime().tick());
        ProgramSessionStore.get(level).detach();
        ProgramServerHost.remove(level,host.session,"dimension-unload");
    }

    private static ProgramSession restore(ServerLevel level,Settings settings) {
        ProgramSessionStore store=ProgramSessionStore.get(level);
        byte[] bytes=store.pending();
        if (bytes==null || bytes.length==0) return null;
        try {
            var decoded=ProgramSessionCodec.decode(bytes,StandardProgramOperations.limits(),StandardProgramOperations.registry());
            // Align to the current tick so offline time does not elapse; the next authority boundary advances.
            long now=level.getGameTime();
            long delta=Math.max(0,now-decoded.runtime().tick());
            var session=ProgramSession.restore(ProgramSessionCodec.rebase(decoded,delta),settings.runtime(),new ServerLevelDecisions(level),StandardProgramOperations.execution());
            Gensokyou.LOGGER.info("[boss-program] restored {} encounter(s) from disk (delta={})",session.runtime().encounters().size(),delta);
            return session;
        } catch (RuntimeException failure) {
            Gensokyou.LOGGER.error("[boss-program] corrupt persisted session dropped; fresh session will start",failure);
            store.clear();
            return null;
        }
    }
    /** Encode only persistent encounters; preview encounters are ephemeral and must never reach disk. */
    private static byte[] encodePersistent(Hosted host) {
        try {
            Set<DanmakuWorldRuntime.Encounter> keep=new HashSet<>();
            for (var encounter:host.session.runtime().encounters()) if (host.persistentOwners.contains(encounter.owner())) keep.add(encounter);
            if (keep.isEmpty()) return null;
            var filtered=ProgramSessionCodec.persistentOnly(host.session.checkpoint(),keep);
            if (filtered.runtime().encounters().isEmpty()) return null;
            return ProgramSessionCodec.encode(filtered,StandardProgramOperations.limits());
        } catch (RuntimeException failure) {
            Gensokyou.LOGGER.error("[boss-program] failed to encode persisted session; previous snapshot kept",failure);
            return null;
        }
    }
}
