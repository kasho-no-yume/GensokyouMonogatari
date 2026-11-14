package com.bitsson.gensokyou.audio.client;

import com.bitsson.gensokyou.audio.AudioCue;
import com.bitsson.gensokyou.audio.BeamEnvelope;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * 持续音实例：循环播放一段素材，音量/音高由 {@link BeamEnvelope} 每 tick 推进。
 *
 * <p>本类刻意保持极薄：所有可断言的逻辑（淡变下限、感知曲线、看门狗）都在
 * {@link BeamEnvelope}（纯类）里。这里只做三件事：把包络推进、把值喂给
 * {@code getVolume()/getPitch()}、包络停止时调用 {@code stop()}。
 */
final class BeamSound extends AbstractTickableSoundInstance {

    private final BeamEnvelope envelope;
    private final float baseVolume;
    private final float basePitch;

    BeamSound(SoundEvent event, SoundSource source, Vec3 pos, AudioCue cue,
              BeamEnvelope envelope) {
        super(event, source, SoundInstance.createUnseededRandom());
        this.envelope = envelope;
        this.baseVolume = cue.baseVolume();
        this.basePitch = cue.basePitch();
        this.volume = this.baseVolume;
        this.pitch = this.basePitch;
        this.looping = true;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
        this.relative = false;
        this.x = pos.x;
        this.y = pos.y;
        this.z = pos.z;
    }

    @Override
    public void tick() {
        envelope.advance();
        if (envelope.stopped()) {
            stop();
        }
    }

    @Override
    public float getVolume() {
        return super.getVolume() * (float) BeamEnvelope.playableAmplitude(envelope.amplitude());
    }

    @Override
    public float getPitch() {
        return super.getPitch() * (float) envelope.pitchFactor();
    }

    /** 供后端在断线/重载时立即回收（{@code stop()} 在父类里是 protected）。 */
    void stopNow() {
        stop();
    }
}
