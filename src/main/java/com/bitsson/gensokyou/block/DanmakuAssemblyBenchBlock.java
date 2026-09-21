package com.bitsson.gensokyou.block;

import com.bitsson.gensokyou.block.entity.DanmakuAssemblyBenchBlockEntity;
import com.bitsson.gensokyou.menu.DanmakuAssemblyBenchMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 弹幕方术装配台：主武器模块化修改的方块化入口。
 *
 * <p>右击恒开界面（潜行无特殊语义），并抢占手持物品的 {@code useOn}，避免手持武器右击时误开火、
 * 手持方块时误放置。武器取放全部在界面内完成（见 {@link DanmakuAssemblyBenchMenu}）。
 */
public class DanmakuAssemblyBenchBlock extends Block implements EntityBlock {

    public DanmakuAssemblyBenchBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DanmakuAssemblyBenchBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        open(level, pos, player);
        // 恒吞交互：手持物品的 useOn 不执行（手持武器不会开火、手持方块不会放置）
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        open(level, pos, player);
        return InteractionResult.SUCCESS;
    }

    private void open(Level level, BlockPos pos, Player player) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new SimpleMenuProvider(
                    (id, inventory, p) -> new DanmakuAssemblyBenchMenu(id, inventory, pos),
                    Component.translatable("container.gensokyou.danmaku_assembly_bench")), pos);
        }
    }

    /** 破坏/移除时释放台内武器（核随武器数据组件一并带出）。 */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())
                && !level.isClientSide
                && level.getBlockEntity(pos) instanceof DanmakuAssemblyBenchBlockEntity bench) {
            bench.dropContents();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
