package com.bitsson.gensokyou.audio.client;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.audio.Audio;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * 客户端接线：安装后端、推进每 tick 额度、断线清理。
 *
 * <p>类本身标注 {@link Dist#CLIENT}，专用服务器不会加载它，
 * 因而也不会加载 {@link ClientAudioSink} / {@code BeamSound} 等客户端类。
 */
@EventBusSubscriber(modid = Gensokyou.MODID, value = Dist.CLIENT)
public final class AudioClientBootstrap {

    private AudioClientBootstrap() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        Audio.install(new ClientAudioSink());
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Audio.onClientTick();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        Audio.reset();
    }
}
