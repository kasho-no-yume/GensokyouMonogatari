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

/** 无槽位仪式菜单：承载核心位置上下文与服务端权威的启停按钮通道。 */
public class RitualCoreMenu extends AbstractContainerMenu {

    /** 按钮 id：0=启动，1=停止；≥10 为各仪式行为注入的自定义操作。 */
    public static final int BUTTON_START = 0;
    public static final int BUTTON_STOP = 1;
    /** 自定义操作 id 基准（行为侧 uiActions 返回的 id + 此值）。 */
    public static final int BUTTON_ACTION_BASE = 100;

    /** 灵力核心输出槽在面板中的位置（相对界面左上角）。 */
    public static final int BATTERY_SLOT_X = 174;
    public static final int BATTERY_SLOT_Y = 44;

    private final BlockPos pos;
    private final DataSlot burnRemaining;
    private final DataSlot burnTotal;

    public RitualCoreMenu(int windowId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(windowId, inventory.player, data.readBlockPos());
    }

    public RitualCoreMenu(int windowId, Inventory inventory, BlockPos pos) {
        this(windowId, inventory.player, pos);
    }

    public RitualCoreMenu(int windowId, Player player, BlockPos pos) {
        super(ModMenus.RITUAL_CORE.get(), windowId);
        this.pos = pos.immutable();
        // 电池槽与燃烧倒计时通道：服务端直读核心 BE，客户端持占位、值随菜单协议收敛
        if (player.level() instanceof ServerLevel serverLevel
                && serverLevel.getBlockEntity(this.pos) instanceof RitualCoreBlockEntity core) {
            addSlot(new BatterySlot(core.batteryHandler(), 0, BATTERY_SLOT_X, BATTERY_SLOT_Y));
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
            net.neoforged.neoforge.items.ItemStackHandler dummy =
                    new net.neoforged.neoforge.items.ItemStackHandler(1) {
                        @Override
                        public boolean isItemValid(int slot, net.minecraft.world.item.ItemStack stack) {
                            return stack.getItem() instanceof com.bitsson.gensokyou.spirit.SpiritCoreItem;
                        }
                    };
            addSlot(new BatterySlot(dummy, 0, BATTERY_SLOT_X, BATTERY_SLOT_Y));
            this.burnRemaining = addDataSlot(DataSlot.standalone());
            this.burnTotal = addDataSlot(DataSlot.standalone());
        }
    }

    public int burnRemaining() {
        return burnRemaining.get();
    }

    public int burnTotal() {
        return burnTotal.get();
    }

    public BlockPos pos() {
        return pos;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
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
