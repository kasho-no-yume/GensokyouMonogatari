package com.bitsson.gensokyou.block.entity;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
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
 * 无尽藏晶方块实体：双模式「类型 → 长计数」存储。
 *
 * <p><b>A 模式（TYPED，新块默认）</b>：类型数上限 {@code STORAGE_MAX_TYPES}（默认 30），
 * 每类型计数上限 {@code STORAGE_PER_TYPE_CAP}（默认 {@link Integer#MAX_VALUE}）；一格一种，同种恒合并。
 * <b>B 模式（TOTAL，旧档回退）</b>：全局总件数上限 {@code STORAGE_TOTAL_CAPACITY}（默认 2000），种类不限。
 * 模式持久化于 {@code Mode} 字段；旧存档缺失该字段时按 B 模式读取。
 *
 * <p>插入路径统一过闸：拒绝能装物品的容器与单件 NBT 序列化超限的物品。
 * 不可破坏（方块属性）：{@link #saveToItem} 不写任何内容，{@link #getUpdateTag}/{@link #getUpdatePacket}
 * 保持空/null，内容绝不自动同步（界面走可见页快照协议）。
 *
 * <p><b>托管预留</b>：{@link #exportContents()} / {@link #importContents(CompoundTag, boolean)} 与
 * {@link #setBinding} 绑定字段，供后续无尽藏仪式上缴/下发；当前仅提供能力，无任何自动逻辑。
 */
public class CrystalBlockEntity extends BlockEntity {

    public static final String TAG_ENTRIES = "Entries";
    private static final String ENTRY_ITEM = "Item";
    private static final String ENTRY_COUNT = "Count";
    private static final String TAG_MODE = "Mode";
    private static final String TAG_OWNER_DIM = "OwnerDim";
    private static final String TAG_OWNER_POS = "OwnerPos";
    private static final String TAG_SEGMENT = "Segment";

    public static final TagKey<Item> STORAGE_BLACKLIST =
            TagKey.create(Registries.ITEM, Gensokyou.id("storage_blacklist"));

    /** 存储模式：TYPED=类型制（有限类型 × 每类 long）；TOTAL=总量制（总量预算 × 不限种类）。 */
    public enum Mode {
        TYPED, TOTAL;

        /** 按名解析（忽略大小写）；未知/空返回 fallback。 */
        public static Mode byName(String name, Mode fallback) {
            if (name != null) {
                for (Mode value : values()) {
                    if (value.name().equalsIgnoreCase(name)) {
                        return value;
                    }
                }
            }
            return fallback;
        }
    }

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
    /** 新放置/仪式生成的晶块默认类型制；旧档无字段时在 loadAdditional 回退为 TOTAL。 */
    private Mode mode = Mode.TYPED;

    // ---- 托管预留绑定字段（当前只存不读）----
    private String ownerDim;
    private long ownerPos = Long.MIN_VALUE;
    private int segment = -1;

    public CrystalBlockEntity(net.minecraft.core.BlockPos pos, BlockState state) {
        super(ModBlockEntities.CRYSTAL.get(), pos, state);
    }

    // ---- 模式与容量 ----

    public Mode mode() {
        return mode;
    }

    public int maxTypes() {
        return GensokyouConfig.STORAGE_MAX_TYPES.get();
    }

    public int totalCapacity() {
        return GensokyouConfig.STORAGE_TOTAL_CAPACITY.get();
    }

    public long perTypeCapacity() {
        return GensokyouConfig.STORAGE_PER_TYPE_CAP.get();
    }

    /** 条目数硬上限：类型制=类型上限；总量制=总量上限（每类至少 1 件）。 */
    private int maxEntries() {
        return mode == Mode.TYPED ? maxTypes() : totalCapacity();
    }

    /** 原子设置模式；clearContents=true 时清空全部条目（调试命令与将来仪式共用）。 */
    public void setMode(Mode newMode, boolean clearContents) {
        boolean changed = this.mode != newMode || clearContents;
        this.mode = newMode;
        if (clearContents) {
            entries.clear();
        }
        if (changed) {
            setChanged();
            revision++;
        }
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

    // ---- 托管预留绑定（当前只存不读）----

    /** 记录该 cell 归属的核心与段位（供后续仪式绑定；当前无自动消费方）。 */
    public void setBinding(String dimension, BlockPos owner, int segmentIndex) {
        this.ownerDim = dimension;
        this.ownerPos = owner == null ? Long.MIN_VALUE : owner.asLong();
        this.segment = segmentIndex;
        setChanged();
    }

    public String ownerDim() {
        return ownerDim;
    }

    public boolean hasOwner() {
        return ownerPos != Long.MIN_VALUE;
    }

    public BlockPos ownerPos() {
        return hasOwner() ? BlockPos.of(ownerPos) : null;
    }

    public int segment() {
        return segment;
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
        if (mode == Mode.TYPED) {
            if (findEntry(stack) >= 0) {
                return true;
            }
            return entries.size() < maxTypes();
        }
        return totalCount() < totalCapacity();
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
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (ItemStack.isSameItemSameComponents(entry.key, stack)) {
                int budget = (int) Math.min(spaceForExisting(entry), stack.getCount());
                if (budget <= 0) {
                    return 0;
                }
                entry.count += budget;
                setChanged();
                revision++;
                return budget;
            }
        }
        if (mode == Mode.TYPED && entries.size() >= maxTypes()) {
            return 0;
        }
        if (entries.size() >= maxEntries()) {
            return 0;
        }
        int budget = (int) Math.min(spaceForNewEntry(), stack.getCount());
        if (budget <= 0) {
            return 0;
        }
        entries.add(new Entry(stack, budget));
        setChanged();
        revision++;
        return budget;
    }

    /** 已有条目还能再收多少（按模式）。 */
    private long spaceForExisting(Entry entry) {
        if (mode == Mode.TYPED) {
            return saturatingSub(perTypeCapacity(), entry.count);
        }
        return Math.max(0L, (long) totalCapacity() - totalCount());
    }

    /** 新建条目还能收多少（按模式）。 */
    private long spaceForNewEntry() {
        if (mode == Mode.TYPED) {
            return perTypeCapacity();
        }
        return Math.max(0L, (long) totalCapacity() - totalCount());
    }

    private static long saturatingSub(long a, long b) {
        return a > b ? a - b : 0L;
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

    // ---- 托管预留：导出 / 导入 ----

    /** 导出该晶块的完整内容（模式 + 条目）；供仪式上缴或迁移。 */
    public CompoundTag exportContents() {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_MODE, mode.name());
        HolderLookup.Provider registries = level == null ? null : level.registryAccess();
        if (registries != null) {
            tag.put(TAG_ENTRIES, writeEntries(registries));
        }
        return tag;
    }

    /** 导入内容；merge=false 先清空，再按同种归并写入（携带模式）。 */
    public void importContents(CompoundTag tag, boolean merge) {
        if (!merge) {
            entries.clear();
        }
        mode = Mode.byName(tag.getString(TAG_MODE), mode);
        readEntries(tag, level == null ? null : level.registryAccess(), true);
        setChanged();
        revision++;
    }

    // ---- 对外能力 ----

    public IItemHandler itemHandler() {
        if (handler == null) {
            handler = new Handler();
        }
        return handler;
    }

    /** 动态条目箱：槽数=条目数；代表栈计数取 min(真实计数, 堆叠上限)；插入忽略槽号并按模式背压。 */
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
            if (stack.isEmpty()) {
                return 0;
            }
            HolderLookup.Provider registries = level == null ? null : level.registryAccess();
            if (isRejected(stack, registries)) {
                return 0;
            }
            for (Entry entry : entries) {
                if (ItemStack.isSameItemSameComponents(entry.key, stack)) {
                    return (int) Math.min(spaceForExisting(entry), stack.getCount());
                }
            }
            if (mode == Mode.TYPED && entries.size() >= maxTypes()) {
                return 0;
            }
            if (entries.size() >= maxEntries()) {
                return 0;
            }
            return (int) Math.min(spaceForNewEntry(), stack.getCount());
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

    private ListTag writeEntries(HolderLookup.Provider registries) {
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
        return list;
    }

    /** 读取条目；merge=true 时按同种累加（保留不截断），否则直接追加（受模式条目上限约束）。 */
    private void readEntries(CompoundTag tag, HolderLookup.Provider registries, boolean merge) {
        if (registries == null || !tag.contains(TAG_ENTRIES, Tag.TAG_LIST)) {
            return;
        }
        ListTag list = tag.getList(TAG_ENTRIES, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size() && entries.size() < maxEntries(); i++) {
            CompoundTag entryTag = list.getCompound(i);
            long count = entryTag.getLong(ENTRY_COUNT);
            if (count <= 0) {
                continue;
            }
            ItemStack.parse(registries, entryTag.getCompound(ENTRY_ITEM))
                    .filter(stack -> !stack.isEmpty())
                    .ifPresent(key -> addLoaded(key, count, merge));
        }
    }

    private void addLoaded(ItemStack key, long count, boolean merge) {
        if (merge) {
            for (Entry entry : entries) {
                if (ItemStack.isSameItemSameComponents(entry.key, key)) {
                    entry.count += count;
                    return;
                }
            }
        }
        if (entries.size() >= maxEntries()) {
            return;
        }
        entries.add(new Entry(key, count));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString(TAG_MODE, mode.name());
        ListTag list = writeEntries(registries);
        if (!list.isEmpty()) {
            tag.put(TAG_ENTRIES, list);
        }
        if (ownerDim != null) {
            tag.putString(TAG_OWNER_DIM, ownerDim);
        }
        if (hasOwner()) {
            tag.putLong(TAG_OWNER_POS, ownerPos);
        }
        if (segment >= 0) {
            tag.putInt(TAG_SEGMENT, segment);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        entries.clear();
        // 旧档无 Mode 字段 → 回退总量制 B（保持既有语义），数据不截断。
        mode = Mode.byName(tag.getString(TAG_MODE), Mode.TOTAL);
        readEntries(tag, registries, false);
        ownerDim = tag.contains(TAG_OWNER_DIM) ? tag.getString(TAG_OWNER_DIM) : null;
        ownerPos = tag.contains(TAG_OWNER_POS) ? tag.getLong(TAG_OWNER_POS) : Long.MIN_VALUE;
        segment = tag.contains(TAG_SEGMENT) ? tag.getInt(TAG_SEGMENT) : -1;
        revision++;
    }

    /** 不携带内容（堵中键选取/任何 saveToItem 复制路径）。 */
    @Override
    public void saveToItem(ItemStack stack, HolderLookup.Provider registries) {
        // 有意不写任何内容。
    }
}
