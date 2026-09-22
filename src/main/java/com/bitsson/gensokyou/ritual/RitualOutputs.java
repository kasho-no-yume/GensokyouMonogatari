package com.bitsson.gensokyou.ritual;

import com.bitsson.gensokyou.config.GensokyouConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * 仪式产物统一空投落点：核心上方第 1 格、水平半径 {@code RITUAL_OUTPUT_DROP_RADIUS}
 * （默认 3）的圆盘内均匀随机。落点列在核心上 1 格处被实心方块占用时在圆盘内重掷
 * （至多 {@link #MAX_RETRIES} 次），仍失败则回退核心正上方——落点始终位于该区域内。
 *
 * <p>被动配方产物、献祭族与绵津见共用此入口；无遮挡时首次落点即命中，
 * 抽取分布与既有被动产物逐位一致。
 */
public final class RitualOutputs {

    /** 落点被占用时的圆盘内重掷次数上限。 */
    private static final int MAX_RETRIES = 8;

    private RitualOutputs() {
    }

    /** 将一个产物栈以物品实体形式空投到核心上 1 格半径 3 区域内的随机落点。 */
    public static void spawn(ServerLevel level, BlockPos corePos, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        int cellY = corePos.getY() + 1;
        double radius = GensokyouConfig.RITUAL_OUTPUT_DROP_RADIUS.get();
        double x = corePos.getX() + 0.5D;
        double z = corePos.getZ() + 0.5D;
        boolean found = false;
        for (int attempt = 0; attempt < MAX_RETRIES && !found; attempt++) {
            double angle = level.random.nextDouble() * Math.PI * 2D;
            double r = radius * Math.sqrt(level.random.nextDouble());
            double cx = corePos.getX() + 0.5D + r * Math.cos(angle);
            double cz = corePos.getZ() + 0.5D + r * Math.sin(angle);
            if (passable(level, new BlockPos(Mth.floor(cx), cellY, Mth.floor(cz)))) {
                x = cx;
                z = cz;
                found = true;
            }
        }
        ItemEntity drop = new ItemEntity(level, x, corePos.getY() + 1.25D, z, stack);
        drop.setDeltaMovement(0D, 0D, 0D);
        drop.setDefaultPickUpDelay();
        level.addFreshEntity(drop);
    }

    private static boolean passable(ServerLevel level, BlockPos cell) {
        if (!level.isLoaded(cell)) {
            return false;
        }
        BlockState state = level.getBlockState(cell);
        return state.getCollisionShape(level, cell, CollisionContext.empty()).isEmpty();
    }
}
