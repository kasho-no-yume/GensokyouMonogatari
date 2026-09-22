package com.bitsson.gensokyou.menu;

import com.bitsson.gensokyou.block.entity.DanmakuAssemblyBenchBlockEntity;
import com.bitsson.gensokyou.item.weapon.AmpCoreItem;
import com.bitsson.gensokyou.item.weapon.BulletCoreItem;
import com.bitsson.gensokyou.item.weapon.DanmakuWeaponItem;
import com.bitsson.gensokyou.item.weapon.WeaponLevelCoreItem;
import com.bitsson.gensokyou.item.weapon.WeaponSlots;
import com.bitsson.gensokyou.item.weapon.WeaponSlotsHelper;
import com.bitsson.gensokyou.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

import javax.annotation.Nullable;

/**
 * 弹幕方术装配台菜单：武器输入槽（独占）+ 三核槽（弹幕核/等级核/增幅核）。
 *
 * <p>武器存放在装配台 BlockEntity 里；三核槽是武器 {@code weapon_slots} 组件的<b>镜像</b>，
 * 任一变更即时经 {@link WeaponSlotsHelper#writeBack} 写回武器（服务端权威），
 * 与旧的「手持虚拟绑定」菜单同构——差别只在武器来源是 BE 而非手部。
 *
 * <p>武器槽内容由 {@link #broadcastChanges()} 轮询比对捕获（不依赖具体 Slot 实现的钩子），
 * 变更即重载/清空镜像；武器被取走时核随武器带出，绝不返还，避免复制。
 */
public class DanmakuAssemblyBenchMenu extends AbstractContainerMenu {

    private static final int CORE_SLOTS = 3;
    /** 槽索引：0 = 武器输入槽，1..3 = 三核槽。 */
    private static final int WEAPON_INDEX = 0;
    private static final int MACHINE_SLOTS = 1 + CORE_SLOTS;

    /** 武器输入槽坐标（与 Screen 绘制一致）。 */
    public static final int WEAPON_SLOT_X = 8;
    public static final int WEAPON_SLOT_Y = 22;
    /** 三核槽坐标（嵌在背景大枪的枪托/机匣/枪管上）。 */
    public static final int[] CORE_SLOT_XS = {34, 62, 90};
    public static final int CORE_SLOT_Y = 22;

    private final Player player;
    private final BlockPos pos;
    @Nullable
    private final DanmakuAssemblyBenchBlockEntity bench;
    private final IItemHandler weaponHandler;
    private final Container coreContainer;
    private boolean syncing;
    /** 上次广播时观察到的武器栈，用于轮询检测武器槽增删/替换。 */
    private ItemStack lastWeapon = ItemStack.EMPTY;

    public DanmakuAssemblyBenchMenu(int windowId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(windowId, inventory, buf.readBlockPos());
    }

    public DanmakuAssemblyBenchMenu(int windowId, Inventory inventory, BlockPos pos) {
        super(ModMenus.DANMAKU_ASSEMBLY_BENCH.get(), windowId);
        this.player = inventory.player;
        this.pos = pos.immutable();
        DanmakuAssemblyBenchBlockEntity found =
                inventory.player.level().getBlockEntity(pos) instanceof DanmakuAssemblyBenchBlockEntity b ? b : null;
        this.bench = found;
        this.weaponHandler = found != null ? found.inventory() : new ItemStackHandler(1);

        this.addSlot(new SlotItemHandler(weaponHandler, WEAPON_INDEX, WEAPON_SLOT_X, WEAPON_SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof DanmakuWeaponItem;
            }
        });

