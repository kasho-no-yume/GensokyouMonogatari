package com.bitsson.gensokyou.danmaku.program.gameplay;

import com.bitsson.gensokyou.danmaku.program.author.BossProgramController;
import com.bitsson.gensokyou.danmaku.program.content.BossProgramCards;
import net.minecraft.server.level.ServerLevel;
import java.util.Objects;
import java.util.UUID;

/**
 * 实体侧的正式程序后端：开于战斗开始、逐 tick 切卡、结束/死亡时关闭共享宿主。
 *
 * <p>正式 BOSS 仅使用程序判伤；实体侧已移除旧轨道、默认发射与失败回退。
 */
public final class BossProgramBackend {
    private final ServerLevel level;
    private final BossProgramController controller;
    private final BossProgramBattle battle;

    private BossProgramBackend(ServerLevel level, BossProgramController controller, BossProgramBattle battle) {
        this.level = level;
        this.controller = controller;
        this.battle = battle;
    }

    public static BossProgramBackend open(ServerLevel level, UUID owner, BossProgramCards.Deck deck,
                                          double hpFraction, long firstTick, int forcedCard) {
        Objects.requireNonNull(level);
        Objects.requireNonNull(owner);
        Objects.requireNonNull(deck);
        BossProgramController controller = BossProgramHost.open(level, owner, BossProgramSettings.standard());
        try {
            var battle = new BossProgramBattle(controller, deck);
            if (controller.resumed()) battle.adopt();
            else battle.install(hpFraction, firstTick, forcedCard);
            return new BossProgramBackend(level, controller, battle);
        } catch (RuntimeException failure) {
            BossProgramHost.close(level, controller);
            throw failure;
        }
    }

    public void tick(double hpFraction, long tick) { battle.tick(hpFraction, tick); }

    public boolean active() { return battle.active(); }

    public boolean movementLocked() { return battle.movementLocked(); }

    /** 1 起的当前符卡下标；未安装为 -1。 */
    public int activeCardIndex() { return battle.activeCardIndex(); }

    public BossProgramCards.Deck deck() { return battle.deck(); }

    public void close() {
        battle.close();
        BossProgramHost.close(level, controller);
    }
}
