package com.bitsson.gensokyou.menu;

import com.bitsson.gensokyou.block.entity.TreasureBasinBlockEntity;
import com.bitsson.gensokyou.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * 聚宝盆界面：3×3 容器槽 + 玩家背包/快捷栏。
 *
 * <p>容器本身是方块实体里的 {@link ItemStackHandler}，客户端通过菜单的槽位同步读取，
 * 不需要额外的方块实体数据包（{@code fill} 走方块状态同步）。
 */
public class TreasureBasinMenu extends AbstractContainerMenu {

    public static final int CONTAINER_SLOTS = 9;
    /** 3×3 左上角槽坐标（与 {@code TreasureBasinScreen} 的底图一致）。 */
    public static final int SLOT_X = 62;
    public static final int SLOT_Y = 17;

    private static final int PLAYER_INV_START = CONTAINER_SLOTS;
    private static final int HOTBAR_START = CONTAINER_SLOTS + 27;
    private static final int TOTAL_SLOTS = CONTAINER_SLOTS + 36;

    private final Player player;
    private final BlockPos pos;

    public TreasureBasinMenu(int windowId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(windowId, inventory, buf.readBlockPos());
    }

    public TreasureBasinMenu(int windowId, Inventory inventory, BlockPos pos) {
        super(ModMenus.TREASURE_BASIN.get(), windowId);
        this.player = inventory.player;
        this.pos = pos.immutable();

        IItemHandler handler = inventory.player.level()
                .getBlockEntity(pos) instanceof TreasureBasinBlockEntity basin
                ? basin.itemHandler()
                : new ItemStackHandler(CONTAINER_SLOTS);

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                this.addSlot(new SlotItemHandler(handler, col + row * 3,
                        SLOT_X + col * 18, SLOT_Y + row * 18));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inventory, 9 + row * 9 + col,
                        8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inventory, col, 8 + col * 18, 142));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index < CONTAINER_SLOTS) {
            // 盆 → 背包
            if (!this.moveItemStackTo(stack, PLAYER_INV_START, TOTAL_SLOTS, true)) {
                return ItemStack.EMPTY;
            }
        } else if (index < HOTBAR_START) {
            // 主背包 → 盆，放不下再进快捷栏
            if (!this.moveItemStackTo(stack, 0, CONTAINER_SLOTS, false)
                    && !this.moveItemStackTo(stack, HOTBAR_START, TOTAL_SLOTS, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            // 快捷栏 → 盆，放不下再进主背包
            if (!this.moveItemStackTo(stack, 0, CONTAINER_SLOTS, false)
                    && !this.moveItemStackTo(stack, PLAYER_INV_START, HOTBAR_START, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return stack;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(this.pos) instanceof TreasureBasinBlockEntity
                && player.distanceToSqr(this.pos.getX() + 0.5D, this.pos.getY() + 0.5D,
                this.pos.getZ() + 0.5D) <= 64.0D;
    }
}
