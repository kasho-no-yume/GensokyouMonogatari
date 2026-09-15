package com.bitsson.gensokyou.jei;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.ritual.RitualRecipeLoader;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** 客户端热重载桥：配方集（ritual_recipes）变化即同步 JEI 页签卡片。 */
@EventBusSubscriber(modid = Gensokyou.MODID, value = Dist.CLIENT)
public final class JeiClientSync {

    private JeiClientSync() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (ModList.get().isLoaded("jei")) {
            GensokyouJeiPlugin.syncFromLoader(RitualRecipeLoader.all());
        }
    }
}
