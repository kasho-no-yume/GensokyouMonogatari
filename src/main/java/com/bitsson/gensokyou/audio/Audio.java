package com.bitsson.gensokyou.audio;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.audio.bossbgm.BgmFade;
import com.bitsson.gensokyou.audio.bossbgm.BgmTrack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 唯一的生产音效入口。
 *
 * <p>调用点只知道<b>语义</b>（哪一条 cue）与位置/实体，不知道音量、音高、衰减、
 * 限流与降级 —— 这些全在 {@link AudioCue} 注册表与 {@link AudioRateLimiter} 里。
 *
 * <h2>边界</h2>
 * <ul>
 *   <li>只作用于<b>本客户端</b>：不发包、不触碰服务端状态、不参与同步。</li>
 *   <li>未安装 {@link AudioSink} 时（专用服务器 / 纯 JUnit）全部为无操作。</li>
 *   <li>任何内部异常都不会外溢到调用方。</li>
 * </ul>
 *
 * <h2>tick 驱动</h2>
 * 限流的「每 tick 上限」由 {@link #onClientTick()} 推进。没有 tick 驱动时，
 * 所有请求落在同一个 tick 上，等价于「一批最多放行 perTickCap 条」，
 * 不会无限放行，也不会抛异常。
 *
 * <p>本变更<b>不含任何调用点</b>：门面就位，接线属后续消费变更。
 */
public final class Audio {

    private static final AudioRateLimiter LIMITER = new AudioRateLimiter();
    private static final Set<AudioCue> REPORTED_FAILURES = EnumSet.noneOf(AudioCue.class);
    private static final Set<ResourceLocation> REPORTED_MUSIC_FAILURES = new LinkedHashSet<>();

    private static volatile AudioSink sink = AudioSink.NONE;

    private Audio() {
    }

    /** 安装播放后端（客户端初始化调用）。 */
    public static void install(AudioSink newSink) {
        sink = newSink == null ? AudioSink.NONE : newSink;
    }

    /** 卸载后端（测试/回滚）。 */
    public static void uninstall() {
        sink = AudioSink.NONE;
    }

    public static boolean hasSink() {
        return sink != AudioSink.NONE;
    }

    /** 客户端 tick 推进：重置每 tick 额度。 */
    public static void onClientTick() {
        LIMITER.onTick();
    }

    /** 非定位播放（UI / 演出节点）。 */
    public static void play(AudioCue cue) {
        request(cue, null, null);
    }

    /** 定位播放。 */
    public static void playAt(AudioCue cue, Vec3 pos) {
        request(cue, pos, null);
    }

    /** 跟随实体播放（位置取调用时刻的实体位置）。 */
    public static void playOn(AudioCue cue, Entity entity) {
        if (entity == null) {
            return;
        }
        request(cue, entity.position(), entity);
    }

    /**
     * 启动一条持续音。
     *
     * <p>返回句柄必须由消费方持有：每 tick {@link BeamHandle#keepAlive()}，
     * 结束时 {@link BeamHandle#stop()}。不持有也不会永久占用声道（看门狗兜底），
     * 但会在 10 tick 后自行停止。
     */
    public static BeamHandle startBeam(AudioCue cue, Vec3 pos) {
        BeamEnvelope envelope = new BeamEnvelope(cue != null && cue.pitchRamp());
        BeamHandle handle = new BeamHandle(envelope);
        if (cue == null || pos == null) {
            return handle;
        }
        try {
            if (!LIMITER.accept(cue)) {
                return handle;
            }
            sink.startBeam(cue, pos, envelope);
        } catch (RuntimeException failure) {
            reportOnce(cue, failure);
        }
        return handle;
    }

    /** 断线/切世界：清空限流状态与后端内部状态（停掉仍在播的持续音）。 */
    public static void reset() {
        LIMITER.reset();
        REPORTED_FAILURES.clear();
        REPORTED_MUSIC_FAILURES.clear();
        try {
            sink.reset();
        } catch (RuntimeException ignored) {
            // 清理由表现层自己负责，不得影响断开连接。
        }
    }

    // ------------------------------------------------------------------
    // 背景音乐（boss-bgm）：与一次性 cue 完全分开
    // ------------------------------------------------------------------

    /**
     * 启动一首背景音乐。
     *
     * <p>与 {@link #play(AudioCue)} 的差别是刻意的：音乐<b>不进</b> cue 注册表、
     * <b>不受</b>限流与音高抖动、<b>不做</b>占位降级（缺曲时静音 + 日志）。
     * 淡变状态保存在返回的句柄里，调用方每 tick keepAlive、结束时 stop。
     */
    public static MusicHandle startMusic(ResourceLocation eventId, BgmTrack track) {
        BgmFade fade = new BgmFade();
        MusicHandle handle = new MusicHandle(fade);
        if (eventId == null || track == null) {
            return handle;
        }
        try {
            sink.startMusic(eventId, track, handle);
        } catch (RuntimeException failure) {
            if (REPORTED_MUSIC_FAILURES.add(eventId)) {
                Gensokyou.LOGGER.error("[audio] 背景音乐启动失败（{}），已吞掉以免外溢", eventId, failure);
            }
        }
        return handle;
    }

    /** 请求淡出（2.5 秒）。重复调用幂等；句柄为 {@code null} 时无操作。 */
    public static void stopMusic(MusicHandle handle) {
        if (handle == null) {
            return;
        }
        handle.stop();
        try {
            sink.stopMusic(handle);
        } catch (RuntimeException ignored) {
            // 表现层清理失败不得外溢。
        }
    }

    /** 该音乐是否仍被声音引擎持有（资源重载后为 {@code false}）。 */
    public static boolean isMusicActive(MusicHandle handle) {
        if (handle == null) {
            return false;
        }
        try {
            return sink.isMusicActive(handle);
        } catch (RuntimeException ignored) {
            return true;
        }
    }

    private static void request(AudioCue cue, @Nullable Vec3 pos, @Nullable Entity entity) {
        if (cue == null) {
            return;
        }
        try {
            if (!LIMITER.accept(cue)) {
                return;
            }
            sink.play(cue, pos, entity, cue.baseVolume(), jitteredPitch(cue));
        } catch (RuntimeException failure) {
            reportOnce(cue, failure);
        }
    }

    private static float jitteredPitch(AudioCue cue) {
        if (cue.pitchJitter() <= 0.0F) {
            return cue.basePitch();
        }
        float offset = (float) ((ThreadLocalRandom.current().nextDouble() * 2.0D - 1.0D)
                * cue.pitchJitter());
        return Math.max(0.5F, Math.min(2.0F, cue.basePitch() + offset));
    }

    private static void reportOnce(AudioCue cue, RuntimeException failure) {
        if (REPORTED_FAILURES.add(cue)) {
            Gensokyou.LOGGER.error("[audio] 播放请求内部失败（cue {}），已吞掉以免外溢到玩法逻辑",
                    cue.eventId(), failure);
        }
    }
}
