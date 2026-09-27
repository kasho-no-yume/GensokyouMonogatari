package com.bitsson.gensokyou.block;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.block.entity.SukimaBlockEntity;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import javax.annotation.Nullable;

/**
 * 隙间——东方式传送门。实体接触即传送：
 * 主世界 → 幻想乡 (0, 地表, 0)；幻想乡 → 主世界出生点。
 * 视觉为眼形棱壳传送门，由 {@code SukimaPortalRenderer}（BER）绘制。
 */
public class SukimaBlock extends Block implements EntityBlock {

    public SukimaBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SukimaBlockEntity(pos, state);
    }

    /**
     * 服务端 ticker：推进开合动画并在关门流程结束时让门体自删。
     *
     * <p>状态变化时才广播（{@code syncIfChanged} 自带 diff），故稳态零包。
     */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide || type != ModBlockEntities.SUKIMA.get()) {
            return null;
        }
        return (lvl, pos, blkState, be) -> {
            if (be instanceof SukimaBlockEntity portal) {
                portal.serverTick(GensokyouConfig.SUKIMA_PORTAL_OPEN_TICKS.get());
                // 必须无条件同步：syncIfChanged() 内部已用 lastSent* 四个字段做差分，
                // 外面再套一层「serverTick 返回 true 才同步」是重复门控，且会吞掉**最后一次**同步——
                // 演出跑满那一刻 fxTicks 从 199 变 200，serverTick 此后恒返回 false，
                // 客户端便永远停在中间值（如实测的 16），既不播完也不触发补播，屏幕上什么都不出现。
                portal.syncIfChanged();
            }
        };
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
