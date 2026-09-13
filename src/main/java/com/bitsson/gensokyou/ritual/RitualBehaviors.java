package com.bitsson.gensokyou.ritual;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.ritual.behavior.BarrierBreakBehavior;
import com.bitsson.gensokyou.ritual.behavior.BafangGuiyuanBehavior;
import com.bitsson.gensokyou.ritual.behavior.KagutsuchiFlameBehavior;
import com.bitsson.gensokyou.ritual.behavior.ResonanceRelayBehavior;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class RitualBehaviors {

    private static final Map<ResourceLocation, RitualBehavior> REGISTRY = new HashMap<>();

    public static final ResourceLocation SUMMON = Gensokyou.id("summon_circle");
    public static final ResourceLocation RESONANCE = Gensokyou.id("resonance_relay");
    public static final ResourceLocation BARRIER_BREAK = Gensokyou.id("barrier_break_circle");
    public static final ResourceLocation KAGUTSUICHI = Gensokyou.id("kagutsuchi_flame_circle");
    public static final ResourceLocation BAFANG_GUIYUAN = Gensokyou.id("bafang_guiyuan_circle");

    static {
        register(RESONANCE, new ResonanceRelayBehavior());
        register(BARRIER_BREAK, new BarrierBreakBehavior());
        register(KAGUTSUICHI, new KagutsuchiFlameBehavior());
        register(BAFANG_GUIYUAN, new BafangGuiyuanBehavior());
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
