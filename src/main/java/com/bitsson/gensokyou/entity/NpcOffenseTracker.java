package com.bitsson.gensokyou.entity;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.spirit.ModAttachments;
import com.bitsson.gensokyou.spirit.NpcOffenseData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

/** 玩家恶意致死 NPC 的计数与三振逐出（design D3）。 */
public final class NpcOffenseTracker {

    private NpcOffenseTracker() {
    }

    /** 仅「将死或真死」且可归因玩家时调用；非致死攻击不进入此处。 */
    public static void recordOffense(ServerPlayer player) {
        NpcOffenseData data = player.getData(ModAttachments.NPC_OFFENSE.get());
        int threshold = GensokyouConfig.NPC_KICK_THRESHOLD.get();
        int count = data.count() + 1;
        if (count < threshold) {
            player.setData(ModAttachments.NPC_OFFENSE.get(), data.withCount(count));
            player.displayClientMessage(Component
                    .translatable("msg.gensokyou.npc_warning", count, threshold)
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        player.setData(ModAttachments.NPC_OFFENSE.get(), data.withCount(0));
        if (player.level().dimension().location().equals(Gensokyou.id("gensokyo"))) {
            kickToOverworld(player);
            player.getServer().getPlayerList().broadcastSystemMessage(
                    Component.translatable("chat.gensokyou.npc_exiled", player.getDisplayName())
                            .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        }
    }

    /** 逐出：主世界 x/z ∈ [min,max] 均匀随机，y 固定（默认 500，坠落计入惩罚）。 */
    private static void kickToOverworld(ServerPlayer player) {
        if (player.getServer() == null) {
            return;
        }
        ServerLevel overworld = player.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return;
        }
        int min = GensokyouConfig.NPC_KICK_XZ_MIN.get();
        int max = Math.max(min + 1, GensokyouConfig.NPC_KICK_XZ_MAX.get());
        RandomSource random = RandomSource.create();
        double x = min + random.nextDouble() * (max - min);
        double z = min + random.nextDouble() * (max - min);
        double y = GensokyouConfig.NPC_KICK_Y.get();
        player.teleportTo(overworld, x + 0.5D, y, z + 0.5D, player.getYRot(), player.getXRot());
        player.displayClientMessage(Component
                .translatable("msg.gensokyou.npc_exiled")
                .withStyle(ChatFormatting.DARK_RED), false);
    }
}
