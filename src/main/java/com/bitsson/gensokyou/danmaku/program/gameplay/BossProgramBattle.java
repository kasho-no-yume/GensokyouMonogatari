package com.bitsson.gensokyou.danmaku.program.gameplay;

import com.bitsson.gensokyou.danmaku.program.author.BossProgramController;
import com.bitsson.gensokyou.danmaku.program.content.BossProgramCards;
import java.util.Objects;

/**
 * 一场战斗内的程序符卡切换（不依赖 Minecraft 类型，便于自动化验证）。
 *
 * <p>规则：
 * <ul>
 *   <li>选中「起始门槛已达成的最后一张」；</li>
 *   <li>血量跨过下一张符卡的起始阈值<b>即刻切换</b>，不挂起到循环边界——符卡的循环长度
 *       只表示节拍的重放周期，不是切牌闸门（原作是达阈值即切）；</li>
 *   <li>切卡由 {@link BossProgramController#card} 原子完成：先结束旧卡，再安装新卡。</li>
 * </ul>
 */
public final class BossProgramBattle {
    private final BossProgramController controller;
    private final BossProgramCards.Deck deck;
    private int current = -1;
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
     *
     * <p>血量跨过下一张符卡的起始阈值即刻切换。{@code deck.select} 是血量的单调函数，
     * 故不会来回抖；一次跨过多张阈值时直接落到按「起始门槛已达成的最后一张」规则应处的符卡。
     */
    public void tick(double hpFraction, long tick) {
        if (!active()) return;
        int selected = deck.select(hpFraction);
        if (selected == current) return;
        installCard(selected, tick + 1);
    }

    private void installCard(int index, long firstTick) {
        controller.card(deck.card(index).program(), firstTick);
        current = index;
    }

    /**
     * Bind to an already-restored encounter: the live instances keep emitting, so only the battle's
     * bookkeeping (HUD card index) is adopted — no background/card re-install.
     *
     * <p>符卡下标从当前生效的符卡定义哈希反查。哈希认不出（内容改版）时下标为 -1，
     * 随后的 {@link #tick} 会按当前血量直接落到应处的符卡——这与「达阈值即切」一致，
     * 且不会重复安装同一张卡：认得出时下标已等于当前卡，{@code tick} 不会再次安装。
     */
    public void adopt() {
        if (!controller.resumed()) throw new IllegalStateException("battle is not resumed");
        String hash = controller.currentDefinitionHash();
        current = -1;
        if (hash != null) for (int i = 1; i <= deck.cards().size(); i++)
            if (deck.card(i).program().definition().hash().equals(hash)) { current = i; break; }
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
