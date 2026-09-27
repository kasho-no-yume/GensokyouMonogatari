package com.bitsson.gensokyou.menu;

import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import com.bitsson.gensokyou.network.ModNetworking;
import com.bitsson.gensokyou.network.RitualInfoPayload;
import com.bitsson.gensokyou.registry.ModMenus;
import com.bitsson.gensokyou.ritual.RitualBehaviors;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** 仪式菜单：核心位置上下文 + 启停按钮通道 + 玩家物品栏（通用，全仪式可用）。 */
public class RitualCoreMenu extends AbstractContainerMenu {

    /** 按钮 id：0=启动，1=停止；≥10 为各仪式行为注入的自定义操作。 */
    public static final int BUTTON_START = 0;
    public static final int BUTTON_STOP = 1;
    /** 自定义操作 id 基准（行为侧 uiActions 返回的 id + 此值）。 */
    public static final int BUTTON_ACTION_BASE = 100;

    /** 灵力核心输出槽在面板中的位置（面板 176 宽，槽位在信息区头排）。 */
    public static final int BATTERY_SLOT_X = 30;
    public static final int BATTERY_SLOT_Y = 40;
    /** BatterySlot 恒为槽表 index 0，背包槽在其后追加。 */
    private static final int BATTERY_SLOT_INDEX = 0;

    /**
     * 星移之仪的增幅核目标槽（紧邻灵力核心槽右侧）。
     *
     * <p>刻意<b>不</b>放在祭品台上：祭品台一台一件且是配方催化剂的载体，1 阶只有 4 台，
     * 核若占一台就只剩 3 个催化剂位。核有自己的 GUI 槽位，祭品台全部留给催化剂。
     * 显隐由行为的 {@code usesTargetSlot} 决定（与 usesCoreSocket 同一套机制）。
     */
    /**
     * 星移之仪的增幅核目标槽 —— 放在<b>信息区首行</b>，不与灵力核心槽争头部那一行
     * （两个槽并排时，灵力核的右侧标签会横跨到第二个槽上）。
     *
     * <p>显隐由行为的 {@code usesTargetSlot} 决定（与 usesCoreSocket 同一套机制）；
     * 可见时客户端把信息区整体下移一行（见 {@code RitualCoreScreen} 的 infoTop）。
     */
    /**
     * 目标槽的<b>槽坐标</b>（= 物品与命中框原点，物品由原版 {@code renderSlot} 画在此处）。
     * 槽框画在其 −1 处，故 18px 框实际占 x∈[8,26)、y∈[60,78) —— 贴着信息框内壁
     * （框自身在 x=7 的分隔线上），物品自然内缩 1px 居中。
     */
    public static final int TARGET_SLOT_X = 9;
    public static final int TARGET_SLOT_Y = 61;
    /**
     * 目标槽的 <b>menu 索引</b>（= addSlot 的调用序）。注意与 {@link #TARGET_HANDLER_INDEX} 区分：
     * {@code SlotItemHandler} 的构造参数是 handler <b>内部</b>索引，不是 menu 索引。两者相等纯属巧合
     * （电池槽恰好都是 0）；传错会让客户端在收 {@code ContainerSetContent} 时抛
     * "Slot N not in valid range" 并被踢。
     */
    private static final int TARGET_SLOT_INDEX = 1;
    /** 目标 handler 只有 1 格，故其内部索引恒为 0。 */
    private static final int TARGET_HANDLER_INDEX = 0;
    /** 核心功能槽（灵力核 + 目标物）区间的右开界，背包从 SLOT_FUNCTION_END 起。 */
    private static final int SLOT_FUNCTION_END = TARGET_SLOT_INDEX + 1;

    /** 背包区起始 y：信息区 150px + 4px 间隔。 */
    private static final int INVENTORY_TOP_Y = 158;

    private final BlockPos pos;
    private final DataSlot burnRemaining;
    private final DataSlot burnTotal;
    private final BatterySlot batterySlot;
    private final TargetSlot targetSlot;
    private final boolean clientSide;

    public RitualCoreMenu(int windowId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(windowId, inventory, data.readBlockPos());
    }

