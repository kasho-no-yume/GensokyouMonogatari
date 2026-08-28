package com.bitsson.gensokyou.ritual;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.ritual.behavior.BarrierBreakBehavior;
import com.bitsson.gensokyou.ritual.behavior.CapacitorBehavior;
import com.bitsson.gensokyou.ritual.behavior.GeneratorBehavior;

import com.bitsson.gensokyou.ritual.behavior.RelayBehavior;
import com.bitsson.gensokyou.ritual.behavior.TemperingBehavior;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class RitualBehaviors {

    private static final Map<ResourceLocation, RitualBehavior> REGISTRY = new HashMap<>();

    public static final ResourceLocation SUMMON = Gensokyou.id("summon_circle");
    public static final ResourceLocation GENERATOR = Gensokyou.id("generator_circle");
    public static final ResourceLocation TEMPERING = Gensokyou.id("tempering_circle");
    public static final ResourceLocation CAPACITOR = Gensokyou.id("capacitor_circle");
    public static final ResourceLocation RELAY = Gensokyou.id("relay_circle");
    public static final ResourceLocation BARRIER_BREAK = Gensokyou.id("barrier_break_circle");

    static {
        register(GENERATOR, new GeneratorBehavior());
        register(TEMPERING, new TemperingBehavior());
        register(CAPACITOR, new CapacitorBehavior());
        register(RELAY, new RelayBehavior());
        register(BARRIER_BREAK, new BarrierBreakBehavior());
    }

    private RitualBehaviors() {
    }

    public static void register(ResourceLocation patternId, RitualBehavior behavior) {
        REGISTRY.put(patternId, behavior);
    }

    public static Optional<RitualBehavior> get(ResourceLocation patternId) {
        return Optional.ofNullable(REGISTRY.get(patternId));
    }
}
