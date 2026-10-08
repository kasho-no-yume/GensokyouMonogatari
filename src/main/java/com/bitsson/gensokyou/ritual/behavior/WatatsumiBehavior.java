package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.ritual.SpiritPowerAccess;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import com.bitsson.gensokyou.block.entity.RitualPedestalBlockEntity;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.network.InfoLine;
import com.bitsson.gensokyou.network.ModNetworking;
import com.bitsson.gensokyou.ritual.RitualBehavior;
import com.bitsson.gensokyou.ritual.RitualLootTable;
import com.bitsson.gensokyou.ritual.RitualMatch;
import com.bitsson.gensokyou.ritual.RitualPedestals;
import com.bitsson.gensokyou.ritual.WatatsumiSpecialLoot;
import com.bitsson.gensokyou.ritual.WatatsumiSpecialLootLoader;
import com.bitsson.gensokyou.spirit.SpiritPowerHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 绵津见神之藏（{@code watatsumi_circle}）：献祭钓鱼竿产水产/海洋特产的持续运行仪式。
 *
 * <p><b>刻意不继承 {@link ToolSacrificeBehavior}</b>：钓鱼竿非 {@code TieredItem}（无材质轴）、产物来自
 * 原版掉落表、解锁维度是仪式等级而非头颅条件池、且产出为「钓鱼池 + 海洋特产池」两个独立池。仅调用其
 * 无状态工具（{@link ToolSacrificeBehavior#spiritCost}、
 * {@link ToolSacrificeBehavior.PedestalTool}）。
 *
 * <p>结算：随机消耗 1 根 {@code gensokyou:fishing_rods} → 扣 {@code 4000×4^L} 灵力 →
 * 钓鱼池掷 {@code 5/10/40} 次（直接掷原版 fish/junk/treasure 子表，保真数量/损耗/附魔/NBT；treasure
 * 仅 L≥1，L0 归一化）→ 特产池掷 {@code 0/10/40} 次（数据驱动）→ L2 额外 0.1% 整表埋藏宝藏 →
 * 空投 + 水蓝光柱 → 动态冷却（基础 1min / 命中大奖 10min）。
 */
public class WatatsumiBehavior implements RitualBehavior {

    @Override
    public long capacity(int level, SpiritPowerAccess core) {
        return RitualCoreBlockEntity.sacrificeCapacity(level);
    }

    /** 合规钓鱼竿标签（原版无 fishing_rods 标签，故自建）。 */
    public static final TagKey<Item> FISHING_RODS =
            TagKey.create(Registries.ITEM, Gensokyou.id("fishing_rods"));

    /** 原版钓鱼根表类别权重（luck=0 时 quality 无效果）；treasure 仅在等级 ≥1 进入池。 */
    private static final double CATEGORY_FISH = 85.0D;
    private static final double CATEGORY_JUNK = 10.0D;
    private static final double CATEGORY_TREASURE = 5.0D;

    private static final int ACCENT = 0xFF4FC3F7;

    // ---- 世界无关纯内核 ----

    /** 单次总产出件数 = 基值 × 倍率^等级（默认 5 × 4^L = 5/20/80）。 */
    public static int totalCount(int level) {
        long value = GensokyouConfig.WATATSUMI_BASE_COUNT.get();
        long mult = GensokyouConfig.WATATSUMI_COUNT_MULT.get();
        for (int i = 0; i < level; i++) {
            value *= mult;
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, value));
    }

    /** 海洋特产池掷数：0 阶 0；1 阶及以上取总量一半。 */
    public static int specialCount(int level) {
        return level <= 0 ? 0 : totalCount(level) / 2;
    }

    /** 钓鱼池掷数 = 总量 − 特产池。 */
    public static int fishingCount(int level) {
        return totalCount(level) - specialCount(level);
    }

    /** 宝藏（原版 treasure 子表）仅 1 阶及以上解锁。 */
    public static boolean treasureUnlocked(int level) {
        return level >= 1;
    }

    /** 海洋特产池仅 1 阶及以上解锁。 */
    public static boolean specialUnlocked(int level) {
        return level >= 1;
    }

    /** 灵力消耗沿用献祭家族共用公式（4000 × 4^L）。 */
    public static long spiritCost(int level) {
        return ToolSacrificeBehavior.spiritCost(level);
    }

    /** 本次结算冷却：命中额外奖励走 10 分钟，否则 1 分钟。 */
    public static int cooldownTicks(boolean bonusTriggered) {
        return bonusTriggered
                ? GensokyouConfig.WATATSUMI_BONUS_COOLDOWN_TICKS.get()
                : GensokyouConfig.WATATSUMI_BASE_COOLDOWN_TICKS.get();
    }

    /** tick → 秒（向上取整），供 GUI 显示。 */
    public static int toSeconds(int ticks) {
        return Math.max(0, (ticks + 19) / 20);
    }

    /** 原版类别选择（L0 时 treasure 权重为 0 → 归一化到 fish/junk）。 */
    public static ResourceKey<LootTable> pickCategory(RandomSource random, boolean treasureUnlocked) {
        double treasure = treasureUnlocked ? CATEGORY_TREASURE : 0.0D;
        double total = CATEGORY_FISH + CATEGORY_JUNK + treasure;
        double pick = random.nextDouble() * total;
        if (pick < CATEGORY_FISH) {
            return BuiltInLootTables.FISHING_FISH;
        }
        if (pick < CATEGORY_FISH + CATEGORY_JUNK) {
            return BuiltInLootTables.FISHING_JUNK;
        }
        return BuiltInLootTables.FISHING_TREASURE;
    }

    // ---- 世界侧扫描 ----

    /** 全部持有合规钓鱼竿的祭品台（按规范序）。 */
    public static List<ToolSacrificeBehavior.PedestalTool> scanRods(ServerLevel level, RitualMatch match) {
        List<ToolSacrificeBehavior.PedestalTool> rods = new ArrayList<>();
        for (BlockPos pos : RitualPedestals.positions(match)) {
            if (level.getBlockEntity(pos) instanceof RitualPedestalBlockEntity pedestal) {
                ItemStack held = pedestal.getHeld();
                if (!held.isEmpty() && held.is(FISHING_RODS)) {
                    rods.add(new ToolSacrificeBehavior.PedestalTool(pos, held));
                }
            }
        }
        return rods;
    }

    private static void consumeRod(ServerLevel level, ToolSacrificeBehavior.PedestalTool rod) {
        if (level.getBlockEntity(rod.pos()) instanceof RitualPedestalBlockEntity pedestal) {
            ItemStack held = pedestal.getHeld();
            int remaining = Math.max(0, held.getCount() - 1);
            pedestal.setHeld(remaining == 0 ? ItemStack.EMPTY : held.copyWithCount(remaining));
        }
    }

    // ---- 掷骰（原版表保真 / 特产池数据驱动）----

    /** 钓鱼池：逐次选类别后直接掷原版子表（保留数量/损耗/附魔/NBT）。 */
    public static List<ItemStack> rollFishing(ServerLevel level, BlockPos corePos, ItemStack tool,
                                              int count, boolean treasureUnlocked) {
        List<ItemStack> out = new ArrayList<>();
        if (count <= 0) {
            return out;
        }
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(corePos))
                .withParameter(LootContextParams.TOOL, tool)
                .create(LootContextParamSets.FISHING);
        for (int i = 0; i < count; i++) {
            ResourceKey<LootTable> key = pickCategory(level.random, treasureUnlocked);
            out.addAll(level.getServer().reloadableRegistries().getLootTable(key)
                    .getRandomItems(params, level.random));
        }
        return out;
    }

    /** 海洋特产池：按权重掷 {@code count} 次，同类聚合拆叠；并入已解锁的幻想乡信物带。 */
    public static List<ItemStack> rollSpecial(ServerLevel level, int count,
                                              boolean gensokyouLow, boolean gensokyouHigh) {
        List<ItemStack> out = new ArrayList<>();
        if (count <= 0) {
            return out;
        }
        Map<Item, Integer> agg = WatatsumiSpecialLootLoader.table()
                .rollMany(count, gensokyouLow, gensokyouHigh, level.random);
        for (Map.Entry<Item, Integer> entry : agg.entrySet()) {
            int remaining = entry.getValue();
            int max = Math.max(1, new ItemStack(entry.getKey()).getMaxStackSize());
            while (remaining > 0) {
                int n = Math.min(remaining, max);
                out.add(new ItemStack(entry.getKey(), n));
                remaining -= n;
            }
        }
        return out;
    }

    /** L2 额外奖励：整表埋藏宝藏（含保底海洋之心）。 */
    public static List<ItemStack> rollBuriedTreasure(ServerLevel level, BlockPos corePos) {
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(corePos))
                .create(LootContextParamSets.CHEST);
        return new ArrayList<>(level.getServer().reloadableRegistries()
                .getLootTable(BuiltInLootTables.BURIED_TREASURE).getRandomItems(params, level.random));
    }

    /** 聚合产物空投：核心上 1 格、水平半径 3 圆盘内随机（统一落点工具）。 */
    public static void dropStacks(ServerLevel level, BlockPos corePos, List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            com.bitsson.gensokyou.ritual.RitualOutputs.spawn(level, corePos, stack);
        }
    }

    // ---- RitualBehavior ----

    @Override
    public long spiritInRatePerSecond(ServerLevel level, BlockPos corePos, RitualMatch match,
                                      SpiritPowerAccess core) {
        return GensokyouConfig.SACRIFICE_SPIRIT_IN_RATE.get().longValue();
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos corePos, RitualMatch match,
                           SpiritPowerAccess core) {
        if (core.actionCooldown() > 0) {
            return;
        }
        List<ToolSacrificeBehavior.PedestalTool> rods = scanRods(level, match);
        if (rods.isEmpty()) {
            return;
        }
        int ritualLevel = match.level();
        long cost = spiritCost(ritualLevel);
        if (!SpiritPowerHelper.canCover(level, corePos, core, cost)) {
            return;
        }
        ToolSacrificeBehavior.PedestalTool chosen = rods.get(level.random.nextInt(rods.size()));
        ItemStack rodCopy = chosen.stack().copy();

        // 提交：先扣费（全有全无）后消耗竿；canCover 同 tick 通过则扣费必成
        if (!SpiritPowerHelper.payCost(level, corePos, core, cost)) {
            return;
        }
        consumeRod(level, chosen);

        boolean treasure = treasureUnlocked(ritualLevel);
        List<ItemStack> outputs = new ArrayList<>();
        outputs.addAll(rollFishing(level, corePos, rodCopy, fishingCount(ritualLevel), treasure));
        if (specialUnlocked(ritualLevel)) {
            boolean gkLow = ToolSacrificeBehavior.countGuideBooks(level, match) >= 1;
            boolean gkHigh = ToolSacrificeBehavior.countSukimaFragments(level, match) >= 1;
            outputs.addAll(rollSpecial(level, specialCount(ritualLevel), gkLow, gkHigh));
        }
        boolean bonus = false;
        if (ritualLevel >= 2
                && level.random.nextDouble() < GensokyouConfig.WATATSUMI_BONUS_CHANCE.get()) {
            bonus = true;
            outputs.addAll(rollBuriedTreasure(level, corePos));
        }
        dropStacks(level, corePos, outputs);

        core.setActionCooldown(cooldownTicks(bonus));
        core.triggerSacrificeFx(GensokyouConfig.FX_PILLAR_TICKS.get());
        ModNetworking.sendRitualInfoToViewers(level, corePos);
    }

    // ---- UI ----

    private static final String KEY_DISABLED = "gui.gensokyou.ritual.watatsumi.disabled";
    private static final String KEY_COOLING = "gui.gensokyou.ritual.watatsumi.cooling";
    private static final String KEY_COOLDOWN = "gui.gensokyou.ritual.watatsumi.cooldown";
    private static final String KEY_NO_ROD = "gui.gensokyou.ritual.watatsumi.no_rod";
    private static final String KEY_NO_POWER = "gui.gensokyou.ritual.watatsumi.no_power";
    private static final String KEY_READY = "gui.gensokyou.ritual.watatsumi.ready";
    private static final String KEY_LEVEL = "gui.gensokyou.ritual.watatsumi.level";
    private static final String KEY_RODS = "gui.gensokyou.ritual.watatsumi.rods";
    private static final String KEY_TREASURE_ON = "gui.gensokyou.ritual.watatsumi.treasure_on";
    private static final String KEY_TREASURE_OFF = "gui.gensokyou.ritual.watatsumi.treasure_off";
    private static final String KEY_SPECIAL_ON = "gui.gensokyou.ritual.watatsumi.special_on";
    private static final String KEY_SPECIAL_OFF = "gui.gensokyou.ritual.watatsumi.special_off";

    @Override
    public List<InfoLine> uiInfo(ServerLevel level, BlockPos corePos, RitualMatch match,
                                 SpiritPowerAccess core) {
        List<InfoLine> lines = new ArrayList<>();
        int ritualLevel = match.level();
        List<ToolSacrificeBehavior.PedestalTool> rods = scanRods(level, match);

        String stateKey;
        String[] stateArgs = new String[0];
        if (!core.isEnabled()) {
            stateKey = KEY_DISABLED;
        } else if (core.actionCooldown() > 0) {
            stateKey = KEY_COOLING;
            stateArgs = new String[]{String.valueOf(toSeconds(core.actionCooldown()))};
        } else if (rods.isEmpty()) {
            stateKey = KEY_NO_ROD;
        } else if (!SpiritPowerHelper.canCover(level, corePos, core, spiritCost(ritualLevel))) {
            stateKey = KEY_NO_POWER;
        } else {
            stateKey = KEY_READY;
        }
        lines.add(new InfoLine(stateKey, stateArgs, "", ACCENT, -1F, null));
        lines.add(new InfoLine(KEY_COOLDOWN,
                new String[]{String.valueOf(toSeconds(GensokyouConfig.WATATSUMI_BASE_COOLDOWN_TICKS.get()))},
                "", ACCENT, -1F, null));
        int pedestals = RitualPedestals.positions(match).size();
        lines.add(new InfoLine(KEY_RODS,
                new String[]{String.valueOf(pedestals), String.valueOf(rods.size())},
                "", ACCENT, -1F, null));
        lines.add(new InfoLine(KEY_LEVEL,
                new String[]{String.valueOf(ritualLevel)}, "", ACCENT, -1F, null));
        lines.add(new InfoLine(treasureUnlocked(ritualLevel) ? KEY_TREASURE_ON : KEY_TREASURE_OFF,
                new String[0], "", ACCENT, -1F, null));
        lines.add(new InfoLine(specialUnlocked(ritualLevel) ? KEY_SPECIAL_ON : KEY_SPECIAL_OFF,
                new String[0], "", ACCENT, -1F, null));
        return lines;
    }

    // ---- 调试 ----

    /** 世界无关 + 世界读的单行摘要（供 /gs_debug watatsumi 探针）。 */
    public static String debugSummary(ServerLevel level, RitualMatch match) {
        int ritualLevel = match.level();
        List<ToolSacrificeBehavior.PedestalTool> rods = scanRods(level, match);
        WatatsumiSpecialLoot special = WatatsumiSpecialLootLoader.table();
        double total = special.totalWeight();
        StringBuilder pool = new StringBuilder();
        for (RitualLootTable.Weighted w : special.entries()) {
            double pct = total > 0.0D ? w.weight() / total * 100.0D : 0.0D;
            pool.append(BuiltInRegistries.ITEM.getKey(w.item())).append('=')
                    .append(String.format(Locale.ROOT, "%.2f", w.weight())).append('~')
                    .append(String.format(Locale.ROOT, "%.2f", pct)).append("% ");
        }
        return "level=" + ritualLevel
                + " ped=" + RitualPedestals.positions(match).size()
                + " rods=" + rods.size()
                + " fishing=" + fishingCount(ritualLevel)
                + " special=" + specialCount(ritualLevel)
                + " treasure=" + treasureUnlocked(ritualLevel)
                + " specialUnlocked=" + specialUnlocked(ritualLevel)
                + " cost=" + spiritCost(ritualLevel)
                + " bonusChance=" + GensokyouConfig.WATATSUMI_BONUS_CHANCE.get()
                + " specialPool={" + pool.toString().trim() + "}";
    }
}
