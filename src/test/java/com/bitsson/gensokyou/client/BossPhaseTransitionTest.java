package com.bitsson.gensokyou.client;

import com.bitsson.gensokyou.support.MinecraftTestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 切阶段演出的触发规则。
 *
 * <p>演出只由「符卡下标<b>真的变了</b>」触发。「玩家开始观测该 BOSS 时补发当前下标」
 * 不是切牌 —— 玩家从未见过那张牌的宣言，不该在晚进场时补播一遍。
 */
class BossPhaseTransitionTest {

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.ensureStarted();
    }

    @Test
    void onlyARealCardChangeTriggersTheDeclaration() {
        ClientSpellCardNames.clear();
        BossPhaseTransition.clear();
        assertFalse(ClientSpellCardNames.put(7, 1), "the first record is a state backfill, not a card change");
        assertTrue(ClientSpellCardNames.put(7, 2), "a changed index is a card switch");
        assertFalse(ClientSpellCardNames.put(7, 2), "the same index again is not a card switch");
    }

    @Test
    void theDeclarationIsForgottenWithTheEntity() {
        ClientSpellCardNames.clear();
        BossPhaseTransition.clear();
        ClientSpellCardNames.put(7, 1);
        BossPhaseTransition.declare(7, 2);
        BossPhaseTransition.forget(7);
        // 实体离开后，同一 entityId 被另一只 BOSS 复用时，它的补发不得被当成切牌
        assertFalse(ClientSpellCardNames.put(7, 1), "a recycled entity id must not read as a card switch");
    }
}
