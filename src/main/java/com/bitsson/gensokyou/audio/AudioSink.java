package com.bitsson.gensokyou.audio;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * 可替换的播放后端。
 *
 * <p>这是<b>唯一</b>接触 {@code Minecraft} 客户端的接口：生产实现走
 * {@code ClientLevel.playLocalSound(...)}，测试实现只记录调用。
 *
 * <p>未安装后端时（专用服务器、纯 JUnit）所有入口为无操作。
 */
public interface AudioSink {

    /**
     * 播放一次性音效。
     *
     * @param pos    定位音的位置；非定位音为 {@code null}
     * @param entity 若按实体定位则非空（实现可优先用实体位置）
     */
    void play(AudioCue cue, @Nullable Vec3 pos, @Nullable Entity entity,
              float volume, float pitch);

    /** 启动一条持续音；音量/音高由 {@code envelope} 每 tick 推进。 */
    void startBeam(AudioCue cue, Vec3 pos, BeamEnvelope envelope);

    /** 客户端卸载/断线：清空后端内部状态（停掉仍在播的持续音）。 */
    default void reset() {
    }

    /** 无操作后端。 */
    AudioSink NONE = new AudioSink() {
        @Override
        public void play(AudioCue cue, @Nullable Vec3 pos, @Nullable Entity entity,
                         float volume, float pitch) {
            // no-op
        }

        @Override
        public void startBeam(AudioCue cue, Vec3 pos, BeamEnvelope envelope) {
            // no-op
        }
    };
}
