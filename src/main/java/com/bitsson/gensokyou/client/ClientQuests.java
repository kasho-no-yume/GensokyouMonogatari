package com.bitsson.gensokyou.client;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.client.screen.QuestBookScreen;
import com.bitsson.gensokyou.entity.NpcQuestMarker;
import com.bitsson.gensokyou.network.QuestSyncPayload;
import com.bitsson.gensokyou.quest.QuestSnapshot;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

@EventBusSubscriber(modid = Gensokyou.MODID, value = Dist.CLIENT)
public final class ClientQuests {
    private static QuestSnapshot snapshot = QuestSnapshot.EMPTY;
    public static QuestSnapshot snapshot() { return snapshot; }
    public static NpcQuestMarker marker(ResourceLocation role) {
        return NpcQuestMarker.fromId(snapshot.markers().getOrDefault(role, "none"));
    }
    public static void handle(QuestSyncPayload payload) {
        try {
            snapshot = QuestSnapshot.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(payload.json())).getOrThrow();
            var minecraft = Minecraft.getInstance();
            if (minecraft.screen instanceof QuestBookScreen screen) screen.refresh();
            else if (payload.openBook() && minecraft.player != null) minecraft.setScreen(new QuestBookScreen());
        } catch (RuntimeException e) {
            snapshot = QuestSnapshot.EMPTY;
            if (Minecraft.getInstance().screen instanceof QuestBookScreen screen) screen.refresh();
            Gensokyou.LOGGER.error("Invalid private quest snapshot", e);
        }
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { snapshot = QuestSnapshot.EMPTY; }
    private ClientQuests() {}
}
