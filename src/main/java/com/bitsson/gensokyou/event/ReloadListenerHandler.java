package com.bitsson.gensokyou.event;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.ritual.RitualLootLoader;
import com.bitsson.gensokyou.ritual.RitualPatternLoader;
import com.bitsson.gensokyou.ritual.RitualRecipeLoader;
import com.bitsson.gensokyou.ritual.RitualSmeltRuleLoader;
import com.bitsson.gensokyou.ritual.WatatsumiSpecialLootLoader;
import com.bitsson.gensokyou.ritual.brew.RitualBrewRuleLoader;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

@EventBusSubscriber(modid = Gensokyou.MODID)
public final class ReloadListenerHandler {

    private ReloadListenerHandler() {
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new RitualPatternLoader());
        event.addListener(new RitualRecipeLoader());
        event.addListener(new RitualSmeltRuleLoader());
        event.addListener(new RitualLootLoader());
        event.addListener(new WatatsumiSpecialLootLoader());
        // 炼药试剂映射：必须排在最后，使它在装载结束时顺带作废 BrewReagentIndex 的反查缓存
        event.addListener(new RitualBrewRuleLoader());
    }
}
