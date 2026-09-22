package com.bitsson.gensokyou.menu;

import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import com.bitsson.gensokyou.network.CrystalStorageClickPayload;
import com.bitsson.gensokyou.network.CrystalStoragePagePayload;
import com.bitsson.gensokyou.registry.ModMenus;
import com.bitsson.gensokyou.ritual.RitualBehaviors;
import com.bitsson.gensokyou.ritual.RitualMatch;
import com.bitsson.gensokyou.ritual.behavior.WujinzangStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 无尽藏终端菜单（批 1）：左侧通用仪式信息 + 右侧跨 128 晶块的聚合仓储网格 + 玩家物品栏 +
 * 原版 3×3 合成格。存储区为自绘网格（不注册原版 Slot），取放经 C2S 手势在条目↔光标/背包间完成。
 */
public class WujinzangTerminalMenu extends AbstractContainerMenu implements CrystalGridHost {

    public static final int BATTERY_SLOT_X = 8;
    public static final int BATTERY_SLOT_Y = 56;
    public static final int COLUMNS = 9;
    public static final int ROWS = 5;
    public static final int PAGE_SIZE = COLUMNS * ROWS;

    // 布局：左列仪式信息 | 右列（仓库格子 / 合成区 / 物品栏）纵向堆叠
    public static final int LEFT_WIDTH = 122;
    public static final int GRID_X = 128;
    public static final int GRID_Y = 26;
    // 合成区在右列水平居中：内容宽 = 3×18 + 间隔 8 + 结果 18 = 80，列宽 162 → 左缩进 41
    public static final int CRAFT_X = 169;
    public static final int CRAFT_Y = 124;
    public static final int RESULT_X = 231;
    public static final int RESULT_Y = 142;
    public static final int PLAYER_INV_X = 128;
    public static final int PLAYER_INV_Y = 184;
    public static final int HOTBAR_Y = 242;
    public static final int WIDTH = 306;
    public static final int HEIGHT = 264;

    /** 菜单按钮 id：2 = 返还合成格材料（0/1 为框架启停保留）。 */
    public static final int BUTTON_RETURN_CRAFT = 2;

    public static final int SORT_COUNT = 0;
    public static final int SORT_NAME = 1;
    public static final int SORT_ID = 2;
    public static final int SORT_MODE_COUNT = 3;

    private static final int CRAFT_INPUT_COUNT = 9;
    private static final int RESULT_SLOT_INDEX = 9;
    private static final int PLAYER_INV_START = 10;

    private final BlockPos pos;
    @Nullable
    private final RitualCoreBlockEntity core;
    @Nullable
    private final ServerPlayer owner;
    @Nullable
    private final TransientCraftingContainer craftSlots;
    @Nullable
    private final ResultContainer resultSlots;
    private final BatterySlot batterySlot;
    private final int batterySlotIndex;
    private final boolean clientSide;

    private String query = "";
    private int offset;
    private int sortMode = SORT_COUNT;
    private final List<Integer> filtered = new ArrayList<>();
    private boolean dirty = true;
    private long lastRecomputeRevision = -1L;
    private long sentRevision = -1L;
    /** 上次推页时的启动态：停止/启动切换需强制重推（清空或恢复可见页）。 */
    private boolean lastSentEnabled = false;

    public WujinzangTerminalMenu(int windowId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(windowId, inventory, data.readBlockPos());
    }

