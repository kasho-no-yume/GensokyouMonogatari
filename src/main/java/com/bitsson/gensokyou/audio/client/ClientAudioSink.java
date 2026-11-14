package com.bitsson.gensokyou.audio.client;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.audio.AudioCue;
import com.bitsson.gensokyou.audio.AudioResolver;
import com.bitsson.gensokyou.audio.AudioSink;
import com.bitsson.gensokyou.audio.BeamEnvelope;
import com.bitsson.gensokyou.audio.MusicHandle;
import com.bitsson.gensokyou.audio.bossbgm.BgmTrack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 生产播放后端（纯客户端）。
 *
 * <p>只在客户端初始化时安装；专用服务器上本类不会被加载，{@code Audio} 保持
 * {@code AudioSink.NONE}，所有入口为无操作。
 *
 * <p>定位音走 {@code ClientLevel.playLocalSound(...)}（不回声到服务端）；
 * 非定位音走 {@code SimpleSoundInstance}（{@code Attenuation.NONE} + {@code relative}），
 * 与既有的 {@code forUI} 行为一致但保留 cue 声明的 {@code SoundSource}。
 *
 * <p>背景音乐走 {@link BgmSound}（{@code MUSIC} 声源、循环、非定位）；
 * 这里额外负责两件事：启动时掐掉原版正在响的情境音乐，以及按句柄回报实例是否还活着
 * （资源重载会清空声音引擎，director 靠这个信号恢复播放）。
 */
public final class ClientAudioSink implements AudioSink {

    private final AudioResolver resolver = AudioResolver.vanilla(Gensokyou.LOGGER::warn);
    private final Set<BeamSound> beams = new HashSet<>();
    /** 音乐句柄 → 实例；value 为 {@code null} 表示事件缺失、已降级为静默。 */
    private final Map<MusicHandle, BgmSound> musics = new IdentityHashMap<>();
    private final Set<ResourceLocation> reportedMissingMusic = new HashSet<>();

    @Override
    public void play(AudioCue cue, @Nullable Vec3 pos, @Nullable Entity entity,
                     float volume, float pitch) {
        SoundEvent event = resolver.resolve(cue);
        if (event == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (!cue.positional()) {
            minecraft.getSoundManager().play(new SimpleSoundInstance(
                    event.getLocation(), cue.source(), volume, pitch,
                    SoundInstance.createUnseededRandom(), false, 0,
                    SoundInstance.Attenuation.NONE, 0.0D, 0.0D, 0.0D, true));
            return;
        }
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        Vec3 at = pos;
        if (at == null && entity != null) {
            at = entity.position();
        }
        if (at == null) {
            return;
        }
        level.playLocalSound(at.x, at.y, at.z, event, cue.source(), volume, pitch, false);
    }

    @Override
    public void startBeam(AudioCue cue, Vec3 pos, BeamEnvelope envelope) {
        SoundEvent event = resolver.resolve(cue);
        if (event == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        BeamSound sound = new BeamSound(event, cue.source(), pos, cue, envelope);
        minecraft.getSoundManager().play(sound);
        beams.add(sound);
    }

    @Override
    public void startMusic(ResourceLocation eventId, BgmTrack track, MusicHandle handle) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        pruneMusic();
        SoundEvent event = BuiltInRegistries.SOUND_EVENT.get(eventId);
        if (event == null) {
            // 缺曲：静音 + 每条事件只记一次诊断（MUST NOT 用占位音效）。
            musics.put(handle, null);
            if (reportedMissingMusic.add(eventId)) {
                Gensokyou.LOGGER.warn("[audio/bgm] 声音事件未注册（{}），本次静默", eventId);
            }
            return;
        }
        BgmSound sound = new BgmSound(event, track, handle.fade());
        minecraft.getSoundManager().play(sound);
        musics.put(handle, sound);
        // 原版情境音乐会在 nextSongDelay 到点后插播；先掐掉正在响的那首，
        // 之后的插播由 VanillaMusicSuppressor 拦住。
        minecraft.getMusicManager().stopPlaying();
    }

    @Override
    public void stopMusic(MusicHandle handle) {
        // 淡出由句柄内的 BgmFade 推进，BgmSound 自行 stop()；这里无需额外动作。
    }

    @Override
    public boolean isMusicActive(MusicHandle handle) {
        BgmSound sound = musics.get(handle);
        if (sound == null) {
            // 事件缺失时 music 表里有键但值为 null —— 视为「已处理」，避免 director 每 tick 重试。
            return musics.containsKey(handle);
        }
        return Minecraft.getInstance().getSoundManager().isActive(sound);
    }

    @Override
    public void reset() {
        for (BeamSound sound : beams) {
            sound.stopNow();
        }
        beams.clear();
        for (BgmSound sound : musics.values()) {
            if (sound != null) {
                sound.stopNow();
            }
        }
        musics.clear();
        reportedMissingMusic.clear();
        resolver.reset();
    }

    private void pruneMusic() {
        musics.entrySet().removeIf(entry -> entry.getKey().stopped());
    }
}
