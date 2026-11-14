package com.bitsson.gensokyou.audio;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudioFacadeTest {

    private RecordingAudioSink sink;

    @BeforeEach
    void setUp() {
        Audio.uninstall();
        Audio.reset();
        sink = new RecordingAudioSink();
        Audio.install(sink);
    }

    @AfterEach
    void tearDown() {
        Audio.uninstall();
        Audio.reset();
    }

    @Test
    void playUsesTheRegistryParametersAndNeverTrustsCallers() {
        Audio.play(AudioCue.SHOT_NORMAL);

        assertEquals(1, sink.plays.size());
        RecordingAudioSink.Play play = sink.plays.get(0);
        assertEquals(AudioCue.SHOT_NORMAL, play.cue());
        assertEquals(AudioCue.SHOT_NORMAL.baseVolume(), play.volume(), 1.0E-6,
                "音量必须来自注册表");
        assertTrue(Math.abs(play.pitch() - AudioCue.SHOT_NORMAL.basePitch())
                        <= AudioCue.SHOT_NORMAL.pitchJitter() + 1.0E-6,
                "音高必须落在注册表声明的抖动范围内: " + play.pitch());
        assertNull(play.pos(), "非定位播放不得凭空带位置");
    }

    @Test
    void playAtCarriesThePosition() {
        Vec3 pos = new Vec3(1.5D, 64.0D, -3.0D);
        Audio.playAt(AudioCue.KNIFE_THROW, pos);

        assertEquals(1, sink.plays.size());
        assertEquals(pos, sink.plays.get(0).pos());
    }

    @Test
    void playOnWithoutAnEntityIsANoOp() {
        assertDoesNotThrow(() -> Audio.playOn(AudioCue.KNIFE_THROW, null));
        assertTrue(sink.plays.isEmpty());
    }

    @Test
    void sameTickRequestsAreCapped() {
        for (int i = 0; i < 100; i++) {
            Audio.play(AudioCue.SHOT_NORMAL);
        }
        assertEquals(AudioRateLimiter.DEFAULT_PER_TICK_CAP, sink.plays.size(),
                "同一 tick 内新增实例数不得超过全局上限");
    }

    @Test
    void aNewTickRestoresTheBudget() {
        for (int i = 0; i < 100; i++) {
            Audio.play(AudioCue.SHOT_NORMAL);
        }
        Audio.onClientTick();
        Audio.play(AudioCue.KNIFE_THROW);
        assertEquals(AudioRateLimiter.DEFAULT_PER_TICK_CAP + 1, sink.plays.size());
    }

    @Test
    void informationalCueStillPlaysWhenTheBudgetIsFull() {
        for (int i = 0; i < AudioRateLimiter.DEFAULT_PER_TICK_CAP; i++) {
            Audio.play(AudioCue.SHOT_NORMAL);
        }
        Audio.play(AudioCue.WARNING_SHIFT);
        assertEquals(AudioRateLimiter.DEFAULT_PER_TICK_CAP + 1, sink.plays.size(),
                "变轨警示不得被普通 cue 挤掉");
    }

    @Test
    void sinkFailureNeverEscapesToTheCaller() {
        sink.failNext = true;
        assertDoesNotThrow(() -> Audio.play(AudioCue.SHOT_NORMAL));
        assertTrue(sink.plays.isEmpty());
        // 失败之后门面仍可继续工作。
        sink.failNext = false;
        Audio.play(AudioCue.SHOT_NORMAL);
        assertEquals(1, sink.plays.size());
    }

    @Test
    void beamStartsOnceAndStopsThroughTheHandle() {
        Vec3 pos = new Vec3(0.0D, 70.0D, 0.0D);
        BeamHandle handle = Audio.startBeam(AudioCue.LASER_BEAM, pos);

        assertEquals(1, sink.beams.size());
        BeamEnvelope envelope = sink.beams.get(0).envelope();
        assertFalse(envelope.stopped());

        handle.keepAlive();
        handle.stop();
        for (int i = 0; i < BeamEnvelope.FADE_TICKS; i++) {
            envelope.advance();
        }
        assertTrue(envelope.stopped(), "显式停止后必须在淡出结束时终止");
    }

    @Test
    void uninstalledBackendIsANoOp() {
        Audio.uninstall();
        assertDoesNotThrow(() -> {
            Audio.play(AudioCue.SHOT_NORMAL);
            Audio.playAt(AudioCue.BOSS_BURST, Vec3.ZERO);
            Audio.startBeam(AudioCue.LASER_BEAM, Vec3.ZERO);
        });
    }
}
