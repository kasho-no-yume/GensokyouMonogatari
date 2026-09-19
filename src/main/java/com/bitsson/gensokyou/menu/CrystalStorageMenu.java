package com.bitsson.gensokyou.menu;

import com.bitsson.gensokyou.block.entity.CrystalBlockEntity;
import com.bitsson.gensokyou.network.CrystalStorageClickPayload;
import com.bitsson.gensokyou.network.CrystalStoragePagePayload;
import com.bitsson.gensokyou.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 无尽藏晶菜单：存储区为自绘网格（可承载超堆叠长计数），仅玩家背包/快捷栏保留原版 Slot。
 *
 * <p>服务端持有搜索/滚动/排序状态与过滤排序列，按需向查看者推送可见页快照；
 * 取放经 C2S 手势包在"条目 ↔ 光标/玩家背包"间完成（不占用原版光标承载上限）。
 */
public class CrystalStorageMenu extends AbstractContainerMenu {

    public static final int COLUMNS = 9;
    public static final int ROWS = 6;
    public static final int PAGE_SIZE = COLUMNS * ROWS;
    public static final int GRID_X = 8;
    public static final int GRID_Y = 40;
    public static final int PLAYER_INV_Y = 154;
    public static final int HOTBAR_Y = 214;

    public static final int SORT_COUNT = 0;
    public static final int SORT_NAME = 1;
    public static final int SORT_ID = 2;
    public static final int SORT_MODE_COUNT = 3;

    private final BlockPos pos;
    @Nullable
    private final CrystalBlockEntity be;
    @Nullable
    private final ServerPlayer owner;

    private String query = "";
    private int offset;
    private int sortMode = SORT_COUNT;
    private final List<Integer> filtered = new ArrayList<>();
    private boolean dirty = true;
    private long lastRecomputeRevision = -1L;
    private long sentRevision = -1L;

    public CrystalStorageMenu(int windowId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(windowId, inventory, data.readBlockPos());
    }

