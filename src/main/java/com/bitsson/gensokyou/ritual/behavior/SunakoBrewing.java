package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.ritual.SpiritPowerAccess;

import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import com.bitsson.gensokyou.block.entity.RitualPedestalBlockEntity;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.network.InfoLine;
import com.bitsson.gensokyou.network.ModNetworking;
import com.bitsson.gensokyou.registry.ModBlocks;
import com.bitsson.gensokyou.registry.ModItems;
import com.bitsson.gensokyou.ritual.RitualMatch;
import com.bitsson.gensokyou.ritual.RitualPedestals;
import com.bitsson.gensokyou.ritual.brew.BrewReagentIndex;
import com.bitsson.gensokyou.ritual.potion.PotionTierTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 少名（`gensokyou:sunako_circle`）的批次炼药结算。
 *
 * <p><b>语义</b>：手动点一次 = 一个批次，产出 {@code min(有效台数, 缓存 ÷ 单价)} 瓶，
 * <b>不做足额预检</b>——灵力不足时按可支付数量部分产出（需求原文："能做几份产几份，
 * 而不是完全不启动"）。整批于启动所在 tick 内一次性写回祭品台（MUST NOT 走
 * {@code RitualOutputs.spawn} 的掉落圆盘）。
 *
 * <p><b>扣费口径</b>：只用<b>核心自身缓存</b>（{@code core.getStored()} / {@code core.extract()}），
 * 刻意<b>不</b>用 {@code SpiritPowerHelper} 的三段式来源——后者会把半径 3 内其他仪式核心的
 * 灵力计入，与"灵力永远只是使用缓存"冲突。判定与扣费同口径，故不存在"算得出、扣不到"。
 *
 * <p><b>祭品台</b>：只认瓶装三途川水（{@code gensokyou:sanzu_flask}）；其余物品与空台
 * <b>完全忽略</b>（不计数、不消耗、不清空）。产出<b>原位替换</b>原料，写前复验台面仍是
 * 启动瞬间的那件，避免把玩家中途换上的东西顶掉。
 *
 * <p><b>试剂</b>：额外槽每批消耗 1 个（槽位容量恒为 1）。结算成功后才清槽——
 * 零产出（无台 / 灵力不足 / 写回全灭）时试剂原样保留。
 */
public final class SunakoBrewing {

    /** 试剂占据的额外槽下标。 */
    public static final int REAGENT_SLOT = 0;

    private static final int COLOR_ACCENT = 0xFF7FD4E8;
    private static final int COLOR_WARN = 0xFFE0A030;
    private static final int COLOR_OK = 0xFF2E8B57;
    private static final int COLOR_IDLE = 0xFFAAAAAA;

    /** 祭品台投放现状：总台数 / 放了瓶装三途川水的台数 / 放了其他东西的台数。 */
    public record PedestalScan(int pedestals, int water, int other) {

        public static final PedestalScan NONE = new PedestalScan(0, 0, 0);
    }

    /** 一次批次的结果，供 GUI 与调试读取。 */
    public record Outcome(int brewed, long spent, long storedBefore, long storedAfter) {

        public static final Outcome NONE = new Outcome(0, 0L, 0L, 0L);

        public boolean producedAnything() {
            return brewed > 0;
        }
    }

    private SunakoBrewing() {
    }

    // ------------------------------------------------------------------ 祭品台扫描