    public RitualCoreMenu(int windowId, Inventory inventory, BlockPos pos) {
        this(windowId, inventory, inventory.player, pos);
    }

    private RitualCoreMenu(int windowId, Inventory inventory, Player player, BlockPos pos) {
        super(ModMenus.RITUAL_CORE.get(), windowId);
        this.pos = pos.immutable();
        // 电池槽与燃烧倒计时通道：服务端直读核心 BE，客户端持占位、值随菜单协议收敛
        if (player.level() instanceof ServerLevel serverLevel
                && serverLevel.getBlockEntity(this.pos) instanceof RitualCoreBlockEntity core) {
            this.clientSide = false;
            addSlot(new BatterySlot(core.batteryHandler(), BATTERY_SLOT_INDEX,
                    BATTERY_SLOT_X, BATTERY_SLOT_Y));
            this.batterySlot = (BatterySlot) this.slots.get(BATTERY_SLOT_INDEX);
            addSlot(new TargetSlot(core.seiiTargetHandler(), TARGET_HANDLER_INDEX,
                    TARGET_SLOT_X, TARGET_SLOT_Y));
            this.targetSlot = (TargetSlot) this.slots.get(TARGET_SLOT_INDEX);
            // 槽显隐由行为声明（默认关闭，仅星移之仪这类"核是洗练目标"的仪式开启）；
            // 隐藏槽 mayPlace 同步拒收；槽内已有核时强制可见——结构拆解失配也要能取出（防吞件）
            this.targetSlot.setShown(!core.seiiTargetStack().isEmpty()
                    || (core.activeMatch() != null
                            && RitualBehaviors.get(core.activeMatch().patternId())
                                    .map(com.bitsson.gensokyou.ritual.RitualBehavior::usesTargetSlot)
                                    .orElse(false)));
            // 槽显隐由行为声明（默认开放，仅路由/托管仪式豁免）；隐藏槽 mayPlace 同步拒收；
            // 槽内已有电池时强制可见——结构拆解失配也要能取出（防吞件）
            this.batterySlot.setShown(!core.batteryStack().isEmpty()
                    || (core.activeMatch() != null
                            && RitualBehaviors.get(core.activeMatch().patternId())
                                    .map(com.bitsson.gensokyou.ritual.RitualBehavior::usesCoreSocket)
                                    .orElse(true)));
            this.burnRemaining = addDataSlot(new DataSlot() {
                @Override
                public int get() {
                    return core.burnRemainingTicks();
                }

                @Override
                public void set(int value) {
                }
            });
            this.burnTotal = addDataSlot(new DataSlot() {
                @Override
                public int get() {
                    return core.burnTotalTicks();
                }

                @Override
                public void set(int value) {
                }
            });
        } else {
            this.clientSide = true;
            net.neoforged.neoforge.items.ItemStackHandler dummy =
                    new net.neoforged.neoforge.items.ItemStackHandler(1) {
                        @Override
                        public boolean isItemValid(int slot, net.minecraft.world.item.ItemStack stack) {
                            return stack.getItem() instanceof com.bitsson.gensokyou.spirit.SpiritCoreItem;
                        }
                    };
            addSlot(new BatterySlot(dummy, BATTERY_SLOT_INDEX, BATTERY_SLOT_X, BATTERY_SLOT_Y));
            this.batterySlot = (BatterySlot) this.slots.get(BATTERY_SLOT_INDEX);
            // 首帧即隐藏：显隐唯一由 containerTick 按服务端 payload 收敛（与启停按钮同范式）
            this.batterySlot.setShown(false);
            net.neoforged.neoforge.items.ItemStackHandler dummyTarget =
                    new net.neoforged.neoforge.items.ItemStackHandler(1) {
                        @Override
                        public boolean isItemValid(int slot, net.minecraft.world.item.ItemStack stack) {
                            return stack.getItem()
                                    instanceof com.bitsson.gensokyou.item.weapon.AmpCoreItem;
                        }
                    };
            addSlot(new TargetSlot(dummyTarget, TARGET_HANDLER_INDEX, TARGET_SLOT_X, TARGET_SLOT_Y));
            this.targetSlot = (TargetSlot) this.slots.get(TARGET_SLOT_INDEX);
            this.targetSlot.setShown(false);
            this.burnRemaining = addDataSlot(DataSlot.standalone());
            this.burnTotal = addDataSlot(DataSlot.standalone());
        }
        // 玩家物品栏：两分支之后统一追加，客户端/服务端槽序一致
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inventory, 9 + row * 9 + col,
                        8 + col * 18, INVENTORY_TOP_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inventory, col, 8 + col * 18, INVENTORY_TOP_Y + 56));
        }
    }

    public int burnRemaining() {
        return burnRemaining.get();
    }

    /** 仅客户端：灵力核心槽显隐随服务端 payload 收敛（见 RitualCoreScreen）。
     *  declaredByBehavior=false 但槽内仍有电池时保持可见，防结构拆解失配吞件。 */
    public void syncCoreSocketVisible(boolean declaredByBehavior) {
        if (clientSide) {
            batterySlot.setShown(declaredByBehavior || !batterySlot.getItem().isEmpty());
        }
    }

    /** 灵力核心槽当前是否可见（客户端渲染标注用）。 */
    public boolean coreSocketShown() {
        return batterySlot.isActive();
    }

    /**
     * 目标物品槽显隐收敛（客户端由 containerTick 按服务端 payload 驱动）。
     * 槽内仍有物时保持可见，防结构拆解失配吞件。
     */
    public void syncTargetSocketVisible(boolean declaredByBehavior) {
        if (clientSide) {
            targetSlot.setShown(declaredByBehavior || !targetSlot.getItem().isEmpty());
        }
    }

    /** 目标物品槽当前是否可见（客户端渲染标注用）。 */
    public boolean targetSocketShown() {
        return targetSlot.isActive();
    }

    public int burnTotal() {
        return burnTotal.get();
    }

    public BlockPos pos() {
        return pos;
    }

    /** shift 转移：背包 → 核心功能槽（灵力核 0 / 目标物 1，类型校验经 mayPlace 收敛）→ 兜底在背包内堆叠；功能槽 → 背包。 */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index == BATTERY_SLOT_INDEX || index == TARGET_SLOT_INDEX) {
            if (!this.moveItemStackTo(stack, SLOT_FUNCTION_END, this.slots.size(), false)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stack, BATTERY_SLOT_INDEX, SLOT_FUNCTION_END, false)) {
            if (!this.moveItemStackTo(stack, this.slots.size() - 9, this.slots.size(), false)) {
                if (!this.moveItemStackTo(stack, SLOT_FUNCTION_END,
                        this.slots.size() - 9, true)) {
                    return ItemStack.EMPTY;
                }
            }
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return stack.getCount() == original.getCount() ? ItemStack.EMPTY : stack;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(pos) instanceof RitualCoreBlockEntity
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer serverPlayer)
                || !(player.level() instanceof ServerLevel serverLevel)
                || !(serverLevel.getBlockEntity(pos) instanceof RitualCoreBlockEntity core)) {
            return false;
        }
        switch (id) {
            case BUTTON_START -> {
                boolean started = core.start(serverPlayer);
                ModNetworking.sendRitualInfo(serverPlayer, serverLevel, pos, core,
                        started ? "msg.gensokyou.ritual_started" : "msg.gensokyou.ritual_start_failed");
                return started;
            }
            case BUTTON_STOP -> {
                core.stop();
                ModNetworking.sendRitualInfo(serverPlayer, serverLevel, pos, core,
                        "msg.gensokyou.ritual_stopped");
                return true;
            }
            default -> {
                if (id >= BUTTON_ACTION_BASE
                        && RitualBehaviors.get(core.activeMatch() != null
                                ? core.activeMatch().patternId() : null)
                        .map(behavior -> behavior.onUiAction(serverLevel, pos,
                                core.activeMatch(), core, serverPlayer, id - BUTTON_ACTION_BASE))
                        .orElse(InteractionResult.PASS) == InteractionResult.SUCCESS) {
                    ModNetworking.sendRitualInfo(serverPlayer, serverLevel, pos, core, "");
                    return true;
                }
                return false;
            }
        }
    }
}
