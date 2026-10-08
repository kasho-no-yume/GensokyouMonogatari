package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.ritual.SpiritPowerAccess;

import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import com.bitsson.gensokyou.block.entity.RitualPedestalBlockEntity;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.network.InfoLine;
import com.bitsson.gensokyou.network.ModNetworking;
import com.bitsson.gensokyou.ritual.RitualMatch;
import com.bitsson.gensokyou.ritual.RitualPedestals;
import com.bitsson.gensokyou.ritual.enchant.OmoikaneMerge;
import com.bitsson.gensokyou.ritual.enchant.OmoikaneRandomPool;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.List;

/**
 * 思兼神封（`gensokyou:omoikane_circle`）的批次附魔结算。
 *
 * <p><b>双模式</b>：核心额外槽放普通书 → 书模式（祭品台认青金石块，每块换一个随机
 * 词条，书原地变附魔书）；否则 → 装备模式（祭品台认附魔书，升序折叠合并入装备）。
 * 与当前模式不匹配的祭品<b>完全忽略</b>（不计数、不消耗、不清空，同少名口径）。
 *
 * <p><b>足额预检</b>：总价 = 单价 × 有效词条数，缓存不足 → <b>整批失败、零消耗</b>
 * （刻意区别于少名的尽力产出——附魔是离散事件，"只附一半"体验不可接受）。
 *
 * <p><b>扣费口径</b>：只用<b>核心自身缓存</b>（{@code core.getStored()} /
 * {@code core.extract()}），刻意<b>不</b>用 {@code SpiritPowerHelper} 三段式。
 *
 * <p><b>消耗口径</b>：附魔书仅 1 阶消耗，且逐本判定——含 ≥1 条对装备有效词条的书才
 * 清空；青金石块三阶皆消耗（催化剂定位）。
 *
 * <p><b>写回</b>：装备在槽内原地变身（组件覆写）；普通书原位替换为附魔书。
 * MUST NOT 走 {@code RitualOutputs.spawn} 掉落圆盘。
 */
public final class OmoikaneForging {

    /** 装备/普通书占据的额外槽下标。 */
    public static final int GEAR_SLOT = 0;

    private static final int COLOR_ACCENT = 0xFF9E8FD8;
    private static final int COLOR_WARN = 0xFFE0A030;
    private static final int COLOR_OK = 0xFF2E8B57;
    private static final int COLOR_IDLE = 0xFFAAAAAA;

    public enum Mode {
        NONE, GEAR, BOOK
    }

    /** 祭品台投放现状：总台数 / 附魔书台数 / 青金石块台数 / 其他物品台数。 */
    public record PedestalScan(int pedestals, int books, int lapis, int other) {

        public static final PedestalScan NONE = new PedestalScan(0, 0, 0, 0);
    }

    /** 一次批次的结果，供 GUI 与调试读取。 */
    public record Outcome(Mode mode, int entries, long spent, long storedBefore, long storedAfter,
                          String failReason) {

        public static final Outcome NONE = new Outcome(Mode.NONE, 0, 0L, 0L, 0L, "none");

        public boolean producedAnything() {
            return entries > 0;
        }
    }

    /** 最近一次批次结果（内存态，供探针读取；结构失效无需清理——幂等覆盖）。 */
    private static volatile Outcome lastOutcome = Outcome.NONE;

    public static Outcome lastOutcome() {
        return lastOutcome;
    }

    private OmoikaneForging() {
    }

    // ------------------------------------------------------------------ 扫描

    /** 当前模式：槽空 → NONE；普通书 → BOOK；否则 GEAR。 */
    public static Mode modeOf(SpiritPowerAccess core) {
        ItemStack slot = core.extraSlot(GEAR_SLOT);
        if (slot.isEmpty()) {
            return Mode.NONE;
        }
        return slot.is(Items.BOOK) ? Mode.BOOK : Mode.GEAR;
    }

    /** 扫一遍祭品台。{@code other} 只用于 GUI 展示，不参与任何计算。 */
    public static PedestalScan scanPedestals(ServerLevel level, RitualMatch match) {
        int pedestals = 0;
        int books = 0;
        int lapis = 0;
        int other = 0;
        for (BlockPos pos : RitualPedestals.positions(match)) {
            if (!(level.getBlockEntity(pos) instanceof RitualPedestalBlockEntity pedestal)) {
                continue;
            }
            pedestals++;
            ItemStack held = pedestal.getHeld();
            if (held.isEmpty()) {
                continue;
            }
            if (held.is(Items.ENCHANTED_BOOK)) {
                books++;
            } else if (held.is(Items.LAPIS_BLOCK)) {
                lapis++;
            } else {
                other++;
            }
        }
        return new PedestalScan(pedestals, books, lapis, other);
    }

