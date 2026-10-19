package com.bitsson.gensokyou.danmaku.program.gameplay;

import com.bitsson.gensokyou.balance.BalanceNumbers;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.danmaku.program.*;
import com.bitsson.gensokyou.danmaku.program.math.*;
import com.bitsson.gensokyou.danmaku.program.ops.*;
import com.bitsson.gensokyou.danmaku.program.network.*;
import com.bitsson.gensokyou.danmaku.program.server.*;
import com.bitsson.gensokyou.entity.SphereDanmaku;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import java.util.*;

/** Explicit test host; never selects a backend for real BOSS content. */
@EventBusSubscriber(modid=Gensokyou.MODID)
public final class ProgramBenchmarkHost {
    private record Run(UUID viewer,ArmorStand owner,String mode,int count,long first,long end,Frame frame,
                       ProgramSession session,long group,List<SphereDanmaku> bullets) {}
    private static final Map<ServerLevel,Run> RUNS=new IdentityHashMap<>();
    private ProgramBenchmarkHost() {}
    public static boolean active(ServerLevel level) { return RUNS.containsKey(level); }
    public static String start(ServerPlayer player,String mode,int count,int seconds) {
        boolean stress=mode.equals("stress");
        var batches=stress?ProgramStressLoad.batches(count):ProgramBenchmarkLoad.batches(count);
        if (!Set.of("legacy","program","stress").contains(mode) || seconds<210 || seconds>600) throw new IllegalArgumentException("invalid benchmark mode/duration");
        ServerLevel level=player.serverLevel();
        if (RUNS.containsKey(level) || ProgramServerHost.installed(level) || com.bitsson.gensokyou.command.DanmakuPreview.isPreviewing(player))
            throw new IllegalStateException("stop the active preview/benchmark first");
        var anchor=player.getEyePosition().add(player.getLookAngle().scale(16));
        var owner=new ArmorStand(EntityType.ARMOR_STAND,level);
        owner.setInvisible(true); owner.setNoGravity(true); owner.setSilent(true); owner.setInvulnerable(true);
        owner.setPos(anchor.x,anchor.y-1,anchor.z);
        if (!level.addFreshEntity(owner)) throw new IllegalStateException("owner spawn failed");
        var origin=new Vec3(anchor.x,anchor.y,anchor.z);
        var look=player.getLookAngle();
        var frame=Frame.along(origin,new Vec3(look.x,look.y,look.z).multiply(-1),Vec3.Y);
        // Reject obstructed fixtures before either backend starts; a removed member
        // would silently turn this into a different load. This is setup validation,
        // never a reason to disable collision during measurement.
        for (var child:batches) for (var member:child.pattern().generate(new Deterministic.Seed(ProgramBenchmarkLoad.SEED,List.of()))) {
            var pose=new Actions.Pose(frame,Vec3.ZERO);
            var a=EmissionPlan.state(child,Actions.Binding.snapshot(),pose,new Deterministic.Seed(ProgramBenchmarkLoad.SEED,List.of()),member,0).position();
            var b=EmissionPlan.state(child,Actions.Binding.snapshot(),pose,new Deterministic.Seed(ProgramBenchmarkLoad.SEED,List.of()),member,ProgramBenchmarkLoad.LIFETIME).position();
            var segment=new ProgramCollisionSweep.Segment(new net.minecraft.world.phys.Vec3(a.x(),a.y(),a.z()),new net.minecraft.world.phys.Vec3(b.x(),b.y(),b.z()));
            var bounds=ProgramCollisionSweep.bounds(List.of(segment),ProgramBenchmarkLoad.RADIUS);
            var boxes=new ArrayList<net.minecraft.world.phys.AABB>();
            for (var shape:level.getBlockCollisions(owner,bounds)) boxes.addAll(shape.toAabbs());
            for (var entity:level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,bounds,e->e!=owner && e.isAlive())) boxes.add(entity.getBoundingBox());
            if (ProgramCollisionSweep.nearest(List.of(segment),boxes,box->box,ProgramBenchmarkLoad.RADIUS,false).isPresent()) {
                owner.discard();
                throw new IllegalStateException("benchmark corridor obstructed at template "+member.index()+"; choose open air, keeping collision enabled");
            }
        }
        long first=level.getGameTime()+1;
        ProgramSession session=null; long group=0;
        if (!mode.equals("legacy")) {
            var runtime=new DanmakuWorldRuntime(level.dimension().location().toString(),new DanmakuWorldRuntime.Limits(4096,2048,8,64,8192),new ServerLevelDecisions(level));
            session=new ProgramSession(runtime,new ProgramExecution().freeze());
            var encounter=runtime.startEncounter(owner.getUUID());
            group=runtime.createGroup(encounter,1,0,null,-1,false,true,frame,List.of());
            ProgramServerHost.install(level,session,4096,events->{});
            var collision=new ProgramCollisionAdapter(level); runtime.collisions(collision);
            ProgramNetworkHost.install(level,session,NetworkLimits.defaults(),64,20,ProgramPreviewBounds::groups,collision::project);
        }
        RUNS.put(level,new Run(player.getUUID(),owner,mode,count,first,first+seconds*20L,frame,session,group,new ArrayList<>()));
        Gensokyou.LOGGER.info("[program-benchmark] START mode={} count={} seed={} seconds={} first={}",mode,count,ProgramBenchmarkLoad.SEED,seconds,first);
        return "benchmark started: "+mode+" "+count+" for "+seconds+"s";
    }
    public static String stop(ServerLevel level) {
        Run run=RUNS.remove(level); if (run==null) return "benchmark idle";
        if (run.session()!=null) ProgramServerHost.remove(level,run.session(),"benchmark-stop");
        for (var bullet:run.bullets()) bullet.discard();
        run.owner().discard();
        Gensokyou.LOGGER.info("[program-benchmark] STOP mode={} count={} runtime={}",run.mode(),run.count(),run.session()==null?"legacy":run.session().runtime().stats());
        return "benchmark stopped";
    }
    public static String status(ServerLevel level) {
        Run run=RUNS.get(level);
        return run==null?"benchmark idle":"benchmark active mode="+run.mode()+" count="+run.count()+" live="+(run.session()==null?run.bullets().stream().filter(b->!b.isRemoved()).count():run.session().runtime().liveMembers())+" remainingTicks="+(run.end()-level.getGameTime());
    }
    public static String stats(ServerLevel level) {
        Run run=RUNS.get(level);
        if (run==null || run.session()==null) return status(level);
        return status(level)+" runtime="+run.session().runtime().stats()+" timing="+ProgramServerHost.timing(level)
                +" network="+ProgramNetworkHost.stats(level);
    }
    @SubscribeEvent public static void tick(LevelTickEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Run run=RUNS.get(level); if (run==null) return;
        var viewer=level.getServer().getPlayerList().getPlayer(run.viewer());
        // LevelTick.Pre precedes the world's gameTime increment. The current value
        // has already completed its Post/runtime boundary; enqueue for the next one.
        long tick=nextBoundary(level.getGameTime());
        if (viewer==null || !viewer.isAlive() || viewer.serverLevel()!=level || !run.owner().isAlive() || tick>=run.end()) { stop(level); return; }
        int period=run.mode().equals("stress")?ProgramStressLoad.PERIOD:ProgramBenchmarkLoad.PERIOD;
        if (tick<run.first() || (tick-run.first())%period!=0) return;
        // Legacy lifetime checks occur within Entity.tick and can overlap the next
        // cohort by one boundary. Retire that cohort explicitly at the shared end.
        for (var bullet:run.bullets()) bullet.discard();
        run.bullets().removeIf(SphereDanmaku::isRemoved);
        if (run.session()!=null) {
            run.session().runtime().schedule(tick,DanmakuWorldRuntime.Phase.PREPARE,()->{
                var runtime=run.session().runtime();
                for (var child:run.mode().equals("stress")?ProgramStressLoad.batches(run.count()):ProgramBenchmarkLoad.batches(run.count())) {
                    var plan=runtime.prepare(run.group(),tick,new Actions.Pose(run.frame(),Vec3.ZERO),child,Actions.Binding.snapshot(),new Deterministic.Seed(ProgramBenchmarkLoad.SEED,List.of()));
                    if (!runtime.commit(plan,new AuthorityDescriptions.Commit(new ProgramRef.Plan(new NodeId(0)),0,2048,AuthorityDescriptions.Failure.REJECT_BATCH,true)))
                        throw new IllegalStateException("benchmark admission rejected");
                }
            });
        } else for (var child:ProgramBenchmarkLoad.batches(run.count())) for (var member:child.pattern().generate(new Deterministic.Seed(ProgramBenchmarkLoad.SEED,List.of()))) {
            var state=EmissionPlan.state(child,Actions.Binding.snapshot(),new Actions.Pose(run.frame(),Vec3.ZERO),new Deterministic.Seed(ProgramBenchmarkLoad.SEED,List.of()),member,0);
            var bullet=new SphereDanmaku(level,run.owner(),BalanceNumbers.DANMAKU_BASE_DAMAGE.floatValue(),0x4080FF,.4F,Set.of(EntityType.ARMOR_STAND));
            bullet.setPos(state.position().x(),state.position().y(),state.position().z());
            bullet.setVelocity(new net.minecraft.world.phys.Vec3(state.velocity().x(),state.velocity().y(),state.velocity().z()));
            bullet.setLifetimeTicks(ProgramBenchmarkLoad.LIFETIME);
            if (!level.addFreshEntity(bullet)) throw new IllegalStateException("benchmark bullet spawn failed");
            run.bullets().add(bullet);
        }
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) stop(level);
    }
    public static long nextBoundary(long preGameTime) { return Math.addExact(preGameTime,1); }
}
