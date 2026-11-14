package com.bitsson.gensokyou.audio;

import com.bitsson.gensokyou.client.ProgramSoundPlayer;
import com.bitsson.gensokyou.danmaku.program.math.Vec3;
import com.bitsson.gensokyou.danmaku.program.network.ProgramRenderView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgramSoundPlayerTest {

    private final RecordingAudioSink sink = new RecordingAudioSink();

    @BeforeEach
    void setUp() {
        Audio.reset();
        Audio.install(sink);
    }

    @AfterEach
    void tearDown() {
        Audio.uninstall();
        Audio.reset();
    }

    @Test
    void routesRegisteredProgramCueToTheAudioFacade() {
        ProgramSoundPlayer.play(new ProgramRenderView.Sound(
                1, 0, "gensokyou:boss_burst", "flower-throw",
                "b:1/0:MEMBER_AGE:0", new Vec3(1, 2, 3)));

        assertEquals(1, sink.plays.size());
        RecordingAudioSink.Play play = sink.plays.get(0);
        assertEquals(AudioCue.BOSS_BURST, play.cue());
        assertEquals(1.0D, play.pos().x, 1.0E-9D);
        assertEquals(2.0D, play.pos().y, 1.0E-9D);
        assertEquals(3.0D, play.pos().z, 1.0E-9D);
    }

    @Test
    void unknownProgramCueIsSkippedWithoutThrowing() {
        ProgramSoundPlayer.play(new ProgramRenderView.Sound(
                1, 0, "minecraft:entity.blaze.shoot", "legacy",
                "b:1/0:MEMBER_AGE:0", Vec3.ZERO));

        assertTrue(sink.plays.isEmpty());
    }
}