    /** 当前模式下的有效祭品台（规范序），供结算按序取用。 */
    private static List<BlockPos> validPedestals(ServerLevel level, RitualMatch match, Mode mode) {
        List<BlockPos> valid = new ArrayList<>();
        for (BlockPos pos : RitualPedestals.positions(match)) {
            if (!(level.getBlockEntity(pos) instanceof RitualPedestalBlockEntity pedestal)) {
                continue;
            }
            ItemStack held = pedestal.getHeld();
            boolean ok = mode == Mode.GEAR ? held.is(Items.ENCHANTED_BOOK)
                    : mode == Mode.BOOK && held.is(Items.LAPIS_BLOCK);
            if (ok) {
                valid.add(pos.immutable());
            }
        }
        return valid;
    }

    /** 装备模式的合并计划预览（GUI 与结算共用同一入口，保证所见即所扣）。 */
    public static OmoikaneMerge.MergePlan planGear(SpiritPowerAccess core,
                                                   List<ItemStack> books, int ritualTier) {
        return OmoikaneMerge.plan(core.extraSlot(GEAR_SLOT), books, ritualTier);
    }

    // ------------------------------------------------------------------ 批次结算

    /**
     * 跑一个批次。
     *
     * @return 实际结果；任何早退（无槽物 / 无有效祭品 / 无有效词条 / 灵力不足）都
     *         返回 {@code producedAnything() == false} 的 Outcome 且<b>零消耗</b>
     */
    public static Outcome forge(ServerLevel level, BlockPos corePos, RitualMatch match,
                                SpiritPowerAccess core) {
        int ritualLevel = match.level();
        Mode mode = modeOf(core);
        long storedBefore = core.getStored();
        if (mode == Mode.NONE) {
            return lastOutcome = new Outcome(Mode.NONE, 0, 0L, storedBefore, storedBefore, "no_item");
        }
        List<BlockPos> valid = validPedestals(level, match, mode);
        if (valid.isEmpty()) {
            return lastOutcome = new Outcome(mode, 0, 0L, storedBefore, storedBefore, "no_offerings");
        }
        Outcome out = mode == Mode.GEAR
                ? forgeGear(level, core, valid, ritualLevel, storedBefore)
                : forgeBook(level, core, valid, ritualLevel, storedBefore);
        if (out.producedAnything()) {
            core.triggerSacrificeFx(GensokyouConfig.FX_PILLAR_TICKS.get());
            ModNetworking.sendRitualInfoToViewers(level, corePos);
        }
        return lastOutcome = out;
    }

    /** 装备模式：书池合并入装备。 */
    private static Outcome forgeGear(ServerLevel level, SpiritPowerAccess core,
                                     List<BlockPos> valid, int ritualLevel, long storedBefore) {
        List<ItemStack> books = new ArrayList<>();
        for (BlockPos pos : valid) {
            if (level.getBlockEntity(pos) instanceof RitualPedestalBlockEntity pedestal) {
                books.add(pedestal.getHeld().copy());
            }
        }
        OmoikaneMerge.MergePlan plan = planGear(core, books, ritualLevel);
        int entries = plan.entryCount();
        if (entries <= 0) {
            return new Outcome(Mode.GEAR, 0, 0L, storedBefore, storedBefore, "no_valid_entries");
        }
        long cost = OmoikaneScaling.batchCost(entries, ritualLevel);
        if (storedBefore < cost) {
            return new Outcome(Mode.GEAR, entries, 0L, storedBefore, storedBefore, "no_power");
        }
        long paid = core.extract(cost);
        if (paid < cost) {
            // 与判定同口径，理论上不可达；真发生了就退回已扣部分并放弃本批次
            core.receive(paid);
            return new Outcome(Mode.GEAR, entries, 0L, storedBefore, core.getStored(), "no_power");
        }
        ItemStack gear = core.extraSlot(GEAR_SLOT);
        OmoikaneMerge.apply(gear, plan);
        core.setExtraSlot(GEAR_SLOT, gear);
        // 消耗：仅 1 阶、且只清空含 ≥1 条有效词条的书（逐本判定）
        if (ritualLevel <= 1) {
            for (int i = 0; i < valid.size() && i < plan.bookEffective().size(); i++) {
                if (!plan.bookEffective().get(i)) {
                    continue;
                }
                if (level.getBlockEntity(valid.get(i)) instanceof RitualPedestalBlockEntity pedestal
                        && pedestal.getHeld().is(Items.ENCHANTED_BOOK)) {
                    pedestal.setHeld(ItemStack.EMPTY);
                }
            }
        }
        return new Outcome(Mode.GEAR, entries, cost, storedBefore, core.getStored(), "");
    }

