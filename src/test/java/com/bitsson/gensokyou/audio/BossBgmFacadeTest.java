package com.bitsson.gensokyou.audio;

import com.bitsson.gensokyou.audio.bossbgm.BgmTrack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 音乐门面的接线断言：选了哪条事件、交给后端的是哪首曲目、停止是否幂等。
 *
 * <p>重点是<b>音乐不经过一次性 cue 的限流</b>：同一 tick 里启动 20 次，
 * 后端就应当收到 20 次（真实场景是双 BOSS 交替 + crossfade，虽然极短，
 * 但语义上不该被「每 tick 新增上限」吞掉）。
 */
class BossBgmFacadeTest {

    @AfterEach
    void tearDown() {
        Audio.uninstall();
        Audio.reset();
    }

    @Test
    void startMusicHandsEventAndTrackToTheSink() {
        RecordingAudioSink sink = new RecordingAudioSink();
        Audio.install(sink);

        MusicHandle handle = Audio.startMusic(BgmTrack.TIER2.eventId(), BgmTrack.TIER2);

        assertEquals(1, sink.musics.size());
        assertEquals(BgmTrack.TIER2.eventId(), sink.musics.get(0).eventId());
        assertEquals(BgmTrack.TIER2, sink.musics.get(0).track());
        assertEquals(handle, sink.musics.get(0).handle());
        assertTrue(Audio.isMusicActive(handle), "无实际引擎时后端缺省视为存活");
    }

    @Test
    void stopMusicIsIdempotentAndFadesRatherThanHangs() {
        RecordingAudioSink sink = new RecordingAudioSink();
        Audio.install(sink);
        MusicHandle handle = Audio.startMusic(BgmTrack.TIER1.eventId(), BgmTrack.TIER1);

        Audio.stopMusic(handle);
        Audio.stopMusic(handle);
        Audio.stopMusic(null);

        assertEquals(2, sink.stoppedMusic.size(), "每次 stop 都要通知后端（幂等由淡变状态机保证）");
        assertTrue(handle.stopped() == false, "刚请求停止时仍在淡出");
        for (int i = 0; i < BgmFadeTotal(); i++) {
            handle.fade().signal();
            handle.fade().advance();
        }
        assertTrue(handle.stopped(), "淡出结束后必须停止");
    }

    private static int BgmFadeTotal() {
        return com.bitsson.gensokyou.audio.bossbgm.BgmFade.FADE_OUT_TICKS + 1;
    }

    @Test
    void musicIsNotSubjectToTheOneShotRateLimiter() {
        RecordingAudioSink sink = new RecordingAudioSink();
        Audio.install(sink);

        for (int i = 0; i < 20; i++) {
            Audio.startMusic(BgmTrack.TIER3.eventId(), BgmTrack.TIER3);
        }

        assertEquals(20, sink.musics.size(), "音乐不得被一次性音效的每 tick 上限吞掉");
    }

    @Test
    void missingEventIdIsANoOpAndDoesNotThrow() {
        RecordingAudioSink sink = new RecordingAudioSink();
        Audio.install(sink);

        MusicHandle handle = Audio.startMusic(null, null);

        assertTrue(sink.musics.isEmpty());
        assertTrue(handle != null);
    }
}
