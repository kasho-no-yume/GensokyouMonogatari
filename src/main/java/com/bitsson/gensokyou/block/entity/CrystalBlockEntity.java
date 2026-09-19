package com.bitsson.gensokyou.block.entity;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.registry.ModBlockEntities;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * 无尽藏晶方块实体：类型 → 长计数存储。
 *
 * <p>物品池为 {@link Entry} 列表，每个条目 = 类型键（计数恒 1 的 {@link ItemStack}）+ 长计数。
 * <b>一格一种</b>：同种物品恒合并进同一条目，计数可超过堆叠上限。唯一容量不变量是
 * 全局物品总数（config {@code STORAGE_TOTAL_CAPACITY}，默认 2000）。
 *
 * <p>插入路径统一过闸：拒绝能装物品的容器与单件 NBT 序列化超限的物品。
 * 破坏即全灭：{@link #saveToItem} 不写任何内容；{@code getUpdateTag}/{@code getUpdatePacket}
 * 保持空/null，内容绝不自动同步（界面走可见页快照协议）。
 */
public class CrystalBlockEntity extends BlockEntity {

    public static final String TAG_ENTRIES = "Entries";
    private static final String ENTRY_ITEM = "Item";
    private static final String ENTRY_COUNT = "Count";
    /** 条目数保险上限（容量已限总量，这里只防御异常存档）。 */
    private static final int MAX_ENTRIES = 8192;

    public static final TagKey<Item> STORAGE_BLACKLIST =
            TagKey.create(Registries.ITEM, Gensokyou.id("storage_blacklist"));

    /** 类型条目：key 计数恒 1，count 为真实存量。 */
    public static final class Entry {
        private final ItemStack key;
        private long count;

        Entry(ItemStack key, long count) {
            this.key = key.copyWithCount(1);
            this.count = count;
        }

        public ItemStack key() {
            return key;
        }

        public long count() {
            return count;
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private IItemHandler handler;
    /** 变更代数：每次物品池变更自增，供菜单侦测外部（漏斗/管道）改动与重推页快照。 */
    private long revision;

    public CrystalBlockEntity(net.minecraft.core.BlockPos pos, BlockState state) {
        super(ModBlockEntities.CRYSTAL.get(), pos, state);
    }

    // ---- 容量与查询 ----

    public int capacity() {
        return GensokyouConfig.STORAGE_TOTAL_CAPACITY.get();
    }

    public int entryCount() {
        return entries.size();
    }

    public long totalCount() {
        long total = 0L;
        for (Entry entry : entries) {
            total += entry.count;
        }
        return total;
    }

    public List<Entry> entries() {
        return entries;
    }

    public ItemStack icon(int index) {
        return index >= 0 && index < entries.size() ? entries.get(index).key() : ItemStack.EMPTY;
    }

    public long revision() {
        return revision;
    }

    /** 按类型键定位条目索引；未找到返回 -1。 */
    public int findEntry(ItemStack key) {
        if (key.isEmpty()) {
            return -1;
        }
        for (int i = 0; i < entries.size(); i++) {
            if (ItemStack.isSameItemSameComponents(entries.get(i).key, key)) {
                return i;
            }
        }
        return -1;
    }

    // ---- 拒收闸 ----

    public static boolean isRejected(ItemStack stack, HolderLookup.Provider registries) {
        return rejectionKey(stack, registries) != null;
    }

    /** 拒收原因的 lang 键；可收纳返回 null。 */
    public static String rejectionKey(ItemStack stack, HolderLookup.Provider registries) {
        if (stack.isEmpty()) {
            return null;
        }
        if (stack.has(DataComponents.CONTAINER) || stack.has(DataComponents.BUNDLE_CONTENTS)
                || stack.is(Items.BUNDLE) || isShulkerBox(stack)
                || stack.getCapability(Capabilities.ItemHandler.ITEM) != null
                || stack.is(STORAGE_BLACKLIST) || isConfigBlacklisted(stack)) {
            return "msg.gensokyou.crystal_rejected_container";
        }
        int limit = GensokyouConfig.STORAGE_ITEM_NBT_LIMIT_BYTES.get();
        if (limit > 0 && registries != null
                && stack.copyWithCount(1).save(registries).sizeInBytes() > limit) {
            return "msg.gensokyou.crystal_rejected_too_large";
        }
        return null;
    }

    private static boolean isShulkerBox(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof ShulkerBoxBlock;
    }

    private static boolean isConfigBlacklisted(ItemStack stack) {
        List<? extends String> list = GensokyouConfig.STORAGE_BLACKLIST.get();
        if (list.isEmpty()) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && list.contains(id.toString());
    }

    public boolean canInsert(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (isRejected(stack, level == null ? null : level.registryAccess())) {
            return false;
        }
        return totalCount() < capacity();
    }

    // ---- 变更原语 ----

    /** 插入（同种恒合并、异种建条），返回实际收纳数量；调用方保留未收纳部分。 */
    public int insert(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        HolderLookup.Provider registries = level == null ? null : level.registryAccess();
        if (isRejected(stack, registries)) {
            return 0;
        }
        long space = capacity() - totalCount();
        if (space <= 0) {
            return 0;
        }
        int budget = (int) Math.min(space, stack.getCount());
        if (budget <= 0) {
            return 0;
        }
        for (Entry entry : entries) {
            if (ItemStack.isSameItemSameComponents(entry.key, stack)) {
                entry.count += budget;
                setChanged();
                revision++;
                return budget;
            }
        }
        if (entries.size() >= MAX_ENTRIES) {
            return 0;
        }
        entries.add(new Entry(stack, budget));
        setChanged();
        revision++;
        return budget;
    }

    /** 从条目扣减至多 amount 件，返回不超过堆叠上限的真实栈；扣空即删条。 */
    public ItemStack removeFromEntry(int index, int amount) {
        if (index < 0 || index >= entries.size() || amount <= 0) {
            return ItemStack.EMPTY;
        }
        Entry entry = entries.get(index);
        int take = (int) Math.min(Math.min((long) amount, entry.count), entry.key.getMaxStackSize());
        if (take <= 0) {
            return ItemStack.EMPTY;
        }
        entry.count -= take;
        if (entry.count <= 0) {
            entries.remove(index);
        }
        setChanged();
        revision++;
        return entry.key.copyWithCount(take);
    }

    // ---- 对外能力 ----

    public IItemHandler itemHandler() {
        if (handler == null) {
            handler = new Handler();
        }
        return handler;
    }

    /** 动态条目箱：槽数=条目数；代表栈计数取 min(真实计数, 堆叠上限)；插入忽略槽号并背压。 */
    private final class Handler implements IItemHandler {

        @Override
        public int getSlots() {
            return entries.size();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot < 0 || slot >= entries.size()) {
                return ItemStack.EMPTY;
            }
            Entry entry = entries.get(slot);
            return entry.key.copyWithCount((int) Math.min(entry.count, entry.key.getMaxStackSize()));
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            int accepted = simulate ? simulatedInsert(stack) : insert(stack);
            if (accepted <= 0) {
                return stack;
            }
            if (accepted >= stack.getCount()) {
                return ItemStack.EMPTY;
            }
            return stack.copyWithCount(stack.getCount() - accepted);
        }

        private int simulatedInsert(ItemStack stack) {
            if (!canInsert(stack)) {
                return 0;
            }
            long space = capacity() - totalCount();
            return (int) Math.min(space, stack.getCount());
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < 0 || slot >= entries.size() || amount <= 0) {
                return ItemStack.EMPTY;
            }
            Entry entry = entries.get(slot);
            int take = (int) Math.min(Math.min((long) amount, entry.count), entry.key.getMaxStackSize());
            if (take <= 0) {
                return ItemStack.EMPTY;
            }
            if (simulate) {
                return entry.key.copyWithCount(take);
            }
            return removeFromEntry(slot, take);
        }

        @Override
        public int getSlotLimit(int slot) {
            if (slot < 0 || slot >= entries.size()) {
                return 64;
            }
            return entries.get(slot).key.getMaxStackSize();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return canInsert(stack);
        }
    }

    // ---- 持久化 ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag list = new ListTag();
        for (Entry entry : entries) {
            if (entry.count <= 0 || entry.key.isEmpty()) {
                continue;
            }
            CompoundTag entryTag = new CompoundTag();
            entryTag.put(ENTRY_ITEM, entry.key.save(registries));
            entryTag.putLong(ENTRY_COUNT, entry.count);
            list.add(entryTag);
        }
        if (!list.isEmpty()) {
            tag.put(TAG_ENTRIES, list);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        entries.clear();
        if (tag.contains(TAG_ENTRIES, Tag.TAG_LIST)) {
            ListTag list = tag.getList(TAG_ENTRIES, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size() && entries.size() < MAX_ENTRIES; i++) {
                CompoundTag entryTag = list.getCompound(i);
                long count = entryTag.getLong(ENTRY_COUNT);
                if (count <= 0) {
                    continue;
                }
                ItemStack.parse(registries, entryTag.getCompound(ENTRY_ITEM))
                        .filter(stack -> !stack.isEmpty())
                        .ifPresent(key -> entries.add(new Entry(key, count)));
            }
        }
        revision++;
    }

    /** 破坏即全灭：物品形态永不带内容（堵中键选取/任何 saveToItem 复制路径）。 */
    @Override
    public void saveToItem(ItemStack stack, HolderLookup.Provider registries) {
        // 有意不写任何内容。
    }
}
