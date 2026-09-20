package com.bitsson.gensokyou.registry;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.block.entity.CrystalBlockEntity;
import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/** 仪式核心对外挂祭品台代理箱 capability（全方向同一活代理实例）。 */
@EventBusSubscriber(modid = Gensokyou.MODID)
public final class ModCapabilities {

    private ModCapabilities() {
    }

    @SubscribeEvent
    static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.RITUAL_CORE.get(),
                (RitualCoreBlockEntity core, net.minecraft.core.Direction side) -> core.itemHandler());
        // 被仪式绑定的晶块不暴露 capability：自动化必须经仪式核心，避免绕过分区逻辑
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.CRYSTAL.get(),
                (CrystalBlockEntity crystal, net.minecraft.core.Direction side) ->
                        crystal.hasOwner() ? null : crystal.itemHandler());
    }
}