    /** 书模式：青金石块换随机词条，普通书原地变附魔书。 */
    private static Outcome forgeBook(ServerLevel level, SpiritPowerAccess core,
                                     List<BlockPos> valid, int ritualLevel, long storedBefore) {
        int entries = valid.size();
        long cost = OmoikaneScaling.batchCost(entries, ritualLevel);
        if (storedBefore < cost) {
            return new Outcome(Mode.BOOK, entries, 0L, storedBefore, storedBefore, "no_power");
        }
        List<Holder<Enchantment>> pool = OmoikaneRandomPool.pool(level.registryAccess(), ritualLevel);
        List<Holder<Enchantment>> drawn = OmoikaneRandomPool.draw(pool, entries, level.getRandom());
        if (drawn.isEmpty()) {
            return new Outcome(Mode.BOOK, 0, 0L, storedBefore, storedBefore, "empty_pool");
        }
        long paid = core.extract(cost);
        if (paid < cost) {
            core.receive(paid);
            return new Outcome(Mode.BOOK, entries, 0L, storedBefore, core.getStored(), "no_power");
        }
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        for (Holder<Enchantment> holder : drawn) {
            mutable.set(holder, OmoikaneRandomPool.rollLevel(ritualLevel,
                    holder.value().getMaxLevel(), level.getRandom()));
        }
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        book.set(DataComponents.STORED_ENCHANTMENTS, mutable.toImmutable());
        core.setExtraSlot(GEAR_SLOT, book);
        // 青金石块三阶皆消耗（催化剂定位）；实际词条数可能因池大小被钳，按实际退款
        int lapisToConsume = drawn.size();
        for (BlockPos pos : valid) {
            if (lapisToConsume <= 0) {
                break;
            }
            if (level.getBlockEntity(pos) instanceof RitualPedestalBlockEntity pedestal
                    && pedestal.getHeld().is(Items.LAPIS_BLOCK)) {
                // getHeld() 是活引用：直接 shrink 不会触发 setChanged/syncToClients，走 setHeld
                ItemStack next = pedestal.getHeld().copy();
                next.shrink(1);
                pedestal.setHeld(next.isEmpty() ? ItemStack.EMPTY : next);
                lapisToConsume--;
            }
        }
        if (drawn.size() < entries) {
            core.receive(OmoikaneScaling.batchCost(entries - drawn.size(), ritualLevel));
        }
        return new Outcome(Mode.BOOK, drawn.size(),
                OmoikaneScaling.batchCost(drawn.size(), ritualLevel), storedBefore,
                core.getStored(), "");
    }

    // ------------------------------------------------------------------ GUI

