package com.bitsson.gensokyou.danmaku.program.gameplay;

import com.bitsson.gensokyou.danmaku.program.author.BossProgramController;
import com.bitsson.gensokyou.danmaku.program.content.BossProgramCards;
import java.util.Objects;

/**
 * 一场战斗内的程序符卡切换（不依赖 Minecraft 类型，便于自动化验证）。
 *
 * <p>规则与旧 {@code TrackRunner} 一致：
 * <ul>
 *   <li>选中「起始门槛已达成的最后一张」；</li>
 *   <li>当前卡声明了循环且尚未走到循环边界时，切卡<b>挂起</b>，保证符卡之间不叠加半程；</li>
 *   <li>切卡由 {@link BossProgramController#card} 原子完成：先结束旧卡，再在新边界安装。</li>
 * </ul>
 */
public final class BossProgramBattle {
    private final BossProgramController controller;
    private final BossProgramCards.Deck deck;
    private int current = -1;
    private long cardStart;
    private boolean closed;

    public BossProgramBattle(BossProgramController controller, BossProgramCards.Deck deck) {
        this.controller = Objects.requireNonNull(controller);
        this.deck = Objects.requireNonNull(deck);
    }

    public BossProgramCards.Deck deck() { return deck; }

    /** 安装独立底噪（如有）与满血首卡；必须在 {@code firstTick > runtime.tick()} 时调用。 */
    public void install(long firstTick) { install(1.0D, firstTick, 0); }

    public void install(double hpFraction, long firstTick) { install(hpFraction, firstTick, 0); }

    /**
     * 按当前血量占比安装首卡；{@code forcedCard}（>0）优先，用于读档/重载恢复已持久化的卡。
     *
     * <p>读档/中途开启时血量可能已经低于首卡门槛；直接按占比选卡，避免「回到首卡并挂起到
     * 下一个循环边界才跳」（旧 {@code TrackRunner} 用 {@code current==null} 无条件切卡绕过同一问题）。
     */
    public void install(double hpFraction, long firstTick, int forcedCard) {
        if (deck.background() != null) controller.background(deck.background(), firstTick);
        int index = forcedCard >= 1 && forcedCard <= deck.cards().size() ? forcedCard : deck.select(hpFraction);
        installCard(index, firstTick);
    }

    /**
     * 每 tick 由实体驱动。{@code hpFraction} 是当前血量占比，{@code tick} 是服务端 gameTime。
     */
    public void tick(double hpFraction, long tick) {
        if (!active()) return;
        int selected = deck.select(hpFraction);
        if (selected == current) return;
        if (current >= 0) {
            var card = deck.card(current);
            long elapsed = tick - cardStart;
            // 循环边界挂起：不在半程切卡。完成边界后 elapsed % cycle == 0。
            if (card.cycleTicks() > 0 && elapsed > 0 && elapsed % card.cycleTicks() != 0) return;
        }
        installCard(selected, tick + 1);
    }

    private void installCard(int index, long firstTick) {
        controller.card(deck.card(index).program(), firstTick);
        current = index;
        cardStart = firstTick;
    }

    /**
     * Bind to an already-restored encounter: the live instances keep emitting, so only the battle's
     * bookkeeping (HUD card index, switch suspension origin) is adopted — no background/card re-install.
     */
    public void adopt() {
        if (!controller.resumed()) throw new IllegalStateException("battle is not resumed");
        String hash = controller.currentDefinitionHash();
        current = -1;
        if (hash != null) for (int i = 1; i <= deck.cards().size(); i++)
            if (deck.card(i).program().definition().hash().equals(hash)) { current = i; break; }
        // Unknown card start: suspend switching until the next loop boundary relative to now.
        cardStart = controller.runtimeTick();
    }

    public boolean active() { return !closed && controller.active(); }

    public boolean movementLocked() { return active() && controller.movementLocked(); }

    /** 1 起的当前符卡下标；未安装为 -1。 */
    public int activeCardIndex() { return current; }

    public void close() {
        if (closed) return;
        closed = true;
        controller.close();
    }
}