    public CrystalStorageMenu(int windowId, Inventory inventory, BlockPos pos) {
        super(ModMenus.CRYSTAL_STORAGE.get(), windowId);
        this.pos = pos.immutable();
        Player player = inventory.player;
        if (player.level() instanceof ServerLevel serverLevel
                && serverLevel.getBlockEntity(this.pos) instanceof CrystalBlockEntity crystal) {
            this.be = crystal;
            this.owner = player instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        } else {
            this.be = null;
            this.owner = null;
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, 9 + row * 9 + col, 8 + col * 18, PLAYER_INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, HOTBAR_Y));
        }
    }

    public BlockPos pos() {
        return pos;
    }

    // ---- 服务端导航与手势 ----

    /** 应用一次导航（搜索 / 滚动 / 排序）并立即重推可见页。 */
    public void applyNav(String newQuery, int scrollDelta, int newSortMode) {
        if (this.be == null) {
            return;
        }
        if (newSortMode >= 0 && newSortMode < SORT_MODE_COUNT && newSortMode != this.sortMode) {
            this.sortMode = newSortMode;
            this.offset = 0;
        }
        if (newQuery != null && !newQuery.equals(this.query)) {
            this.query = newQuery;
            this.offset = 0;
        }
        if (scrollDelta != 0) {
            this.offset = Math.max(0, this.offset + scrollDelta);
        }
        recompute();
        sendPage();
    }

    /** 网格手势分发（服务端权威）。 */
    public void handleClick(ServerPlayer player, int action, ItemStack key) {
        if (this.be == null) {
            return;
        }
        ensureFresh();
        switch (action) {
            case CrystalStorageClickPayload.TAKE_STACK -> takeToCursor(this.be.findEntry(key), 64);
            case CrystalStorageClickPayload.TAKE_HALF -> takeToCursor(this.be.findEntry(key), 32);
            case CrystalStorageClickPayload.TAKE_STACK_INV -> giveToInventory(player, key, 1);
            case CrystalStorageClickPayload.TAKE_ALL_INV -> giveToInventory(player, key, Integer.MAX_VALUE);
            case CrystalStorageClickPayload.PUT_ONE -> putCursor(player, 1);
            case CrystalStorageClickPayload.PUT_ALL -> putCursor(player, Integer.MAX_VALUE);
            default -> {
            }
        }
    }

    private void takeToCursor(int index, int want) {
        if (index < 0 || index >= this.be.entries().size()) {
            return;
        }
        CrystalBlockEntity.Entry entry = this.be.entries().get(index);
        ItemStack carried = getCarried();
        if (!carried.isEmpty() && !ItemStack.isSameItemSameComponents(carried, entry.key())) {
            return;
        }
        int max = entry.key().getMaxStackSize();
        int space = carried.isEmpty() ? max : max - carried.getCount();
        if (space <= 0) {
            return;
        }
        ItemStack taken = this.be.removeFromEntry(index, Math.min(want, space));
        if (taken.isEmpty()) {
            return;
        }
        if (carried.isEmpty()) {
            setCarried(taken);
        } else {
            carried.grow(taken.getCount());
            setCarried(carried);
        }
        broadcastChanges();
    }

    private void giveToInventory(ServerPlayer player, ItemStack key, int maxStacks) {
        int done = 0;
        while (done < maxStacks) {
            int index = this.be.findEntry(key);
            if (index < 0) {
                break;
            }
            ItemStack taken = this.be.removeFromEntry(index, 64);
            if (taken.isEmpty()) {
                break;
            }
            player.getInventory().add(taken);
            if (!taken.isEmpty()) {
                this.be.insert(taken);
                break;
            }
            done++;
        }
        broadcastChanges();
    }

    private void putCursor(ServerPlayer player, int amount) {
        ItemStack carried = getCarried();
        if (carried.isEmpty()) {
            return;
        }
        int put = Math.min(amount, carried.getCount());
        if (put <= 0) {
            return;
        }
        int accepted = this.be.insert(carried.copyWithCount(put));
        if (accepted <= 0) {
            String key = CrystalBlockEntity.rejectionKey(carried, player.level().registryAccess());
            if (key != null) {
                player.displayClientMessage(Component.translatable(key), true);
            }
            return;
        }
        carried.shrink(accepted);
        setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
        broadcastChanges();
    }

    // ---- 过滤 + 排序 ----

    private void ensureFresh() {
        if (this.be == null) {
            return;
        }
        if (this.dirty || this.lastRecomputeRevision != this.be.revision()) {
            recompute();
        }
    }

    private void recompute() {
        this.filtered.clear();
        if (this.be != null) {
            List<CrystalBlockEntity.Entry> entries = this.be.entries();
            String q = this.query.toLowerCase(Locale.ROOT);
            for (int i = 0; i < entries.size(); i++) {
                ItemStack key = entries.get(i).key();
                if (q.isEmpty()
                        || key.getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)) {
                    this.filtered.add(i);
                }
            }
            Comparator<Integer> comparator = switch (this.sortMode) {
                case SORT_NAME -> Comparator.comparing(
                        (Integer i) -> entries.get(i).key().getHoverName().getString(),
                        String.CASE_INSENSITIVE_ORDER);
                case SORT_ID -> Comparator.comparing((Integer i) -> {
                    ResourceLocation id = BuiltInRegistries.ITEM.getKey(entries.get(i).key().getItem());
                    return id == null ? "" : id.toString();
                });
                default -> Comparator.comparingLong(
                        (Integer i) -> entries.get(i).count()).reversed();
            };
            this.filtered.sort(comparator);
            int totalRows = (this.filtered.size() + COLUMNS - 1) / COLUMNS;
            int maxRowStart = Math.max(0, totalRows - ROWS);
            int rowStart = Mth.clamp(this.offset / COLUMNS, 0, maxRowStart);
            this.offset = rowStart * COLUMNS;
        }
        this.dirty = false;
        this.lastRecomputeRevision = this.be == null ? -1L : this.be.revision();
    }

    private CrystalStoragePagePayload snapshot() {
        ensureFresh();
        List<CrystalStoragePagePayload.View> views = new ArrayList<>();
        if (this.be != null) {
            List<CrystalBlockEntity.Entry> entries = this.be.entries();
            for (int i = 0; i < PAGE_SIZE; i++) {
                int k = this.offset + i;
                if (k >= 0 && k < this.filtered.size()) {
                    CrystalBlockEntity.Entry entry = entries.get(this.filtered.get(k));
                    views.add(new CrystalStoragePagePayload.View(entry.key(), entry.count()));
                }
            }
        }
        return new CrystalStoragePagePayload(containerId, this.offset, this.filtered.size(),
                this.be == null ? 0 : (int) this.be.totalCount(),
                this.be == null ? 0 : this.be.capacity(),
                this.sortMode, List.copyOf(views));
    }

    private void sendPage() {
        if (this.owner == null || this.be == null) {
            return;
        }
        PacketDistributor.sendToPlayer(this.owner, snapshot());
        this.sentRevision = this.be.revision();
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (this.be == null || this.owner == null) {
            return;
        }
        if (this.sentRevision != this.be.revision()) {
            sendPage();
        }
    }

    // ---- 原版交互 ----

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem() || this.be == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int accepted = this.be.insert(stack);
        if (accepted <= 0) {
            if (player instanceof ServerPlayer serverPlayer) {
                String key = CrystalBlockEntity.rejectionKey(stack, player.level().registryAccess());
                if (key != null) {
                    serverPlayer.displayClientMessage(Component.translatable(key), true);
                }
            }
            return ItemStack.EMPTY;
        }
        stack.shrink(accepted);
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return stack.getCount() == original.getCount() ? ItemStack.EMPTY : stack;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(this.pos) instanceof CrystalBlockEntity
                && player.distanceToSqr(this.pos.getX() + 0.5, this.pos.getY() + 0.5,
                        this.pos.getZ() + 0.5) <= 64.0;
    }
}
