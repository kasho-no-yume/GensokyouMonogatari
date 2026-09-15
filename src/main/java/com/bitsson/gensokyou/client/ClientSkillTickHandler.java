package com.bitsson.gensokyou.client;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.network.CastSkillPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Gensokyou.MODID, value = Dist.CLIENT)
public final class ClientSkillTickHandler {

    private ClientSkillTickHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        while (ClientKeyBindings.SKILL_SLOT_1.consumeClick()) {
            PacketDistributor.sendToServer(new CastSkillPayload(0));
        }
        while (ClientKeyBindings.SKILL_SLOT_2.consumeClick()) {
            PacketDistributor.sendToServer(new CastSkillPayload(1));
        }
        while (ClientKeyBindings.SKILL_SLOT_3.consumeClick()) {
            PacketDistributor.sendToServer(new CastSkillPayload(2));
        }
        while (ClientKeyBindings.SKILL_SLOT_4.consumeClick()) {
            PacketDistributor.sendToServer(new CastSkillPayload(3));
        }
        while (ClientKeyBindings.SKILL_SLOT_5.consumeClick()) {
            PacketDistributor.sendToServer(new CastSkillPayload(4));
        }
        ClientSkillState.tickDown();
    }
}
