package com.bitsson.gensokyou.balance;

import com.bitsson.gensokyou.spirit.attr.AttributeKey;
import com.bitsson.gensokyou.spirit.grace.GraceNumbers;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Tests the actual production roll path and boundaries, not duplicated candidate tables. */
class BuildNumbersIntegrationTest {
    @Test
    void realRollsKeepHealthSmallAndGrantNewCategoriesNotNewLegacyAmp() {
        for (int seed = 0; seed < 1000; seed++) {
            RandomSource random = RandomSource.create(seed);
            double health = 20D;
            double ward = 0D;
            double chance = 0D;
            double regen = 0D;
            for (int tier = 1; tier <= 5; tier++) {
                var roll = GraceNumbers.rollTier(tier, random);
                health += roll.contributions().getOrDefault(AttributeKey.HEALTH_BONUS, 0F);
                ward += roll.contributions().getOrDefault(AttributeKey.DANMAKU_REDUCE, 0F);
                chance += roll.contributions().getOrDefault(AttributeKey.CRIT_CHANCE, 0F);
                regen += roll.contributions().getOrDefault(AttributeKey.SPIRIT_REGEN_RATE, 0F);
                assertTrue(health <= BuildNumbers.health(tier) + 1e-5D);
                assertTrue(health >= 20D + (BuildNumbers.health(tier) - 20D) * .95D - 1e-5D);
                assertTrue(ward >= BuildNumbers.ward(tier) - .04D * tier - 1e-5D);
                assertTrue(ward <= BuildNumbers.ward(tier) + .04D * tier + 1e-5D);
                assertTrue(chance <= BuildNumbers.innateMaximum("crit_chance", tier) + 1e-6D);
                assertTrue(regen >= BuildNumbers.pool(tier) * .00108D - .01D);
                assertTrue(regen <= BuildNumbers.pool(tier) * .00132D + .01D);
                assertEquals(0F, roll.contributions().getOrDefault(AttributeKey.SPELL_AMP, 0F));
                assertTrue(roll.contributions().containsKey(AttributeKey.SPELL_EFFECT_AMP));
                assertTrue(roll.contributions().containsKey(AttributeKey.SPELL_COST_REDUCTION));
            }
        }
    }

    @Test
    void extraPowerAndRegenUseDifferentBoundariesFromRawInnate() {
        assertEquals(156.552D, BuildNumbers.cap("spirit_power", 5), 1e-9D);
        assertEquals(BuildNumbers.pool(5) * .00132D + BuildNumbers.cap("spirit_regen_rate", 5),
                BuildNumbers.attributeUpper("spirit_regen_rate", 5, -1D), 1e-8D);
        assertEquals(40D, BuildNumbers.attributeUpper("health_bonus", 5, 200D));
        assertEquals(.5D, BuildNumbers.attributeLower("crit_damage", 0));
    }

    @Test
    void finalPoliciesProtectShieldCountsButAllowLifetimeAndLimitCooldown() {
        var shield = SpellParameterPolicies.card("flower_armor");
        assertEquals(2D, shield.parameter("petals").apply(1D, 5D, 3D));
        assertEquals(1600D, shield.parameter("duration").apply(400D, 5D, 3D));
        assertEquals(2400L, SpellAttributeMath.cooldown(300L, .6D, shield.minimumCooldownTicks()));
        assertEquals(1200L, SpellParameterPolicies.card("healing_garden").minimumCooldownTicks());
    }
}
