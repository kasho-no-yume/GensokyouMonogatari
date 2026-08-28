package com.bitsson.gensokyou.jei;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.ritual.RitualPatternLoader;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = Gensokyou.MODID, value = Dist.CLIENT)
public final class JeiClientSync {

    private JeiClientSync() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (ModList.get().isLoaded("jei")) {
            Bridge.sync(RitualPatternLoader.all());
        }
    }

    private static final class Bridge {
        private static void sync(java.util.List<com.bitsson.gensokyou.ritual.RitualPattern> current) {
            GensokyouJeiPlugin.syncFromLoader(current);
        }
    }
}
