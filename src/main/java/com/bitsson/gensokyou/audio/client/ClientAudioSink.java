package com.bitsson.gensokyou.audio.client;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.audio.AudioCue;
import com.bitsson.gensokyou.audio.AudioResolver;
import com.bitsson.gensokyou.audio.AudioSink;
import com.bitsson.gensokyou.audio.BeamEnvelope;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashSet;
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
 */
public final class ClientAudioSink implements AudioSink {

    private final AudioResolver resolver = AudioResolver.vanilla(Gensokyou.LOGGER::warn);
    private final Set<BeamSound> beams = new HashSet<>();

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
    public void reset() {
        for (BeamSound sound : beams) {
            sound.stopNow();
        }
        beams.clear();
        resolver.reset();
    }
}
