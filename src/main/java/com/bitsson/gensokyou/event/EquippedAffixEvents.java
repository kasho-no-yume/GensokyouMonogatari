package com.bitsson.gensokyou.event;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.item.weapon.EquippedAffixBridge;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 主手武器增幅核的玩家属性装备来源注入（seii-reroll-ritual）。
 *
 * <p>每 tick 幂等调用：签名（词条 id + 值）未变则不重复写入。玩家登出时清签名缓存。
 */
@EventBusSubscriber(modid = Gensokyou.MODID)
public final class EquippedAffixEvents {

    private EquippedAffixEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            EquippedAffixBridge.refresh(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        EquippedAffixBridge.forget(event.getEntity().getUUID());
    }
}
