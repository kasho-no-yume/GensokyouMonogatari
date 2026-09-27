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
     * 服务端 ticker：推进开合动画、在爆炸那一 tick 触发音效与击退，并在关门流程结束时让门体自删。
     *
     * <p>{@code syncIfChanged} <b>无条件调用</b>：它内部自带 diff，而
     * {@code serverTick} 的返回值代表的是"另一件事"（本 tick 是否推进了状态）。
     * 两者语义不同，<b>不能用前者的返回值去 gate 后者</b>——历史上正是这么写的，
     * 结果最后一次状态变化（关门倒计时归零那一 tick）永远发不出去。
     *
     * <p>演出进度不在 diff 里：客户端用 {@code fxStartGameTime} 锚点自算，故稳态零包。
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
