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
     * 泛化额外物品槽（{@link com.bitsson.gensokyou.ritual.RitualExtraSlots}）的槽原点坐标。
     *
     * <p>槽体由原版 {@code renderSlot} 画在此处，故 18px 见方；坐标与迁移前的"星移目标槽"
     * 完全一致，迁移后玩家看到的核位置不变。
     */
    public static int extraSlotX(int index) {
        return RitualCoreBlockEntity.EXTRA_SLOT_X
                + index * RitualCoreBlockEntity.EXTRA_SLOT_SPACING;
    }

    public static int extraSlotY() {
        return RitualCoreBlockEntity.EXTRA_SLOT_Y;
    }

    /** 缺省布局把全部额外槽排在同一行；重载保留接口的多行自定义可能。 */
    public static int extraSlotY(int index) {
        return RitualCoreBlockEntity.EXTRA_SLOT_Y;
    }

    /** 额外槽 handler 只有 MAX_EXTRA_SLOTS 格，故其内部索引 = menu 索引。 */
    private static int extraHandlerIndex(int index) {
        return index;
    }

    /** 功能槽区（灵力核 + 全部额外槽）的右开界，背包从 SLOT_FUNCTION_END 起。 */
    private static final int SLOT_FUNCTION_END =
            1 + RitualCoreBlockEntity.MAX_EXTRA_SLOTS;

    /** 背包区起始 y：信息区 150px + 4px 间隔。 */
    private static final int INVENTORY_TOP_Y = 158;

    private final BlockPos pos;
    private final DataSlot burnRemaining;
    private final DataSlot burnTotal;
    private final BatterySlot batterySlot;
    private final ExtraSlot[] extraSlots;
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
        this.extraSlots = new ExtraSlot[RitualCoreBlockEntity.MAX_EXTRA_SLOTS];
        if (player.level() instanceof ServerLevel serverLevel
                && serverLevel.getBlockEntity(this.pos) instanceof RitualCoreBlockEntity core) {
            this.clientSide = false;
            addSlot(new BatterySlot(core.batteryHandler(), BATTERY_SLOT_INDEX,
                    BATTERY_SLOT_X, BATTERY_SLOT_Y));
            this.batterySlot = (BatterySlot) this.slots.get(BATTERY_SLOT_INDEX);
            int declared = core.extraSlotCount();
            for (int i = 0; i < RitualCoreBlockEntity.MAX_EXTRA_SLOTS; i++) {
                ExtraSlot slot = new ExtraSlot(core.extraSlotsHandler(), extraHandlerIndex(i),
                        extraSlotX(i), extraSlotY());
                addSlot(slot);
                this.extraSlots[i] = slot;
                // 槽显隐由行为声明（默认 0 格）；隐藏槽 mayPlace 同步拒收；
                // 槽内已有物时强制可见 —— 结构拆解失配也要能取出（防吞件）
                slot.setShown(i < declared || !core.extraSlot(i).isEmpty());
            }
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
            // 客户端 dummy 一律放行：合法性由服务端 IItemHandler 权威拒收
            net.neoforged.neoforge.items.ItemStackHandler dummyExtra =
                    new net.neoforged.neoforge.items.ItemStackHandler(
                            RitualCoreBlockEntity.MAX_EXTRA_SLOTS) {
                        @Override
                        public boolean isItemValid(int slot, net.minecraft.world.item.ItemStack stack) {
                            return true;
                        }
                    };
            for (int i = 0; i < RitualCoreBlockEntity.MAX_EXTRA_SLOTS; i++) {
                ExtraSlot slot = new ExtraSlot(dummyExtra, extraHandlerIndex(i),
                        extraSlotX(i), extraSlotY());
                addSlot(slot);
                this.extraSlots[i] = slot;
                slot.setShown(false);
            }
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
    public void syncExtraSlotsVisible(int declaredCount) {
        if (clientSide) {
            for (int i = 0; i < extraSlots.length; i++) {
                extraSlots[i].setShown(i < declaredCount || !extraSlots[i].getItem().isEmpty());
            }
        }
    }

    /** 当前是否至少有一个额外槽可见（客户端渲染标注与信息区下移用）。 */
    public boolean anyExtraSlotShown() {
        for (ExtraSlot slot : extraSlots) {
            if (slot.isActive()) {
                return true;
            }
        }
        return false;
    }

    /** 当前可见的额外槽数量（客户端画标注用）。 */
    public int extraSlotsShown() {
        int shown = 0;
        for (ExtraSlot slot : extraSlots) {
            if (slot.isActive()) {
                shown++;
            }
        }
        return shown;
    }

    /** 该仪式额外槽的标注 lang 键（无标注时返回空串）。 */
    public static String extraSlotLabelKey(com.bitsson.gensokyou.ritual.RitualBehavior behavior) {
        if (behavior instanceof com.bitsson.gensokyou.ritual.RitualExtraSlots slots) {
            return slots.labelKey();
        }
        return "";
    }

    public int burnTotal() {
        return burnTotal.get();
    }

    public BlockPos pos() {
        return pos;
    }

    /**
     * shift 转移：功能槽（灵力核 + 全部额外槽，menu 索引 {@code [0, SLOT_FUNCTION_END)}）
     * → 背包；背包 → 功能槽（类型校验经各槽 {@code mayPlace} 收敛）→ 兜底在背包内堆叠。
     *
     * <p>功能槽区右开界由 {@link RitualCoreBlockEntity#MAX_EXTRA_SLOTS} 动态算出，
     * MUST NOT 依赖固定索引常量——否则额外槽数量一变，转移区间就会把物品送错槽或漏送。
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < SLOT_FUNCTION_END) {
            if (!this.moveItemStackTo(stack, SLOT_FUNCTION_END, this.slots.size(), false)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stack, 0, SLOT_FUNCTION_END, false)) {
            // MUST NOT 在背包内部再 merge：源槽本身就在该区间里，merge 循环会把源栈与自身
            // 合并（j = count + count），数量翻倍。vanilla 的各类 ContainerMenu 也从不
            // 让快捷移动在同一背包内 merge —— 移不动就直接失败。
            return ItemStack.EMPTY;
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
