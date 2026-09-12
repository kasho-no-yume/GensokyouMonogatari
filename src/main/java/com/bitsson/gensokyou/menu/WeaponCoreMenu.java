package com.bitsson.gensokyou.menu;

import com.bitsson.gensokyou.item.weapon.AmpCoreItem;
import com.bitsson.gensokyou.item.weapon.BulletCoreItem;
import com.bitsson.gensokyou.item.weapon.WeaponLevelCoreItem;
import com.bitsson.gensokyou.item.weapon.WeaponSlots;
import com.bitsson.gensokyou.item.weapon.WeaponSlotsHelper;
import com.bitsson.gensokyou.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 武器三槽装入菜单（虚拟绑定）：
 * 武器不离手——打开时从 weapon_slots 组件载入核槽，槽位变更即时写回（服务端权威），
 * 等级校验与连带取核统一走 {@link WeaponSlotsHelper#writeBack}。
 */
public class WeaponCoreMenu extends AbstractContainerMenu {

    private static final int CORE_SLOTS = 3;

    private final Player player;
    private final InteractionHand hand;
    /** 双端均为玩家手上那把武器的活引用（服务端权威写入目标）。 */
    private final ItemStack weapon;
    private final Container coreContainer;
    private boolean syncing;

    public WeaponCoreMenu(int windowId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(windowId, inventory, InteractionHand.values()[buf.readByte()]);
    }

    public WeaponCoreMenu(int windowId, Inventory inventory, InteractionHand hand) {
        super(ModMenus.WEAPON_CORE.get(), windowId);
        this.player = inventory.player;
        this.hand = hand;
        this.weapon = inventory.player.getItemInHand(hand);
        this.coreContainer = new SimpleContainer(CORE_SLOTS) {
            @Override
            public void setChanged() {
                super.setChanged();
                WeaponCoreMenu.this.onCoreSlotsChanged();
            }
        };

        if (!inventory.player.level().isClientSide) {
            // 初始载入期间屏蔽写回：逐槽 setItem 会触发 setChanged，
            // 未载入槽2等级核的中间态会把槽1/3误判超等级而弹回
            this.syncing = true;
            try {
                WeaponSlots slots = WeaponSlotsHelper.read(this.weapon);
                for (int i = 0; i < CORE_SLOTS; i++) {
                    ItemStack stack = slots.slot(i);
                    this.coreContainer.setItem(i, stack == null ? ItemStack.EMPTY : stack.copy());
                }
            } finally {
                this.syncing = false;
            }
        }

        for (int i = 0; i < CORE_SLOTS; i++) {
            final int coreIndex = i;
            // 槽位嵌在背景大枪的对应部位上：枪托 / 机匣核心仓 / 枪管中段
            this.addSlot(new Slot(this.coreContainer, i, 34 + i * 28, 22) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return WeaponCoreMenu.this.mayPlaceCore(coreIndex, stack);
                }
            });
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inventory, 9 + row * 9 + col, 8 + col * 18, 66 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inventory, col, 8 + col * 18, 124));
        }
    }

    private boolean mayPlaceCore(int coreIndex, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        boolean typeOk = switch (coreIndex) {
            case 0 -> stack.getItem() instanceof BulletCoreItem;
            case 1 -> stack.getItem() instanceof WeaponLevelCoreItem;
            default -> stack.getItem() instanceof AmpCoreItem;
        };
        if (!typeOk) {
            return false;
        }
        return WeaponSlotsHelper.canFit(this.weapon, stack);
    }

    /** 即时写回：任一核槽变更即把三槽写回武器并执行等级回落校验。 */
    private void onCoreSlotsChanged() {
        if (this.syncing || this.player.level().isClientSide || !(this.player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (!stillValid(this.player)) {
            return;
        }
        this.syncing = true;
        try {
            WeaponSlots current = new WeaponSlots(
                    this.coreContainer.getItem(0), this.coreContainer.getItem(1), this.coreContainer.getItem(2));
            WeaponSlots adjusted = WeaponSlotsHelper.writeBack(serverPlayer, this.weapon, current);
            for (int i = 0; i < CORE_SLOTS; i++) {
                ItemStack expected = adjusted.slot(i);
                if (!ItemStack.matches(this.coreContainer.getItem(i), expected)) {
                    this.coreContainer.setItem(i, expected.isEmpty() ? ItemStack.EMPTY : expected.copy());
                }
            }
        } finally {
            this.syncing = false;
        }
    }

    public ItemStack weapon() {
        return this.weapon;
    }

    public InteractionHand hand() {
        return this.hand;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < CORE_SLOTS) {
            if (!this.moveItemStackTo(stack, CORE_SLOTS, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            boolean moved = false;
            for (int core = 0; core < CORE_SLOTS && !stack.isEmpty(); core++) {
                Slot target = this.slots.get(core);
                if (target.mayPlace(stack) && this.moveItemStackTo(stack, core, core + 1, false)) {
                    moved = true;
                    break;
                }
            }
            if (!moved) {
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
        return stack;
    }

    @Override
    public boolean stillValid(Player player) {
        // 虚拟绑定：手上的武器栈引用必须未变（死亡/丢弃/换手即失效）
        return player.isAlive()
                && !this.weapon.isEmpty()
                && player.getItemInHand(this.hand) == this.weapon;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player.level().isClientSide) {
            return;
        }
        // 已即时写回武器时容器余物是镜像，直接清空；
        // 武器失效（写回被跳过）则余核返还玩家，避免丢失
        boolean weaponStillInHand = player.getItemInHand(this.hand) == this.weapon && !this.weapon.isEmpty();
        for (int i = 0; i < CORE_SLOTS; i++) {
            ItemStack left = this.coreContainer.removeItemNoUpdate(i);
            if (left.isEmpty()) {
                continue;
            }
            if (weaponStillInHand && player instanceof ServerPlayer serverPlayer) {
                continue;
            }
            if (player instanceof ServerPlayer serverPlayer) {
                WeaponSlotsHelper.giveOrDrop(serverPlayer, left);
            }
        }
    }
}
