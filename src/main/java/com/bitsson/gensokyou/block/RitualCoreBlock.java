package com.bitsson.gensokyou.block;

import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import com.bitsson.gensokyou.menu.RitualCoreMenu;
import com.bitsson.gensokyou.network.ModNetworking;
import com.bitsson.gensokyou.registry.ModBlockEntities;
import com.bitsson.gensokyou.ritual.RitualBehavior;
import com.bitsson.gensokyou.ritual.RitualBehaviors;
import com.bitsson.gensokyou.ritual.RitualMatcher;
import com.bitsson.gensokyou.ritual.RitualMatch;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * 全 mod 唯一的主仪式方块。不同仪式由多方块结构（RitualPattern）区分，
 * 行为经 patternId 分发到 RitualBehavior。
 *
 * 点击分发规则：
 * - 结构不匹配或结构无注册行为（如召唤环）→ 返回 PASS，把交互让给手中物品
 *   （这样催化剂等物品的 useOn 才有机会执行）
 * - 结构有行为 → 行为结果即为最终结果
 */
public class RitualCoreBlock extends Block implements EntityBlock {

    public RitualCoreBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RitualCoreBlockEntity(pos, state);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return type == ModBlockEntities.RITUAL_CORE.get()
                ? (BlockEntityTicker<T>) (BlockEntityTicker<RitualCoreBlockEntity>)
                RitualCoreBlockEntity::serverTick
                : null;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            // 非潜行：成型即开 UI（无例外）；未成型提示后让位物品链
            if (!player.isShiftKeyDown()) {
                return openOrHint(serverPlayer, (ServerLevel) level, pos, player)
                        ? ItemInteractionResult.SUCCESS
                        : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            // 潜行：保留旧行为直连链路
            InteractionResult result = dispatchUse((ServerLevel) level, pos, player, stack);
            return switch (result) {
                case SUCCESS -> ItemInteractionResult.SUCCESS;
                case FAIL -> ItemInteractionResult.FAIL;
                default -> ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            };
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            if (!player.isShiftKeyDown()) {
                return openOrHint(serverPlayer, (ServerLevel) level, pos, player)
                        ? InteractionResult.SUCCESS
                        : InteractionResult.PASS;
            }
            return dispatchUse((ServerLevel) level, pos, player, ItemStack.EMPTY);
        }
        return InteractionResult.PASS;
    }

    /** 成型 → 打开 UI 返回 true；未成型 → 提示并返回 false。 */
    private boolean openOrHint(net.minecraft.server.level.ServerPlayer serverPlayer,
                               ServerLevel level, BlockPos pos, Player player) {
        var match = RitualMatcher.matchAt(level, pos);
        if (match.isEmpty()) {
            serverPlayer.displayClientMessage(
                    Component.translatable("msg.gensokyou.ritual_incomplete"), true);
            return false;
        }
        serverPlayer.openMenu(new net.minecraft.world.SimpleMenuProvider(
                        (id, inventory, p) -> new RitualCoreMenu(id, inventory, pos),
                        Component.translatable("container.gensokyou.ritual_core")),
                pos);
        ModNetworking.sendRitualInfo(serverPlayer, level, pos, core(level, pos), "");
        return true;
    }

    private InteractionResult dispatchUse(ServerLevel level, BlockPos pos, Player player,
                                          ItemStack stack) {
        var match = RitualMatcher.matchAt(level, pos);
        if (match.isEmpty()) {
            return InteractionResult.PASS;
        }
        return RitualBehaviors.get(match.get().patternId())
                .map(behavior -> stack.isEmpty()
                        ? behavior.onUseEmptyHand(level, pos, match.get(), core(level, pos), (net.minecraft.server.level.ServerPlayer) player)
                        : behavior.onUseItem(level, pos, match.get(), core(level, pos), (net.minecraft.server.level.ServerPlayer) player, stack))
                .orElse(InteractionResult.PASS);
    }

    private RitualCoreBlockEntity core(Level level, BlockPos pos) {
        return (RitualCoreBlockEntity) level.getBlockEntity(pos);
    }
}
