package com.bitsson.gensokyou.block;

import com.bitsson.gensokyou.block.entity.RitualPedestalBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 祭品台：单一注册方块，`tier`（0-5）BlockState 属性驱动变体模型。
 * 放置恒 tier=0 灰；仪式成型后由核心重扫写入当前仪式等级，与核心同色。
 * 属性变化不改方块身份——BE 与台面物品无损（见 onRemove 的 is 判定）。
 */
public class RitualPedestalBlock extends Block implements EntityBlock {

    /** 仪式等级驱动的品阶视觉属性（与 RitualCoreBlock.TIER 同源同值）。 */
    public static final IntegerProperty TIER = IntegerProperty.create("tier", 0, 5);

    public RitualPedestalBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(TIER, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TIER);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RitualPedestalBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hitResult) {
        if (!level.isClientSide) {
            // 注意：必须传真身引用，split/shrink 才能作用到玩家手上
            handle(state, level, pos, player, stack);
        }
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (!level.isClientSide) {
            handle(state, level, pos, player, ItemStack.EMPTY);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * 统一交互语义（与手部状态/路由无关）：
     * 空手或潜行 → 取出；持物且台面为空 → 放入；其余情况回显。
     */
    private void handle(BlockState state, Level level, BlockPos pos,
                        Player player, ItemStack stack) {
        if (!(level.getBlockEntity(pos) instanceof RitualPedestalBlockEntity pedestal)) {
            return;
        }
        boolean wantTake = stack.isEmpty() || player.isShiftKeyDown();
        if (wantTake) {
            takeFor(player, level, pedestal);
            return;
        }
        if (!pedestal.getHeld().isEmpty()) {
            takeFor(player, level, pedestal);
            return;
        }
        // 创造/无限材料模式：放置复制体不扣手上数量（与服务端背包同步语义一致）
        ItemStack single = player.hasInfiniteMaterials()
                ? stack.copyWithCount(1)
                : stack.split(1);
        pedestal.setHeld(single);
    }

    private void takeFor(Player player, Level level, RitualPedestalBlockEntity pedestal) {
        ItemStack held = pedestal.takeHeld();
        if (!held.isEmpty() && !player.getInventory().add(held)) {
            level.addFreshEntity(new ItemEntity(level,
                    player.getX(), player.getY() + 0.5D, player.getZ(), held));
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof RitualPedestalBlockEntity pedestal) {
            ItemStack held = pedestal.takeHeld();
            if (!held.isEmpty()) {
                level.addFreshEntity(new ItemEntity(level,
                        pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, held));
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
