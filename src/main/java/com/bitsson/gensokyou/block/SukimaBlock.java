package com.bitsson.gensokyou.block;

import com.bitsson.gensokyou.Gensokyou;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * 隙间——东方式传送门。实体接触即传送：
 * 主世界 → 幻想乡 (0, 地表, 0)；幻想乡 → 主世界出生点。
 */
public class SukimaBlock extends Block {

    public SukimaBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!(level instanceof ServerLevel serverLevel)
                || !(entity instanceof ServerPlayer player)
                || entity.isOnPortalCooldown()) {
            return;
        }
        ServerLevel target = playerInGensokyo(player)
                ? serverLevel.getServer().getLevel(Level.OVERWORLD)
                : serverLevel.getServer().getLevel(
                        ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                                Gensokyou.id("gensokyo")));
        if (target == null) {
            return;
        }
        BlockPos landing = target.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, BlockPos.ZERO);
        player.teleportTo(target, landing.getX() + 0.5D, landing.getY(),
                landing.getZ() + 0.5D, player.getYRot(), player.getXRot());
        entity.setPortalCooldown();
    }

    private boolean playerInGensokyo(ServerPlayer player) {
        return player.level().dimension().location().equals(Gensokyou.id("gensokyo"));
    }
}
