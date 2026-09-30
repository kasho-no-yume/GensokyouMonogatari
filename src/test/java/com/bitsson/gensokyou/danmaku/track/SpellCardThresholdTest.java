package com.bitsson.gensokyou.danmaku.track;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 符卡<b>切换</b>的阈值语义（回归测试）。
 *
 * <p>曾出的 bug：{@code selectCard} 取「第一个 {@code f <= threshold} 的卡」。而卡 0 的
 * 起始占比最高（1.00），于是它在任何血量下都第一个命中——<b>符卡永远停在第一张</b>，
 * 玩家从头到尾只见过一种弹幕，还以为是内容单调。
 *
 * <p>正确语义：{@code hpFraction} 是该卡<b>开始时</b>的占比，卡表从高到低排；
 * 卡 i 覆盖 {@code [cards[i+1].hpFraction, cards[i].hpFraction]}。
 */
class SpellCardThresholdTest {

    private static SpellCard card(String name, double start) {
        return new SpellCard(name, start, List.of(Track.of("t", 0xFFFFFF)
                .terminates().repeatEvery(40)
                .at(0, Shape.RING_FACING, Shape.Params.defaults()
                        .count(10).gap(60.0D).speed(0.3D), TargetMode.SELF_AXIS)
                .build()));
    }

    /** 与 {@code TrackRunner.selectCard} 同规则的独立实现（供断言）。 */
    private static String select(List<SpellCard> cards, double fraction) {
        if (cards.isEmpty()) {
            return "-";
        }
        SpellCard next = cards.get(0);
        for (SpellCard card : cards) {
            if (fraction <= card.hpFraction() + 1.0E-6D) {
                next = card;
            } else {
                break;
            }
        }
        return next.name().getString();
    }

    @Test
    void cardsAdvanceAsHealthDrops() {
        List<SpellCard> cards = List.of(card("A", 1.00D), card("B", 0.66D), card("C", 0.33D));
        assertEquals("A", select(cards, 1.00D), "满血进第一张");
        assertEquals("A", select(cards, 0.80D), "0.80 还没跌破 0.66，仍在第一张");
        assertEquals("B", select(cards, 0.66D), "恰好在门槛上即算已进入第二张（边界无歧义）");
        assertEquals("B", select(cards, 0.65D), "跌破 0.66 进第二张");
        assertEquals("B", select(cards, 0.34D), "0.34 还没跌破 0.33");
        assertEquals("C", select(cards, 0.32D), "跌破 0.33 进第三张");
        assertEquals("C", select(cards, 0.00D), "空血也在最后一张");
    }

    /** 死到 0 血都必须换过卡——这正是玩家观察到「只有一个弹幕」的那条。 */
    @Test
    void everyBossRunsThroughAllItsCards() {
        for (List<SpellCard> cards : List.of(BossCards.bigFairy(), BossCards.kuzumono(),
                BossCards.kitsuneBi(), BossCards.nomenMask())) {
            java.util.Set<String> seen = new java.util.LinkedHashSet<>();
            for (double f = 1.0D; f >= 0.0D; f -= 0.01D) {
                seen.add(select(cards, f));
            }
            assertEquals(cards.size(), seen.size(),
                    "「" + cards.get(0).name().getString() + "」等 BOSS 的符卡表在血量 100%→0% 期间"
                            + "只触发了 " + seen + "，期望全部 " + cards.size() + " 张");
        }
    }

    @Test
    void everyCardIsReachable() {
        for (List<SpellCard> cards : List.of(BossCards.bigFairy(), BossCards.kuzumono(),
                BossCards.kitsuneBi(), BossCards.nomenMask())) {
            for (SpellCard card : cards) {
                boolean reachable = select(cards, card.hpFraction()).equals(card.name().getString());
                assertTrue(reachable, "符卡「" + card.name().getString() + "」永远不会被选中");
            }
        }
    }

    /** 符卡表必须从高到低排列。 */
    @Test
    void thresholdsAreMonotonicDescending() {
        for (List<SpellCard> cards : List.of(BossCards.bigFairy(), BossCards.kuzumono(),
                BossCards.kitsuneBi(), BossCards.nomenMask())) {
            for (int i = 1; i < cards.size(); i++) {
                assertTrue(cards.get(i).hpFraction() < cards.get(i - 1).hpFraction(),
                        "符卡「" + cards.get(i).name().getString() + "」的起始占比未低于前一张");
            }
            assertTrue(cards.get(0).hpFraction() > 0.9D, "首卡应从接近满血开始");
        }
    }
}
