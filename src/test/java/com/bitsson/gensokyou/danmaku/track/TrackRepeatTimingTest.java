package com.bitsson.gensokyou.danmaku.track;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 重复轨道的<b>发射时序</b>（回归测试）。
 *
 * <p>曾出的 bug：{@code TrackRunner} 拿<b>绝对 tick</b> 去比 {@code beat.tick()}，
 * 于是一条 {@code repeatEvery(50)} 且拍在 {@code t=0} 的轨道永远不命中——
 * <b>所有 BOSS 一弹不发</b>。
 *
 * <p>语义：{@code beat.tick()} 是<b>重复周期内的相位</b>，不是绝对 tick。
 * 一条 {@code repeatEvery(50)}、拍在 {@code t=0} 的轨道，应当在 0/50/100… 各发一次。
 */
class TrackRepeatTimingTest {

    /** 与 {@code TrackRunner.isDue} 同规则。 */
    private static boolean isDue(Track.Beat beat, Track track, int tick) {
        int period = track.repeatEvery();
        if (period > 0) {
            return Math.floorMod(beat.tick(), period) == Math.floorMod(tick, period);
        }
        return beat.tick() == tick;
    }

    /** 与 {@code TrackRunner.isDue(track, tick)} 同规则。 */
    private static boolean trackDue(Track track, int tick) {
        if (track.repeatEvery() > 0) {
            return tick % track.repeatEvery() == 0;
        }
        return track.firesAt(tick);
    }

    @Test
    void beatAtZeroInRepeatingTrackFiresOnEveryPeriod() {
        Track track = Track.of("散華扇", 0x9BE7FF)
                .identity(0, 0, 0)
                .repeatEvery(50)
                .at(0, Shape.FAN, Shape.Params.defaults()
                        .count(5).spread(60.0D).speed(0.3D), TargetMode.SELF_AXIS)
                .build();
        int fired = 0;
        for (int tick = 0; tick < 200; tick++) {
            if (trackDue(track, tick) && isDue(track.beats().get(0), track, tick)) {
                fired++;
            }
        }
        assertEquals(4, fired, "200 tick 内、周期 50 的轨道应当恰好发 4 次");
    }

    @Test
    void everyProductionTrackActuallyFires() {
        for (List<SpellCard> cards : List.of(BossCards.bigFairy(), BossCards.kuzumono(),
                BossCards.kitsuneBi(), BossCards.nomenMask())) {
            for (SpellCard card : cards) {
                for (Track track : card.tracks()) {
                    int fired = 0;
                    for (int tick = 0; tick < 600; tick++) {
                        final int at = tick;
                        boolean due = trackDue(track, at);
                        boolean beatDue = track.beats().stream()
                                .anyMatch(b -> isDue(b, track, at));
                        if (due && beatDue) {
                            fired++;
                        }
                    }
                    assertTrue(fired > 0, "符卡「" + card.name() + "」的轨「" + track.name()
                            + "」在 600 tick 内一次都没发射——这正是「BOSS 不射弹幕」那个 bug");
                }
            }
        }
    }

    /** 无终止轨道（缺結型）必须持续发射，永不进入收束。 */
    @Test
    void endlessTracksKeepFiringPastAnyHorizon() {
        for (SpellCard card : BossCards.nomenMask()) {
            for (Track track : card.tracks()) {
                assertFalse(track.terminates(),
                        "缺「結」符卡的轨道 MUST 无终止条件：" + track.name());
                int firstHalf = 0;
                int secondHalf = 0;
                for (int tick = 0; tick < 7200; tick++) {
                    final int at = tick;
                    boolean hit = trackDue(track, at) && track.beats().stream()
                            .anyMatch(b -> isDue(b, track, at));
                    if (tick < 3600 && hit) {
                        firstHalf++;
                    } else if (hit) {
                        secondHalf++;
                    }
                }
                assertTrue(firstHalf > 0 && secondHalf > 0,
                        "无终止轨道在长时间后仍应持续发射：" + track.name()
                                + "（前半 " + firstHalf + " 次 / 后半 " + secondHalf + " 次）");
            }
        }
    }

    /** 相位随重复推进，环才会自转。 */
    @Test
    void phaseAdvancesWithRepetitions() {
        Track track = Track.of("旋風環", 0xFFD9F0)
                .identity(1, 1, 0)
                .repeatEvery(45)
                .phaseStep(14)
                .at(0, Shape.RING_FACING, Shape.Params.defaults()
                        .count(12).gap(70.0D).speed(0.28D), TargetMode.SELF_AXIS)
                .build();
        assertEquals(0.0D, track.phaseAt(0), 1.0E-9D);
        assertEquals(14.0D, track.phaseAt(45), 1.0E-9D);
        assertEquals(28.0D, track.phaseAt(90), 1.0E-9D);
    }
}
