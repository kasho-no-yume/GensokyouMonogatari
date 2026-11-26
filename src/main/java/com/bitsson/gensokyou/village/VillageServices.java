package com.bitsson.gensokyou.village;

import com.bitsson.gensokyou.spirit.ModAttachments;
import net.minecraft.server.level.ServerPlayer;

public final class VillageServices {
    public static VillagePlayerData data(ServerPlayer player) {
        var old = player.getData(ModAttachments.VILLAGE_SERVICES.get());
        long day = Math.max(0L, Math.floorDiv(player.getServer().overworld().getDayTime(), 24000L));
        var next = old.forDay(day);
        if (next != old) store(player, next);
        return next;
    }
    public static void store(ServerPlayer player, VillagePlayerData data) { player.setData(ModAttachments.VILLAGE_SERVICES.get(), data); }
    public static long seed(ServerPlayer player, String service) {
        return DailySelection.seed(player.getServer().overworld().getSeed(), player.getUUID(), data(player).day(), service);
    }
    private VillageServices() {}
}