    /**
     * 仪式结构内是否放置了 {@code gensokyou:magic_wood}——它决定 mod 试剂是否被接受。
     *
     * <p><b>为什么扫结构包围盒而不是加一个 palette 键</b>：加 palette 键会把魔法木变成
     * <b>必需</b>方块，直接改变 pattern 的搭建要求与既有存档的匹配结果；而需求是"可选构件"。
     * 结构包围盒（{@code structureMinY/MaxY/RadiusXZ}）由核心在重扫时写入，是现成的边界来源，
     * 一次遍历成本随结构规模线性增长，仪式只在点击与 GUI 刷新时各扫一遍，可接受。
     *
     * <p><b>退化路径</b>：本方法的返回值只影响"是否接受 mod 试剂"，与祭品台机制无关，
     * 因此若将来要改成"祭品台放魔法木"，只需把这里的扫描换成台面判定。
     */
    public static boolean hasBrewingCore(ServerLevel level, SpiritPowerAccess core) {
        BlockPos center = core.getBlockPos();
        int minY = core.structureMinY();
        int maxY = core.structureMaxY();
        int radius = core.structureRadiusXZ();
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-radius, minY, -radius),
                center.offset(radius, maxY, radius))) {
            if (level.getBlockState(pos).is(ModBlocks.MAGIC_WOOD.get())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 当前试剂是否是 mod 专属试剂（四种 mod 植物）。这些试剂只有在结构内有魔法木时才可用。
     */
    public static boolean isModReagent(ItemStack reagent) {
        if (reagent == null || reagent.isEmpty()) {
            return false;
        }
        return reagent.is(ModItems.SPIRIT_HERB.get())
                || reagent.is(ModItems.MAGIC_MUSHROOM.get())
                || reagent.is(ModItems.GENTIAN.get())
                || reagent.is(ModItems.HIGANBANA.get());
    }

    /**
     * 扫一遍祭品台。{@code other} 只用于 GUI 展示"有东西但不是三途川水"，
     * <b>不参与</b>任何产出计算，也不被消耗或清空。
     */
    public static PedestalScan scanPedestals(ServerLevel level, RitualMatch match) {
        int pedestals = 0;
        int water = 0;
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
            if (held.is(ModItems.SANZU_FLASK.get())) {
                water++;
            } else {
                other++;
            }
        }
        return new PedestalScan(pedestals, water, other);
    }

    /** 有效台位（放了三途川水的）规范序坐标，供结算按序取用。 */
    public static List<BlockPos> validPedestals(ServerLevel level, RitualMatch match) {
        List<BlockPos> valid = new ArrayList<>();
        for (BlockPos pos : RitualPedestals.positions(match)) {
            if (level.getBlockEntity(pos) instanceof RitualPedestalBlockEntity pedestal
                    && pedestal.getHeld().is(ModItems.SANZU_FLASK.get())) {
                valid.add(pos.immutable());
            }
        }
        return valid;
    }

    // ------------------------------------------------------------------ 批次结算

    /** 试剂槽当前解析出的结果（GUI 预览用；解析失败返回空）。 */
    public static Optional<BrewReagentIndex.Resolution> reagent(ServerLevel level,
                                                                SpiritPowerAccess core) {
        return BrewReagentIndex.resolve(level, core.extraSlot(REAGENT_SLOT));
    }

    /**
     * 跑一个批次。
     *
     * @return 实际产出；零产出（无试剂 / 无有效台 / 灵力不足一瓶）时返回
     *         {@link Outcome#NONE} 且<b>零消耗</b>
     */
    public static Outcome brew(ServerLevel level, BlockPos corePos, RitualMatch match,
                               SpiritPowerAccess core) {
        Optional<BrewReagentIndex.Resolution> resolution = reagent(level, core);
        if (resolution.isEmpty()) {
            return Outcome.NONE;
        }
        // mod 试剂门槛：结构内没有魔法木时拒绝炼 mod 药水，零消耗返回失败
        if (isModReagent(core.extraSlot(REAGENT_SLOT))
                && !hasBrewingCore(level, core)) {
            return Outcome.NONE;
        }
        List<BlockPos> valid = validPedestals(level, match);
        if (valid.isEmpty()) {
            return Outcome.NONE;
        }
        int ritualLevel = match.level();
        long storedBefore = core.getStored();
        int bottles = SunakoScaling.affordableBottles(valid.size(), storedBefore, ritualLevel);
        if (bottles <= 0) {
            return Outcome.NONE;
        }
        long cost = SunakoScaling.batchCost(bottles, ritualLevel);
        long paid = core.extract(cost);
        if (paid < cost) {
            // 与判定同口径，理论上不可达；真发生了就退回已扣部分并放弃本批次
            core.receive(paid);
            return Outcome.NONE;
        }
        BrewReagentIndex.Resolution resolved = resolution.get();
        int brewed = 0;
        for (BlockPos pos : valid) {
            if (brewed >= bottles) {
                break;
            }
            if (!(level.getBlockEntity(pos) instanceof RitualPedestalBlockEntity pedestal)) {
                continue;
            }
            ItemStack held = pedestal.getHeld();
            // 写前复验：玩家在"扫描 → 写回"之间把原料换成了别的东西时跳过该台，不顶掉
            if (!held.is(ModItems.SANZU_FLASK.get())) {
                continue;
            }
            pedestal.setHeld(PotionTierTransform.build(
                    resolved.base(), resolved.source(), ritualLevel, resolved.options()));
            brewed++;
        }
        if (brewed <= 0) {
            core.receive(paid);
            return Outcome.NONE;
        }
        // 试剂随批次消耗：槽位容量恒为 1（setExtraSlot 归一），一次炼成扣掉一个。
        // 注意：resolve 用的也是同一槽位，故此处必须在 resolve 返回值拿到之后再清。
        core.setExtraSlot(REAGENT_SLOT, ItemStack.EMPTY);
        // 产出数可能被复验削减（玩家中途换料），按实际产出退差额
        if (brewed < bottles) {
            core.receive(SunakoScaling.batchCost(bottles - brewed, ritualLevel));
        }
        core.triggerSacrificeFx(GensokyouConfig.FX_PILLAR_TICKS.get());
        ModNetworking.sendRitualInfoToViewers(level, corePos);
        return new Outcome(brewed, SunakoScaling.batchCost(brewed, ritualLevel), storedBefore,
                core.getStored());
    }

    // ------------------------------------------------------------------ GUI

    public static List<InfoLine> uiInfo(ServerLevel level, BlockPos corePos, RitualMatch match,
                                        SpiritPowerAccess core) {
        int ritualLevel = match.level();
        PedestalScan scan = scanPedestals(level, match);
        long unitCost = SunakoScaling.unitCostOf(ritualLevel);
        long stored = core.getStored();
        Optional<BrewReagentIndex.Resolution> resolution = reagent(level, core);
        int affordable = SunakoScaling.affordableBottles(scan.water(), stored, ritualLevel);

        List<InfoLine> lines = new ArrayList<>();
        // 产出预览走**纯文字行**：真槽位（左下 9,61）已经把试剂画出来了，信息栏再画一个
        // 带框的物品格就会并排出现两个"原料槽"（实机反馈）。
        lines.add(previewLine(ritualLevel, resolution));
        lines.add(stateLine(ritualLevel, level, core, scan, stored, affordable, resolution));
        // 台位：可见行只放两个数，"放了别的东西"的台数下沉 tooltip
        lines.add(InfoLine.tipped("gui.gensokyou.ritual.sunako.pedestals",
                new String[]{String.valueOf(scan.water()), String.valueOf(scan.pedestals())},
                COLOR_ACCENT, "gui.gensokyou.ritual.sunako.pedestals_tip",
                new String[]{String.valueOf(scan.water()), String.valueOf(scan.pedestals()),
                        String.valueOf(scan.other())}));
        // 缓存：紧凑显示，明细入 tooltip
        lines.add(InfoLine.tipped("gui.gensokyou.ritual.sunako.cache",
                new String[]{InfoLine.compact(stored),
                        InfoLine.compact(SunakoScaling.capacityOf(ritualLevel))},
                COLOR_ACCENT, "gui.gensokyou.ritual.sunako.cache_tip",
                new String[]{String.valueOf(stored),
                        String.valueOf(SunakoScaling.capacityOf(ritualLevel))}));
        lines.add(new InfoLine("gui.gensokyou.ritual.sunako.in_rate",
                new String[]{InfoLine.compact(SunakoScaling.inRateOf(ritualLevel))},
                "", COLOR_ACCENT, -1F, null));
        // 单价 / 本批总耗灵：两个字段一行（紧凑数字），精确值入 tooltip
        lines.add(InfoLine.tipped("gui.gensokyou.ritual.sunako.cost",
                new String[]{InfoLine.compact(unitCost),
                        InfoLine.compact(SunakoScaling.batchCost(scan.water(), ritualLevel))},
                COLOR_ACCENT, "gui.gensokyou.ritual.sunako.cost_tip",
                new String[]{String.valueOf(unitCost),
                        String.valueOf(SunakoScaling.batchCost(scan.water(), ritualLevel))}));
return lines;
    }

    /**
     * 产出预览行：<b>纯文字</b>，刻意<b>不</b>画 {@link InfoLine#CONTROL_ITEM} 槽框。
     *
     * <p>踩过的坑：核心 GUI 左下角那个<b>真槽位</b>（{@code RitualExtraMenu} 的 extra slot，
     * 坐标 9,61）本来就把试剂画出来了。信息栏若再画一个带框的试剂行，放齐材料后界面里就会
     * 并排出现**两个**装着同一件东西的"原料槽"（实机反馈）。空槽时那个真槽位也照样可见，
     * 所以玩家早就能看见这里能放东西——信息栏完全不需要重复画一遍。
     */
    private static InfoLine previewLine(int level,
                                        Optional<BrewReagentIndex.Resolution> resolution) {
        if (resolution.isEmpty()) {
            return new InfoLine("gui.gensokyou.ritual.sunako.reagent_slot",
                    new String[0], "", COLOR_IDLE, -1F, null, 0, InfoLine.CONTROL_NONE,
                    InfoLine.LINK_NONE, "gui.gensokyou.ritual.sunako.reagent_slot_tip",
                    new String[0]);
        }
        BrewReagentIndex.Resolution resolved = resolution.get();
        String name = PotionTierTransform
                .build(resolved.base(), resolved.source(), level, resolved.options())
                .getHoverName().getString();
        return new InfoLine("gui.gensokyou.ritual.sunako.reagent_slot",
                new String[]{name}, "", COLOR_ACCENT, -1F, null, 0, InfoLine.CONTROL_NONE,
                InfoLine.LINK_NONE, "gui.gensokyou.ritual.sunako.reagent_slot_tip",
                effectSummary(level, resolved));
    }

    /** 供 tooltip 用的效果明细（品质以 amp+1 的"罗马数字级别"口述，避免玩家对不上"力量 2"）。 */
    private static String[] effectSummary(int level, BrewReagentIndex.Resolution resolved) {
        List<String> parts = new ArrayList<>();
        for (var instance : PotionTierTransform.apply(resolved.source(), level, resolved.options())) {
            parts.add(instance.getEffect().value().getDescriptionId()
                    + " " + (instance.getAmplifier() + 1)
                    + " / " + (instance.getDuration() / 20) + "s");
        }
        return new String[]{String.join("\n", parts)};
    }

    private static InfoLine stateLine(int level, ServerLevel serverLevel, SpiritPowerAccess core,
                                      PedestalScan scan, long stored, int affordable,
                                      Optional<BrewReagentIndex.Resolution> resolution) {
        if (resolution.isEmpty()) {
            return new InfoLine("gui.gensokyou.ritual.sunako.no_reagent", new String[0], "",
                    COLOR_IDLE, -1F, null);
        }
        // mod 试剂但结构内没有魔法木：给出明确原因，而不是笼统的"无法炼制"
        if (isModReagent(core.extraSlot(REAGENT_SLOT))
                && !hasBrewingCore(serverLevel, core)) {
            return new InfoLine("gui.gensokyou.ritual.sunako.no_magic_wood",
                    new String[0], "", COLOR_WARN, -1F, null);
        }
        if (scan.water() <= 0) {
            return new InfoLine("gui.gensokyou.ritual.sunako.no_water", new String[0], "",
                    COLOR_IDLE, -1F, null);
        }
        if (affordable <= 0) {
            return new InfoLine("gui.gensokyou.ritual.sunako.no_power",
                    new String[0], "", COLOR_WARN, -1F, null);
        }
        return new InfoLine("gui.gensokyou.ritual.sunako.ready",
                new String[]{String.valueOf(affordable), String.valueOf(scan.water())},
                "", COLOR_OK, -1F, null);
    }
}