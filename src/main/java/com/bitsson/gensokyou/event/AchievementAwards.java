package com.bitsson.gensokyou.event;

import com.bitsson.gensokyou.Gensokyou;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class AchievementAwards {

    private AchievementAwards() {
    }

    public static void award(ServerPlayer player, String path) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        AdvancementHolder holder = server.getAdvancements().get(
                ResourceLocation.fromNamespaceAndPath(Gensokyou.MODID, "achievement/" + path));
        if (holder != null) {
            player.getAdvancements().award(holder, "reached");
        }
    }

    public static boolean has(ServerPlayer player, String path) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }
        AdvancementHolder holder = server.getAdvancements().get(
                ResourceLocation.fromNamespaceAndPath(Gensokyou.MODID, "achievement/" + path));
        return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
    }
}
