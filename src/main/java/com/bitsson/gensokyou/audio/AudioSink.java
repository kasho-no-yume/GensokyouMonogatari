package com.bitsson.gensokyou.audio;

import com.bitsson.gensokyou.audio.bossbgm.BgmTrack;
import net.minecraft.resources.ResourceLocation;
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

    /**
     * 启动一首背景音乐（循环、非定位）。
     *
     * @param eventId 实际播放的事件 id（可能是单只 BOSS 的事件，指向阶音频文件）
     * @param track   曲目参数（音量、淡变）
     * @param handle  句柄；淡变状态在句柄里，实现只需按它取值
     */
    default void startMusic(ResourceLocation eventId, BgmTrack track, MusicHandle handle) {
        // 无操作后端：音乐是纯客户端表现
    }

    /** 立即释放一首音乐（正常淡出由 {@link MusicHandle#stop()} 完成）。 */
    default void stopMusic(MusicHandle handle) {
        // 无操作后端
    }

    /**
     * 该音乐实例是否仍被声音引擎持有。
     *
     * <p>缺省返回 {@code true}：无后端时 director 不该误判为「被丢弃」而反复重起。
     */
    default boolean isMusicActive(MusicHandle handle) {
        return true;
    }

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