        this.coreContainer = new SimpleContainer(CORE_SLOTS) {
            @Override
            public void setChanged() {
                super.setChanged();
                DanmakuAssemblyBenchMenu.this.onCoreSlotsChanged();
            }
        };
        for (int i = 0; i < CORE_SLOTS; i++) {
            final int coreIndex = i;
            this.addSlot(new Slot(this.coreContainer, i, CORE_SLOT_XS[i], CORE_SLOT_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return DanmakuAssemblyBenchMenu.this.mayPlaceCore(coreIndex, stack);
                }
            });
        }

        if (!inventory.player.level().isClientSide) {
            this.syncing = true;
            try {
                loadMirror();
            } finally {
                this.syncing = false;
            }
            this.lastWeapon = this.weaponHandler.getStackInSlot(WEAPON_INDEX).copy();
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
        ItemStack weapon = this.weaponHandler.getStackInSlot(WEAPON_INDEX);
        if (!(weapon.getItem() instanceof DanmakuWeaponItem)) {
            return false;
        }
        boolean typeOk = switch (coreIndex) {
            case 0 -> stack.getItem() instanceof BulletCoreItem;
            case 1 -> stack.getItem() instanceof WeaponLevelCoreItem;
            default -> stack.getItem() instanceof AmpCoreItem;
        };
        return typeOk && WeaponSlotsHelper.canFit(weapon, stack);
    }

    /** 从台内武器载入三核槽镜像；无合法武器则清空。 */
    private void loadMirror() {
        for (int i = 0; i < CORE_SLOTS; i++) {
            this.coreContainer.setItem(i, ItemStack.EMPTY);
        }
        ItemStack weapon = this.weaponHandler.getStackInSlot(WEAPON_INDEX);
        if (weapon.getItem() instanceof DanmakuWeaponItem) {
            WeaponSlots slots = WeaponSlotsHelper.read(weapon);
            for (int i = 0; i < CORE_SLOTS; i++) {
                ItemStack stack = slots.slot(i);
                this.coreContainer.setItem(i, stack == null ? ItemStack.EMPTY : stack.copy());
            }
        }
    }

    /** 核槽变更：即时写回武器（服务端权威），并把等级回落弹出的核返还玩家。 */
    private void onCoreSlotsChanged() {
        if (this.syncing || this.player.level().isClientSide
                || !(this.player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        ItemStack weapon = this.weaponHandler.getStackInSlot(WEAPON_INDEX);
        if (!(weapon.getItem() instanceof DanmakuWeaponItem)) {
            return;
        }
        this.syncing = true;
        try {
            WeaponSlots current = new WeaponSlots(
                    this.coreContainer.getItem(0), this.coreContainer.getItem(1), this.coreContainer.getItem(2));
            WeaponSlots adjusted = WeaponSlotsHelper.writeBack(serverPlayer, weapon, current);
            for (int i = 0; i < CORE_SLOTS; i++) {
                ItemStack expected = adjusted.slot(i);
                if (!ItemStack.matches(this.coreContainer.getItem(i), expected)) {
                    this.coreContainer.setItem(i, expected.isEmpty() ? ItemStack.EMPTY : expected.copy());
                }
            }
            if (this.bench != null) {
                this.bench.setChanged();
            }
        } finally {
            this.syncing = false;
        }
    }

    /**
     * 每 tick 广播前轮询武器槽：内容变化（放入/取走/替换）即重载或清空镜像。
     * 不依赖 Slot 实现的回调细节，避免因 SlotItemHandler 的 setChanged 语义差异漏判。
     */
    @Override
    public void broadcastChanges() {
        if (!this.syncing && !this.player.level().isClientSide) {
            ItemStack current = this.weaponHandler.getStackInSlot(WEAPON_INDEX);
            if (!ItemStack.matches(current, this.lastWeapon)) {
                this.lastWeapon = current.copy();
                this.syncing = true;
                try {
                    loadMirror();
                } finally {
                    this.syncing = false;
                }
            }
        }
        super.broadcastChanges();
    }

    /** 台内武器（供界面灰显判定）；客户端为同步后的镜像。 */
    public ItemStack weapon() {
        return this.weaponHandler.getStackInSlot(WEAPON_INDEX);
    }

    /** 该 Slot 是否为三核槽（界面灰显判定用）。 */
    public boolean isCoreSlot(Slot slot) {
        int i = this.slots.indexOf(slot);
        return i >= 1 && i <= CORE_SLOTS;
    }

    /** 核是否可装入当前台内武器（武器缺失或等级不足即不可）。 */
    public boolean coreStackAllowed(ItemStack stack) {
        ItemStack weapon = weapon();
        return weapon.getItem() instanceof DanmakuWeaponItem && WeaponSlotsHelper.canFit(weapon, stack);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < MACHINE_SLOTS) {
            if (!this.moveItemStackTo(stack, MACHINE_SLOTS, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            boolean moved = false;
            for (int i = 0; i < MACHINE_SLOTS && !stack.isEmpty(); i++) {
                Slot target = this.slots.get(i);
                if (target.mayPlace(stack) && this.moveItemStackTo(stack, i, i + 1, false)) {
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
        return stack.getCount() == original.getCount() ? ItemStack.EMPTY : stack;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(this.pos) instanceof DanmakuAssemblyBenchBlockEntity
                && player.distanceToSqr(this.pos.getX() + 0.5, this.pos.getY() + 0.5, this.pos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player.level().isClientSide) {
            return;
        }
        // 镜像已即时写回武器；余物只是镜像，直接清空（不返还，避免复制）
        for (int i = 0; i < CORE_SLOTS; i++) {
            this.coreContainer.removeItemNoUpdate(i);
        }
    }
}