    public static List<InfoLine> uiInfo(ServerLevel level, BlockPos corePos, RitualMatch match,
                                        SpiritPowerAccess core) {
        int ritualLevel = match.level();
        Mode mode = modeOf(core);
        PedestalScan scan = scanPedestals(level, match);
        long unitCost = OmoikaneScaling.unitCostOf(ritualLevel);
        long stored = core.getStored();

        int entries = 0;
        OmoikaneMerge.MergePlan gearPlan = OmoikaneMerge.MergePlan.EMPTY;
        if (mode == Mode.GEAR) {
            List<ItemStack> books = new ArrayList<>();
            for (BlockPos pos : validPedestals(level, match, Mode.GEAR)) {
                if (level.getBlockEntity(pos) instanceof RitualPedestalBlockEntity pedestal) {
                    books.add(pedestal.getHeld().copy());
                }
            }
            gearPlan = planGear(core, books, ritualLevel);
            entries = gearPlan.entryCount();
        } else if (mode == Mode.BOOK) {
            entries = scan.lapis();
        }
        long total = OmoikaneScaling.batchCost(entries, ritualLevel);
        boolean affordable = OmoikaneScaling.canAfford(stored, entries, ritualLevel);

        List<InfoLine> lines = new ArrayList<>();
        lines.add(previewLine(mode, gearPlan, scan));
        lines.add(stateLine(mode, entries, affordable, scan));
        lines.add(InfoLine.tipped("gui.gensokyou.ritual.omoikane.pedestals",
                new String[]{String.valueOf(mode == Mode.BOOK ? scan.lapis() : scan.books()),
                        String.valueOf(scan.pedestals())},
                COLOR_ACCENT, "gui.gensokyou.ritual.omoikane.pedestals_tip",
                new String[]{String.valueOf(scan.books()), String.valueOf(scan.lapis()),
                        String.valueOf(scan.pedestals()), String.valueOf(scan.other())}));
        lines.add(InfoLine.tipped("gui.gensokyou.ritual.omoikane.cache",
                new String[]{InfoLine.compact(stored),
                        InfoLine.compact(OmoikaneScaling.capacityOf(ritualLevel))},
                COLOR_ACCENT, "gui.gensokyou.ritual.omoikane.cache_tip",
                new String[]{String.valueOf(stored),
                        String.valueOf(OmoikaneScaling.capacityOf(ritualLevel))}));
        lines.add(new InfoLine("gui.gensokyou.ritual.omoikane.in_rate",
                new String[]{InfoLine.compact(OmoikaneScaling.inRateOf(ritualLevel))},
                "", COLOR_ACCENT, -1F, null));
        lines.add(InfoLine.tipped("gui.gensokyou.ritual.omoikane.cost",
                new String[]{InfoLine.compact(unitCost), InfoLine.compact(total)},
                COLOR_ACCENT, "gui.gensokyou.ritual.omoikane.cost_tip",
                new String[]{String.valueOf(unitCost), String.valueOf(total)}));
        return lines;
    }

    /** 产出预览行：纯文字（真槽位已画出槽内容，信息栏不重复画物品框——少名先例）。 */
    private static InfoLine previewLine(Mode mode, OmoikaneMerge.MergePlan plan, PedestalScan scan) {
        if (mode == Mode.NONE) {
            return new InfoLine("gui.gensokyou.ritual.omoikane.slot", new String[0], "",
                    COLOR_IDLE, -1F, null, 0, InfoLine.CONTROL_NONE, InfoLine.LINK_NONE,
                    "gui.gensokyou.ritual.omoikane.slot_tip", new String[0]);
        }
        if (mode == Mode.BOOK) {
            return new InfoLine("gui.gensokyou.ritual.omoikane.slot_book",
                    new String[]{String.valueOf(scan.lapis())}, "", COLOR_ACCENT, -1F, null,
                    0, InfoLine.CONTROL_NONE, InfoLine.LINK_NONE,
                    "gui.gensokyou.ritual.omoikane.slot_book_tip", new String[0]);
        }
        if (plan.entryCount() <= 0) {
            return new InfoLine("gui.gensokyou.ritual.omoikane.slot_gear_none", new String[0],
                    "", COLOR_IDLE, -1F, null, 0, InfoLine.CONTROL_NONE, InfoLine.LINK_NONE,
                    "gui.gensokyou.ritual.omoikane.slot_gear_none_tip", new String[0]);
        }
        List<String> parts = new ArrayList<>();
        for (var e : plan.levels().entrySet()) {
            parts.add(e.getKey().value().description().getString() + " " + e.getValue());
        }
        return new InfoLine("gui.gensokyou.ritual.omoikane.slot_gear",
                new String[]{String.valueOf(plan.entryCount())}, "", COLOR_ACCENT, -1F, null,
                0, InfoLine.CONTROL_NONE, InfoLine.LINK_NONE,
                "gui.gensokyou.ritual.omoikane.slot_gear_tip",
                new String[]{String.join("\n", parts)});
    }

    private static InfoLine stateLine(Mode mode, int entries, boolean affordable,
                                      PedestalScan scan) {
        if (mode == Mode.NONE) {
            return new InfoLine("gui.gensokyou.ritual.omoikane.no_item", new String[0], "",
                    COLOR_IDLE, -1F, null);
        }
        if (entries <= 0) {
            return new InfoLine("gui.gensokyou.ritual.omoikane.no_offerings", new String[0], "",
                    COLOR_IDLE, -1F, null);
        }
        if (!affordable) {
            return new InfoLine("gui.gensokyou.ritual.omoikane.no_power", new String[0], "",
                    COLOR_WARN, -1F, null);
        }
        return new InfoLine("gui.gensokyou.ritual.omoikane.ready",
                new String[]{String.valueOf(entries)}, "", COLOR_OK, -1F, null);
    }
}