    public WujinzangTerminalMenu(int windowId, Inventory inventory, BlockPos pos) {
        super(ModMenus.WUJINZANG_TERMINAL.get(), windowId);
        this.pos = pos.immutable();
        Player player = inventory.player;
        ServerLevel serverLevel = player.level() instanceof ServerLevel sl ? sl : null;
        if (serverLevel != null
                && serverLevel.getBlockEntity(this.pos) instanceof RitualCoreBlockEntity c) {
            this.core = c;
            this.owner = player instanceof ServerPlayer sp ? sp : null;
        } else {
            this.core = null;
            this.owner = null;
        }
        // 原版 3×3 合成格 + 结果格（不自动抽料、不写回仓储）；客户端同样构造以保证槽位坐标一致
        this.craftSlots = new TransientCraftingContainer(this, 3, 3);
        this.resultSlots = new ResultContainer();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                addSlot(new Slot(this.craftSlots, col + row * 3,
                        CRAFT_X + col * 18, CRAFT_Y + row * 18));
            }
        }
        addSlot(new TerminalResultSlot(player, this.craftSlots, this.resultSlots,
                RESULT_X, RESULT_Y));
        // 玩家物品栏
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, 9 + row * 9 + col,
                        PLAYER_INV_X + col * 18, PLAYER_INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, PLAYER_INV_X + col * 18, HOTBAR_Y));
        }
        // 灵力核心槽（电池→缓存供能入口）：与通用仪式面板同范式，显隐由屏幕按 payload 收敛
        if (serverLevel != null && this.core != null) {
            this.clientSide = false;
            addSlot(new BatterySlot(this.core.batteryHandler(), 0, BATTERY_SLOT_X, BATTERY_SLOT_Y));
            this.batterySlot = (BatterySlot) this.slots.get(this.slots.size() - 1);
            this.batterySlotIndex = this.slots.size() - 1;
            this.batterySlot.setShown(true);
        } else {
            this.clientSide = true;
            net.neoforged.neoforge.items.ItemStackHandler dummy =
                    new net.neoforged.neoforge.items.ItemStackHandler(1);
            addSlot(new BatterySlot(dummy, 0, BATTERY_SLOT_X, BATTERY_SLOT_Y));
            this.batterySlot = (BatterySlot) this.slots.get(this.slots.size() - 1);
            this.batterySlotIndex = this.slots.size() - 1;
            this.batterySlot.setShown(false);
        }
    }

    /** 客户端：灵力核心槽显隐随服务端 payload 收敛。 */
    public void syncCoreSocketVisible(boolean declaredByBehavior) {
        if (clientSide) {
            batterySlot.setShown(declaredByBehavior || !batterySlot.getItem().isEmpty());
        }
    }

    public boolean coreSocketShown() {
        return batterySlot.isActive();
    }

    public BlockPos pos() {
        return pos;
    }

    @Override
    public int containerId() {
        return containerId;
    }

    @Nullable
    private RitualMatch match() {
        if (core == null || core.activeMatch() == null
                || !RitualBehaviors.WUJINZANG.equals(core.activeMatch().patternId())) {
            return null;
        }
        return core.activeMatch();
    }

    /** 仓储是否开放：核心存在且已启动（停止态下全部经仓储的路径关闭）。 */
    private boolean storageAvailable() {
        return core != null && core.isEnabled();
    }

    @Nullable
    private ServerLevel level() {
        return owner != null ? owner.serverLevel() : null;
    }

    // ---- 服务端导航与手势 ----

    @Override
    public void applyNav(String newQuery, int scrollDelta, int newSortMode) {
        if (owner == null) {
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

    @Override
    public void handleClick(ServerPlayer player, int action, ItemStack key) {
        ServerLevel level = level();
        RitualMatch match = match();
        if (level == null || match == null || !storageAvailable()) {
            return;
        }
        ensureFresh(level, match);
        switch (action) {
            case CrystalStorageClickPayload.TAKE_STACK -> takeToCursor(level, match, indexOfKey(key), 64);
            case CrystalStorageClickPayload.TAKE_HALF -> takeToCursor(level, match, indexOfKey(key), 32);
            case CrystalStorageClickPayload.TAKE_STACK_INV -> giveToInventory(player, level, match, key, 1);
            case CrystalStorageClickPayload.TAKE_ALL_INV -> giveToInventory(player, level, match, key, Integer.MAX_VALUE);
            case CrystalStorageClickPayload.PUT_ONE -> putCursor(level, match, 1);
            case CrystalStorageClickPayload.PUT_ALL -> putCursor(level, match, Integer.MAX_VALUE);
            default -> {
            }
        }
    }

    private int indexOfKey(ItemStack key) {
        if (key.isEmpty()) {
            return -1;
        }
        for (int i = 0; i < filtered.size(); i++) {
            if (ItemStack.isSameItemSameComponents(entries().get(filtered.get(i)).key(), key)) {
                return i;
            }
        }
        return -1;
    }

    private List<WujinzangStorage.Agg> entries() {
        ServerLevel level = level();
        RitualMatch match = match();
        if (level == null || match == null) {
            return List.of();
        }
        return WujinzangStorage.aggregate(level, match);
    }

    private void takeToCursor(ServerLevel level, RitualMatch match, int filteredIndex, int want) {
        if (filteredIndex < 0 || filteredIndex >= filtered.size()) {
            return;
        }
        ItemStack key = null;
        List<WujinzangStorage.Agg> agg = WujinzangStorage.aggregate(level, match);
        int aggIndex = filtered.get(filteredIndex);
        if (aggIndex < 0 || aggIndex >= agg.size()) {
            return;
        }
        key = agg.get(aggIndex).key();
        ItemStack carried = getCarried();
        if (!carried.isEmpty() && !ItemStack.isSameItemSameComponents(carried, key)) {
            return;
        }
        int max = key.getMaxStackSize();
        int space = carried.isEmpty() ? max : max - carried.getCount();
        if (space <= 0) {
            return;
        }
        ItemStack taken = WujinzangStorage.extract(level, match, key, Math.min(want, space));
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

    private void giveToInventory(ServerPlayer player, ServerLevel level, RitualMatch match,
                                 ItemStack key, int maxStacks) {
        int done = 0;
        while (done < maxStacks) {
            ItemStack taken = WujinzangStorage.extract(level, match, key, 64);
            if (taken.isEmpty()) {
                break;
            }
            player.getInventory().add(taken);
            if (!taken.isEmpty()) {
                WujinzangStorage.insert(level, match, taken, core == null ? null : core.wujinzangVault());
                break;
            }
            done++;
        }
        broadcastChanges();
    }

    private void putCursor(ServerLevel level, RitualMatch match, int amount) {
        ItemStack carried = getCarried();
        if (carried.isEmpty()) {
            return;
        }
        int put = Math.min(amount, carried.getCount());
        if (put <= 0) {
            return;
        }
        int accepted = WujinzangStorage.insert(level, match, carried.copyWithCount(put),
                core == null ? null : core.wujinzangVault());
        if (accepted <= 0) {
            if (owner != null) {
                String key = com.bitsson.gensokyou.block.entity.CrystalBlockEntity
                        .rejectionKey(carried, owner.level().registryAccess());
                owner.displayClientMessage(Component.translatable(
                        key != null ? key : "msg.gensokyou.wujinzang_full"), true);
            }
            return;
        }
        carried.shrink(accepted);
        setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
        broadcastChanges();
    }

    // ---- JEI 配方填充（仓储优先取料） ----

    /** 清空合成格后按配方输入逐格填 1 件：无尽藏仓储优先，其次玩家背包，皆无则留空。 */
    public void handleRecipeFill(ServerPlayer player, List<ItemStack> ingredients) {
        if (craftSlots == null) {
            return;
        }
        ServerLevel level = level();
        RitualMatch match = match();
        for (int i = 0; i < CRAFT_INPUT_COUNT; i++) {
            ItemStack current = craftSlots.getItem(i);
            if (current.isEmpty()) {
                continue;
            }
            craftSlots.setItem(i, ItemStack.EMPTY);
            player.getInventory().add(current);
            if (!current.isEmpty()) {
                if (level != null && match != null && storageAvailable()) {
                    int accepted = WujinzangStorage.insert(level, match, current,
                            core == null ? null : core.wujinzangVault());
                    if (accepted < current.getCount()) {
                        player.drop(current.copyWithCount(current.getCount() - accepted), false);
                    }
                } else {
                    player.drop(current, false);
                }
            }
        }
        for (int i = 0; i < CRAFT_INPUT_COUNT; i++) {
            ItemStack want = i < ingredients.size() ? ingredients.get(i) : ItemStack.EMPTY;
            if (want.isEmpty()) {
                continue;
            }
            ItemStack unit = want.copyWithCount(1);
            ItemStack got = ItemStack.EMPTY;
            if (level != null && match != null && storageAvailable()) {
                got = WujinzangStorage.extract(level, match, unit, 1);
            }
            if (got.isEmpty()) {
                got = takeOneFromPlayer(player, unit);
            }
            if (!got.isEmpty()) {
                craftSlots.setItem(i, got.copyWithCount(1));
            }
        }
        broadcastChanges();
    }

    private static ItemStack takeOneFromPlayer(Player player, ItemStack unit) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, unit)) {
                ItemStack one = stack.copyWithCount(1);
                stack.shrink(1);
                return one;
            }
        }
        return ItemStack.EMPTY;
    }

    // ---- 过滤 + 排序 ----

    private void ensureFresh(ServerLevel level, RitualMatch match) {
        if (dirty || lastRecomputeRevision != storageRevision()) {
            recompute();
        }
    }

    /** 存储变更代数：聚合各在线晶块 revision 之和。 */
    private long storageRevision() {
        ServerLevel level = level();
        RitualMatch match = match();
        if (level == null || match == null) {
            return -1L;
        }
        long sum = 0L;
        for (WujinzangStorage.Segment s : WujinzangStorage.segments(level, match, new net.minecraft.nbt.CompoundTag())) {
            if (s.crystal() != null) {
                sum = sum * 31L + s.crystal().revision();
            }
        }
        return sum;
    }

    private void recompute() {
        filtered.clear();
        ServerLevel level = level();
        RitualMatch match = match();
        if (level != null && match != null) {
            List<WujinzangStorage.Agg> agg = WujinzangStorage.aggregate(level, match);
            String q = query.toLowerCase(Locale.ROOT);
            for (int i = 0; i < agg.size(); i++) {
                if (q.isEmpty() || agg.get(i).key().getHoverName().getString()
                        .toLowerCase(Locale.ROOT).contains(q)) {
                    filtered.add(i);
                }
            }
            Comparator<Integer> comparator = switch (sortMode) {
                case SORT_NAME -> Comparator.comparing(
                        (Integer i) -> agg.get(i).key().getHoverName().getString(),
                        String.CASE_INSENSITIVE_ORDER);
                case SORT_ID -> Comparator.comparing((Integer i) -> {
                    ResourceLocation id = BuiltInRegistries.ITEM.getKey(agg.get(i).key().getItem());
                    return id == null ? "" : id.toString();
                });
                default -> Comparator.comparingLong(
                        (Integer i) -> agg.get(i).count()).reversed();
            };
            filtered.sort(comparator);
            int totalRows = (filtered.size() + COLUMNS - 1) / COLUMNS;
            int maxRowStart = Math.max(0, totalRows - ROWS);
            int rowStart = Mth.clamp(offset / COLUMNS, 0, maxRowStart);
            offset = rowStart * COLUMNS;
        }
        dirty = false;
        lastRecomputeRevision = storageRevision();
    }

    private CrystalStoragePagePayload snapshot() {
        ServerLevel level = level();
        RitualMatch match = match();
        // 停止态：内容不外泄（服务端真相），可见页收敛为空
        if (!storageAvailable()) {
            return new CrystalStoragePagePayload(containerId, 0, 0,
                    CrystalStoragePagePayload.MODE_TYPED, 0, -1L, sortMode, List.of());
        }
        recompute();
        List<CrystalStoragePagePayload.View> views = new ArrayList<>();
        if (level != null && match != null) {
            List<WujinzangStorage.Agg> agg = WujinzangStorage.aggregate(level, match);
            for (int i = 0; i < PAGE_SIZE; i++) {
                int k = offset + i;
                if (k >= 0 && k < filtered.size()) {
                    WujinzangStorage.Agg a = agg.get(filtered.get(k));
                    views.add(new CrystalStoragePagePayload.View(a.key(), a.count()));
                }
            }
        }
        int used = filtered.size();
        return new CrystalStoragePagePayload(containerId, offset, filtered.size(),
                CrystalStoragePagePayload.MODE_TYPED, used, -1L, sortMode, List.copyOf(views));
    }

    private void sendPage() {
        if (owner == null) {
            return;
        }
        PacketDistributor.sendToPlayer(owner, snapshot());
        sentRevision = storageRevision();
        lastSentEnabled = storageAvailable();
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (core == null || owner == null) {
            return;
        }
        long rev = storageRevision();
        if (sentRevision != rev || lastSentEnabled != storageAvailable()) {
            recompute();
            sendPage();
        }
    }

    // ---- 启停 ----

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer serverPlayer) || core == null) {
            return false;
        }
        ServerLevel serverLevel = serverPlayer.serverLevel();
        switch (id) {
            case RitualCoreMenu.BUTTON_START -> {
                boolean started = core.start(serverPlayer);
                com.bitsson.gensokyou.network.ModNetworking.sendRitualInfo(serverPlayer,
                        serverLevel, pos, core,
                        started ? "msg.gensokyou.ritual_started" : "msg.gensokyou.ritual_start_failed");
                return started;
            }
            case RitualCoreMenu.BUTTON_STOP -> {
                core.stop();
                com.bitsson.gensokyou.network.ModNetworking.sendRitualInfo(serverPlayer,
                        serverLevel, pos, core, "msg.gensokyou.ritual_stopped");
                return true;
            }
            case BUTTON_RETURN_CRAFT -> {
                returnCraftMaterials(serverPlayer);
                return true;
            }
            default -> {
                RitualMatch match = match();
                if (id >= RitualCoreMenu.BUTTON_ACTION_BASE && match != null
                        && RitualBehaviors.get(match.patternId())
                        .map(b -> b.onUiAction(serverLevel, pos, match, core, serverPlayer,
                                id - RitualCoreMenu.BUTTON_ACTION_BASE))
                        .orElse(InteractionResult.PASS) == InteractionResult.SUCCESS) {
                    return true;
                }
                return false;
            }
        }
    }

    // ---- 原版交互 ----

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        if (index == RESULT_SLOT_INDEX) {
            // 结果格 shift：尽最大努力批量合成（材料仓储优先），并正确消耗合成格材料
            craftMax(player);
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index == batterySlotIndex) {
            if (!this.moveItemStackTo(stack, PLAYER_INV_START, PLAYER_INV_START + 36, false)) {
                return ItemStack.EMPTY;
            }
        } else if (index < CRAFT_INPUT_COUNT) {
            if (!this.moveItemStackTo(stack, PLAYER_INV_START, PLAYER_INV_START + 36, true)) {
                return ItemStack.EMPTY;
            }
        } else if (index >= PLAYER_INV_START && index < PLAYER_INV_START + 36) {
            ServerLevel level = level();
            RitualMatch match = match();
            if (level == null || match == null || !storageAvailable()) {
                return ItemStack.EMPTY;
            }
            int accepted = WujinzangStorage.insert(level, match, stack,
                    core == null ? null : core.wujinzangVault());
            if (accepted <= 0) {
                return ItemStack.EMPTY;
            }
            stack.shrink(accepted);
        } else {
            return ItemStack.EMPTY;
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
        return original;
    }

    /**
     * 结果格 shift 批量合成：循环产出结果 → 整摞交给玩家背包 → 触发原版 onTake 消耗合成格材料 →
     * 再从仓储（不足回退背包）补回被消耗的槽位，直到背包放不下或材料耗尽。
     */
    /**
     * 结果格 shift：尽力合成**一个堆叠**（至多结果物品的堆叠上限，如 64），
     * 材料不足或背包放不下则提前停止；不填满整个背包。
     */
    private void craftMax(Player player) {
        if (craftSlots == null || resultSlots == null || owner == null) {
            return;
        }
        Slot resultSlot = this.slots.get(RESULT_SLOT_INDEX);
        int maxStack = 64;
        int crafted = 0;
        int guard = 0;
        while (guard++ < 100000) {
            ItemStack result = resultSlots.getItem(0);
            if (result.isEmpty()) {
                break;
            }
            if (guard == 1) {
                maxStack = result.getMaxStackSize();
            }
            if (crafted + result.getCount() > maxStack || !canFullyAccept(player, result)) {
                break;
            }
            ItemStack original = result.copy();
            if (!this.moveItemStackTo(result, PLAYER_INV_START, PLAYER_INV_START + 36, true)
                    || !result.isEmpty()) {
                // 未能整摞搬走：不消耗材料，保持结果原样后停止，避免复制
                break;
            }
            resultSlot.setByPlayer(ItemStack.EMPTY);
            // 消耗合成格材料；TerminalResultSlot 会在消耗后自动从仓储补回同类材料
            resultSlot.onTake(player, original);
            crafted += original.getCount();
        }
        broadcastChanges();
    }

    /**
     * 合成结果槽：任何一次的 onTake（单次点击或批量）消耗材料后，
     * 若仓储尚有同类，自动把被消耗的合成格补回 1 件（不足回退玩家背包）。
     */
    private final class TerminalResultSlot extends ResultSlot {

        TerminalResultSlot(Player player, TransientCraftingContainer craft,
                           ResultContainer container, int x, int y) {
            super(player, craft, container, 0, x, y);
        }

        @Override
        public void onTake(Player player, ItemStack stack) {
            ItemStack[] pattern = new ItemStack[CRAFT_INPUT_COUNT];
            for (int i = 0; i < CRAFT_INPUT_COUNT; i++) {
                ItemStack s = craftSlots.getItem(i);
                pattern[i] = s.isEmpty() ? ItemStack.EMPTY : s.copyWithCount(1);
            }
            super.onTake(player, stack);
            refillCraftGrid(player, pattern);
        }
    }

    /** 把被合成消耗、现已空的合成格，用同类材料从仓储（不足回退背包）补回 1 件。 */
    private void refillCraftGrid(Player player, ItemStack[] pattern) {
        if (craftSlots == null || owner == null) {
            return;
        }
        ServerLevel level = owner.serverLevel();
        RitualMatch match = match();
        for (int i = 0; i < CRAFT_INPUT_COUNT; i++) {
            if (pattern[i].isEmpty() || !craftSlots.getItem(i).isEmpty()) {
                continue;
            }
            ItemStack got = match == null || !storageAvailable() ? ItemStack.EMPTY
                    : WujinzangStorage.extract(level, match, pattern[i], 1);
            if (got.isEmpty()) {
                got = takeOneFromPlayer(player, pattern[i]);
            }
            if (!got.isEmpty()) {
                craftSlots.setItem(i, got.copyWithCount(1));
            }
        }
    }

    /** 叉叉按钮：把合成格内材料全部返还（优先回仓储，其次背包，最后掉落）。 */
    public void returnCraftMaterials(ServerPlayer player) {
        if (craftSlots == null) {
            return;
        }
        ServerLevel level = level();
        RitualMatch match = match();
        for (int i = 0; i < CRAFT_INPUT_COUNT; i++) {
            ItemStack stack = craftSlots.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            craftSlots.setItem(i, ItemStack.EMPTY);
            if (level != null && match != null && storageAvailable()) {
                int accepted = WujinzangStorage.insert(level, match, stack,
                        core == null ? null : core.wujinzangVault());
                if (accepted >= stack.getCount()) {
                    continue;
                }
                stack.shrink(accepted);
            }
            player.getInventory().add(stack);
            if (!stack.isEmpty()) {
                player.drop(stack, false);
            }
        }
        broadcastChanges();
    }

    /** 玩家背包能否容纳整摞（合并已有堆叠 + 空槽）。 */
    private static boolean canFullyAccept(Player player, ItemStack stack) {
        int remaining = stack.getCount();
        int max = stack.getMaxStackSize();
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize() && remaining > 0; i++) {
            ItemStack s = inventory.getItem(i);
            if (s.isEmpty()) {
                remaining -= max;
            } else if (ItemStack.isSameItemSameComponents(s, stack)) {
                remaining -= Math.max(0, s.getMaxStackSize() - s.getCount());
            }
        }
        return remaining <= 0;
    }

    /** 合成格变更即重算结果格（等价原版 CraftingMenu.slotChangedCraftingGrid）。 */
    @Override
    public void slotsChanged(net.minecraft.world.Container container) {
        super.slotsChanged(container);
        if (container != this.craftSlots || craftSlots == null || resultSlots == null
                || owner == null) {
            return;
        }
        ServerLevel level = owner.serverLevel();
        List<ItemStack> items = new ArrayList<>(CRAFT_INPUT_COUNT);
        for (int i = 0; i < craftSlots.getContainerSize(); i++) {
            items.add(craftSlots.getItem(i));
        }
        net.minecraft.world.item.crafting.CraftingInput input =
                net.minecraft.world.item.crafting.CraftingInput.of(
                        craftSlots.getWidth(), craftSlots.getHeight(), items);
        ItemStack result = ItemStack.EMPTY;
        var found = level.getRecipeManager().getRecipeFor(
                net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, level);
        if (found.isPresent()) {
            var holder = found.get();
            ItemStack assembled = holder.value().assemble(input, level.registryAccess());
            if (assembled.isItemEnabled(level.enabledFeatures())) {
                result = assembled;
            }
            resultSlots.setRecipeUsed(holder);
        }
        resultSlots.setItem(0, result);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (resultSlots != null) {
            resultSlots.clearContent();
        }
        if (craftSlots != null && !player.level().isClientSide) {
            clearContainer(player, craftSlots);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(this.pos) instanceof RitualCoreBlockEntity
                && player.distanceToSqr(this.pos.getX() + 0.5, this.pos.getY() + 0.5,
                        this.pos.getZ() + 0.5) <= 64.0;
    }
}
