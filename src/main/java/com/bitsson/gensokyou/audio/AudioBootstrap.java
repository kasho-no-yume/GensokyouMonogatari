package com.bitsson.gensokyou.audio;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.audio.AudioCues;
import com.bitsson.gensokyou.audio.bossbgm.BossBgms;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * 音频子系统的加载期自检。
 *
 * <p>不抛异常：校验失败逐条记 ERROR，并降级到占位机制（见 {@link AudioResolver}）。
 * 放在 {@code FMLCommonSetupEvent} 而不是模组构造器里，是为了让 {@code Gensokyou}
 * 只保留一行注册改动。
 */
@EventBusSubscriber(modid = Gensokyou.MODID)
public final class AudioBootstrap {

    private AudioBootstrap() {
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        for (String problem : AudioCues.validate()) {
            Gensokyou.LOGGER.error("[audio] {}", problem);
        }
        for (String problem : BossBgms.validate()) {
            Gensokyou.LOGGER.error("[audio/bgm] {}", problem);
        }
    }
}
