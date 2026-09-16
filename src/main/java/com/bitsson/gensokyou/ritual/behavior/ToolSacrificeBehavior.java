package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import com.bitsson.gensokyou.block.entity.RitualPedestalBlockEntity;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.network.InfoLine;
import com.bitsson.gensokyou.network.ModNetworking;
import com.bitsson.gensokyou.ritual.RitualBehavior;
import com.bitsson.gensokyou.ritual.RitualLootLoader;
import com.bitsson.gensokyou.ritual.RitualLootTable;
import com.bitsson.gensokyou.ritual.RitualMatch;
import com.bitsson.gensokyou.ritual.RitualPedestals;
import com.bitsson.gensokyou.spirit.SpiritPowerHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具献祭仪式族（大山祇神之座 / 久久能智神庭 / 埴山姬神之壤 / 草野姬神之亭）共同行为。
 *
 * <p>持续运行型（pattern {@code toggleable:true}）：启动后每 tick 监控祭品台，
 * 「有合规工具 + 灵力足够 + 冷却已过」即结算一次——随机消耗一把工具、扣
 * {@code BASE_SP_COST × 4^L} 灵力、按工具材质掷 {@code BASE_COUNT × 4^L} 次加权产出
 * （同类聚合、核心上方空投），随后强制冷却 {@code COOLDOWN_TICKS}；任一条件不满足则待机。
 *
 * <p>工具类别由 loot 数据的 {@code toolTag} 判定，材质经 {@code TieredItem#getTier()} 归一
 * （读不出回退最低档）；附魔不影响任何语义。祭品台上 ≥{@code skullsRequired} 个凋灵骷髅头
 * 解锁地狱池、≥{@code dragonHeadsRequired} 个龙首解锁末地池，头颅**不消耗**。
 */
public abstract class ToolSacrificeBehavior implements RitualBehavior {

    /** 单个合规工具（台位 + 持有栈）。 */
    public record PedestalTool(BlockPos pos, ItemStack stack) {
    }

    /** 结算诊断（调试命令回显）。 */
    public record Settlement(String tier, boolean nether, boolean end,
                             int produced, Map<Item, Integer> drops) {
    }

    /** lang 前缀（例：{@code oyamatsumi}）。 */
    protected abstract String langPrefix();

    /** 状态行强调色（ARGB）。 */
    protected abstract int accentColor();

    // ---- 世界无关纯内核（调试命令可直调）----

    /** 单次产出件数 = 基值 × 倍率^等级。 */
    public static int producedCount(int level) {
        long value = GensokyouConfig.SACRIFICE_BASE_COUNT.get();
        long mult = GensokyouConfig.SACRIFICE_COUNT_MULT.get();
        for (int i = 0; i < level; i++) {
            value *= mult;
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, value));
    }

    /** 单次灵力消耗 = 基值 × 倍率^等级。 */
    public static long spiritCost(int level) {
        long value = GensokyouConfig.SACRIFICE_BASE_SP_COST.get();
        long mult = GensokyouConfig.SACRIFICE_SP_COST_MULT.get();
        for (int i = 0; i < level; i++) {
            value *= mult;
        }
        return Math.max(0L, value);
    }

    /** tick → 秒（向上取整），供 GUI 显示。 */
    public static int toSeconds(int ticks) {
        return Math.max(0, (ticks + 19) / 20);
    }

    // ---- 世界侧扫描 ----

    /** 全部持有合规工具的祭品台（按规范序）。 */
    public static List<PedestalTool> scanTools(ServerLevel level, RitualMatch match,
                                               RitualLootTable table) {
        List<PedestalTool> tools = new ArrayList<>();
        for (BlockPos pos : RitualPedestals.positions(match)) {
            if (level.getBlockEntity(pos) instanceof RitualPedestalBlockEntity pedestal) {
                ItemStack held = pedestal.getHeld();
                if (!held.isEmpty() && held.is(table.toolTag())) {
                    tools.add(new PedestalTool(pos, held));
                }
            }
        }
        return tools;
    }

    /** 台上凋灵骷髅头数量。 */
    public static int countSkulls(ServerLevel level, RitualMatch match) {
        return countItem(level, match, Items.WITHER_SKELETON_SKULL);
    }

    /** 台上龙首数量。 */
    public static int countDragonHeads(ServerLevel level, RitualMatch match) {
        return countItem(level, match, Items.DRAGON_HEAD);
    }

    private static int countItem(ServerLevel level, RitualMatch match, Item item) {
        int count = 0;
        for (BlockPos pos : RitualPedestals.positions(match)) {
            if (level.getBlockEntity(pos) instanceof RitualPedestalBlockEntity pedestal
                    && pedestal.getHeld().is(item)) {
                count++;
            }
        }
        return count;
    }

    /** 工具材质键（附魔无关；自定义 Tier 回退 wood）。 */
    public static String tierKey(ItemStack stack) {
        if (stack.getItem() instanceof TieredItem tieredItem) {
            return RitualLootTable.tierKeyOf(tieredItem.getTier());
        }
        return "wood";
    }

    /** 组装该工具材质 + 条件解锁后的实际抽取池。 */
    public static List<RitualLootTable.Weighted> poolFor(RitualLootTable table, String tier,
                                                         boolean nether, boolean end) {
        RitualLootTable.TierTable tierTable = table.table(tier)
                .orElseGet(() -> table.tables().get(0));
        return RitualLootTable.buildPool(table.commons(), table.commonsTotal(),
                tierTable, nether, end);
    }

    /** 核心上方最高可穿过位置（上限内，首个遮挡方块之前）；返回落物基准 Y。 */
    public static int dropHeight(ServerLevel level, BlockPos corePos) {
        int maxHeight = GensokyouConfig.SACRIFICE_FALL_MAX_HEIGHT.get();
        int top = corePos.getY();
        for (int i = 1; i <= maxHeight; i++) {
            BlockPos p = corePos.above(i);
            BlockState state = level.getBlockState(p);
            if (!state.getCollisionShape(level, p, CollisionContext.empty()).isEmpty()) {
                break;
            }
            top = p.getY();
        }
        return top + 1;
    }

    // ---- RitualBehavior ----

    @Override
    public long spiritInRatePerSecond(ServerLevel level, BlockPos corePos, RitualMatch match,
                                      RitualCoreBlockEntity core) {
        return GensokyouConfig.SACRIFICE_SPIRIT_IN_RATE.get().longValue();
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos corePos, RitualMatch match,
                           RitualCoreBlockEntity core) {
        RitualLootTable table = RitualLootLoader.byPattern(match.patternId()).orElse(null);
        if (table == null) {
            return;
        }
        if (core.actionCooldown() > 0) {
            return;
        }
        List<PedestalTool> tools = scanTools(level, match, table);
        if (tools.isEmpty()) {
            return;
        }
        long cost = spiritCost(match.level());
        if (!SpiritPowerHelper.canCover(level, corePos, core, cost)) {
            return;
        }
        // 条件解锁（头颅不消耗，仅作存在条件）
        int skulls = countSkulls(level, match);
        int heads = countDragonHeads(level, match);
        boolean nether = skulls >= table.skullsRequired();
        boolean end = heads >= table.dragonHeadsRequired();

        // 随机抽一把工具（其余留守）
        PedestalTool chosen = tools.get(level.random.nextInt(tools.size()));
        String tier = tierKey(chosen.stack());

        // 提交：先扣费（全有全无）后消耗工具；canCover 同 tick 通过则扣费必成
        if (!SpiritPowerHelper.payCost(level, corePos, core, cost)) {
            return;
        }
        consumeTool(level, chosen);

        // 掷骰 + 聚合 + 空投
        List<RitualLootTable.Weighted> pool = poolFor(table, tier, nether, end);
        int count = producedCount(match.level());
        Map<Item, Integer> agg = RitualLootTable.rollMany(pool, count, level.random);
        dropAll(level, corePos, agg);

        core.setActionCooldown(GensokyouConfig.SACRIFICE_COOLDOWN_TICKS.get());
        core.triggerSacrificeFx(GensokyouConfig.FX_PILLAR_TICKS.get());
        ModNetworking.sendRitualInfoToViewers(level, corePos);
    }

    /** 消耗台面上 1 件（单件不变量 → 置空）。 */
    private static void consumeTool(ServerLevel level, PedestalTool tool) {
        if (level.getBlockEntity(tool.pos()) instanceof RitualPedestalBlockEntity pedestal) {
            ItemStack held = pedestal.getHeld();
            int remaining = Math.max(0, held.getCount() - 1);
            pedestal.setHeld(remaining == 0 ? ItemStack.EMPTY : held.copyWithCount(remaining));
        }
    }

    /** 聚合产物空投：XZ 复用被动掉落圆盘半径，同类按最大堆叠拆叠。 */
    private static void dropAll(ServerLevel level, BlockPos corePos, Map<Item, Integer> agg) {
        if (agg.isEmpty()) {
            return;
        }
        double radius = GensokyouConfig.RITUAL_OUTPUT_DROP_RADIUS.get();
        double spawnY = dropHeight(level, corePos) + 0.2D;
        List<ItemStack> stacks = new ArrayList<>();
        for (Map.Entry<Item, Integer> entry : agg.entrySet()) {
            int remaining = entry.getValue();
            int max = Math.max(1, new ItemStack(entry.getKey()).getMaxStackSize());
            while (remaining > 0) {
                int n = Math.min(remaining, max);
                stacks.add(new ItemStack(entry.getKey(), n));
                remaining -= n;
            }
        }
        for (ItemStack stack : stacks) {
            double angle = level.random.nextDouble() * Math.PI * 2D;
            double r = radius * Math.sqrt(level.random.nextDouble());
            ItemEntity drop = new ItemEntity(level,
                    corePos.getX() + 0.5D + r * Math.cos(angle),
                    spawnY,
                    corePos.getZ() + 0.5D + r * Math.sin(angle),
                    stack);
            drop.setDeltaMovement(0D, 0D, 0D);
            drop.setDefaultPickUpDelay();
            level.addFreshEntity(drop);
        }
    }

    // ---- UI ----

    /** 状态行键显式列举（拼接前缀会被 lang_audit 当字面量误报）。 */
    private static final String KEY_DISABLED = "gui.gensokyou.ritual.sacrifice.disabled";
    private static final String KEY_MISSING_DATA = "gui.gensokyou.ritual.sacrifice.missing_data";
    private static final String KEY_COOLING = "gui.gensokyou.ritual.sacrifice.cooling";
    private static final String KEY_COOLDOWN = "gui.gensokyou.ritual.sacrifice.cooldown";
    private static final String KEY_NO_TOOL = "gui.gensokyou.ritual.sacrifice.no_tool";
    private static final String KEY_NO_POWER = "gui.gensokyou.ritual.sacrifice.no_power";
    private static final String KEY_READY = "gui.gensokyou.ritual.sacrifice.ready";
    private static final String KEY_TOOLS = "gui.gensokyou.ritual.sacrifice.tools";
    private static final String KEY_NETHER_ON = "gui.gensokyou.ritual.sacrifice.nether_on";
    private static final String KEY_NETHER_OFF = "gui.gensokyou.ritual.sacrifice.nether_off";
    private static final String KEY_END_ON = "gui.gensokyou.ritual.sacrifice.end_on";
    private static final String KEY_END_OFF = "gui.gensokyou.ritual.sacrifice.end_off";

    @Override
    public List<InfoLine> uiInfo(ServerLevel level, BlockPos corePos, RitualMatch match,
                                 RitualCoreBlockEntity core) {
        List<InfoLine> lines = new ArrayList<>();
        int color = accentColor();
        RitualLootTable table = RitualLootLoader.byPattern(match.patternId()).orElse(null);
        if (table == null) {
            lines.add(new InfoLine(KEY_MISSING_DATA, new String[0], "", color, -1F, null));
            return lines;
        }
        List<PedestalTool> tools = scanTools(level, match, table);
        int skulls = countSkulls(level, match);
        int heads = countDragonHeads(level, match);
        boolean nether = skulls >= table.skullsRequired();
        boolean end = heads >= table.dragonHeadsRequired();

        String stateKey;
        String[] stateArgs = new String[0];
        if (!core.isEnabled()) {
            stateKey = KEY_DISABLED;
        } else if (core.actionCooldown() > 0) {
            stateKey = KEY_COOLING;
            stateArgs = new String[]{String.valueOf(toSeconds(core.actionCooldown()))};
        } else if (tools.isEmpty()) {
            stateKey = KEY_NO_TOOL;
        } else if (!SpiritPowerHelper.canCover(level, corePos, core, spiritCost(match.level()))) {
            stateKey = KEY_NO_POWER;
        } else {
            stateKey = KEY_READY;
        }
        lines.add(new InfoLine(stateKey, stateArgs, "", color, -1F, null));
        lines.add(new InfoLine(KEY_COOLDOWN,
                new String[]{String.valueOf(toSeconds(GensokyouConfig.SACRIFICE_COOLDOWN_TICKS.get()))},
                "", color, -1F, null));
        int pedestals = RitualPedestals.positions(match).size();
        lines.add(new InfoLine(KEY_TOOLS,
                new String[]{String.valueOf(pedestals), String.valueOf(tools.size())},
                "", color, -1F, null));
        lines.add(new InfoLine(nether ? KEY_NETHER_ON : KEY_NETHER_OFF,
                new String[]{String.valueOf(skulls)}, "", color, -1F, null));
        lines.add(new InfoLine(end ? KEY_END_ON : KEY_END_OFF,
                new String[]{String.valueOf(heads)}, "", color, -1F, null));
        return lines;
    }

    /** 调试用：当前材质键（无工具返回 null）。 */
    @Nullable
    public static String debugTier(ServerLevel level, RitualMatch match, RitualLootTable table) {
        List<PedestalTool> tools = scanTools(level, match, table);
        return tools.isEmpty() ? null : tierKey(tools.get(0).stack());
    }

    /** 调试用：按当前条件与首个工具材质组装抽取池。 */
    public static Map<String, Double> debugPool(RitualLootTable table, String tier,
                                                boolean nether, boolean end) {
        Map<String, Double> out = new LinkedHashMap<>();
        for (RitualLootTable.Weighted w : poolFor(table, tier, nether, end)) {
            String id = BuiltInRegistries.ITEM.getKey(w.item()).toString();
            out.merge(id, w.weight(), Double::sum);
        }
        return out;
    }
}
