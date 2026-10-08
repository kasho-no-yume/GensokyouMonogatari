package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.ritual.WujinzangState;

import com.bitsson.gensokyou.ritual.SpiritPowerAccess;

import com.bitsson.gensokyou.block.CrystalBlock;
import com.bitsson.gensokyou.block.entity.CrystalBlockEntity;
import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.registry.ModBlocks;
import com.bitsson.gensokyou.ritual.RitualMatch;
import com.bitsson.gensokyou.ritual.RitualPedestals;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 无尽藏之仪的托管内核：把 128 个晶块当作一个扁平仓储。
 *
 * <p><b>真源</b>：物品实体住在各晶块的条目池；核心只持久化分区组表、孤儿段与段位坐标。
 * <b>动态分区</b>：可堆叠（{@code maxStackSize>1} 且无组件差异）归类型制组，其余归总量制组；
 * 插入先复用同组可接收晶块，否则格式化一个零条目空闲晶块。非空晶块永不改模式。
 *
 * <p>分段键：祭品台规范序下标 0..n-1；晶位 = 祭品台正上方一格。
 */
public final class WujinzangStorage {

    private static WujinzangState wujinzang(SpiritPowerAccess core) {
        return (WujinzangState) core.behaviorState();
    }

    /** 未分区（空闲晶块）。 */
    public static final int GROUP_NONE = 0;
    /** 类型制组（可堆叠：30 类 × long）。 */
    public static final int GROUP_TYPED = 1;
    /** 总量制组（带组件/不可堆叠：2000 件不限种类）。 */
    public static final int GROUP_TOTAL = 2;

    private static final String TAG_GROUPS = "Grp";
    private static final String TAG_SEGMENTS = "Seg";
    private static final String TAG_ORPHANS = "Orp";
    private static final String TAG_LEVEL = "Lvl";
    private static final String TAG_CONCEALED = "Con";

    private WujinzangStorage() {
    }

    // ---- 分类判据（世界无关纯函数） ----

    /** 可堆叠 = 堆叠上限 &gt; 1 且不携带任何非默认组件。 */
    public static boolean isStackable(ItemStack stack) {
        return !stack.isEmpty() && stack.getMaxStackSize() > 1
                && stack.getComponentsPatch().isEmpty();
    }

    public static int groupFor(ItemStack stack) {
        return isStackable(stack) ? GROUP_TYPED : GROUP_TOTAL;
    }

    // ---- 分段模型 ----

    /** 一个晶位：规范序下标 + 晶位坐标 + 当前分组 + 在线晶块（离线为 null）。 */
    public record Segment(int index, BlockPos pos, int group, @Nullable CrystalBlockEntity crystal) {
    }

