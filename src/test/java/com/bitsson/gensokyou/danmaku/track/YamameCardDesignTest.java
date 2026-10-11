package com.bitsson.gensokyou.danmaku.track;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 黑谷山女四张符卡的结构断言（change {@code redesign-yamame-spellcards}）。
 *
 * <p>锁住重写后的设计骨架：卡序与阈值、循环长度、轨道数上限、
 * 网系用拖尾 / 瘴系用螺旋、以及 lint 全绿。
 */
class YamameCardDesignTest {

    @Test
    void fourCardsWithDecreasingThresholdsAndCycles() {
        List<SpellCard> cards = BossCards.yamame();
        assertEquals(4, cards.size(), "山女应为 4 张符卡");
        assertEquals(1.00D, cards.get(0).hpFraction(), 1.0E-9D);
        assertEquals(0.75D, cards.get(1).hpFraction(), 1.0E-9D);
        assertEquals(0.50D, cards.get(2).hpFraction(), 1.0E-9D);
        assertEquals(0.25D, cards.get(3).hpFraction(), 1.0E-9D);
        for (SpellCard card : cards) {
            assertTrue(card.hasCycle(), "每张卡都应有循环长度: " + card.name().getString());
            int tracks = card.tracks().size();
            assertTrue(tracks >= 1 && tracks <= 5,
                    "轨道数应在 1~5: " + card.name().getString() + " = " + tracks);
        }
    }

    @Test
    void webCardsUseTrailAndMiasmaCardsUseSpiral() {
        List<SpellCard> cards = BossCards.yamame();
        assertTrue(hasTrail(cards.get(0)), "第 1 张（罠符）应为引线弹拖尾");
        assertTrue(hasSpiral(cards.get(1)), "第 2 张（瘴符）应为瘴气螺旋");
        assertTrue(hasTrail(cards.get(2)), "第 3 张（蜘蛛）应为引线弹拖尾");
        assertTrue(hasSpiral(cards.get(3)), "第 4 张（热病）应为瘴气螺旋");
    }

    @Test
    void lintClean() {
        assertTrue(TrackLint.lint("黑谷山女", BossCards.yamame(), BossCards.YAMAME_PALETTE).isEmpty(),
                "山女符卡表应通过 lint");
    }

    private static boolean hasTrail(SpellCard card) {
        return card.tracks().stream().flatMap(t -> t.beats().stream())
                .anyMatch(b -> b.behaviour().trail().active());
    }

    private static boolean hasSpiral(SpellCard card) {
        return card.tracks().stream().flatMap(t -> t.beats().stream())
                .anyMatch(b -> b.shape() == Shape.SPIRAL);
    }
}
