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
        String item = source("item/weapon/DanmakuWeaponItem.java");
        assertTrue(item.contains("player.startUsingItem(hand)"));
        assertTrue(item.contains("WeaponFiring.tickHeld(player, stack)"));
        assertTrue(item.contains("WeaponFiring.stopHeld(player)"));
    }

    private static String source(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/com/bitsson/gensokyou", relative), StandardCharsets.UTF_8);
    }
}
