package com.bitsson.gensokyou.client;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.audio.Audio;
import com.bitsson.gensokyou.audio.AudioCues;
import com.bitsson.gensokyou.danmaku.program.network.ProgramRenderView;

import java.util.HashSet;
import java.util.Set;

/**
 * 程序投影声音事件的播放适配。
 *
 * <p>把「cue id → AudioCue → Audio.playAt」这一段从渲染器里拆出来，保持无 Minecraft
 * 客户端类依赖，因而可以被纯 JUnit 用 {@code RecordingAudioSink} 直接断言。
 */
public final class ProgramSoundPlayer {

    private static final Set<String> REPORTED_UNKNOWN = new HashSet<>();

    private ProgramSoundPlayer() {
    }

    public static void play(ProgramRenderView.Sound sound) {
        var cue = AudioCues.byId(sound.sound());
        if (cue.isEmpty()) {
            if (REPORTED_UNKNOWN.add(sound.sound())) {
                Gensokyou.LOGGER.warn("[audio] program sound cue id is not registered: {}", sound.sound());
            }
            return;
        }
        var p = sound.position();
        Audio.playAt(cue.get(), new net.minecraft.world.phys.Vec3(p.x(), p.y(), p.z()));
    }

    /** 断线 / 切世界：清掉未知 id 的诊断去重。 */
    public static void clearDiagnostics() {
        REPORTED_UNKNOWN.clear();
    }
}
