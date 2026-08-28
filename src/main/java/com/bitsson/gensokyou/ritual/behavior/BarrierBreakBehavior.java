package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.registry.ModBlocks;
import com.bitsson.gensokyou.ritual.RitualBehavior;
import com.bitsson.gensokyou.ritual.RitualMatch;
import com.bitsson.gensokyou.spirit.ModAttachments;
import com.bitsson.gensokyou.spirit.SpiritPowerHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 结界引爆：经 UI 启动按钮激活（一次性灵力消耗在 onStart 收取）。
 * 激活后隙间方块由本行为维持——结构在则存（免费重建），结构破则消。
 */
public class BarrierBreakBehavior implements RitualBehavior {

    @Override
    public InteractionResult onStart(ServerLevel level, BlockPos corePos, RitualMatch match,
                                     RitualCoreBlockEntity core, ServerPlayer player) {
        double cost = GensokyouConfig.BARRIER_SP_COST.get();
        var data = ModAttachments.get(player);
        float paidFromPlayer = Math.min(data.current(), (float) cost);
        float remaining = (float) cost - paidFromPlayer;
        if (remaining > 0F && !SpiritPowerHelper.hasCapacitorAround(level, player.blockPosition(), 3)) {
            player.displayClientMessage(
                    Component.translatable("msg.gensokyou.temper_no_power", (int) cost), true);
            return InteractionResult.FAIL;
        }
        ModAttachments.set(player, data.withCurrent(data.current() - paidFromPlayer));
        if (remaining > 0F) {
            float drained = SpiritPowerHelper.drainCapacitorsAround(
                    level, player.blockPosition(), 3, remaining);
            if (drained < remaining - 0.01F) {
                ModAttachments.set(player,
                        ModAttachments.get(player).withAddedCurrent(remaining - drained));
                player.displayClientMessage(
                        Component.translatable("msg.gensokyou.temper_no_power", (int) cost), true);
                return InteractionResult.FAIL;
            }
        }
        placePortal(level, core.portalPos() != null ? core.portalPos() : corePos.above(), core);
        player.displayClientMessage(
                Component.translatable("msg.gensokyou.barrier_opened"), false);
        return InteractionResult.SUCCESS;
    }

    private void placePortal(ServerLevel level, BlockPos portalPos, RitualCoreBlockEntity core) {
        level.setBlock(portalPos, ModBlocks.SUKIMA.get().defaultBlockState(), 3);
        core.setPortalPos(portalPos.immutable());
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos corePos, RitualMatch match,
                           RitualCoreBlockEntity core) {
        BlockPos portalPos = core.portalPos() != null ? core.portalPos() : corePos.above();
        BlockState state = level.getBlockState(portalPos);
        boolean present = state.is(ModBlocks.SUKIMA.get());
        if (present) {
            return;
        }
        // 结构有效但隙间缺失 → 免费重建
        placePortal(level, portalPos, core);
    }

    @Override
    public void onStructureLost(ServerLevel level, BlockPos corePos) {
        // 结构失效 → 移除隙间（隙间只在启动后存在；此时 enabled 已被核心先行停机）
        if (level.getBlockEntity(corePos) instanceof RitualCoreBlockEntity core
                && core.portalPos() != null) {
            BlockPos portalPos = core.portalPos();
            if (level.getBlockState(portalPos).is(ModBlocks.SUKIMA.get())) {
                level.removeBlock(portalPos, false);
            }
        }
    }
}
