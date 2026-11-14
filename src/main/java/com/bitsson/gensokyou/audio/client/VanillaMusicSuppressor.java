package com.bitsson.gensokyou.audio.client;

import com.bitsson.gensokyou.Gensokyou;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

import java.util.Set;

/**
 * BOSS 战期间抑制原版情境音乐。
 *
 * <h2>为什么不用 mixin</h2>
 * {@code Minecraft.getMusicManager().tick()} 完全不认识我们的实例，会在
 * {@code nextSongDelay} 到点后插播。有两条路：
 * <ul>
 *   <li>每 tick 调 {@code MusicManager.stopPlaying()}：每次 {@code nextSongDelay += 100}，
 *       战斗期间一路推高，战后最长二十分钟没有原版音乐；</li>
 *   <li>拦 {@link PlaySoundEvent}：没有副作用，且精确到实例。</li>
 * </ul>
 * 这里用后者（NeoForge 在 {@code SoundEngine.play} 入口就派发该事件，可以
 * {@code setSound(null)} 取消）。
 *
 * <h2>为什么只拦原版情境音乐</h2>
 * 用事件 id 白名单而不是"所有 {@code MUSIC} 声源"：唱片机、其它 mod 的音乐不该被牵连。
 * 判据是原版 {@code Musics} 里那几条情境音乐。
 */
@EventBusSubscriber(modid = Gensokyou.MODID, value = Dist.CLIENT)
public final class VanillaMusicSuppressor {

    private static final Set<ResourceLocation> VANILLA_SITUATIONAL_MUSIC = Set.of(
            ResourceLocation.withDefaultNamespace("music.creative"),
            ResourceLocation.withDefaultNamespace("music.dragon"),
            ResourceLocation.withDefaultNamespace("music.end"),
            ResourceLocation.withDefaultNamespace("music.game"),
            ResourceLocation.withDefaultNamespace("music.menu"),
            ResourceLocation.withDefaultNamespace("music.under_water"),
            ResourceLocation.withDefaultNamespace("music.nether.basalt_deltas"),
            ResourceLocation.withDefaultNamespace("music.nether.crimson_forest"),
            ResourceLocation.withDefaultNamespace("music.nether.nether_wastes"),
            ResourceLocation.withDefaultNamespace("music.nether.soul_sand_valley"),
            ResourceLocation.withDefaultNamespace("music.nether.warped_forest"));

    private VanillaMusicSuppressor() {
    }

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        if (!BossBgmDirector.isMusicActive()) {
            return;
        }
        SoundInstance sound = event.getSound();
        if (sound == null || sound.getSource() != SoundSource.MUSIC) {
            return;
        }
        if (VANILLA_SITUATIONAL_MUSIC.contains(sound.getLocation())) {
            event.setSound(null);
        }
    }
}
