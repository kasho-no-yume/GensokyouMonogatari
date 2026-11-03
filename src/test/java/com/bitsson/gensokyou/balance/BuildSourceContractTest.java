package com.bitsson.gensokyou.balance;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Static integration guard complements pure math until world acceptance is available. */
class BuildSourceContractTest {
    @Test
    void actualSpellAndWeaponPathsUseSharedPaymentAndPolicies() throws Exception {
        String network = source("network/ModNetworking.java");
        String weapon = source("item/weapon/WeaponFiring.java");
        String spells = source("spirit/SpellCardEffects.java");
        assertTrue(network.contains("SpiritPayments.pay(player, spCost)"));
        assertTrue(network.contains(".effectiveCooldown(cardId, player"));
        assertTrue(weapon.contains("SpiritPayments.pay(player, cost)"));
        assertFalse(weapon.contains("Math.round(stats.spiritCost()"));
        assertTrue(spells.contains("SpellParameterPolicies.card(card)"));
        assertTrue(spells.contains("BuildNumbers.gardenHealingPerSecond"));
        assertTrue(spells.contains("BuildNumbers.foxDamagePerSecond"));
    }

    @Test
    void loginMigrationHasNoRandomRerollAndHeldUseIsWired() throws Exception {
        String service = source("spirit/grace/GraceService.java");
        String migration = service.substring(service.indexOf("public static void migrateIfNeeded"));
        assertFalse(migration.contains("getRandom"));
        assertFalse(migration.contains("applyContributions"));
        String attachments = source("spirit/ModAttachments.java");
        assertTrue(attachments.contains("withClearedCurrentForDeath()"),
                "死亡清池必须走 SpiritPowerData.withClearedCurrentForDeath（tasks 2.4 可测纯函数）");
        String item = source("item/weapon/DanmakuWeaponItem.java");
        assertTrue(item.contains("player.startUsingItem(hand)"));
        assertTrue(item.contains("WeaponFiring.tickHeld(player, stack)"));
        assertTrue(item.contains("WeaponFiring.stopHeld(player)"));
        String effects = source("spirit/SpellCardEffects.java");
        assertTrue(effects.contains("ctx.itemForm() || !(ctx.player() instanceof ServerPlayer sp)"),
                "道具形态 MUST 不读玩家伤害增幅（amplified 守卫，spec item-spellcards）");
        assertTrue(effects.contains("if (!ctx.itemForm() && ctx.serverPlayer() != null)"),
                "道具形态 MUST 不读玩家逐参数增幅/时长（parameter 守卫）");
    }

    private static String source(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/com/bitsson/gensokyou", relative), StandardCharsets.UTF_8);
    }
}
