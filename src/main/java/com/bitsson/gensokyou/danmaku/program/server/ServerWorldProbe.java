package com.bitsson.gensokyou.danmaku.program.server;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.danmaku.program.math.Vec3;
import com.bitsson.gensokyou.danmaku.program.ops.AuthorityDescriptions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import com.bitsson.gensokyou.danmaku.program.*;
import java.util.*;

/** Explicit opt-in integration probe; the caller supplies a disposable server game directory. */
@EventBusSubscriber(modid=Gensokyou.MODID)
public final class ServerWorldProbe {
    private static int ticks;
    private static boolean stopping;
    private static DanmakuWorldRuntime probeRuntime;
    private ServerWorldProbe() {}
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("gensokyou.programWorldProbe")) return;
        var server=event.getServer(); var level=server.overworld();
        try {
            var world=new ServerLevelDecisions(level);
            int x=8,y=200,z=8;
            Map<BlockPos,net.minecraft.world.level.block.state.BlockState> previous=new LinkedHashMap<>();
            try {
                level.getChunk(0,0);
                for (int dy=0;dy<5;dy++) {
                    BlockPos pos=new BlockPos(x,y+dy,z); previous.put(pos,level.getBlockState(pos));
                    level.setBlock(pos,dy==0?Blocks.STONE_SLAB.defaultBlockState():Blocks.AIR.defaultBlockState(),3);
                }
                var surface=world.resolveSurface(List.of(new Vec3(x+.5,y+3,z+.5)),AuthorityDescriptions.SurfaceMode.DOWNWARD_SOLID_TOP,5,5);
                check(surface.points().getFirst().orElseThrow().y()==y+.5,"slab collision height");
                var air=world.resolveSurface(List.of(new Vec3(x+.5,y,z+.5)),AuthorityDescriptions.SurfaceMode.FIRST_AIR_ABOVE,5,5);
                check(air.points().getFirst().orElseThrow().y()==y+1,"first air above slab");
                check(!world.corridor(new Vec3(x+.5,y+.25,z+.5),new Vec3(x+.5,y+2,z+.5),0,20),"occupied corridor");
                check(world.corridor(new Vec3(x+.5,y+1,z+.5),new Vec3(x+.5,y+3,z+.5),0,20),"clear corridor");
                System.out.println("PROGRAM_WORLD_PROBE PASS surface=slab firstAir=verified corridor=blocked+clear");
                var runtime=new DanmakuWorldRuntime("probe",new DanmakuWorldRuntime.Limits(100,100,100,100,1000),world);
                probeRuntime=runtime;
                ProgramServerHost.install(level,new ProgramSession(runtime,new ProgramExecution().freeze()),100,events->{});
            } finally { previous.forEach((pos,state)->level.setBlock(pos,state,3)); }
        } catch (Throwable failure) {
            System.out.println("PROGRAM_WORLD_PROBE FAIL "+failure); failure.printStackTrace();
            server.halt(false);
        }
    }
    @SubscribeEvent public static void tick(LevelTickEvent.Post event) {
        if (stopping || !Boolean.getBoolean("gensokyou.programWorldProbe") || !(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) return;
        if (++ticks>=3) {
            stopping=true;
            check(probeRuntime!=null && probeRuntime.tick()>=0,"explicit host advanced dimension runtime");
            System.out.println("PROGRAM_HOST_PROBE PASS dedicated-level-ticks runtimeTick="+probeRuntime.tick()); level.getServer().halt(false);
        }
    }
    private static void check(boolean condition,String message) { if (!condition) throw new AssertionError(message); }
}
