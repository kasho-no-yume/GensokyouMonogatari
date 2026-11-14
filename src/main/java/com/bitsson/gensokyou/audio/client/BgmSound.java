package com.bitsson.gensokyou.audio.client;

import com.bitsson.gensokyou.audio.bossbgm.BgmFade;
import com.bitsson.gensokyou.audio.bossbgm.BgmTrack;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * 一首背景音乐实例：循环、<b>非定位</b>、走 {@code MUSIC} 声源。
 *
 * <p>本类刻意保持极薄：所有可断言的逻辑（淡入/淡出时长、感知曲线、看门狗）都在
 * {@link BgmFade}（纯类）里。这里只做三件事：推进包络、把值喂给 {@code getVolume()}、
 * 包络停止时 {@code stop()}。
 *
 * <h2>三个容易踩的点</h2>
 * <ul>
 *   <li>{@code getVolume()} 返回 0 会被 {@code SoundEngine} 直接跳过（"volume was zero"），
 *       所以起音必须经 {@link BgmFade#playableAmplitude()} 夹到非零下限；</li>
 *   <li>循环是靠 {@code looping = true} + {@code delay = 0} 走"自动循环"分支，
 *       即 {@code LoopingAudioStream} 读空后重开；</li>
 *   <li>{@code relative = true} + {@code Attenuation.NONE}：音乐不随玩家位置衰减，
 *       否则跑远几步歌就没了。</li>
 * </ul>
 */
final class BgmSound extends AbstractTickableSoundInstance {

    private final BgmFade fade;

    BgmSound(SoundEvent event, BgmTrack track, BgmFade fade) {
        super(event, SoundSource.MUSIC, SoundInstance.createUnseededRandom());
        this.fade = fade;
        this.volume = track.volume();
        this.pitch = 1.0F;
        this.looping = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.relative = true;
        this.x = 0.0D;
        this.y = 0.0D;
        this.z = 0.0D;
    }

    @Override
    public void tick() {
        fade.advance();
        if (fade.stopped()) {
            stop();
        }
    }

    @Override
    public float getVolume() {
        return super.getVolume() * (float) fade.playableAmplitude();
    }

    /** 供后端在断线/重载时立即回收（{@code stop()} 在父类里是 protected）。 */
    void stopNow() {
        stop();
    }
}
