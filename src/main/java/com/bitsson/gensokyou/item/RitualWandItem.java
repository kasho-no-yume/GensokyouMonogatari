package com.bitsson.gensokyou.item;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.ritual.RitualCapture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 仪式构造仗：左键方块设角点 A，右键方块设角点 B（两点确立即捕获），
 * 潜行右键清除选择。捕获结果为稀疏偏移格式 rituals JSON 骨架，输出至日志。
 */
public class RitualWandItem extends Item {

    private static final Map<UUID, BlockPos> FIRST_CORNER = new ConcurrentHashMap<>();

    public RitualWandItem(Properties properties) {
        super(properties);
    }

    /** 左键：设定/重设角点 A。 */
    public static void selectCornerA(ServerLevel level, ServerPlayer player, BlockPos pos) {
        FIRST_CORNER.put(player.getUUID(), pos.immutable());
        player.displayClientMessage(
                Component.translatable("msg.gensokyou.wand_corner_a", pos.toShortString()), true);
    }

    /** 右键：潜行=清除；否则设定角点 B 并在 A 已存在时执行捕获。 */
    public static void selectCornerB(ServerLevel level, ServerPlayer player, BlockPos pos) {
        UUID id = player.getUUID();
        if (player.isShiftKeyDown()) {
            if (FIRST_CORNER.remove(id) != null) {
                player.displayClientMessage(
                        Component.translatable("msg.gensokyou.wand_cleared"), true);
            }
            return;
        }
        BlockPos first = FIRST_CORNER.remove(id);
        if (first == null) {
            player.displayClientMessage(
                    Component.translatable("msg.gensokyou.wand_need_first"), true);
            return;
        }
        int limit = GensokyouConfig.WAND_MAX_DIMENSION.get();
        int dx = Math.abs(first.getX() - pos.getX()) + 1;
        int dy = Math.abs(first.getY() - pos.getY()) + 1;
        int dz = Math.abs(first.getZ() - pos.getZ()) + 1;
        if (dx > limit || dy > limit || dz > limit) {
            player.displayClientMessage(
                    Component.translatable("msg.gensokyou.wand_too_large", limit), true);
            return;
        }
        BlockPos min = new BlockPos(Math.min(first.getX(), pos.getX()),
                Math.min(first.getY(), pos.getY()), Math.min(first.getZ(), pos.getZ()));
        BlockPos max = new BlockPos(Math.max(first.getX(), pos.getX()),
                Math.max(first.getY(), pos.getY()), Math.max(first.getZ(), pos.getZ()));
        String name = "wand_" + level.getGameTime();
        RitualCapture.Result result = RitualCapture.capture(level, name, min, max);
        Gensokyou.LOGGER.info("[ritual wand capture: {}]\n{}", name, result.json());
        java.nio.file.Path file = saveCapture(name, result.json());
        player.displayClientMessage(Component.translatable("msg.gensokyou.wand_captured",
                result.blocksCaptured(), result.violations().size()), false);
        if (file != null) {
            player.displayClientMessage(
                    Component.translatable("msg.gensokyou.wand_saved", file.toString()), false);
        }
        for (String violation : result.violations()) {
            player.displayClientMessage(Component.literal("§c" + violation), false);
        }
    }

    /** 骨架落盘：<游戏目录>/ritual_captures/<名称>.json，核对后手动入库 rituals 目录。 */
    @Nullable
    private static java.nio.file.Path saveCapture(String name, String json) {
        try {
            java.nio.file.Path dir = net.neoforged.fml.loading.FMLPaths.GAMEDIR.get()
                    .resolve("ritual_captures");
            java.nio.file.Files.createDirectories(dir);
            java.nio.file.Path file = dir.resolve(name + ".json");
            java.nio.file.Files.writeString(file, json);
            return file;
        } catch (Exception exception) {
            Gensokyou.LOGGER.warn("Failed to save ritual capture {}: {}", name, exception.getMessage());
            return null;
        }
    }

    /** 玩家登出时清理选择状态。 */
    public static void clearSelection(UUID playerId) {
        FIRST_CORNER.remove(playerId);
    }
}
