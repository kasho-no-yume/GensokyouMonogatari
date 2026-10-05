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
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

/**
 * 全 mod 唯一的主仪式方块。不同仪式由多方块结构（RitualPattern）区分，
 * 行为经 patternId 分发到 RitualBehavior。
 *
 * 点击分发规则：
 * - 结构不匹配或结构无注册行为（如召唤环）→ 返回 PASS，把交互让给手中物品
 *   （这样催化剂等物品的 useOn 才有机会执行）
 * - 结构有行为 → 行为结果即为最终结果
 *
 * 品阶视觉：tier 属性（0-5）随仪式等级（结构内仪式石最高品阶）由
 * RitualCoreBlockEntity 服务端写入，驱动 blockstate 切换 ritual_core_0..5 模型；
 * 祭品台同拍写入同一 tier 值（见 RitualPedestalBlock）。
 */
public class RitualCoreBlock extends Block implements EntityBlock {

    /** 仪式等级驱动的品阶视觉属性。 */
    public static final IntegerProperty TIER = IntegerProperty.create("tier", 0, 5);

    public RitualCoreBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(TIER, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TIER);
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
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof RitualCoreBlockEntity core) {
            if (level instanceof ServerLevel serverLevel
                    && core.activeMatch() != null
                    && com.bitsson.gensokyou.ritual.RitualBehaviors.WUJINZANG
                            .equals(core.activeMatch().patternId())) {
                com.bitsson.gensokyou.ritual.behavior.WujinzangStorage
                        .releaseForceLoads(core, serverLevel);
            }
            // 结界破坏：核心被挖 → 两扇门（含幻想乡侧孪生门）走同一条关门路径。
            // 幂等，且经由行为侧派生孪生门坐标，避免核心 BE 上另存一份会漂移的坐标。
            if (level instanceof ServerLevel serverLevel
                    && core.activeMatch() != null
                    && com.bitsson.gensokyou.ritual.RitualBehaviors.BARRIER_BREAK
                            .equals(core.activeMatch().patternId())) {
                com.bitsson.gensokyou.ritual.behavior.BarrierBreakBehavior
                        .removePortals(serverLevel, pos, core);
            }
            ItemStack battery = core.batteryStack();
            if (!battery.isEmpty()) {
                core.setBatteryStack(ItemStack.EMPTY);
                level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level,
                        pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, battery));
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /**
     * 红石上升沿：邻居信号 0→>0 跳变时回调当前行为。
     *
     * <p>三条优先级：
     * <ol>
     *   <li>行为自行覆写了 {@code onRedstonePulse} → 只调它（覆写优先）；</li>
     *   <li>否则 {@code redstoneTriggersUiAction()} 为真 → 代管触发其首个可用操作
     *       （"能手动点按钮的仪式也能用红石控制"的通用口径）；</li>
     *   <li>否则不响应。</li>
     * </ol>
     * 常亮保持与下降沿不触发；标志持久化，防卸载重载后常亮信号误触一次。
     */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos,
                                   Block neighborBlock, BlockPos neighborPos,
                                   boolean movedByPiston) {
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel)
                || !(level.getBlockEntity(pos) instanceof RitualCoreBlockEntity core)) {
            return;
        }
        boolean powered = level.hasNeighborSignal(pos);
        if (powered == core.wasPowered()) {
            return;
        }
        core.setWasPowered(powered);
        if (powered && core.activeMatch() != null) {
            RitualMatch match = core.activeMatch();
            RitualBehaviors.get(match.patternId())
                    .ifPresent(behavior -> dispatchRedstoneRise(serverLevel, pos, match, core, behavior));
        }
    }

    /**
     * 红石上升沿的分派（包级可见以便单测锁定优先级）。
     *
     * @param player 恒为 {@code null}：红石触发时玩家不在场
     */
    public static void dispatchRedstoneRise(ServerLevel level, BlockPos corePos, RitualMatch match,
                                        RitualCoreBlockEntity core, RitualBehavior behavior) {
        if (declaresOwnRedstoneHandler(behavior)) {
            behavior.onRedstonePulse(level, corePos, match, core);
            return;
        }
        if (!behavior.redstoneTriggersUiAction()) {
            return;
        }
        for (RitualBehavior.UiAction action : behavior.uiActions(level, corePos, match, core, null)) {
            if (action.enabled()) {
                behavior.onUiAction(level, corePos, match, core, null, action.id());
                return;
            }
        }
    }

    /**
     * 行为是否自行覆写了 {@code onRedstonePulse}。
     *
     * <p>用反射判定"声明类"而非"当前值"：源初造化既覆写了该方法又想保留默认触发时，
     * 只有前者能区分二者。缓存结果避免每次红石变化都反射。
     */
    public static boolean declaresOwnRedstoneHandler(RitualBehavior behavior) {
        return OWN_REDSTONE_HANDLERS.computeIfAbsent(behavior.getClass(), type -> {
            try {
                return type.getMethod("onRedstonePulse", ServerLevel.class, BlockPos.class,
                                RitualMatch.class, RitualCoreBlockEntity.class)
                        .getDeclaringClass() != RitualBehavior.class;
            } catch (NoSuchMethodException exception) {
                return false;
            }
        });
    }

    private static final Map<Class<?>, Boolean> OWN_REDSTONE_HANDLERS = new ConcurrentHashMap<>();

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            // 非潜行：成型即开 UI；例外——手持仪式构建器时让位物品链路（升级分发归杖侧）；
            // 例外——手持编辑杖时恒让位（纯锚定，成型与否皆不开 GUI）；未成型提示后让位物品链
            if (!player.isShiftKeyDown()) {
                if (stack.getItem() instanceof com.bitsson.gensokyou.item.RitualWandItem) {
                    return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
                }
                if (stack.getItem() instanceof com.bitsson.gensokyou.item.RitualBuilderItem
                        && RitualMatcher.matchAt(level, pos).isPresent()) {
                    return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
                }
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
                // 编辑杖：纯锚定，恒让位物品链路（与 useItemOn 守卫成对，防补调抢回 GUI）
                if (holdsWand(player)) {
                    return InteractionResult.PASS;
                }
                // 主手空但任一手持构建器：PASS 后 vanilla 落到副手物品的 useOn（升级分发归杖侧）
                if (holdsBuilder(player) && RitualMatcher.matchAt(level, pos).isPresent()) {
                    return InteractionResult.PASS;
                }
                return openOrHint(serverPlayer, (ServerLevel) level, pos, player)
                        ? InteractionResult.SUCCESS
                        : InteractionResult.PASS;
            }
            return dispatchUse((ServerLevel) level, pos, player, ItemStack.EMPTY);
        }
        return InteractionResult.PASS;
    }

    /** 成型 → 打开 UI 返回 true；未成型 → 提示并返回 false（玩家手持构建器时静默让位，由其 useOn 接手）。 */
    private boolean openOrHint(net.minecraft.server.level.ServerPlayer serverPlayer,
                               ServerLevel level, BlockPos pos, Player player) {
        var match = RitualMatcher.matchAt(level, pos);
        if (match.isEmpty()) {
            if (!holdsBuilder(player)) {
                serverPlayer.displayClientMessage(
                        Component.translatable("msg.gensokyou.ritual_incomplete"), true);
            }
            return false;
        }
        RitualMatch ritualMatch = match.get();
        boolean terminal = RitualBehaviors.get(ritualMatch.patternId())
                .map(behavior -> com.bitsson.gensokyou.Gensokyou.id("wujinzang_terminal")
                        .equals(behavior.screenMenuId()))
                .orElse(false);
        if (terminal) {
            serverPlayer.openMenu(new net.minecraft.world.SimpleMenuProvider(
                            (id, inventory, p) -> new com.bitsson.gensokyou.menu.WujinzangTerminalMenu(
                                    id, inventory, pos),
                            Component.translatable("container.gensokyou.wujinzang_terminal")),
                    pos);
        } else {
            serverPlayer.openMenu(new net.minecraft.world.SimpleMenuProvider(
                            (id, inventory, p) -> new RitualCoreMenu(id, inventory, pos),
                            Component.translatable("container.gensokyou.ritual_core")),
                    pos);
        }
        ModNetworking.sendRitualInfo(serverPlayer, level, pos, core(level, pos), "");
        return true;
    }

    /** 玩家任一主/副手是否持有仪式构建器（用于抑制"结构不完整"提示，交给构建器接手）。 */
    private static boolean holdsBuilder(Player player) {
        return player.getItemInHand(InteractionHand.MAIN_HAND)
                       .getItem() instanceof com.bitsson.gensokyou.item.RitualBuilderItem
                || player.getItemInHand(InteractionHand.OFF_HAND)
                       .getItem() instanceof com.bitsson.gensokyou.item.RitualBuilderItem;
    }

    /** 玩家任一手是否持编辑杖（成型核心右键只锚定、不开界面）。 */
    private static boolean holdsWand(Player player) {
        return player.getItemInHand(InteractionHand.MAIN_HAND)
                       .getItem() instanceof com.bitsson.gensokyou.item.RitualWandItem
                || player.getItemInHand(InteractionHand.OFF_HAND)
                       .getItem() instanceof com.bitsson.gensokyou.item.RitualWandItem;
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
