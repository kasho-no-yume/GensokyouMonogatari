package com.bitsson.gensokyou.balance;

import java.util.Map;

/**
 * Shared card/parameter amplification contract. Unlisted parameters remain fixed.
 * Unlisted parameters are fixed, never implicitly multiplied because they are numeric.
 */
public final class SpellParameterPolicies {
    public record Card(Map<String, SpellAttributeMath.ParameterPolicy> parameters,
                       long minimumCooldownTicks) {
        public Card {
            parameters = Map.copyOf(parameters);
        }

        public SpellAttributeMath.ParameterPolicy parameter(String id) {
            return parameters.getOrDefault(id, SpellAttributeMath.ParameterPolicy.fixed());
        }
    }

    private static final SpellAttributeMath.ParameterPolicy MAGNITUDE =
            SpellAttributeMath.ParameterPolicy.magnitude();
    private static final SpellAttributeMath.ParameterPolicy DURATION =
            new SpellAttributeMath.ParameterPolicy(false, true, 0D, -1D);

    private static final Map<String, Card> CARDS = Map.of(
            "musou_fuuin", new Card(Map.of("damage_cap", MAGNITUDE, "duration", DURATION), -1L),
            "icicle_fall", new Card(Map.of("damage", MAGNITUDE), -1L),
            "light_reflect", new Card(Map.of(), -1L),
            "healing_garden", new Card(Map.of("healing_per_second", MAGNITUDE, "duration", DURATION), 1200L),
            // Candidate low-hit safety limits: at most two complete blocks and >=120s between casts.
            "flower_armor", new Card(Map.of("petals",
                    new SpellAttributeMath.ParameterPolicy(true, false, 1D, 2D),
                    "duration", DURATION), 2400L),
            "demarcation", new Card(Map.of(), -1L),
            "spider_web", new Card(Map.of(), -1L),
            "plague_repay", new Card(Map.of(), -1L),
            "fox_servant", new Card(Map.of("damage", MAGNITUDE, "duration", DURATION), -1L));

    private SpellParameterPolicies() {
    }

    public static Card card(String id) {
        return CARDS.getOrDefault(id, new Card(Map.of(), -1L));
    }

    public static Map<String, Card> all() {
        return CARDS;
    }
}
