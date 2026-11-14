package com.bitsson.gensokyou.audio;

import com.bitsson.gensokyou.audio.bossbgm.BgmTrack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** 测试后端：只记录调用，不触碰 Minecraft。 */
final class RecordingAudioSink implements AudioSink {

    record Play(AudioCue cue, @Nullable Vec3 pos, @Nullable Entity entity,
                float volume, float pitch) {
    }

    record BeamStart(AudioCue cue, Vec3 pos, BeamEnvelope envelope) {
    }

    record MusicStart(ResourceLocation eventId, BgmTrack track, MusicHandle handle) {
    }

    final List<Play> plays = new ArrayList<>();
    final List<BeamStart> beams = new ArrayList<>();
    final List<MusicStart> musics = new ArrayList<>();
    final List<MusicHandle> stoppedMusic = new ArrayList<>();

    boolean failNext;
    int resets;

    @Override
    public void play(AudioCue cue, @Nullable Vec3 pos, @Nullable Entity entity,
                     float volume, float pitch) {
        if (failNext) {
            failNext = false;
            throw new IllegalStateException("simulated sink failure");
        }
        plays.add(new Play(cue, pos, entity, volume, pitch));
    }

    @Override
    public void startBeam(AudioCue cue, Vec3 pos, BeamEnvelope envelope) {
        if (failNext) {
            failNext = false;
            throw new IllegalStateException("simulated sink failure");
        }
        beams.add(new BeamStart(cue, pos, envelope));
    }

    @Override
    public void startMusic(ResourceLocation eventId, BgmTrack track, MusicHandle handle) {
        if (failNext) {
            failNext = false;
            throw new IllegalStateException("simulated sink failure");
        }
        musics.add(new MusicStart(eventId, track, handle));
    }

    @Override
    public void stopMusic(MusicHandle handle) {
        stoppedMusic.add(handle);
    }

    @Override
    public void reset() {
        resets++;
        plays.clear();
        beams.clear();
        musics.clear();
        stoppedMusic.clear();
    }
}