    public static List<Segment> segments(ServerLevel level, RitualMatch match, CompoundTag vault) {
        List<BlockPos> peds = RitualPedestals.positions(match);
        int n = peds.size();
        int[] grp = groups(vault, n);
        List<Segment> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            BlockPos cp = peds.get(i).above();
            CrystalBlockEntity cbe = level.getBlockEntity(cp) instanceof CrystalBlockEntity c ? c : null;
            out.add(new Segment(i, cp, i < grp.length ? grp[i] : GROUP_NONE, cbe));
        }
        return out;
    }

    // ---- 插入 / 抽取 / 聚合 ----

    /** 插入：分类 → 复用同组（同种优先、有空位次之）→ 格式化空闲晶块 → 背压。 */
    public static int insert(ServerLevel level, RitualMatch match, ItemStack stack,
                             @Nullable CompoundTag vault) {
        if (stack.isEmpty() || CrystalBlockEntity.isRejected(stack, level.registryAccess())) {
            return 0;
        }
        CompoundTag tag = vault == null ? new CompoundTag() : vault;
        int target = groupFor(stack);
        List<Segment> segs = segments(level, match, tag);
        for (Segment s : segs) {
            if (s.group() == target && s.crystal() != null && s.crystal().findEntry(stack) >= 0) {
                int accepted = s.crystal().insert(stack);
                if (accepted > 0) {
                    return accepted;
                }
            }
        }
        for (Segment s : segs) {
            if (s.group() == target && s.crystal() != null && s.crystal().canInsert(stack)) {
                int accepted = s.crystal().insert(stack);
                if (accepted > 0) {
                    return accepted;
                }
            }
        }
        for (Segment s : segs) {
            if (s.group() == GROUP_NONE && s.crystal() != null && s.crystal().entryCount() == 0) {
                s.crystal().setMode(target == GROUP_TYPED
                        ? CrystalBlockEntity.Mode.TYPED : CrystalBlockEntity.Mode.TOTAL, true);
                setGroup(tag, s.index(), target);
                int accepted = s.crystal().insert(stack);
                if (accepted > 0) {
                    return accepted;
                }
            }
        }
        return 0;
    }

    /** 非破坏性插入模拟：返回将被收纳的数量（不改世界）。 */
    public static int simulateInsert(ServerLevel level, RitualMatch match, ItemStack stack) {
        if (stack.isEmpty() || CrystalBlockEntity.isRejected(stack, level.registryAccess())) {
            return 0;
        }
        List<Segment> segs = segments(level, match, new CompoundTag());
        for (Segment s : segs) {
            if (s.crystal() == null) {
                continue;
            }
            int idx = s.crystal().findEntry(stack);
            if (idx >= 0) {
                ItemStack rem = s.crystal().itemHandler().insertItem(idx, stack, true);
                return stack.getCount() - rem.getCount();
            }
        }
        // 保守估计：存在同组或空闲晶块时假定可收纳整组
        for (Segment s : segs) {
            if (s.group() == groupFor(stack) && s.crystal() != null && s.crystal().canInsert(stack)) {
                return stack.getCount();
            }
        }
        for (Segment s : segs) {
            if (s.group() == GROUP_NONE && s.crystal() != null && s.crystal().entryCount() == 0) {
                return stack.getCount();
            }
        }
        return 0;
    }

    /** 抽取：按规范序定位首个持有该条目的晶块。 */
    public static ItemStack extract(ServerLevel level, RitualMatch match, ItemStack key, int amount) {
        for (Segment s : segments(level, match, new CompoundTag())) {
            if (s.crystal() == null) {
                continue;
            }
            int idx = s.crystal().findEntry(key);
            if (idx >= 0) {
                ItemStack removed = s.crystal().removeFromEntry(idx, amount);
                if (!removed.isEmpty()) {
                    return removed;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    /** 聚合条目（同种跨晶合并，保留首次出现顺序）。 */
    public record Agg(ItemStack key, long count, int segment) {
    }

    public static List<Agg> aggregate(ServerLevel level, RitualMatch match) {
        List<Agg> out = new ArrayList<>();
        for (Segment s : segments(level, match, new CompoundTag())) {
            if (s.crystal() == null) {
                continue;
            }
            for (CrystalBlockEntity.Entry entry : s.crystal().entries()) {
                int found = -1;
                for (int i = 0; i < out.size(); i++) {
                    if (ItemStack.isSameItemSameComponents(out.get(i).key(), entry.key())) {
                        found = i;
                        break;
                    }
                }
                if (found >= 0) {
                    Agg a = out.get(found);
                    out.set(found, new Agg(a.key(), a.count() + entry.count(), a.segment()));
                } else {
                    out.add(new Agg(entry.key().copy(), entry.count(), s.index()));
                }
            }
        }
        return out;
    }

    // ---- 生命周期 ----

    /** 成型/升级：补齐晶位晶块（幂等），恢复孤儿段，写回段位坐标与分组，申请强制加载。 */
    public static void ensureCrystals(SpiritPowerAccess core, ServerLevel level, RitualMatch match) {
        CompoundTag vault = wujinzang(core).vault();
        List<BlockPos> peds = RitualPedestals.positions(match);
        int n = peds.size();
        int[] grp = groups(vault, n);
        long[] seg = new long[n];
        CompoundTag orphans = orphans(vault);
        String dim = level.dimension().location().toString();
        boolean concealed = !core.isEnabled();
        for (int i = 0; i < n; i++) {
            BlockPos cp = peds.get(i).above();
            seg[i] = cp.asLong();
            BlockState state = level.getBlockState(cp);
            CrystalBlockEntity cbe = level.getBlockEntity(cp) instanceof CrystalBlockEntity c ? c : null;
            if (cbe == null && (state.isAir() || state.canBeReplaced())) {
                level.setBlock(cp, ModBlocks.CRYSTAL.get().defaultBlockState()
                        .setValue(CrystalBlock.CONCEALED, concealed), 3);
                if (level.getBlockEntity(cp) instanceof CrystalBlockEntity placed) {
                    cbe = placed;
                }
            }
            if (cbe != null) {
                cbe.setBinding(dim, core.getBlockPos(), i);
                String key = Integer.toString(i);
                if (cbe.entryCount() == 0 && orphans.contains(key)) {
                    cbe.importContents(orphans.getCompound(key), false);
                    orphans.remove(key);
                }
                setConcealed(level, cp, concealed);
            }
        }
        vault.put(TAG_ORPHANS, orphans);
        vault.putIntArray(TAG_GROUPS, grp);
        vault.putLongArray(TAG_SEGMENTS, seg);
        vault.putInt(TAG_LEVEL, match.level());
        vault.putBoolean(TAG_CONCEALED, concealed);
        wujinzang(core).setVault(vault); core.markDirty();
        forceChunks(level, seg, true);
    }

    /** 不成型：导出各晶块内容为孤儿段后移除晶块，释放强制加载。 */
    public static void onStructureLost(SpiritPowerAccess core, ServerLevel level) {
        CompoundTag vault = wujinzang(core).rawVault();
        if (vault == null) {
            return;
        }
        long[] seg = vault.contains(TAG_SEGMENTS) ? vault.getLongArray(TAG_SEGMENTS) : new long[0];
        CompoundTag orphans = orphans(vault);
        for (int i = 0; i < seg.length; i++) {
            BlockPos cp = BlockPos.of(seg[i]);
            if (level.getBlockEntity(cp) instanceof CrystalBlockEntity cbe) {
                if (cbe.entryCount() > 0) {
                    orphans.put(Integer.toString(i), cbe.exportContents());
                }
                level.removeBlock(cp, false);
            }
        }
        vault.put(TAG_ORPHANS, orphans);
        wujinzang(core).setVault(vault); core.markDirty();
        forceChunks(level, seg, false);
    }

    /** 核心被移除时释放强制加载（内容随 BE 消亡）。 */
    public static void releaseForceLoads(SpiritPowerAccess core, ServerLevel level) {
        CompoundTag vault = wujinzang(core).rawVault();
        if (vault == null || !vault.contains(TAG_SEGMENTS)) {
            return;
        }
        forceChunks(level, vault.getLongArray(TAG_SEGMENTS), false);
    }

    /** 每 tick（成型即跑）：等级迁移、隐藏态同步、电池→缓存补料。 */
    public static void passiveTick(SpiritPowerAccess core, ServerLevel level, RitualMatch match) {
        CompoundTag vault = wujinzang(core).vault();
        int storedLevel = vault.contains(TAG_LEVEL) ? vault.getInt(TAG_LEVEL) : match.level();
        if (storedLevel != match.level()) {
            if (match.level() < storedLevel) {
                trimToLevel(core, level, match);
            }
            ensureCrystals(core, level, match);
        }
        boolean wantConcealed = !core.isEnabled();
        if (!vault.contains(TAG_CONCEALED) || vault.getBoolean(TAG_CONCEALED) != wantConcealed) {
            long[] seg = vault.contains(TAG_SEGMENTS) ? vault.getLongArray(TAG_SEGMENTS) : new long[0];
            for (long p : seg) {
                setConcealed(level, BlockPos.of(p), wantConcealed);
            }
            vault.putBoolean(TAG_CONCEALED, wantConcealed);
            wujinzang(core).setVault(vault); core.markDirty();
        }
        if (core.isEnabled()) {
            core.tickBatteryToCacheFill();
        }
    }

    /** 降级：撤下超出当前等级的晶位，内容存为孤儿段（升级时恢复）。 */
    private static void trimToLevel(SpiritPowerAccess core, ServerLevel level, RitualMatch match) {
        CompoundTag vault = wujinzang(core).vault();
        long[] seg = vault.contains(TAG_SEGMENTS) ? vault.getLongArray(TAG_SEGMENTS) : new long[0];
        int keep = RitualPedestals.positions(match).size();
        CompoundTag orphans = orphans(vault);
        List<Long> kept = new ArrayList<>();
        for (int i = 0; i < seg.length; i++) {
            BlockPos cp = BlockPos.of(seg[i]);
            if (i < keep) {
                kept.add(seg[i]);
                continue;
            }
            if (level.getBlockEntity(cp) instanceof CrystalBlockEntity cbe) {
                if (cbe.entryCount() > 0) {
                    orphans.put(Integer.toString(i), cbe.exportContents());
                }
                level.removeBlock(cp, false);
            }
        }
        long[] newSeg = new long[kept.size()];
        for (int i = 0; i < newSeg.length; i++) {
            newSeg[i] = kept.get(i);
        }
        vault.put(TAG_ORPHANS, orphans);
        vault.putLongArray(TAG_SEGMENTS, newSeg);
        wujinzang(core).setVault(vault); core.markDirty();
    }

    /**
     * 统计：{已用晶块, 全部晶块, 可堆叠物品种类数, 不可堆叠物品种类数}。
     * 「已用」= 该晶块条目数 &gt; 0；种类数按条目数汇总（同种恒合并，条目即种类）。
     */
    public static int[] counts(ServerLevel level, RitualMatch match, CompoundTag vault) {
        CompoundTag tag = vault == null ? new CompoundTag() : vault;
        int used = 0;
        int total = 0;
        int stackTypes = 0;
        int totalTypes = 0;
        for (Segment s : segments(level, match, tag)) {
            total++;
            if (s.crystal() == null) {
                continue;
            }
            int entries = s.crystal().entryCount();
            if (entries > 0) {
                used++;
            }
            if (s.crystal().mode() == CrystalBlockEntity.Mode.TYPED) {
                stackTypes += entries;
            } else {
                totalTypes += entries;
            }
        }
        return new int[]{used, total, stackTypes, totalTypes};
    }

    /** 被占晶位数量（晶位既非本仪式晶块也非空气/可替换）。 */
    public static int blockedCount(ServerLevel level, RitualMatch match) {
        int blocked = 0;
        for (BlockPos ped : RitualPedestals.positions(match)) {
            BlockPos cp = ped.above();
            if (level.getBlockEntity(cp) instanceof CrystalBlockEntity) {
                continue;
            }
            BlockState state = level.getBlockState(cp);
            if (!state.isAir() && !state.canBeReplaced()) {
                blocked++;
            }
        }
        return blocked;
    }

    public static void concealAll(ServerLevel level, RitualMatch match, boolean concealed) {
        for (BlockPos ped : RitualPedestals.positions(match)) {
            setConcealed(level, ped.above(), concealed);
        }
    }

    // ---- 端点 / 渲染辅助 ----

    /** 底座 8 个中心对称激光锚点（绝对坐标，y = 结构最低层）。 */
    public static long[] laserAnchors(SpiritPowerAccess core, RitualMatch match) {
        BlockPos c = core.getBlockPos();
        int minY = Integer.MAX_VALUE;
        int radius = 0;
        for (List<BlockPos> positions : match.keyedPositions().values()) {
            for (BlockPos p : positions) {
                minY = Math.min(minY, p.getY());
                radius = Math.max(radius, (int) Math.ceil(Math.hypot(
                        p.getX() - c.getX(), p.getZ() - c.getZ())));
            }
        }
        if (minY == Integer.MAX_VALUE) {
            minY = c.getY();
        }
        double r = Math.max(1.0D, radius * GensokyouConfig.FX_WUJINZANG_LASER_RADIUS_RATIO.get());
        long[] out = new long[8];
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4.0D;
            int x = c.getX() + (int) Math.round(Math.cos(angle) * r);
            int z = c.getZ() + (int) Math.round(Math.sin(angle) * r);
            out[i] = new BlockPos(x, minY, z).asLong();
        }
        return out;
    }

    // ---- 内部工具 ----

    private static int[] groups(CompoundTag vault, int n) {
        int[] out = new int[n];
        if (vault.contains(TAG_GROUPS)) {
            int[] stored = vault.getIntArray(TAG_GROUPS);
            System.arraycopy(stored, 0, out, 0, Math.min(stored.length, n));
        }
        return out;
    }

    private static void setGroup(CompoundTag vault, int index, int group) {
        int[] grp = groups(vault, Math.max(index + 1, 1));
        if (index >= grp.length) {
            int[] grown = new int[index + 1];
            System.arraycopy(grp, 0, grown, 0, grp.length);
            grp = grown;
        }
        grp[index] = group;
        vault.putIntArray(TAG_GROUPS, grp);
    }

    private static CompoundTag orphans(CompoundTag vault) {
        if (!vault.contains(TAG_ORPHANS)) {
            vault.put(TAG_ORPHANS, new CompoundTag());
        }
        return vault.getCompound(TAG_ORPHANS);
    }

    private static void setConcealed(ServerLevel level, BlockPos pos, boolean concealed) {
        BlockState state = level.getBlockState(pos);
        if (state.is(ModBlocks.CRYSTAL.get())
                && state.getValue(CrystalBlock.CONCEALED) != concealed) {
            level.setBlock(pos, state.setValue(CrystalBlock.CONCEALED, concealed), 3);
        }
    }

    private static void forceChunks(ServerLevel level, long[] seg, boolean forced) {
        Set<Long> seen = new HashSet<>();
        for (long p : seg) {
            BlockPos pos = BlockPos.of(p);
            int cx = pos.getX() >> 4;
            int cz = pos.getZ() >> 4;
            if (seen.add(ChunkPos.asLong(cx, cz))) {
                level.setChunkForced(cx, cz, forced);
            }
        }
    }

    /** 核心 IItemHandler 代理：跨晶块合并箱（忽略槽号，按分类分区写入）。 */
    public static final class ProxyHandler implements IItemHandler {

        private final SpiritPowerAccess core;
        private List<Agg> cache;

        public ProxyHandler(SpiritPowerAccess core) {
            this.core = core;
        }

        private @Nullable ServerLevel level() {
            return core.getLevel() instanceof ServerLevel server ? server : null;
        }

        private @Nullable RitualMatch match() {
            if (core.activeMatch() == null
                    || !com.bitsson.gensokyou.ritual.RitualBehaviors.WUJINZANG
                            .equals(core.activeMatch().patternId())) {
                return null;
            }
            return core.activeMatch();
        }

        private List<Agg> snapshot(ServerLevel level, RitualMatch match) {
            if (cache == null) {
                cache = aggregate(level, match);
            }
            return cache;
        }

        @Override
        public int getSlots() {
            ServerLevel level = level();
            RitualMatch match = match();
            if (level == null || match == null) {
                return 0;
            }
            return snapshot(level, match).size();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            ServerLevel level = level();
            RitualMatch match = match();
            if (level == null || match == null) {
                return ItemStack.EMPTY;
            }
            List<Agg> agg = snapshot(level, match);
            if (slot < 0 || slot >= agg.size()) {
                return ItemStack.EMPTY;
            }
            Agg a = agg.get(slot);
            return a.key().copyWithCount((int) Math.min(a.count(), a.key().getMaxStackSize()));
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            ServerLevel level = level();
            RitualMatch match = match();
            if (level == null || match == null || stack.isEmpty()) {
                return stack;
            }
            int accepted = simulate
                    ? simulateInsert(level, match, stack)
                    : insert(level, match, stack, wujinzang(core).vault());
            if (accepted > 0) {
                cache = null;
            }
            if (accepted >= stack.getCount()) {
                return ItemStack.EMPTY;
            }
            return stack.copyWithCount(stack.getCount() - Math.max(0, accepted));
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            ServerLevel level = level();
            RitualMatch match = match();
            if (level == null || match == null || amount <= 0) {
                return ItemStack.EMPTY;
            }
            List<Agg> agg = snapshot(level, match);
            if (slot < 0 || slot >= agg.size()) {
                return ItemStack.EMPTY;
            }
            ItemStack key = agg.get(slot).key();
            if (simulate) {
                return key.copyWithCount(Math.min(amount, key.getMaxStackSize()));
            }
            ItemStack removed = extract(level, match, key, amount);
            if (!removed.isEmpty()) {
                cache = null;
            }
            return removed;
        }

        @Override
        public int getSlotLimit(int slot) {
            ServerLevel level = level();
            RitualMatch match = match();
            if (level == null || match == null) {
                return 64;
            }
            List<Agg> agg = snapshot(level, match);
            return slot >= 0 && slot < agg.size() ? agg.get(slot).key().getMaxStackSize() : 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            ServerLevel level = level();
            RitualMatch match = match();
            return level != null && match != null
                    && !CrystalBlockEntity.isRejected(stack, level.registryAccess());
        }
    }
}
