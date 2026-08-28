package com.bitsson.gensokyou.event;

import com.bitsson.gensokyou.Gensokyou;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * 兜底保护：玩家登录时若位于维度底部（如出生点搜索失败的基岩层），
 * 自动挪到该维度的地表高度。幻想乡维度地形精调完成前防止"出生在基岩底下"。
 */
@EventBusSubscriber(modid = Gensokyou.MODID)
public final class SpawnSafetyHandler {

    private static final int BOTTOM_MARGIN = 8;

    private SpawnSafetyHandler() {
    }

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && player.getY() < player.level().getMinBuildHeight() + BOTTOM_MARGIN) {
            relocateToSurface(player);
        }
    }

    /** 玩家通过隙间等途径进入新维度后也可能落在底部，统一在每次维度变更后校验。 */
    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && player.getY() < player.level().getMinBuildHeight() + BOTTOM_MARGIN) {
            relocateToSurface(player);
        }
    }

    private static void relocateToSurface(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos spawn = level.getSharedSpawnPos();
        int x = spawn.getX();
        int z = spawn.getZ();
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        // 共享出生点也无效时，螺旋外扩找几圈
        if (y <= level.getMinBuildHeight() + BOTTOM_MARGIN) {
            outer:
            for (int r = 64; r <= 512; r *= 2) {
                for (int dx = -r; dx <= r; dx += Math.max(16, r / 4)) {
                    for (int dz = -r; dz <= r; dz += Math.max(16, r / 4)) {
                        int ty = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                                x + dx, z + dz);
                        if (ty > level.getMinBuildHeight() + BOTTOM_MARGIN) {
                            x = x + dx;
                            z = z + dz;
                            y = ty;
                            break outer;
                        }
                    }
                }
            }
        }
        player.teleportTo(level, x + 0.5D, y + 1D, z + 0.5D, player.getYRot(), player.getXRot());
        Gensokyou.LOGGER.info("Relocated {} out of the void to ({}, {}, {}) in {}",
                player.getName().getString(), x, y, z, level.dimension().location());
    }
}
