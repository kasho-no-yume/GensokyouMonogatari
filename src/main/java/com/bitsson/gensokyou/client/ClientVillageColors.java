package com.bitsson.gensokyou.client;

import com.bitsson.gensokyou.landmark.HakureiShrineLandmark;
import com.bitsson.gensokyou.landmark.HumanVillageShowcase;
import com.bitsson.gensokyou.landmark.VillageColorIndex;
import com.bitsson.gensokyou.network.HumanVillageLayoutSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

@EventBusSubscriber(modid = "gensokyou", value = Dist.CLIENT)
public final class ClientVillageColors {
    private static final VillageColorIndex EMPTY = new VillageColorIndex(List.of(), id -> null);
    private static volatile VillageColorIndex index = EMPTY;
    private ClientVillageColors() {}
    public static void handle(HumanVillageLayoutSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> applySync(payload));
    }
    public static void applySync(HumanVillageLayoutSyncPayload payload) {
        VillageColorIndex next = new VillageColorIndex(payload.instances(), HumanVillageShowcase::loadLocalColors);
        VillageColorIndex previous = index;
        index = next;
        var minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        if (level == null || !level.dimension().equals(HakureiShrineLandmark.GENSOKYO_DIMENSION)) return;
        var dirty = new java.util.HashSet<>(previous.sections());
        dirty.addAll(next.sections());
        for (var section : dirty) {
            if (!level.hasChunk(section.x(), section.z())) continue;
            minecraft.levelRenderer.setSectionDirty(section.x(), section.y(), section.z());
        }
    }
    public static int colorAt(int x, int y, int z, ResourceLocation block) { return index.colorAt(x, y, z, block); }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { index = EMPTY; }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) { if (event.getLevel().isClientSide()) index = EMPTY; }
}
