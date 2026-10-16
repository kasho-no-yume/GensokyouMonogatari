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

/** Client-only transport adapter. Gameplay installs its frozen operation registry explicitly. */
@EventBusSubscriber(modid=Gensokyou.MODID,value=Dist.CLIENT)
public final class ProgramClientNetwork {
    private static final ProgramWire WIRE=new ProgramWire(NetworkLimits.defaults());
    private static ProgramReplica replica=new ProgramReplica(WIRE,new OperationRegistry().freeze(),ProgramLimits.defaults());
    private static int tick;
    private static long request;
    private ProgramClientNetwork() {}
    public static void install(OperationRegistry registry,ProgramLimits limits) { replica.clear(); replica=new ProgramReplica(WIRE,registry,limits); }
    public static ProgramReplica replica() { return replica; }
    public static void handle(ProgramDataPayload payload,IPayloadContext context) { context.enqueueWork(()->replica.receive(payload.bytes(),tick)); }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        tick++;
        replica.noteTick(tick);
        for (var scope:replica.recoveryRequests(tick)) replica.request(scope,++request).ifPresent(e->PacketDistributor.sendToServer(new ProgramResyncPayload(WIRE.encode(e))));
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { replica.clear(); tick=0; request=0; }
}
