package com.bitsson.gensokyou.client;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.danmaku.program.*;
import com.bitsson.gensokyou.danmaku.program.network.*;
import com.bitsson.gensokyou.network.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.client.Minecraft;
import java.util.List;

/** Client-only transport adapter. Gameplay installs its frozen operation registry explicitly. */
@EventBusSubscriber(modid=Gensokyou.MODID,value=Dist.CLIENT)
public final class ProgramClientNetwork {
    private static final ProgramWire WIRE=new ProgramWire(NetworkLimits.defaults());
    private static ProgramReplica replica=new ProgramReplica(WIRE,com.bitsson.gensokyou.danmaku.program.author.StandardProgramOperations.registry(),com.bitsson.gensokyou.danmaku.program.author.StandardProgramOperations.limits());
    private static int tick;
    private static long request;
    private static int lastDiagnostic;
    private static final com.bitsson.gensokyou.danmaku.program.gameplay.ProgramTimingWindow RECEIVE_TIMING=new com.bitsson.gensokyou.danmaku.program.gameplay.ProgramTimingWindow(2048);
    private ProgramClientNetwork() {}
    public static void install(OperationRegistry registry,ProgramLimits limits) { replica.clear(); replica=new ProgramReplica(WIRE,registry,limits); }
    public static ProgramReplica replica() { return replica; }
    public static List<ProgramRenderView.Snapshot> renderViews() {
        return renderViews(0);
    }
    public static List<ProgramRenderView.Snapshot> renderViews(float partialTick) {
        Minecraft minecraft=Minecraft.getInstance();
        if (minecraft.level==null) return List.of();
        String dimension=minecraft.level.dimension().location().toString();
        // Integrated authority shares this JVM. Use its actual dimension boundary rather
        // than fitting packet arrival time against an independently scheduled client clock.
        var integrated=minecraft.getSingleplayerServer();
        if (integrated!=null) {
            var authority=integrated.getLevel(minecraft.level.dimension());
            if (authority!=null) return replica.renderViews(dimension,authority.getGameTime()+(minecraft.isPaused()?0:partialTick));
        }
        double time=replica.renderTime(dimension,tick+(minecraft.isPaused()?0:partialTick));
        // Uncertain clocks freeze their last pose; authority End/Release still removes content.
        return replica.clock(dimension).renderable()?replica.renderViews(dimension,time):List.of();
    }
    public static void handle(ProgramDataPayload payload,IPayloadContext context) {
        context.enqueueWork(()->{
            Minecraft minecraft=Minecraft.getInstance();
            if (minecraft.level==null) return;
            String dimension=minecraft.level.dimension().location().toString();
            var before=replica.clock(dimension);
            long disruptions=before.disruptions(),recoveries=before.recoveries();
            long started=System.nanoTime();
            replica.receive(payload.bytes(),tick);
            RECEIVE_TIMING.record(System.nanoTime()-started);
            var after=replica.clock(dimension);
            if (after.disruptions()!=disruptions || after.recoveries()!=recoveries)
                Gensokyou.LOGGER.info("[program-clock-transition] local={} trusted={} renderable={} {}",
                        tick,after.trusted(),after.renderable(),after.diagnostic());
        });
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().isPaused()) return;
        tick++;
        replica.noteTick(tick);
        if (tick-lastDiagnostic>=100 && Minecraft.getInstance().level!=null) {
            lastDiagnostic=tick;
            String dimension=Minecraft.getInstance().level.dimension().location().toString();
            if (replica.active(dimension)) {
                var clock=replica.clock(dimension);
                Gensokyou.LOGGER.info("[program-client] local={} render={} clockError={} trusted={} clockRecoveries={} failures={} late={} transfers={}",
                    tick,clock.time(tick),clock.error(tick),clock.trusted(),clock.recoveries(),replica.failures(),replica.lateEvents(),replica.pendingTransfers());
                Gensokyou.LOGGER.info("[program-clock] {}",clock.diagnostic());
                Gensokyou.LOGGER.info("[program-client-timing] receive={} projection={} geometryAndSubmit={}",
                        RECEIVE_TIMING.snapshot(),ProgramDanmakuRenderer.projectionTiming(),ProgramDanmakuRenderer.renderTiming());
            }
        }
        for (var scope:replica.recoveryRequests(tick)) replica.request(scope,++request).ifPresent(e->PacketDistributor.sendToServer(new ProgramResyncPayload(WIRE.encode(e))));
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { replica.clear(); tick=0; request=0; lastDiagnostic=0; RECEIVE_TIMING.clear(); ProgramDanmakuRenderer.clearSounds(); }
}
